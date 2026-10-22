package com.pro.ereportbacken.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record FileTransferDownloadDTO(
        UUID fileId,
        String fileName,
        String contentType,
        long fileSize,
        long chunkSize,
        String sha256,
        String downloadUrl,
        LocalDateTime expiresAt
) {
}
