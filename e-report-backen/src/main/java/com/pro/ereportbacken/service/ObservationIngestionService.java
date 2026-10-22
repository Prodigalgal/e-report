package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.ClientObservationBatchRequestDTO;
import com.pro.ereportbacken.model.dto.ClientObservationIngestResponseDTO;
import com.pro.ereportbacken.model.dto.BufferedObservationBatch;
import com.pro.ereportbacken.model.pojo.ObservationEventType;
import com.pro.ereportbacken.model.pojo.ObservationPlatform;
import com.pro.ereportbacken.model.pojo.ObservationSeverity;
import com.pro.ereportbacken.model.pojo.ObservationOutcome;
import com.pro.ereportbacken.model.pojo.ObservationSignalType;
import com.pro.ereportbacken.model.pojo.ObservationSpanKind;
import com.pro.ereportbacken.model.pojo.ObservationSpanStatusCode;
import com.pro.ereportbacken.service.ObservationRequestIdentityResolver.ObservationIdentity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ObservationIngestionService {

    private static final int MAX_CLOCK_SKEW_SECONDS = 300;
    private static final int MAX_EVENT_AGE_DAYS = 365;
    private static final java.util.regex.Pattern TRACE_ID_PATTERN = java.util.regex.Pattern.compile("[0-9a-f]{32}");
    private static final java.util.regex.Pattern SPAN_ID_PATTERN = java.util.regex.Pattern.compile("[0-9a-f]{16}");

    private final ObservationEventBuffer eventBuffer;
    private final ObservationSettingsService settingsService;
    private final ObservationRequestIdentityResolver identityResolver;
    private final ObservationDataSanitizer sanitizer;

    public ObservationIngestionService(ObservationEventBuffer eventBuffer,
                                       ObservationSettingsService settingsService,
                                       ObservationRequestIdentityResolver identityResolver,
                                       ObservationDataSanitizer sanitizer) {
        this.eventBuffer = eventBuffer;
        this.settingsService = settingsService;
        this.identityResolver = identityResolver;
        this.sanitizer = sanitizer;
    }

    public ClientObservationIngestResponseDTO ingest(ClientObservationBatchRequestDTO request,
                                                     String observedUserAgent) {
        if (request.platform() == ObservationPlatform.SERVER) {
            throw new IllegalArgumentException("客户端不能上报 SERVER 平台事件。");
        }
        validateProtocol(request);
        ObservationSettingsService.Policy policy = settingsService.currentPolicy();
        if (!policy.enabled()) {
            return new ClientObservationIngestResponseDTO(0, 0, request.events().size(), true);
        }

        ObservationIdentity identity = identityResolver.resolve();
        Set<String> submittedIds = new HashSet<>();
        List<BufferedObservationBatch.Event> accepted = new ArrayList<>();
        int duplicateCount = 0;
        int ignoredCount = 0;
        LocalDateTime receivedAt = LocalDateTime.now(ZoneOffset.UTC);

        for (ClientObservationBatchRequestDTO.Event eventRequest : request.events()) {
            if (!submittedIds.add(eventRequest.clientEventId())) {
                duplicateCount++;
                continue;
            }
            if (shouldIgnore(eventRequest.eventType(), eventRequest.outcome(), policy)) {
                ignoredCount++;
                continue;
            }
            accepted.add(toBufferedEvent(request, eventRequest, identity, observedUserAgent, receivedAt));
        }
        if (!accepted.isEmpty()) {
            eventBuffer.enqueue(new BufferedObservationBatch(
                    BufferedObservationBatch.CURRENT_SCHEMA_VERSION,
                    java.util.UUID.randomUUID().toString(),
                    List.copyOf(accepted)
            ));
        }
        return new ClientObservationIngestResponseDTO(accepted.size(), duplicateCount, ignoredCount, false);
    }

    private boolean shouldIgnore(ObservationEventType eventType,
                                 ObservationOutcome outcome,
                                 ObservationSettingsService.Policy policy) {
        return (eventType == ObservationEventType.WARNING && !policy.warningEnabled())
                || (eventType == ObservationEventType.OPERATION && !policy.operationEnabled());
    }

    private BufferedObservationBatch.Event toBufferedEvent(ClientObservationBatchRequestDTO batch,
                                                           ClientObservationBatchRequestDTO.Event request,
                                                           ObservationIdentity identity,
                                                           String observedUserAgent,
                                                           LocalDateTime receivedAt) {
        ClientObservationBatchRequestDTO.Environment environment = batch.environment();
        int schemaVersion = batch.schemaVersion() == null ? 1 : batch.schemaVersion();
        CanonicalObservation canonical = canonical(batch, request, schemaVersion);
        String appVersion = null;
        String appBuild = null;
        String osName = null;
        String osVersion = null;
        String manufacturer = null;
        String deviceModel = null;
        String networkType = null;
        if (environment != null) {
            appVersion = sanitizer.text(environment.appVersion(), 50);
            appBuild = sanitizer.text(environment.appBuild(), 50);
            osName = sanitizer.text(environment.osName(), 30);
            osVersion = sanitizer.text(environment.osVersion(), 50);
            manufacturer = sanitizer.text(environment.manufacturer(), 100);
            deviceModel = sanitizer.text(environment.deviceModel(), 100);
            networkType = sanitizer.text(environment.networkType(), 50);
        }
        String userAgent = observedUserAgent == null && environment != null
                ? environment.userAgent()
                : observedUserAgent;
        return new BufferedObservationBatch.Event(
                request.clientEventId(), identity.tenantId(), identity.orgUnitId(), identity.userId(), batch.platform(),
                request.eventType(), normalizeSeverity(request.eventType(), request.severity(), request.outcome()), request.category(),
                request.eventName(), sanitizer.text(request.message(), 2000), sanitizer.text(request.stackTrace(), 16_000),
                sanitizer.route(request.route()), sanitizer.text(request.operationName(), 120), request.outcome(),
                sanitizer.text(canonical.correlationId(), 100), batch.installationId(), batch.sessionId(), appVersion, appBuild,
                osName, osVersion, manufacturer, deviceModel, sanitizer.text(userAgent, 500), networkType,
                sanitizer.json(context(environment, request), 16_000),
                sanitizer.json(breadcrumbs(request.breadcrumbs()), 24_000),
                normalizeOccurredAt(request.occurredAt(), receivedAt), receivedAt,
                schemaVersion, canonical.signalType(), canonical.requestId(), canonical.traceId(), canonical.spanId(),
                canonical.parentSpanId(), canonical.traceFlags(), canonical.severityNumber(), canonical.severityText(),
                canonical.body(), canonical.scopeName(), canonical.scopeVersion(), canonical.resourceJson(),
                canonical.scopeAttributesJson(), canonical.attributesJson(), canonical.spanName(), canonical.spanKind(),
                canonical.spanStatusCode(), canonical.spanStatusMessage(), canonical.durationNanos(), canonical.spanEventsJson()
        );
    }

    private void validateProtocol(ClientObservationBatchRequestDTO request) {
        int schemaVersion = request.schemaVersion() == null ? 1 : request.schemaVersion();
        if (schemaVersion < 2) {
            return;
        }
        if (request.resource() == null || request.resource().attributes() == null
                || request.resource().attributes().isEmpty()) {
            throw new IllegalArgumentException("OpenTelemetry Resource attributes 不能为空。");
        }
        if (request.instrumentationScope() == null) {
            throw new IllegalArgumentException("OpenTelemetry InstrumentationScope 不能为空。");
        }
    }

    private CanonicalObservation canonical(ClientObservationBatchRequestDTO batch,
                                             ClientObservationBatchRequestDTO.Event request,
                                             int schemaVersion) {
        ObservationSignalType signalType = request.signalType() != null
                ? request.signalType()
                : request.eventType() == ObservationEventType.OPERATION ? ObservationSignalType.SPAN : ObservationSignalType.LOG;
        String traceId = validIdentifier(request.traceId(), TRACE_ID_PATTERN, 32);
        String spanId = validIdentifier(request.spanId(), SPAN_ID_PATTERN, 16);
        String parentSpanId = validIdentifier(request.parentSpanId(), SPAN_ID_PATTERN, 16);
        if (schemaVersion >= 2) {
            requireValidWhenPresent("Trace ID", request.traceId(), traceId);
            requireValidWhenPresent("Span ID", request.spanId(), spanId);
            requireValidWhenPresent("父 Span ID", request.parentSpanId(), parentSpanId);
            if (signalType == ObservationSignalType.SPAN && (traceId == null || spanId == null)) {
                throw new IllegalArgumentException("Span 必须包含有效的 Trace ID 和 Span ID。");
            }
        }
        ObservationSeverity normalizedSeverity = normalizeSeverity(request.eventType(), request.severity(), request.outcome());
        int severityNumber = request.severityNumber() == null
                ? severityNumber(normalizedSeverity)
                : request.severityNumber();
        String severityText = sanitizer.text(
                request.severityText() == null ? normalizedSeverity.name() : request.severityText(), 20);
        ClientObservationBatchRequestDTO.InstrumentationScope scope = batch.instrumentationScope();
        String scopeName = scope == null ? "e-report-" + batch.platform().name().toLowerCase() : scope.name();
        String scopeVersion = scope == null ? null : sanitizer.text(scope.version(), 50);
        Map<String, Object> resourceAttributes = batch.resource() == null
                ? resourceFromEnvironment(batch)
                : batch.resource().attributes();
        Map<String, Object> attributes = request.attributes() == null ? Map.of() : request.attributes();
        String body = sanitizer.text(request.body() == null ? request.message() : request.body(), 2000);
        String requestId = sanitizer.text(request.requestId(), 100);
        String correlationId = traceId != null ? traceId
                : requestId != null ? requestId : sanitizer.text(request.traceId(), 100);
        ObservationSpanKind spanKind = signalType == ObservationSignalType.SPAN
                ? (request.spanKind() == null ? ObservationSpanKind.INTERNAL : request.spanKind())
                : null;
        ObservationSpanStatusCode statusCode = signalType == ObservationSignalType.SPAN
                ? (request.spanStatusCode() == null ? spanStatus(request.outcome()) : request.spanStatusCode())
                : null;
        return new CanonicalObservation(
                signalType, requestId, traceId, spanId, parentSpanId, request.traceFlags(), severityNumber, severityText,
                body, sanitizer.text(scopeName, 255), scopeVersion, sanitizer.json(resourceAttributes, 16_000),
                sanitizer.json(scope == null ? Map.of() : scope.attributes(), 4_000), sanitizer.json(attributes, 16_000),
                signalType == ObservationSignalType.SPAN
                        ? sanitizer.text(request.spanName() == null ? request.operationName() : request.spanName(), 255)
                        : null,
                spanKind, statusCode, sanitizer.text(request.spanStatusMessage(), 255), request.durationNanos(),
                sanitizer.json(spanEvents(request), 24_000), correlationId
        );
    }

    private List<Map<String, Object>> spanEvents(ClientObservationBatchRequestDTO.Event event) {
        if (event.spanEvents() != null && !event.spanEvents().isEmpty()) {
            return event.spanEvents().stream().map(spanEvent -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("occurredAt", spanEvent.occurredAt());
                item.put("name", spanEvent.name());
                item.put("attributes", spanEvent.attributes());
                return item;
            }).toList();
        }
        return breadcrumbs(event.breadcrumbs());
    }

    private Map<String, Object> resourceFromEnvironment(ClientObservationBatchRequestDTO batch) {
        Map<String, Object> resource = new LinkedHashMap<>();
        ClientObservationBatchRequestDTO.Environment environment = batch.environment();
        resource.put("service.name", "e-report-" + batch.platform().name().toLowerCase());
        resource.put("service.namespace", "e-report");
        resource.put("telemetry.sdk.name", "e-report-client");
        if (environment != null) {
            resource.put("service.version", environment.appVersion());
            resource.put("os.name", environment.osName());
            resource.put("os.version", environment.osVersion());
            resource.put("device.manufacturer", environment.manufacturer());
            resource.put("device.model.identifier", environment.deviceModel());
            resource.put("network.connection.type", environment.networkType());
        }
        return resource;
    }

    private String validIdentifier(String value, java.util.regex.Pattern pattern, int length) {
        if (value == null || value.length() != length || !pattern.matcher(value).matches() || value.chars().allMatch(c -> c == '0')) {
            return null;
        }
        return value;
    }

    private void requireValidWhenPresent(String field, String supplied, String normalized) {
        if (supplied != null && normalized == null) {
            throw new IllegalArgumentException(field + " 不符合 W3C Trace Context 格式。");
        }
    }

    private int severityNumber(ObservationSeverity severity) {
        return switch (severity) {
            case INFO -> 9;
            case WARN -> 13;
            case ERROR -> 17;
            case FATAL -> 21;
        };
    }

    private ObservationSpanStatusCode spanStatus(ObservationOutcome outcome) {
        if (outcome == ObservationOutcome.SUCCESS) return ObservationSpanStatusCode.OK;
        if (outcome == ObservationOutcome.FAILURE) return ObservationSpanStatusCode.ERROR;
        return ObservationSpanStatusCode.UNSET;
    }

    private Map<String, Object> context(ClientObservationBatchRequestDTO.Environment environment,
                                        ClientObservationBatchRequestDTO.Event event) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (environment != null && environment.attributes() != null && !environment.attributes().isEmpty()) {
            context.put("environment", environment.attributes());
        }
        if (event.attributes() != null && !event.attributes().isEmpty()) {
            context.put("event", event.attributes());
        }
        return context;
    }

    private List<Map<String, Object>> breadcrumbs(List<ClientObservationBatchRequestDTO.Breadcrumb> breadcrumbs) {
        if (breadcrumbs == null || breadcrumbs.isEmpty()) {
            return List.of();
        }
        return breadcrumbs.stream().map(breadcrumb -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("occurredAt", breadcrumb.occurredAt());
            item.put("category", breadcrumb.category());
            item.put("name", breadcrumb.name());
            item.put("message", breadcrumb.message());
            item.put("attributes", breadcrumb.attributes());
            return item;
        }).toList();
    }

    private ObservationSeverity normalizeSeverity(ObservationEventType eventType,
                                                  ObservationSeverity severity,
                                                  ObservationOutcome outcome) {
        return switch (eventType) {
            case ERROR -> severity == ObservationSeverity.FATAL ? ObservationSeverity.FATAL : ObservationSeverity.ERROR;
            case WARNING -> ObservationSeverity.WARN;
            case OPERATION -> outcome == ObservationOutcome.FAILURE
                    ? ObservationSeverity.WARN
                    : ObservationSeverity.INFO;
            case DIAGNOSTIC -> severity;
        };
    }

    private LocalDateTime normalizeOccurredAt(Instant occurredAt, LocalDateTime receivedAt) {
        if (occurredAt == null) {
            return receivedAt;
        }
        LocalDateTime value = LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC);
        if (value.isAfter(receivedAt.plusSeconds(MAX_CLOCK_SKEW_SECONDS))
                || value.isBefore(receivedAt.minusDays(MAX_EVENT_AGE_DAYS))) {
            return receivedAt;
        }
        return value;
    }

    private record CanonicalObservation(
            ObservationSignalType signalType,
            String requestId,
            String traceId,
            String spanId,
            String parentSpanId,
            Integer traceFlags,
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
            String spanEventsJson,
            String correlationId
    ) {
    }
}
