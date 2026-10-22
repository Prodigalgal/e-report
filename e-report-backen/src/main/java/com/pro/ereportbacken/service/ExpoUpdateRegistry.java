package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.ExpoUpdate;
import com.pro.ereportbacken.repository.ExpoUpdateRepository;
import com.pro.ereportbacken.time.UtcTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Objects;
import java.util.UUID;

@Service
public class ExpoUpdateRegistry {
    private final ExpoUpdateRepository repository;
    private final TransactionTemplate transaction;
    public ExpoUpdateRegistry(ExpoUpdateRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository; this.transaction = new TransactionTemplate(transactionManager);
    }

    public Publication stage(ExpoUpdate candidate) {
        try { return stageInTransaction(candidate); }
        catch (DataIntegrityViolationException concurrentInsert) {
            if (!repository.existsById(candidate.getId())) throw concurrentInsert;
            return stageInTransaction(candidate);
        }
    }

    private Publication stageInTransaction(ExpoUpdate candidate) {
        return Objects.requireNonNull(transaction.execute(status -> {
            ExpoUpdate update = repository.findForPublication(candidate.getId()).orElse(candidate);
            if (!update.getSha256().equals(candidate.getSha256())
                    || !update.getEnvironment().equals(candidate.getEnvironment())
                    || !update.getRuntimeVersion().equals(candidate.getRuntimeVersion())) {
                throw new IllegalArgumentException("相同 OTA 标识已登记不同内容，禁止覆盖。");
            }
            if ("PUBLISHED".equals(update.getStatus()) || ("PUBLISHING".equals(update.getStatus())
                    && update.getLastAttemptAt().isAfter(UtcTime.now().minusMinutes(15)))) return new Publication(update, false);
            update.setStatus("PUBLISHING"); update.setFailureMessage(null); update.setPublishedAt(null);
            update.setAttemptId(UUID.randomUUID()); update.setLastAttemptAt(UtcTime.now());
            return new Publication(repository.saveAndFlush(update), true);
        }));
    }

    public void finish(UUID id, UUID attemptId, boolean success) {
        transaction.executeWithoutResult(status -> repository.finishPublication(id, attemptId,
                success ? "PUBLISHED" : "FAILED", success ? null : "资源分发失败，请重试发布同一归档。",
                success ? UtcTime.now() : null, UtcTime.now()));
    }

    public record Publication(ExpoUpdate update, boolean claimed) {}
}
