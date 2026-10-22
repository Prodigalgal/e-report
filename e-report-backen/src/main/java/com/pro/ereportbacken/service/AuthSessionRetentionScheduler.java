package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.pojo.AuthSession;
import com.pro.ereportbacken.model.pojo.AuthSessionStatus;
import com.pro.ereportbacken.repository.AuthSessionRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Log4j2
@ConditionalOnProperty(name = "app.auth-session.retention-enabled", havingValue = "true", matchIfMissing = true)
public class AuthSessionRetentionScheduler {

    private static final int MAX_RETENTION_BATCHES = 20;
    private static final int RETENTION_DAYS = 90;

    private final AuthSessionRepository repository;
    private final RedisDistributedLockService lockService;
    private final TransactionTemplate transactionTemplate;

    public AuthSessionRetentionScheduler(AuthSessionRepository repository,
                                         RedisDistributedLockService lockService,
                                         TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.lockService = lockService;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(cron = "${app.auth-session.retention-cron:0 35 3 * * *}", zone = "UTC")
    public void expireAndCleanSessions() {
        lockService.runWithLock(
                "auth-session:retention",
                Duration.ofMinutes(30),
                () -> transactionTemplate.executeWithoutResult(status -> expireAndClean())
        );
    }

    private void expireAndClean() {
        LocalDateTime now = UtcTime.now();
        int expired = repository.expireActiveSessions(now, "会话已超时");
        LocalDateTime retentionCutoff = now.minusDays(RETENTION_DAYS);
        int deleted = 0;
        for (int batch = 0; batch < MAX_RETENTION_BATCHES; batch++) {
            List<AuthSession> candidates = repository
                    .findTop500ByStatusNotAndRevokedAtBeforeOrderByRevokedAtAsc(
                            AuthSessionStatus.ACTIVE, retentionCutoff);
            if (candidates.isEmpty()) {
                break;
            }
            repository.deleteAllByIdInBatch(candidates.stream().map(AuthSession::getId).toList());
            deleted += candidates.size();
            if (candidates.size() < 500) {
                break;
            }
        }
        if (expired > 0 || deleted > 0) {
            log.info("认证会话保留任务完成。expired={}, deleted={}, retentionDays={}",
                    expired, deleted, RETENTION_DAYS);
        }
    }
}
