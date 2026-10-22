package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.UserTransferJobStatus;
import com.pro.ereportbacken.model.pojo.UserTransferJobType;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserTransferJobDTO(
        UUID id,
        UserTransferJobType jobType,
        UserTransferJobStatus status,
        UUID inputFileId,
        UUID resultFileId,
        UUID errorFileId,
        FileTransferDownloadDTO resultFile,
        FileTransferDownloadDTO errorFile,
        String inputSha256,
        String templateVersion,
        int totalRows,
        int validRows,
        int invalidRows,
        int processedRows,
        int succeededRows,
        int failedRows,
        String failureMessage,
        UserTransferPreviewSummaryDTO previewSummary,
        String previewToken,
        LocalDateTime previewExpiresAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
