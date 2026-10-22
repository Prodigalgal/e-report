package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.ExpoUpdateDTO;
import com.pro.ereportbacken.model.pojo.ExpoUpdate;
import com.pro.ereportbacken.repository.ExpoUpdateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Service
@Log4j2
public class ExpoUpdateService {
    private final ExpoUpdateConfiguration configuration;
    private final ExpoUpdateArchive archive;
    private final ExpoUpdateRepository repository;
    private final ExpoUpdateRegistry registry;
    private final CloudflareR2AppReleaseService storage;
    private final TaskExecutor executor;
    public ExpoUpdateService(ExpoUpdateConfiguration configuration, ExpoUpdateArchive archive,
            ExpoUpdateRepository repository, ExpoUpdateRegistry registry, CloudflareR2AppReleaseService storage,
            @Qualifier("appReleaseR2TaskExecutor") TaskExecutor executor) {
        this.configuration = configuration; this.archive = archive; this.repository = repository;
        this.registry = registry; this.storage = storage; this.executor = executor;
    }

    public ExpoUpdateDTO publish(MultipartFile file) {
        configuration.requireEnabled();
        if (!storage.isEnabled()) throw new IllegalStateException("OTA 需要配置 R2 资源分发。");
        ExpoUpdateArchive.Extracted extracted;
        try { extracted = archive.extract(file); }
        catch (IOException exception) { throw new IllegalArgumentException("无法读取 OTA 归档，请检查文件。", exception); }
        ExpoUpdateRegistry.Publication publication;
        try { publication = registry.stage(extracted.update()); }
        catch (RuntimeException exception) { closeArchive(extracted); throw exception; }
        if (!publication.claimed()) {
            closeArchive(extracted);
            return ExpoUpdateDTO.from(publication.update());
        }
        try { executor.execute(() -> distribute(extracted, publication.update())); }
        catch (RuntimeException exception) {
            registry.finish(publication.update().getId(), publication.update().getAttemptId(), false);
            closeArchive(extracted);
            throw new IllegalStateException("OTA 发布任务暂时无法提交，请稍后重试。", exception);
        }
        return ExpoUpdateDTO.from(publication.update());
    }

    private void distribute(ExpoUpdateArchive.Extracted extracted, ExpoUpdate update) {
        try {
            for (var asset : extracted.assets()) storage.uploadExpoAsset(update.getRuntimeVersion(), asset.sha256(), asset.contentType(), asset.file());
            registry.finish(update.getId(), update.getAttemptId(), true);
            log.info("Expo update published. id={}, environment={}, runtime={}, source={}", update.getId(), update.getEnvironment(), update.getRuntimeVersion(), update.getSourceSha());
        } catch (RuntimeException exception) {
            registry.finish(update.getId(), update.getAttemptId(), false);
            log.error("Expo update distribution failed. id={}", update.getId(), exception);
        } finally { closeArchive(extracted); }
    }

    private void closeArchive(ExpoUpdateArchive.Extracted extracted) {
        try { extracted.close(); }
        catch (IOException exception) { log.warn("Unable to remove OTA staging directory. id={}", extracted.update().getId(), exception); }
    }
    public Optional<ExpoUpdate> latest(String runtime) {
        ExpoUpdateArchive.requireRuntime(runtime);
        if (!configuration.enabled()) return Optional.empty();
        return repository.findFirstByEnvironmentAndRuntimeVersionAndStatusOrderByCreatedAtDescPublishedAtDesc(configuration.environment(), runtime, "PUBLISHED");
    }
    public Page<ExpoUpdateDTO> list(Pageable pageable) {
        return repository.findByEnvironment(configuration.environment(), pageable).map(ExpoUpdateDTO::from);
    }
    public ExpoUpdateDTO find(UUID id) {
        return repository.findById(id).filter(update -> update.getEnvironment().equals(configuration.environment()))
                .map(ExpoUpdateDTO::from).orElseThrow(() -> new EntityNotFoundException("OTA 发布记录不存在。"));
    }
}
