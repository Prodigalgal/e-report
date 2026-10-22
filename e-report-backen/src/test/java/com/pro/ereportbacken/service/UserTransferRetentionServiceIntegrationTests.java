package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.UserTransferExecutionStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.model.pojo.UserTransferJob;
import com.pro.ereportbacken.model.pojo.UserTransferJobRow;
import com.pro.ereportbacken.model.pojo.UserTransferJobStatus;
import com.pro.ereportbacken.model.pojo.UserTransferJobType;
import com.pro.ereportbacken.model.pojo.UserTransferValidationStatus;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.repository.UserTransferJobRepository;
import com.pro.ereportbacken.repository.UserTransferJobRowRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UserTransferRetentionServiceIntegrationTests {

    @Autowired
    private UserTransferRetentionService retentionService;

    @Autowired
    private UserTransferJobRepository jobRepository;

    @Autowired
    private UserTransferJobRowRepository rowRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DefaultTenantService defaultTenantService;

    @Test
    @Transactional
    void deletesRowsBeforeExpiredJobAndKeepsUnexpiredJob() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        var operator = saveOperator(tenantId);
        LocalDateTime cutoff = LocalDateTime.now();
        UserTransferJob expired = saveJob(tenantId, operator.getId(), cutoff.minusMinutes(1));
        UserTransferJobRow row = saveRow(tenantId, expired.getId());
        UserTransferJob active = saveJob(tenantId, operator.getId(), cutoff.plusMinutes(30));

        assertThat(retentionService.deleteExpiredJob(tenantId, expired.getId(), cutoff)).isTrue();
        assertThat(jobRepository.findById(expired.getId())).isEmpty();
        assertThat(rowRepository.existsById(row.getId())).isFalse();

        assertThat(retentionService.deleteExpiredJob(tenantId, active.getId(), cutoff)).isFalse();
        assertThat(jobRepository.findById(active.getId())).isPresent();
    }

    private UserTransferJob saveJob(UUID tenantId, UUID operatorId, LocalDateTime expiresAt) {
        UserTransferJob job = new UserTransferJob();
        job.setTenantId(tenantId);
        job.setCreatedByMembershipId(operatorId);
        job.setCreatedByUsername("retention-test");
        job.setJobType(UserTransferJobType.IMPORT);
        job.setStatus(UserTransferJobStatus.COMPLETED);
        job.setExpiresAt(expiresAt);
        return jobRepository.saveAndFlush(job);
    }

    private User saveOperator(UUID tenantId) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setOrgUnitId(defaultTenantService.requireRootOrgUnit(tenantId).getId());
        user.setUsername("retention-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPassword("test-password-hash");
        user.setName("保留期测试用户");
        user.setPhone("139" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100_000_000));
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private UserTransferJobRow saveRow(UUID tenantId, UUID jobId) {
        UserTransferJobRow row = new UserTransferJobRow();
        row.setTenantId(tenantId);
        row.setJobId(jobId);
        row.setSheetName("用户资料");
        row.setRowNumber(4);
        row.setValidationStatus(UserTransferValidationStatus.VALID);
        row.setExecutionStatus(UserTransferExecutionStatus.SUCCEEDED);
        return rowRepository.saveAndFlush(row);
    }
}
