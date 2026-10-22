package com.pro.ereportbacken.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.pojo.Tenant;
import com.pro.ereportbacken.model.pojo.UserTransferJob;
import com.pro.ereportbacken.model.pojo.UserTransferJobStatus;
import com.pro.ereportbacken.model.pojo.UserTransferJobType;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.repository.UserTransferJobRepository;
import com.pro.ereportbacken.repository.UserTransferJobRowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserTransferJobStateServiceTests {

    private UserTransferJobRepository jobRepository;
    private FileTransferService fileTransferService;
    private TenantRepository tenantRepository;
    private UserTransferJobStateService service;

    @BeforeEach
    void setUp() {
        jobRepository = mock(UserTransferJobRepository.class);
        fileTransferService = mock(FileTransferService.class);
        tenantRepository = mock(TenantRepository.class);
        service = new UserTransferJobStateService(
                jobRepository, mock(UserTransferJobRowRepository.class), fileTransferService,
                tenantRepository, new ObjectMapper());
    }

    @Test
    void rejectsNewJobWhenTenantAlreadyHasTwoActiveJobs() {
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        when(jobRepository.findByTenantIdAndCreatedByMembershipIdAndIdempotencyKey(
                tenantId, membershipId, "request-1")).thenReturn(Optional.empty());
        when(tenantRepository.findByIdForUpdate(tenantId)).thenReturn(Optional.of(new Tenant()));
        when(jobRepository.countByTenantIdAndStatusIn(org.mockito.ArgumentMatchers.eq(tenantId), anyCollection()))
                .thenReturn(2L);

        assertThatThrownBy(() -> service.createExport(
                tenantId, membershipId, "administrator", "request-1", "hash", "{}",
                "snapshot", LocalDateTime.now().plusMinutes(10), 12))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已有较多批量任务");
        verify(jobRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(UserTransferJob.class));
    }

    @Test
    void leavesSecondTenantJobQueuedWhileAnotherJobIsProcessing() {
        UUID jobId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserTransferJob job = new UserTransferJob();
        job.setId(jobId);
        job.setTenantId(tenantId);
        job.setJobType(UserTransferJobType.EXPORT);
        job.setStatus(UserTransferJobStatus.QUEUED);
        when(jobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(tenantRepository.findByIdForUpdate(tenantId)).thenReturn(Optional.of(new Tenant()));
        when(jobRepository.countByTenantIdAndStatusIn(org.mockito.ArgumentMatchers.eq(tenantId), anyCollection()))
                .thenReturn(1L);

        assertThat(service.startExport(jobId)).isNull();
        assertThat(job.getStatus()).isEqualTo(UserTransferJobStatus.QUEUED);
        verify(jobRepository, never()).saveAndFlush(job);
    }
}
