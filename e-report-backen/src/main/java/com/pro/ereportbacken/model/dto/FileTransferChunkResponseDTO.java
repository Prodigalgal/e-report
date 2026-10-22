package com.pro.ereportbacken.model.dto;

import java.util.UUID;

public record FileTransferChunkResponseDTO(
        UUID uploadId,
        int chunkIndex,
        long receivedBytes,
        long totalSize,
        boolean readyToComplete
) {
}
