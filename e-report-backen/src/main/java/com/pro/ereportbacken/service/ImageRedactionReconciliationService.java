package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.Attachment;
import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.FileTransferStatus;
import com.pro.ereportbacken.model.pojo.ImageRedactionJob;
import com.pro.ereportbacken.model.pojo.ImageRedactionStatus;
import com.pro.ereportbacken.repository.AttachmentRepository;
import com.pro.ereportbacken.repository.FileTransferUploadSessionRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * Reconnects historical image attachments with the existing upload-session/job
 * chain. No files are read or written here; the worker remains responsible for
 * all image I/O and model inference.
 */
@Service
@Log4j2
public class ImageRedactionReconciliationService {

    private static final String IMAGE_TYPE_PATTERN = "image/%";
    private static final String SANITIZED_PATH_PATTERN = "!_sanitized/%";

    private final AttachmentRepository attachmentRepository;
    private final FileTransferUploadSessionRepository uploadSessionRepository;
    private final ImageRedactionService imageRedactionService;

    public ImageRedactionReconciliationService(
            AttachmentRepository attachmentRepository,
            FileTransferUploadSessionRepository uploadSessionRepository,
            ImageRedactionService imageRedactionService) {
        this.attachmentRepository = attachmentRepository;
        this.uploadSessionRepository = uploadSessionRepository;
        this.imageRedactionService = imageRedactionService;
    }

    @Transactional
    public ReconciliationBatch reconcileTenant(UUID tenantId, int batchSize, UUID afterAttachmentId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("巡检任务必须提供租户 ID。");
        }
        int normalizedBatchSize = Math.max(1, Math.min(batchSize, 500));

        List<Attachment> candidates = attachmentRepository.findUnprocessedImageAttachments(
                tenantId,
                IMAGE_TYPE_PATTERN,
                SANITIZED_PATH_PATTERN,
                afterAttachmentId,
                PageRequest.of(0, normalizedBatchSize)
        );
        UUID lastAttachmentId = candidates.isEmpty()
                ? afterAttachmentId
                : candidates.get(candidates.size() - 1).getId();
        int linked = 0;
        int missingSession = 0;
        int incompleteSession = 0;
        int cancelledJob = 0;
        for (Attachment attachment : candidates) {
            if (!StringUtils.hasText(attachment.getStoredFileName())) {
                missingSession++;
                continue;
            }
            var session = uploadSessionRepository
                    .findFirstByTenantIdAndPurposeAndStoredFileNameOrderByCreatedAtDesc(
                            tenantId,
                            FileTransferPurpose.WORK_ORDER_ATTACHMENT,
                            attachment.getStoredFileName())
                    .orElse(null);
            if (session == null) {
                missingSession++;
                continue;
            }
            if (!FileTransferStatus.COMPLETED.equals(session.getStatus())
                    && !FileTransferStatus.CLAIMED.equals(session.getStatus())) {
                incompleteSession++;
                continue;
            }

            ImageRedactionJob job = imageRedactionService.enqueue(session);
            if (ImageRedactionStatus.CANCELLED.equals(job.getStatus())) {
                cancelledJob++;
                continue;
            }
            attachment.setImageRedactionJobId(job.getId());
            attachmentRepository.save(attachment);
            linked++;
        }
        return new ReconciliationBatch(
                candidates.size(), linked, missingSession, incompleteSession, cancelledJob, lastAttachmentId);
    }

    public record ReconciliationBatch(
            int candidates,
            int linked,
            int missingSession,
            int incompleteSession,
            int cancelledJob,
            UUID lastAttachmentId) {

        public ReconciliationBatch(
                int candidates,
                int linked,
                int missingSession,
                int incompleteSession,
                int cancelledJob) {
            this(candidates, linked, missingSession, incompleteSession, cancelledJob, null);
        }
    }
}
