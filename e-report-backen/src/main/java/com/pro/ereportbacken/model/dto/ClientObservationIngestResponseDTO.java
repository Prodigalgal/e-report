package com.pro.ereportbacken.model.dto;

public record ClientObservationIngestResponseDTO(
        int acceptedCount,
        int duplicateCount,
        int ignoredCount,
        boolean disabled
) {
}
