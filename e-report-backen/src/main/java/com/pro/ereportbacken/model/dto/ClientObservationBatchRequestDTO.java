package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.ObservationEventType;
import com.pro.ereportbacken.model.pojo.ObservationOutcome;
import com.pro.ereportbacken.model.pojo.ObservationPlatform;
import com.pro.ereportbacken.model.pojo.ObservationSeverity;
import com.pro.ereportbacken.model.pojo.ObservationSignalType;
import com.pro.ereportbacken.model.pojo.ObservationSpanKind;
import com.pro.ereportbacken.model.pojo.ObservationSpanStatusCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ClientObservationBatchRequestDTO(
        @Min(1) @Max(2) Integer schemaVersion,
        @NotNull ObservationPlatform platform,
        @Size(max = 36) @Pattern(regexp = "[0-9a-fA-F-]{36}") String installationId,
        @Size(max = 36) @Pattern(regexp = "[0-9a-fA-F-]{36}") String sessionId,
        @Valid Environment environment,
        @Valid Resource resource,
        @Valid InstrumentationScope instrumentationScope,
        @NotEmpty @Size(max = 50) List<@Valid Event> events
) {
    public record Environment(
            @Size(max = 50) String appVersion,
            @Size(max = 50) String appBuild,
            @Size(max = 30) String osName,
            @Size(max = 50) String osVersion,
            @Size(max = 100) String manufacturer,
            @Size(max = 100) String deviceModel,
            @Size(max = 500) String userAgent,
            @Size(max = 50) String networkType,
            Map<String, Object> attributes
    ) {
    }

    public record Resource(Map<String, Object> attributes) {
    }

    public record InstrumentationScope(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 50) String version,
            Map<String, Object> attributes
    ) {
    }

    public record Event(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._:-]+") String clientEventId,
            @NotNull ObservationEventType eventType,
            @NotNull ObservationSeverity severity,
            @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Z0-9_:-]+") String category,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Z0-9_.:-]+") String eventName,
            @Size(max = 2000) String message,
            @Size(max = 20000) String stackTrace,
            @Size(max = 255) String route,
            @Size(max = 120) String operationName,
            ObservationOutcome outcome,
            @Size(max = 100) String requestId,
            @Size(max = 100) String traceId,
            @Pattern(regexp = "[0-9a-f]{16}") String spanId,
            @Pattern(regexp = "[0-9a-f]{16}") String parentSpanId,
            @Min(0) @Max(255) Integer traceFlags,
            ObservationSignalType signalType,
            @Min(0) @Max(24) Integer severityNumber,
            @Size(max = 20) String severityText,
            @Size(max = 2000) String body,
            @Size(max = 255) String spanName,
            ObservationSpanKind spanKind,
            ObservationSpanStatusCode spanStatusCode,
            @Size(max = 255) String spanStatusMessage,
            @Min(0) Long durationNanos,
            Instant occurredAt,
            Map<String, Object> attributes,
            @Size(max = 30) List<@Valid SpanEvent> spanEvents,
            @Size(max = 30) List<@Valid Breadcrumb> breadcrumbs
    ) {
    }

    public record Breadcrumb(
            Instant occurredAt,
            @NotBlank @Size(max = 40) String category,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String message,
            Map<String, Object> attributes
    ) {
    }

    public record SpanEvent(
            Instant occurredAt,
            @NotBlank @Size(max = 100) String name,
            Map<String, Object> attributes
    ) {
    }
}
