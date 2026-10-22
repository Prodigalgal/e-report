package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.ObservationEventType;
import com.pro.ereportbacken.model.pojo.ObservationOutcome;
import com.pro.ereportbacken.model.pojo.ObservationPlatform;
import com.pro.ereportbacken.model.pojo.ObservationSeverity;
import com.pro.ereportbacken.model.pojo.ObservationSignalType;

import java.time.Instant;
import java.util.UUID;

public record ObservationEventSummaryDTO(
        UUID id,
        ObservationPlatform platform,
        ObservationEventType eventType,
        ObservationSeverity severity,
        String category,
        String eventName,
        String message,
        String route,
        String operationName,
        ObservationOutcome outcome,
        String traceId,
        int observationSchemaVersion,
        ObservationSignalType signalType,
        String requestId,
        String otelTraceId,
        String otelSpanId,
        Integer severityNumber,
        String spanName,
        Long durationNanos,
        UUID orgUnitId,
        String orgUnitName,
        UUID userId,
        String userName,
        String appVersion,
        String osName,
        String osVersion,
        String manufacturer,
        String deviceModel,
        String networkType,
        Instant clientOccurredAt,
        Instant receivedAt
) {
}
