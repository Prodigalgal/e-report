package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.pojo.UserTransferJobStatus;
import com.pro.ereportbacken.model.pojo.UserTransferJobType;
import com.pro.ereportbacken.repository.UserTransferJobRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Log4j2
public class UserTransferRecoveryScheduler {

    private final UserTransferJobRepository jobRepository;
    private final UserTransferJobStateService stateService;
    private final UserTransferAsyncProcessor asyncProcessor;

    public UserTransferRecoveryScheduler(UserTransferJobRepository jobRepository,
                                         UserTransferJobStateService stateService,
                                         UserTransferAsyncProcessor asyncProcessor) {
        this.jobRepository = jobRepository;
        this.stateService = stateService;
        this.asyncProcessor = asyncProcessor;
    }

    @Scheduled(
            initialDelayString = "${app.user-transfer.recovery-initial-delay-ms:30000}",
            fixedDelayString = "${app.user-transfer.recovery-interval-ms:60000}"
    )
    public void recoverInterruptedJobs() {
        LocalDateTime cutoff = UtcTime.now().minusMinutes(5);
        jobRepository.findTop100ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                List.of(UserTransferJobStatus.VALIDATING, UserTransferJobStatus.EXECUTING), cutoff
        ).forEach(job -> {
            try {
                stateService.recoverInterrupted(job.getId());
                jobRepository.findById(job.getId()).ifPresent(recovered ->
                        schedule(recovered.getId(), recovered.getJobType(), recovered.getStatus()));
            } catch (RuntimeException exception) {
                log.warn("用户传输中断任务恢复失败，稍后重试。jobId={}", job.getId(), exception);
            }
        });
        jobRepository.findTop100ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                List.of(UserTransferJobStatus.UPLOADED, UserTransferJobStatus.QUEUED),
                UtcTime.now().minusSeconds(30)
        ).forEach(job -> schedule(job.getId(), job.getJobType(), job.getStatus()));
    }

    private void schedule(UUID jobId,
                          UserTransferJobType jobType,
                          UserTransferJobStatus status) {
        try {
            if (jobType == UserTransferJobType.EXPORT && status == UserTransferJobStatus.QUEUED) {
                asyncProcessor.export(jobId);
            } else if (jobType == UserTransferJobType.IMPORT && status == UserTransferJobStatus.UPLOADED) {
                asyncProcessor.validate(jobId);
            } else if (jobType == UserTransferJobType.IMPORT && status == UserTransferJobStatus.QUEUED) {
                asyncProcessor.execute(jobId);
            }
        } catch (org.springframework.core.task.TaskRejectedException exception) {
            log.warn("用户传输恢复队列已满，稍后重试。jobId={}", jobId);
        }
    }
}
