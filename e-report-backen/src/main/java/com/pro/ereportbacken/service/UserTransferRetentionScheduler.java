package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.repository.UserTransferJobRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Log4j2
public class UserTransferRetentionScheduler {

    private final UserTransferJobRepository jobRepository;
    private final FileTransferService fileTransferService;
    private final UserTransferRetentionService retentionService;

    public UserTransferRetentionScheduler(UserTransferJobRepository jobRepository,
                                          FileTransferService fileTransferService,
                                          UserTransferRetentionService retentionService) {
        this.jobRepository = jobRepository;
        this.fileTransferService = fileTransferService;
        this.retentionService = retentionService;
    }

    @Scheduled(
            initialDelayString = "${app.user-transfer.retention-initial-delay-ms:300000}",
            fixedDelayString = "${app.user-transfer.retention-interval-ms:900000}"
    )
    public void cleanupExpiredJobs() {
        LocalDateTime now = UtcTime.now();
        jobRepository.findTop100ByExpiresAtBeforeOrderByExpiresAtAsc(now).forEach(job -> {
            try {
                FileTransferService.ExpiredFile expiredFile = job.getInputFileId() == null ? null
                        : fileTransferService.expireClaimedUserImport(
                        job.getTenantId(), job.getInputFileId(), now);
                fileTransferService.deleteExpiredFiles(expiredFile);
                retentionService.deleteExpiredJob(job.getTenantId(), job.getId(), now);
            } catch (RuntimeException exception) {
                log.warn("过期用户传输任务清理失败，稍后重试。jobId={}", job.getId(), exception);
            }
        });
    }

}
