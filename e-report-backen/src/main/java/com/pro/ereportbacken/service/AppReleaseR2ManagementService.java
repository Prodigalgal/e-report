package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import java.util.UUID;

import com.pro.ereportbacken.model.dto.AppReleaseR2ItemDTO;
import com.pro.ereportbacken.model.dto.AppReleaseR2OverviewDTO;
import com.pro.ereportbacken.model.pojo.AppPlatform;
import com.pro.ereportbacken.model.pojo.AppEdition;
import com.pro.ereportbacken.model.pojo.AppRelease;
import com.pro.ereportbacken.repository.AppReleaseRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Log4j2
public class AppReleaseR2ManagementService {

    private final AppReleaseRepository appReleaseRepository;
    private final AppReleaseStorageService appReleaseStorageService;
    private final CloudflareR2AppReleaseService r2Service;
    private final TaskExecutor taskExecutor;
    private final AppReleaseSyncOperationStore syncOperationStore;

    public AppReleaseR2ManagementService(AppReleaseRepository appReleaseRepository,
                                         AppReleaseStorageService appReleaseStorageService,
                                         CloudflareR2AppReleaseService r2Service,
                                         @Qualifier("appReleaseR2TaskExecutor") TaskExecutor taskExecutor,
                                         AppReleaseSyncOperationStore syncOperationStore) {
        this.appReleaseRepository = appReleaseRepository;
        this.appReleaseStorageService = appReleaseStorageService;
        this.r2Service = r2Service;
        this.taskExecutor = taskExecutor;
        this.syncOperationStore = syncOperationStore;
    }

    public AppReleaseR2OverviewDTO overview(AppEdition edition) {
        AppEdition selectedEdition = AppEdition.orNative(edition);
        List<AppRelease> releases = appReleaseRepository.findByEdition(selectedEdition,
                Sort.by(Sort.Order.desc("versionCode"), Sort.Order.desc("publishedAt"))
        );
        List<CloudflareR2AppReleaseService.R2Object> objects = r2Service.isEnabled()
                ? r2Service.listAndroidObjects().stream().filter(object -> AppReleaseObjectKey.parse(r2Service.relativeObjectKey(object.key()))
                    .map(identity -> identity.edition() == selectedEdition).orElse(false)).toList()
                : List.of();
        Map<String, CloudflareR2AppReleaseService.R2Object> objectByKey = new HashMap<>();
        objects.forEach(object -> objectByKey.put(object.key(), object));

        Set<UUID> currentReleaseIds = currentReleaseIds(releases);
        Set<String> registeredKeys = new HashSet<>();
        List<AppReleaseR2ItemDTO> items = new ArrayList<>();
        int synchronizedCount = 0;
        int missingCount = 0;
        int mismatchCount = 0;
        Map<UUID, AppReleaseSyncOperationStore.SyncOperation> syncOperations = syncOperationStore.findAll(
                releases.stream().map(AppRelease::getId).toList()
        );

        for (AppRelease release : releases) {
            String objectKey = r2Service.objectKey(release);
            registeredKeys.add(objectKey);
            CloudflareR2AppReleaseService.R2Object object = objectByKey.get(objectKey);
            String status;
            if (!r2Service.isEnabled()) {
                status = "DISABLED";
            } else if (!Boolean.TRUE.equals(release.getPublished())) {
                status = "PENDING";
            } else if (object == null) {
                status = "MISSING";
                missingCount++;
            } else if (release.getFileSize() != null && release.getFileSize() != object.size()) {
                status = "SIZE_MISMATCH";
                mismatchCount++;
            } else {
                status = "SYNCED";
                synchronizedCount++;
            }
            AppReleaseSyncOperationStore.SyncOperation operation = syncOperations.get(release.getId());
            if (operation != null && ("SYNCING".equals(operation.status()) || !"SYNCED".equals(status))) {
                status = operation.status();
            }
            items.add(toRegisteredItem(
                    release,
                    objectKey,
                    object,
                    status,
                    currentReleaseIds.contains(release.getId()),
                    operation == null ? "" : operation.message()
            ));
        }

        int orphanCount = 0;
        for (CloudflareR2AppReleaseService.R2Object object : objects) {
            if (!registeredKeys.contains(object.key())) {
                orphanCount++;
                items.add(toOrphanItem(object));
            }
        }

        return new AppReleaseR2OverviewDTO(
                r2Service.isEnabled(),
                r2Service.bucketName(),
                r2Service.publicBaseUrl(),
                objects.size(),
                releases.size(),
                synchronizedCount,
                missingCount,
                mismatchCount,
                orphanCount,
                List.copyOf(items)
        );
    }

