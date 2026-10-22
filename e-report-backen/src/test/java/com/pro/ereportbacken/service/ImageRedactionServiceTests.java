package com.pro.ereportbacken.service;

import com.pro.ereportbacken.TestIds;
import com.pro.ereportbacken.model.pojo.FileTransferUploadSession;
import com.pro.ereportbacken.model.pojo.ImageRedactionJob;
import com.pro.ereportbacken.model.pojo.ImageRedactionStatus;
import com.pro.ereportbacken.repository.ImageRedactionJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageRedactionServiceTests {

    private ImageRedactionJobRepository repository;
    private AppConfigService appConfigService;
    private ImageRedactionService service;
    private FileTransferUploadSession session;

    @BeforeEach
    void setUp() {
        repository = mock(ImageRedactionJobRepository.class);
        appConfigService = mock(AppConfigService.class);
        when(appConfigService.getTenantConfigValueAsBoolean(any(), any(), anyBoolean())).thenReturn(true);
        service = new ImageRedactionService(repository, appConfigService, 3);
        session = new FileTransferUploadSession();
        session.setId(TestIds.uuid(900));
        session.setTenantId(TestIds.uuid(901));
        session.setOriginalFileName("现场照片.heic");
        session.setStoredFileName("_quarantine/source.heic");
        session.setContentType("image/heic");
        session.setTotalSize(1024L);
        session.setActualSha256("source-sha");
    }

    @Test
    void enqueuesExactlyOneDurableJob() {
        when(repository.findByTenantIdAndUploadSessionId(session.getTenantId(), session.getId()))
                .thenReturn(Optional.empty());
        when(repository.save(any(ImageRedactionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ImageRedactionJob job = service.enqueue(session);

        assertThat(job.getStatus()).isEqualTo(ImageRedactionStatus.QUEUED);
        assertThat(job.getSourceStoredFileName()).isEqualTo("_quarantine/source.heic");
        assertThat(job.getAvailableAt()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(job.getMaxAttempts()).isEqualTo(3);
        verify(repository).save(job);
    }

    @Test
    void defersNewJobWhenSubmissionSwitchIsOff() {
        when(appConfigService.getTenantConfigValueAsBoolean(any(), any(), anyBoolean())).thenReturn(false);
        when(repository.findByTenantIdAndUploadSessionId(session.getTenantId(), session.getId()))
                .thenReturn(Optional.empty());
        when(repository.save(any(ImageRedactionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ImageRedactionJob job = service.enqueue(session);

        assertThat(job.getStatus()).isEqualTo(ImageRedactionStatus.DEFERRED);
        verify(repository).save(job);
    }

    @Test
    void leavesAlreadyQueuedJobUntouchedWhenSubmissionSwitchIsOff() {
        ImageRedactionJob queued = job(ImageRedactionStatus.QUEUED);
        when(appConfigService.getTenantConfigValueAsBoolean(any(), any(), anyBoolean())).thenReturn(false);
        when(repository.findByTenantIdAndUploadSessionId(session.getTenantId(), session.getId()))
                .thenReturn(Optional.of(queued));

        ImageRedactionJob result = service.enqueue(session);

        assertThat(result).isSameAs(queued);
        assertThat(result.getStatus()).isEqualTo(ImageRedactionStatus.QUEUED);
        verify(repository, org.mockito.Mockito.never()).save(queued);
    }

    @Test
    void releasesOnlyDeferredJobsAfterSubmissionSwitchIsReenabled() {
        ImageRedactionJob deferred = job(ImageRedactionStatus.DEFERRED);
        when(appConfigService.getTenantConfigValueAsBoolean(any(), any(), anyBoolean())).thenReturn(true);
        when(repository.findByTenantIdAndStatusOrderByCreatedAtAsc(
                eq(session.getTenantId()),
                eq(ImageRedactionStatus.DEFERRED),
                any(Pageable.class)
        )).thenReturn(java.util.List.of(deferred));

        int released = service.releaseDeferred(session.getTenantId(), 50);

        assertThat(released).isEqualTo(1);
        assertThat(deferred.getStatus()).isEqualTo(ImageRedactionStatus.QUEUED);
        verify(repository).saveAll(java.util.List.of(deferred));
    }

    @Test
    void rejectsUnlinkedImageSessionInsteadOfReturningTheSourceFile() {
        when(repository.findByTenantIdAndUploadSessionId(session.getTenantId(), session.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.claimableFile(session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("图片安全处理任务尚未提交，请稍后重试。");
    }

    @Test
    void returnsOnlyReadySanitizedDerivative() {
        ImageRedactionJob job = job(ImageRedactionStatus.READY);
        job.setOutputOriginalFileName("现场照片-已脱敏.jpg");
        job.setOutputStoredFileName("_sanitized/output.jpg");
        job.setOutputContentType("image/jpeg");
        job.setOutputFileSize(512L);
        job.setOutputSha256("safe-sha");
        when(repository.findByTenantIdAndUploadSessionId(session.getTenantId(), session.getId()))
                .thenReturn(Optional.of(job));

        ImageRedactionService.SanitizedFile safeFile = service.claimableFile(session);

        assertThat(safeFile.originalFileName()).isEqualTo("现场照片-已脱敏.jpg");
        assertThat(safeFile.storedFileName()).isEqualTo("_sanitized/output.jpg");
        assertThat(safeFile.sha256()).isEqualTo("safe-sha");
    }

    @Test
    void allowsWorkOrderBindingWhileQueuedAndKeepsRawImageHidden() {
        when(repository.findByTenantIdAndUploadSessionId(session.getTenantId(), session.getId()))
                .thenReturn(Optional.of(job(ImageRedactionStatus.PROCESSING)));
        ImageRedactionService.SanitizedFile processing = service.claimableFile(session);
        assertThat(processing.redactionStatus()).isEqualTo(ImageRedactionStatus.PROCESSING);
        assertThat(processing.storedFileName()).startsWith("_sanitized/");
        assertThat(processing.storedFileName()).doesNotContain("source.heic");

        ImageRedactionJob failed = job(ImageRedactionStatus.FAILED);
        failed.setErrorMessage("图片安全处理失败，请移除后重新上传。");
        when(repository.findByTenantIdAndUploadSessionId(session.getTenantId(), session.getId()))
                .thenReturn(Optional.of(failed));
        ImageRedactionService.SanitizedFile failedFile = service.claimableFile(session);
        assertThat(failedFile.redactionStatus()).isEqualTo(ImageRedactionStatus.FAILED);
        assertThat(failedFile.redactionMessage()).contains("安全处理失败");
    }

    private ImageRedactionJob job(ImageRedactionStatus status) {
        ImageRedactionJob job = new ImageRedactionJob();
        job.setId(TestIds.uuid(902 + status.ordinal()));
        job.setTenantId(session.getTenantId());
        job.setUploadSessionId(session.getId());
        job.setStatus(status);
        job.setSourceStoredFileName(session.getStoredFileName());
        job.setSourceContentType(session.getContentType());
        job.setSourceFileSize(session.getTotalSize());
        job.setPolicyVersion(ImageRedactionService.DEFAULT_POLICY_VERSION);
        job.setAvailableAt(LocalDateTime.now());
        return job;
    }
}
