package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "client_observation_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_client_observation_event_id", columnNames = "client_event_id"),
        indexes = {
                @Index(name = "idx_observation_tenant_received", columnList = "tenant_id,received_at,id"),
                @Index(name = "idx_observation_org_received", columnList = "tenant_id,org_unit_id,received_at,id"),
                @Index(name = "idx_observation_severity_received", columnList = "tenant_id,severity,received_at"),
                @Index(name = "idx_observation_category_received", columnList = "tenant_id,category,received_at"),
                @Index(name = "idx_observation_behavior_user_received", columnList = "tenant_id,event_type,user_id,received_at,id")
        }
)
@Getter
@Setter
public class ClientObservationEvent extends UuidV7Entity {

    @Column(name = "client_event_id", nullable = false, length = 64, updatable = false)
    private String clientEventId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "org_unit_id", updatable = false)
    private UUID orgUnitId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private ObservationPlatform platform;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20, updatable = false)
    private ObservationEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private ObservationSeverity severity;

    @Column(nullable = false, length = 40, updatable = false)
    private String category;

    @Column(name = "event_name", nullable = false, length = 100, updatable = false)
    private String eventName;

    @Column(length = 2000, updatable = false)
    private String message;

    @Column(name = "stack_trace", columnDefinition = "TEXT", updatable = false)
    private String stackTrace;

    @Column(length = 255, updatable = false)
    private String route;

    @Column(name = "operation_name", length = 120, updatable = false)
    private String operationName;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, updatable = false)
    private ObservationOutcome outcome;

    @Column(name = "trace_id", length = 100, updatable = false)
    private String traceId;

    @Column(name = "installation_id", length = 64, updatable = false)
    private String installationId;

    @Column(name = "session_id", length = 64, updatable = false)
    private String sessionId;

    @Column(name = "app_version", length = 50, updatable = false)
    private String appVersion;

    @Column(name = "app_build", length = 50, updatable = false)
    private String appBuild;

    @Column(name = "os_name", length = 30, updatable = false)
    private String osName;

    @Column(name = "os_version", length = 50, updatable = false)
    private String osVersion;

    @Column(length = 100, updatable = false)
    private String manufacturer;

    @Column(name = "device_model", length = 100, updatable = false)
    private String deviceModel;

    @Column(name = "user_agent", length = 500, updatable = false)
    private String userAgent;

    @Column(name = "network_type", length = 50, updatable = false)
    private String networkType;

    @Column(name = "context_json", columnDefinition = "TEXT", updatable = false)
    private String contextJson;

    @Column(name = "breadcrumbs_json", columnDefinition = "TEXT", updatable = false)
    private String breadcrumbsJson;

    @Column(name = "client_occurred_at", updatable = false)
    private LocalDateTime clientOccurredAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "observation_schema_version", nullable = false, updatable = false)
    private int observationSchemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", length = 10, updatable = false)
    private ObservationSignalType signalType;

    @Column(name = "request_id", length = 100, updatable = false)
    private String requestId;

    @Column(name = "otel_trace_id", length = 32, updatable = false)
    private String otelTraceId;

    @Column(name = "otel_span_id", length = 16, updatable = false)
    private String otelSpanId;

    @Column(name = "otel_parent_span_id", length = 16, updatable = false)
    private String otelParentSpanId;

    @Column(name = "otel_trace_flags", updatable = false)
    private Integer otelTraceFlags;

    @Column(name = "severity_number", updatable = false)
    private Integer severityNumber;

    @Column(name = "severity_text", length = 20, updatable = false)
    private String severityText;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String body;

    @Column(name = "scope_name", length = 255, updatable = false)
    private String scopeName;

    @Column(name = "scope_version", length = 50, updatable = false)
    private String scopeVersion;

    @Column(name = "resource_json", columnDefinition = "TEXT", updatable = false)
    private String resourceJson;

    @Column(name = "scope_attributes_json", columnDefinition = "TEXT", updatable = false)
    private String scopeAttributesJson;

    @Column(name = "attributes_json", columnDefinition = "TEXT", updatable = false)
    private String attributesJson;

    @Column(name = "span_name", length = 255, updatable = false)
    private String spanName;

    @Enumerated(EnumType.STRING)
    @Column(name = "span_kind", length = 20, updatable = false)
    private ObservationSpanKind spanKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "span_status_code", length = 20, updatable = false)
    private ObservationSpanStatusCode spanStatusCode;

    @Column(name = "span_status_message", length = 255, updatable = false)
    private String spanStatusMessage;

    @Column(name = "duration_nanos", updatable = false)
    private Long durationNanos;

    @Column(name = "span_events_json", columnDefinition = "TEXT", updatable = false)
    private String spanEventsJson;
}
