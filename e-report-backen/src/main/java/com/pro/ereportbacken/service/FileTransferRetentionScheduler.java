package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.FileTransferStatus;
import com.pro.ereportbacken.model.pojo.FileTransferUploadSession;
import com.pro.ereportbacken.repository.FileTransferUploadSessionRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Log4j2
public class FileTransferRetentionScheduler {

    private static final List<FileTransferPurpose> TEMPORARY_FILE_PURPOSES = List.of(
            FileTransferPurpose.WORK_ORDER_ATTACHMENT,
            FileTransferPurpose.USER_IMPORT,
            FileTransferPurpose.USER_IMPORT_TEMPLATE,
            FileTransferPurpose.USER_IMPORT_ERROR,
            FileTransferPurpose.USER_EXPORT
    );

    private final FileTransferUploadSessionRepository uploadSessionRepository;
    private final FileTransferService fileTransferService;

    public FileTransferRetentionScheduler(FileTransferUploadSessionRepository uploadSessionRepository,
                                          FileTransferService fileTransferService) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.fileTransferService = fileTransferService;
    }

    @Scheduled(
            initialDelayString = "${file.transfer-retention.initial-delay-ms:300000}",
            fixedDelayString = "${file.transfer-retention.cleanup-interval-ms:900000}"
    )
    public void cleanupExpiredFiles() {
        LocalDateTime now = UtcTime.now();
        cleanup(
                uploadSessionRepository.findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        FileTransferStatus.INITIATED,
                        now
                ),
                now
        );
        cleanup(
                uploadSessionRepository.findTop100ByStatusAndPurposeInAndExpiresAtBeforeOrderByExpiresAtAsc(
                        FileTransferStatus.COMPLETED,
                        TEMPORARY_FILE_PURPOSES,
                        now
                ),
                now
        );
        cleanup(
                uploadSessionRepository.findTop100ByStatusAndPurposeInAndExpiresAtBeforeOrderByExpiresAtAsc(
                        FileTransferStatus.PROCESSING,
                        TEMPORARY_FILE_PURPOSES,
                        now
                ),
                now.minusHours(1)
        );
    }

    private void cleanup(List<FileTransferUploadSession> sessions,
                         LocalDateTime lockCutoff) {
        sessions.forEach(session -> {
            try {
                FileTransferService.ExpiredFile expiredFile = fileTransferService.expireSession(
                        session.getTenantId(),
                        session.getId(),
                        lockCutoff
                );
                fileTransferService.deleteExpiredFiles(expiredFile);
            } catch (RuntimeException exception) {
                log.warn("过期文件会话清理失败，稍后将重试。fileId={}, status={}",
                        session.getId(), session.getStatus(), exception);
            }
        });
    }
}
