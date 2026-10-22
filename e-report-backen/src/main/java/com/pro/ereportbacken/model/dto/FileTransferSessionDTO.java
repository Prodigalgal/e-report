package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.FileTransferStatus;
import com.pro.ereportbacken.model.pojo.ImageRedactionStatus;

import java.time.LocalDateTime;

public record FileTransferSessionDTO(
        UUID uploadId,
        FileTransferPurpose purpose,
        FileTransferStatus status,
        String fileName,
        String contentType,
        Long totalSize,
        Long chunkSize,
        Long receivedBytes,
        String sha256,
        String storedFileName,
        LocalDateTime expiresAt,
        ImageRedactionStatus imageRedactionStatus,
        String imageRedactionMessage
) {
}
