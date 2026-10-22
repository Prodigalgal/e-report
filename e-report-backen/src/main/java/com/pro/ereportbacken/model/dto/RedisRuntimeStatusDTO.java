package com.pro.ereportbacken.model.dto;

import java.time.Instant;

public record RedisRuntimeStatusDTO(
        boolean available,
        Instant collectedAt,
        String version,
        Long uptimeSeconds,
        Long connectedClients,
        Long usedMemoryBytes,
        Long maxMemoryBytes,
        String maxMemoryPolicy,
        Long totalKeys,
        Long keyspaceHits,
        Long keyspaceMisses,
        Double hitRate,
        boolean aofEnabled,
        String aofLastWriteStatus,
        Long lastSuccessfulSaveEpochSeconds,
        Long observationReadyBatches,
        Long observationProcessingBatches,
        Long observationDeadLetterBatches,
        String error
) {
}
