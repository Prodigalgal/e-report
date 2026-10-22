package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.BufferedObservationBatch;
import com.pro.ereportbacken.model.pojo.ObservationEventType;
import com.pro.ereportbacken.model.pojo.ObservationOutcome;
import com.pro.ereportbacken.model.pojo.ObservationPlatform;
import com.pro.ereportbacken.model.pojo.ObservationSeverity;
import com.pro.ereportbacken.model.pojo.ObservationSignalType;
import com.pro.ereportbacken.model.pojo.ObservationSpanKind;
import com.pro.ereportbacken.model.pojo.ObservationSpanStatusCode;
import com.pro.ereportbacken.service.ObservationRequestIdentityResolver.ObservationIdentity;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

@Service
@Log4j2
public class ObservationServerOperationRecorder {

    private final ObservationEventBuffer eventBuffer;
    private final ObservationDataSanitizer sanitizer;
    private final String serviceVersion;
    private final String serviceInstanceId;
    private final String deploymentEnvironment;

    public ObservationServerOperationRecorder(ObservationEventBuffer eventBuffer,
                                              ObservationDataSanitizer sanitizer,
                                              @Value("${spring.application.version:unknown}") String serviceVersion,
                                              @Value("${HOSTNAME:local}") String serviceInstanceId,
                                              @Value("${DEPLOYMENT_ENVIRONMENT:production}") String deploymentEnvironment) {
        this.eventBuffer = eventBuffer;
        this.sanitizer = sanitizer;
        this.serviceVersion = serviceVersion;
        this.serviceInstanceId = serviceInstanceId;
        this.deploymentEnvironment = deploymentEnvironment;
    }

    @Async("observabilityTaskExecutor")
    public void record(OperationSnapshot snapshot) {
        try {
            LocalDateTime receivedAt = LocalDateTime.now(ZoneOffset.UTC);
            String traceId = ObservationTraceContext.traceId(snapshot.traceId());
            String spanId = ObservationTraceContext.spanId(snapshot.spanId());
            String parentSpanId = ObservationTraceContext.spanId(snapshot.parentSpanId());
            ObservationSeverity severity = snapshot.outcome() == ObservationOutcome.FAILURE
                    ? ObservationSeverity.WARN
                    : ObservationSeverity.INFO;
            BufferedObservationBatch.Event event = new BufferedObservationBatch.Event(
                    "server:" + UUID.randomUUID(),
                    snapshot.identity().tenantId(),
                    snapshot.identity().orgUnitId(),
                    snapshot.identity().userId(),
                    ObservationPlatform.SERVER,
                    ObservationEventType.OPERATION,
                    severity,
                    snapshot.category(),
                    snapshot.eventName(),
                    sanitizer.text(snapshot.message(), 2000),
                    null,
                    sanitizer.route(snapshot.route()),
                    sanitizer.text(snapshot.method() + " " + snapshot.route(), 120),
                    snapshot.outcome(),
                    sanitizer.text(snapshot.traceId(), 100),
                    null,
                    null,
                    sanitizer.text(snapshot.appVersion(), 50),
                    sanitizer.text(snapshot.appBuild(), 50),
                    null,
                    null,
                    null,
                    null,
                    sanitizer.text(snapshot.userAgent(), 500),
                    sanitizer.text(snapshot.networkType(), 50),
                    sanitizer.json(Map.of(
                            "recordedBy", "SERVER",
                            "httpMethod", snapshot.method(),
                            "httpStatus", snapshot.status(),
                            "durationMs", snapshot.durationMs(),
                            "exceptionType", snapshot.exceptionType() == null ? "" : snapshot.exceptionType()
                    ), 16_000),
                    null,
                    receivedAt,
                    receivedAt,
                    2,
                    ObservationSignalType.SPAN,
                    sanitizer.text(snapshot.requestId(), 100),
                    traceId,
                    spanId,
                    parentSpanId,
                    1,
                    severity == ObservationSeverity.WARN ? 13 : 9,
                    severity.name(),
                    sanitizer.text(snapshot.message(), 2000),
                    "e-report-backend.http",
                    serviceVersion,
                    sanitizer.json(resourceAttributes(), 16_000),
                    sanitizer.json(Map.of("ereport.telemetry.protocol", "ereport-otel/1"), 4_000),
                    sanitizer.json(spanAttributes(snapshot), 16_000),
                    sanitizer.text(snapshot.method() + " " + snapshot.route(), 255),
                    ObservationSpanKind.SERVER,
                    snapshot.outcome() == ObservationOutcome.FAILURE
                            ? ObservationSpanStatusCode.ERROR
                            : ObservationSpanStatusCode.OK,
                    snapshot.outcome() == ObservationOutcome.FAILURE ? sanitizer.text(snapshot.message(), 255) : null,
                    java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(snapshot.durationMs()),
                    sanitizer.json(java.util.List.of(), 24_000)
            );
            eventBuffer.enqueue(new BufferedObservationBatch(
                    BufferedObservationBatch.CURRENT_SCHEMA_VERSION,
                    UUID.randomUUID().toString(),
                    java.util.List.of(event)
            ));
        } catch (Exception exception) {
            log.warn("关键写操作诊断记录失败，不影响原业务请求。traceId={}", snapshot.traceId(), exception);
        }
    }

    private Map<String, Object> resourceAttributes() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("service.name", "e-report-backend");
        attributes.put("service.namespace", "e-report");
        attributes.put("service.version", serviceVersion);
        attributes.put("service.instance.id", serviceInstanceId);
        attributes.put("deployment.environment.name", deploymentEnvironment);
        attributes.put("telemetry.sdk.name", "spring-boot-opentelemetry");
        attributes.put("telemetry.sdk.language", "java");
        attributes.put("telemetry.sdk.version", serviceVersion);
        return attributes;
    }

    private Map<String, Object> spanAttributes(OperationSnapshot snapshot) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("http.request.method", snapshot.method());
        attributes.put("http.response.status_code", snapshot.status());
        attributes.put("url.path", snapshot.route());
        attributes.put("ereport.request.id", snapshot.requestId());
        attributes.put("ereport.event.category", snapshot.category());
        attributes.put("ereport.event.name", snapshot.eventName());
        attributes.put("ereport.operation.outcome", snapshot.outcome().name());
        attributes.put("ereport.client.platform", snapshot.platform().name());
        if (snapshot.exceptionType() != null) attributes.put("exception.type", snapshot.exceptionType());
        return attributes;
    }

    public record OperationSnapshot(
            ObservationIdentity identity,
            ObservationPlatform platform,
            String category,
            String eventName,
            String method,
            String route,
            int status,
            long durationMs,
            ObservationOutcome outcome,
            String requestId,
            String traceId,
            String spanId,
            String parentSpanId,
            String appVersion,
            String appBuild,
            String networkType,
            String userAgent,
            String exceptionType,
            String message
    ) {
    }
}
