package com.pro.ereportbacken.service;

import com.pro.ereportbacken.repository.UserTransferJobRepository;
import com.pro.ereportbacken.repository.UserTransferJobRowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UserTransferRetentionService {

    private final UserTransferJobRepository jobRepository;
    private final UserTransferJobRowRepository rowRepository;

    public UserTransferRetentionService(UserTransferJobRepository jobRepository,
                                        UserTransferJobRowRepository rowRepository) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
    }

    @Transactional
    public boolean deleteExpiredJob(UUID tenantId, UUID jobId, LocalDateTime cutoff) {
        var job = jobRepository.findByTenantIdAndIdForUpdate(tenantId, jobId).orElse(null);
        if (job == null || !job.getExpiresAt().isBefore(cutoff)) {
            return false;
        }
        rowRepository.deleteAllByTenantIdAndJobId(tenantId, jobId);
        jobRepository.delete(job);
        jobRepository.flush();
        return true;
    }
}
