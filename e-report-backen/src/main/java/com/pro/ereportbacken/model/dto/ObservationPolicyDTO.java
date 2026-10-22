package com.pro.ereportbacken.model.dto;

public record ObservationPolicyDTO(
        boolean enabled,
        boolean warningEnabled,
        boolean operationEnabled,
        int operationSuccessSampleRatePercent,
        int batchSize,
        int flushIntervalSeconds
) {
}
