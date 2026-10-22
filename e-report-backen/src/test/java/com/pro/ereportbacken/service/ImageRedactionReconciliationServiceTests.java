package com.pro.ereportbacken.service;

import com.pro.ereportbacken.TestIds;
import com.pro.ereportbacken.model.pojo.Attachment;
import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.FileTransferStatus;
import com.pro.ereportbacken.model.pojo.FileTransferUploadSession;
import com.pro.ereportbacken.model.pojo.ImageRedactionJob;
import com.pro.ereportbacken.model.pojo.ImageRedactionStatus;
import com.pro.ereportbacken.repository.AttachmentRepository;
import com.pro.ereportbacken.repository.FileTransferUploadSessionRepository;
import com.pro.ereportbacken.repository.ImageRedactionJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageRedactionReconciliationServiceTests {

    @Test
    void linksHistoricalAttachmentToAnExistingOrNewIdempotentJob() {
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        FileTransferUploadSessionRepository sessionRepository = mock(FileTransferUploadSessionRepository.class);
        ImageRedactionJobRepository jobRepository = mock(ImageRedactionJobRepository.class);
        AppConfigService appConfigService = mock(AppConfigService.class);
        when(appConfigService.getTenantConfigValueAsBoolean(any(), any(), anyBoolean())).thenReturn(true);
        ImageRedactionService imageRedactionService = new ImageRedactionService(jobRepository, appConfigService, 3);
        ImageRedactionReconciliationService service = new ImageRedactionReconciliationService(
                attachmentRepository, sessionRepository, imageRedactionService);
        UUID tenantId = TestIds.uuid(950);
        UUID uploadId = TestIds.uuid(951);
        UUID jobId = TestIds.uuid(952);
        Attachment attachment = attachment(tenantId, "sample.jpg");
        FileTransferUploadSession session = session(tenantId, uploadId, "sample.jpg");
        when(attachmentRepository.findUnprocessedImageAttachments(
                eq(tenantId), eq("image/%"), eq("!_sanitized/%"), eq(null), any(Pageable.class)))
                .thenReturn(List.of(attachment));
        when(sessionRepository.findFirstByTenantIdAndPurposeAndStoredFileNameOrderByCreatedAtDesc(
                tenantId, FileTransferPurpose.WORK_ORDER_ATTACHMENT, "sample.jpg"))
                .thenReturn(Optional.of(session));
        when(jobRepository.findByTenantIdAndUploadSessionId(tenantId, uploadId))
                .thenReturn(Optional.empty());
        when(jobRepository.save(any(ImageRedactionJob.class))).thenAnswer(invocation -> {
            ImageRedactionJob job = invocation.getArgument(0);
            job.setId(jobId);
            return job;
        });

        ImageRedactionReconciliationService.ReconciliationBatch result = service.reconcileTenant(tenantId, 50, null);

        assertThat(result.candidates()).isEqualTo(1);
        assertThat(result.linked()).isEqualTo(1);
        assertThat(attachment.getImageRedactionJobId()).isEqualTo(jobId);
        verify(attachmentRepository).save(attachment);
    }

    @Test
    void leavesUnmatchableAttachmentUnchangedAndReportsItForDiagnosis() {
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        FileTransferUploadSessionRepository sessionRepository = mock(FileTransferUploadSessionRepository.class);
        AppConfigService appConfigService = mock(AppConfigService.class);
        when(appConfigService.getTenantConfigValueAsBoolean(any(), any(), anyBoolean())).thenReturn(true);
        ImageRedactionService imageRedactionService = new ImageRedactionService(
                mock(ImageRedactionJobRepository.class), appConfigService, 3);
        ImageRedactionReconciliationService service = new ImageRedactionReconciliationService(
                attachmentRepository, sessionRepository, imageRedactionService);
        UUID tenantId = TestIds.uuid(953);
        Attachment attachment = attachment(tenantId, "missing-session.jpg");
        when(attachmentRepository.findUnprocessedImageAttachments(
                eq(tenantId), eq("image/%"), eq("!_sanitized/%"), eq(null), any(Pageable.class)))
                .thenReturn(List.of(attachment));
        when(sessionRepository.findFirstByTenantIdAndPurposeAndStoredFileNameOrderByCreatedAtDesc(
                tenantId, FileTransferPurpose.WORK_ORDER_ATTACHMENT, "missing-session.jpg"))
                .thenReturn(Optional.empty());

        ImageRedactionReconciliationService.ReconciliationBatch result = service.reconcileTenant(tenantId, 50, null);

        assertThat(result.missingSession()).isEqualTo(1);
        assertThat(attachment.getImageRedactionJobId()).isNull();
        verify(attachmentRepository, never()).save(any(Attachment.class));
    }

    private Attachment attachment(UUID tenantId, String storedFileName) {
        Attachment attachment = new Attachment();
        attachment.setId(UUID.randomUUID());
        attachment.setTenantId(tenantId);
        attachment.setStoredFileName(storedFileName);
        attachment.setFileType("image/jpeg");
        return attachment;
    }

    private FileTransferUploadSession session(UUID tenantId, UUID uploadId, String storedFileName) {
        FileTransferUploadSession session = new FileTransferUploadSession();
        session.setId(uploadId);
        session.setTenantId(tenantId);
        session.setPurpose(FileTransferPurpose.WORK_ORDER_ATTACHMENT);
        session.setStatus(FileTransferStatus.CLAIMED);
        session.setStoredFileName(storedFileName);
        session.setOriginalFileName(storedFileName);
        session.setContentType("image/jpeg");
        session.setTotalSize(1024L);
        session.setActualSha256("source-sha");
        session.setCreatedAt(LocalDateTime.now());
        return session;
    }
}
