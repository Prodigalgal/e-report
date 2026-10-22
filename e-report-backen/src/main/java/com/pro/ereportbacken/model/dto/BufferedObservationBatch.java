package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.ObservationEventType;
import com.pro.ereportbacken.model.pojo.ObservationOutcome;
import com.pro.ereportbacken.model.pojo.ObservationPlatform;
import com.pro.ereportbacken.model.pojo.ObservationSeverity;
import com.pro.ereportbacken.model.pojo.ObservationSignalType;
import com.pro.ereportbacken.model.pojo.ObservationSpanKind;
import com.pro.ereportbacken.model.pojo.ObservationSpanStatusCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record BufferedObservationBatch(
        int schemaVersion,
        String messageId,
        List<Event> events
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public record Event(
            String clientEventId,
            UUID tenantId,
            UUID orgUnitId,
            UUID userId,
            ObservationPlatform platform,
            ObservationEventType eventType,
            ObservationSeverity severity,
            String category,
            String eventName,
            String message,
            String stackTrace,
            String route,
            String operationName,
            ObservationOutcome outcome,
            String traceId,
            String installationId,
            String sessionId,
            String appVersion,
            String appBuild,
            String osName,
            String osVersion,
            String manufacturer,
            String deviceModel,
            String userAgent,
            String networkType,
            String contextJson,
            String breadcrumbsJson,
            LocalDateTime clientOccurredAt,
            LocalDateTime receivedAt,
            int observationSchemaVersion,
            ObservationSignalType signalType,
            String requestId,
            String otelTraceId,
            String otelSpanId,
            String otelParentSpanId,
            Integer otelTraceFlags,
            Integer severityNumber,
            String severityText,
            String body,
            String scopeName,
            String scopeVersion,
            String resourceJson,
            String scopeAttributesJson,
            String attributesJson,
            String spanName,
            ObservationSpanKind spanKind,
            ObservationSpanStatusCode spanStatusCode,
            String spanStatusMessage,
            Long durationNanos,
            String spanEventsJson
    ) {
    }
}