    public void synchronizeRelease(UUID releaseId) {
        requireEnabledForPublication();
        AppRelease release = appReleaseRepository.findById(releaseId)
                .orElseThrow(() -> new EntityNotFoundException("安装包版本不存在。"));
        java.nio.file.Path apkPath;
        try {
            apkPath = appReleaseStorageService.storedFilePath(release.getStoredFileName());
        } catch (FileNotFoundException ex) {
            throw new IllegalStateException("业务服务器上的安装包源文件不存在，无法同步到对象存储。", ex);
        }
        if (!syncOperationStore.tryStart(releaseId, "正在上传到对象存储，请稍候。")) {
            return;
        }
        try {
            taskExecutor.execute(() -> executeSynchronization(release, apkPath));
        } catch (RuntimeException ex) {
            syncOperationStore.clear(releaseId);
            throw new IllegalStateException("对象存储同步任务暂时无法提交，请稍后重试。", ex);
        }
    }

    public void requireEnabledForPublication() {
        if (!r2Service.isEnabled()) {
            throw new IllegalStateException("当前运行环境未配置安装包对象存储，不能上传或发布新版本。");
        }
    }

    public void deleteObject(String objectKey) {
        r2Service.validateAndroidObjectKey(objectKey);
        AppReleaseObjectKey.Identity registeredObject = parseRegisteredObject(objectKey);
        if (registeredObject != null) {
            appReleaseRepository.findByEditionAndPlatformAndChannelAndVersionCode(
                    registeredObject.edition(),
                    AppPlatform.ANDROID,
                    registeredObject.channel(),
                    registeredObject.versionCode()
            ).ifPresent(release -> {
                boolean current = appReleaseRepository
                        .findFirstByEditionAndPlatformAndChannelAndPublishedTrueOrderByVersionCodeDescPublishedAtDesc(
                                release.getEdition(),
                                release.getPlatform(),
                                release.getChannel()
                        )
                        .map(latest -> latest.getId().equals(release.getId()))
                        .orElse(false);
                if (current) {
                    throw new IllegalArgumentException("不能删除当前稳定版的对象存储安装包，请先发布更高版本。");
                }
            });
        }
        r2Service.deleteObject(objectKey);
    }

    private Set<UUID> currentReleaseIds(List<AppRelease> releases) {
        Map<String, UUID> currentByChannel = new HashMap<>();
        for (AppRelease release : releases) {
            if (!Boolean.TRUE.equals(release.getPublished())) {
                continue;
            }
            String key = release.getEdition().name() + "\u0000" + release.getPlatform().name() + "\u0000" + release.getChannel();
            currentByChannel.putIfAbsent(key, release.getId());
        }
        return Set.copyOf(currentByChannel.values());
    }

    private AppReleaseR2ItemDTO toRegisteredItem(
            AppRelease release,
            String objectKey,
            CloudflareR2AppReleaseService.R2Object object,
            String status,
            boolean current,
            String operationMessage) {
        return new AppReleaseR2ItemDTO(
                objectKey,
                status,
                release.getId(),
                release.getPlatform(),
                release.getChannel(),
                release.getVersionCode(),
                release.getVersionName(),
                release.getRuntimeVersion(),
                release.getMinSupportedVersionCode(),
                release.getForceUpdate(),
                release.getReleaseNotes(),
                release.getUpdatedAt(),
                release.getFileSize(),
                object == null ? null : object.size(),
                release.getSha256(),
                object == null ? null : object.etag(),
                object == null ? null : object.lastModified(),
                r2Service.isEnabled() ? r2Service.publicUrl(objectKey) : "",
                Boolean.TRUE.equals(release.getPublished()),
                current,
                operationMessage,
                release.getEdition()
        );
    }

    private AppReleaseR2ItemDTO toOrphanItem(CloudflareR2AppReleaseService.R2Object object) {
        AppReleaseObjectKey.Identity parsed = parseRegisteredObject(object.key());
        return new AppReleaseR2ItemDTO(
                object.key(),
                "ORPHAN",
                null,
                AppPlatform.ANDROID,
                parsed == null ? null : parsed.channel(),
                parsed == null ? null : parsed.versionCode(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                object.size(),
                null,
                object.etag(),
                object.lastModified(),
                r2Service.publicUrl(object.key()),
                false,
                false,
                "",
                parsed == null ? null : parsed.edition()
        );
    }

    private void executeSynchronization(AppRelease release, java.nio.file.Path apkPath) {
        try {
            r2Service.upload(release, apkPath);
            appReleaseRepository.markPublishedAfterStorageSync(release.getId(), UtcTime.now());
            syncOperationStore.clear(release.getId());
        } catch (RuntimeException ex) {
            log.error("R2 app release synchronization failed. releaseId={}, versionCode={}",
                    release.getId(), release.getVersionCode(), ex);
            syncOperationStore.markFailed(release.getId(), "同步失败，请稍后重试。错误已记录供管理员排查。");
        }
    }

    private AppReleaseObjectKey.Identity parseRegisteredObject(String objectKey) {
        return AppReleaseObjectKey.parse(r2Service.relativeObjectKey(objectKey)).orElse(null);
    }

}

