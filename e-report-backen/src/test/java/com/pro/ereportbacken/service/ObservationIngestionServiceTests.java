package com.pro.ereportbacken.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.dto.BufferedObservationBatch;
import com.pro.ereportbacken.model.dto.ClientObservationBatchRequestDTO;
import com.pro.ereportbacken.model.pojo.ObservationEventType;
import com.pro.ereportbacken.model.pojo.ObservationOutcome;
import com.pro.ereportbacken.model.pojo.ObservationPlatform;
import com.pro.ereportbacken.model.pojo.ObservationSeverity;
import com.pro.ereportbacken.model.pojo.ObservationSignalType;
import com.pro.ereportbacken.model.pojo.ObservationSpanKind;
import com.pro.ereportbacken.model.pojo.ObservationSpanStatusCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObservationIngestionServiceTests {

    @Mock
    private ObservationEventBuffer eventBuffer;
    @Mock
    private ObservationSettingsService settingsService;
    @Mock
    private ObservationRequestIdentityResolver identityResolver;

    private ObservationIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new ObservationIngestionService(
                eventBuffer,
                settingsService,
                identityResolver,
                new ObservationDataSanitizer(new ObjectMapper().findAndRegisterModules())
        );
    }

    @Test
    void filtersDuplicateAndDisabledWarningsBeforeRedisAndSanitizesPayload() {
        UUID tenantId = UUID.randomUUID();
        UUID orgUnitId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(settingsService.currentPolicy()).thenReturn(new ObservationSettingsService.Policy(true, false, true, 20, 20, 15));
        when(identityResolver.resolve()).thenReturn(
                new ObservationRequestIdentityResolver.ObservationIdentity(tenantId, orgUnitId, userId, true)
        );

        ClientObservationBatchRequestDTO.Event error = event(
                "event-1", ObservationEventType.ERROR, ObservationSeverity.FATAL,
                "定位失败，手机号13912345678", Map.of("androidId", "abcdef1234567890", "provider", "gps")
        );
        ClientObservationBatchRequestDTO.Event duplicate = event(
                "event-1", ObservationEventType.ERROR, ObservationSeverity.ERROR, "重复", Map.of()
        );
        ClientObservationBatchRequestDTO.Event warning = event(
                "event-2", ObservationEventType.WARNING, ObservationSeverity.WARN, "警告", Map.of()
        );
        ClientObservationBatchRequestDTO request = new ClientObservationBatchRequestDTO(
                1,
                ObservationPlatform.ANDROID,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                new ClientObservationBatchRequestDTO.Environment(
                        "1.0.34", "35", "Android", "16", "Xiaomi", "Test Model", null, "wifi",
                        Map.of("imei", "867530900000000", "locationServicesEnabled", true)
                ),
                null,
                null,
                List.of(error, duplicate, warning)
        );

        var response = ingestionService.ingest(request, "e-report-app/1.0 Android");

        assertThat(response.acceptedCount()).isEqualTo(1);
        assertThat(response.duplicateCount()).isEqualTo(1);
        assertThat(response.ignoredCount()).isEqualTo(1);
        ArgumentCaptor<BufferedObservationBatch> captor = ArgumentCaptor.forClass(BufferedObservationBatch.class);
        verify(eventBuffer).enqueue(captor.capture());
        BufferedObservationBatch.Event buffered = captor.getValue().events().getFirst();
        assertThat(buffered.tenantId()).isEqualTo(tenantId);
        assertThat(buffered.orgUnitId()).isEqualTo(orgUnitId);
        assertThat(buffered.userId()).isEqualTo(userId);
        assertThat(buffered.message()).doesNotContain("13912345678").contains("[手机号已脱敏]");
        assertThat(buffered.contextJson())
                .doesNotContain("abcdef1234567890", "867530900000000")
                .contains("gps", "locationServicesEnabled");
    }

    @Test
    void acceptsSuccessfulOperationsAndKeepsFailedOperationsAsWarnings() {
        UUID tenantId = UUID.randomUUID();
        when(settingsService.currentPolicy()).thenReturn(new ObservationSettingsService.Policy(true, true, true, 20, 20, 15));
        when(identityResolver.resolve()).thenReturn(
                new ObservationRequestIdentityResolver.ObservationIdentity(tenantId, null, null, true)
        );

        ClientObservationBatchRequestDTO.Event success = event(
                "operation-success",
                ObservationEventType.OPERATION,
                ObservationSeverity.INFO,
                "保存成功",
                Map.of(),
                ObservationOutcome.SUCCESS
        );
        ClientObservationBatchRequestDTO.Event failure = event(
                "operation-failure",
                ObservationEventType.OPERATION,
                ObservationSeverity.INFO,
                "保存失败",
                Map.of("status", 500),
                ObservationOutcome.FAILURE
        );
        ClientObservationBatchRequestDTO request = new ClientObservationBatchRequestDTO(
                1,
                ObservationPlatform.WEB,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                null,
                null,
                null,
                List.of(success, failure)
        );

        var response = ingestionService.ingest(request, "test-browser");

        assertThat(response.acceptedCount()).isEqualTo(2);
        assertThat(response.ignoredCount()).isZero();
        ArgumentCaptor<BufferedObservationBatch> captor = ArgumentCaptor.forClass(BufferedObservationBatch.class);
        verify(eventBuffer).enqueue(captor.capture());
        assertThat(captor.getValue().events())
                .extracting(BufferedObservationBatch.Event::clientEventId, BufferedObservationBatch.Event::severity)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("operation-success", ObservationSeverity.INFO),
                        org.assertj.core.groups.Tuple.tuple("operation-failure", ObservationSeverity.WARN)
                );
    }

    @Test
    void mapsSchemaTwoSpanToCanonicalOpenTelemetryFields() {
        UUID tenantId = UUID.randomUUID();
        when(settingsService.currentPolicy()).thenReturn(new ObservationSettingsService.Policy(true, true, true, 20, 20, 15));
        when(identityResolver.resolve()).thenReturn(
                new ObservationRequestIdentityResolver.ObservationIdentity(tenantId, null, null, true)
        );
        String traceId = "0123456789abcdef0123456789abcdef";
        String spanId = "0123456789abcdef";
        ClientObservationBatchRequestDTO.Event span = new ClientObservationBatchRequestDTO.Event(
                "otel-span-1", ObservationEventType.OPERATION, ObservationSeverity.INFO, "NETWORK",
                "HTTP_CLIENT_REQUEST", "接口请求完成。", null, "/work-orders", "GET /work-orders",
                ObservationOutcome.SUCCESS, "request-42", traceId, spanId, null, 1,
                ObservationSignalType.SPAN, 9, "INFO", "接口请求完成。", "GET /work-orders",
                ObservationSpanKind.CLIENT, ObservationSpanStatusCode.OK, null, 25_000_000L, Instant.now(),
                Map.of("http.request.method", "GET", "http.response.status_code", 200),
                List.of(new ClientObservationBatchRequestDTO.SpanEvent(Instant.now(), "response.received", Map.of("status", 200))),
                List.of()
        );
        ClientObservationBatchRequestDTO request = new ClientObservationBatchRequestDTO(
                2, ObservationPlatform.ANDROID, UUID.randomUUID().toString(), UUID.randomUUID().toString(), null,
                new ClientObservationBatchRequestDTO.Resource(Map.of(
                        "service.name", "e-report-android",
                        "service.namespace", "e-report",
                        "deployment.environment.name", "test"
                )),
                new ClientObservationBatchRequestDTO.InstrumentationScope(
                        "e-report-android.telemetry", "5.3.0", Map.of("ereport.telemetry.protocol", "ereport-otel/1")
                ),
                List.of(span)
        );

        ingestionService.ingest(request, "e-report-app/5.3.0 Android");

        ArgumentCaptor<BufferedObservationBatch> captor = ArgumentCaptor.forClass(BufferedObservationBatch.class);
        verify(eventBuffer).enqueue(captor.capture());
        BufferedObservationBatch.Event buffered = captor.getValue().events().getFirst();
        assertThat(buffered.observationSchemaVersion()).isEqualTo(2);
        assertThat(buffered.signalType()).isEqualTo(ObservationSignalType.SPAN);
        assertThat(buffered.otelTraceId()).isEqualTo(traceId);
        assertThat(buffered.otelSpanId()).isEqualTo(spanId);
        assertThat(buffered.requestId()).isEqualTo("request-42");
        assertThat(buffered.resourceJson()).contains("e-report-android", "deployment.environment.name");
        assertThat(buffered.attributesJson()).contains("http.request.method");
        assertThat(buffered.spanEventsJson()).contains("response.received");
    }

    @Test
    void rejectsInvalidTraceIdBeforeBuffering() {
        when(settingsService.currentPolicy()).thenReturn(new ObservationSettingsService.Policy(true, true, true, 20, 20, 15));
        when(identityResolver.resolve()).thenReturn(
                new ObservationRequestIdentityResolver.ObservationIdentity(UUID.randomUUID(), null, null, true)
        );
        ClientObservationBatchRequestDTO.Event invalidSpan = new ClientObservationBatchRequestDTO.Event(
                "otel-span-invalid", ObservationEventType.OPERATION, ObservationSeverity.WARN, "NETWORK",
                "HTTP_CLIENT_REQUEST", "失败", null, "/test", "GET /test", ObservationOutcome.FAILURE,
                "request-invalid", "00000000000000000000000000000000", "0123456789abcdef", null, 1,
                ObservationSignalType.SPAN, 13, "WARN", "失败", "GET /test", ObservationSpanKind.CLIENT,
                ObservationSpanStatusCode.ERROR, "失败", 1L, Instant.now(), Map.of(), List.of(), List.of()
        );
        ClientObservationBatchRequestDTO request = new ClientObservationBatchRequestDTO(
                2, ObservationPlatform.WEB, UUID.randomUUID().toString(), UUID.randomUUID().toString(), null,
                new ClientObservationBatchRequestDTO.Resource(Map.of("service.name", "e-report-web")),
                new ClientObservationBatchRequestDTO.InstrumentationScope("e-report-web.telemetry", "5.3.0", Map.of()),
                List.of(invalidSpan)
        );

        assertThatThrownBy(() -> ingestionService.ingest(request, "browser"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trace ID");
    }

    private ClientObservationBatchRequestDTO.Event event(String id,
                                                         ObservationEventType type,
                                                         ObservationSeverity severity,
                                                         String message,
                                                         Map<String, Object> attributes) {
        return event(id, type, severity, message, attributes, ObservationOutcome.FAILURE);
    }

    private ClientObservationBatchRequestDTO.Event event(String id,
                                                         ObservationEventType type,
                                                         ObservationSeverity severity,
                                                         String message,
                                                         Map<String, Object> attributes,
                                                         ObservationOutcome outcome) {
        return new ClientObservationBatchRequestDTO.Event(
                id, type, severity, "LOCATION", "LOCATION_FAILURE", message, null, "/report/fault", null,
                outcome, null, "request-1", null, null, null, null, null, null, null, null, null, null, null,
                null, Instant.now(), attributes, null, List.of()
        );
    }
}
