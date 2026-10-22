package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.BufferedObservationBatch;
import com.pro.ereportbacken.model.pojo.ClientObservationEvent;
import com.pro.ereportbacken.repository.ClientObservationEventRepository;
import com.pro.ereportbacken.security.TenantContext;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.Duration;

@Service
@Log4j2
public class ObservationBufferWriter {

    private static final int CLAIM_BATCH_COUNT = 10;

    private final ObservationEventBuffer eventBuffer;
    private final ClientObservationEventRepository eventRepository;
    private final RedisDistributedLockService lockService;
    private final TenantContext tenantContext;

    public ObservationBufferWriter(ObservationEventBuffer eventBuffer,
                                   ClientObservationEventRepository eventRepository,
                                   RedisDistributedLockService lockService,
                                   TenantContext tenantContext) {
        this.eventBuffer = eventBuffer;
        this.eventRepository = eventRepository;
        this.lockService = lockService;
        this.tenantContext = tenantContext;
    }

    @Scheduled(fixedDelayString = "${observability.buffer.flush-delay-ms:1000}")
    public void flush() {
        lockService.runWithLock("observability:buffer-writer", Duration.ofSeconds(30), this::flushClaimed);
    }

    private void flushClaimed() {
        List<ObservationEventBuffer.ClaimedBatch> claimed;
        try {
            claimed = eventBuffer.claim(CLAIM_BATCH_COUNT);
        } catch (Exception exception) {
            log.warn("读取运行诊断 Redis 缓冲失败，将在下一轮重试。", exception);
            return;
        }
        if (claimed.isEmpty()) {
            return;
        }

        Map<UUID, List<ObservationEventBuffer.ClaimedBatch>> batchesByTenant = new LinkedHashMap<>();
        for (ObservationEventBuffer.ClaimedBatch claimedBatch : claimed) {
            try {
                UUID tenantId = requireSingleTenant(claimedBatch);
                batchesByTenant.computeIfAbsent(tenantId, ignored -> new ArrayList<>()).add(claimedBatch);
            } catch (Exception exception) {
                log.error("拒绝无租户或跨租户的运行诊断批次。messageId={}",
                        claimedBatch.batch().messageId(), exception);
                recordFailureSafely(claimedBatch);
            }
        }
        batchesByTenant.forEach((tenantId, tenantBatches) -> {
            try {
                tenantContext.runInTenant(tenantId, () -> persistTenantBatches(tenantId, tenantBatches));
            } catch (Exception exception) {
                log.error("租户运行诊断上下文执行失败。tenantId={}, batchCount={}",
                        tenantId, tenantBatches.size(), exception);
                tenantBatches.forEach(this::recordFailureSafely);
            }
        });
    }

    private void persistTenantBatches(UUID tenantId, List<ObservationEventBuffer.ClaimedBatch> tenantBatches) {
        try {
            persist(tenantBatches);
            tenantBatches.forEach(this::acknowledgeSafely);
        } catch (Exception batchException) {
            log.warn(
                    "租户运行诊断合并批次写入失败，正在隔离具体失败消息。tenantId={}, batchCount={}",
                    tenantId, tenantBatches.size(), batchException
            );
            tenantBatches.forEach(this::persistIndividually);
        }
    }

    private UUID requireSingleTenant(ObservationEventBuffer.ClaimedBatch claimed) {
        Set<UUID> tenantIds = claimed.batch().events().stream()
                .map(BufferedObservationBatch.Event::tenantId)
                .collect(Collectors.toSet());
        if (tenantIds.size() != 1 || tenantIds.contains(null)) {
            throw new IllegalArgumentException("单个运行诊断批次必须且只能属于一个租户。");
        }
        return tenantIds.iterator().next();
    }

    private void persistIndividually(ObservationEventBuffer.ClaimedBatch claimed) {
        try {
            persist(List.of(claimed));
            acknowledgeSafely(claimed);
        } catch (Exception exception) {
            boolean deadLettered = recordFailureSafely(claimed);
            log.error("运行诊断消息写入数据库失败。messageId={}, deadLettered={}",
                    claimed.batch().messageId(), deadLettered, exception);
        }
    }

    private boolean recordFailureSafely(ObservationEventBuffer.ClaimedBatch claimed) {
        try {
            return eventBuffer.recordFailure(claimed);
        } catch (Exception bufferException) {
            log.warn("记录运行诊断缓冲失败次数时 Redis 不可用。messageId={}",
                    claimed.batch().messageId(), bufferException);
            return false;
        }
    }

    private void persist(List<ObservationEventBuffer.ClaimedBatch> claimedBatches) {
        List<BufferedObservationBatch.Event> bufferedEvents = claimedBatches.stream()
                .flatMap(claimed -> claimed.batch().events().stream())
                .toList();
        if (bufferedEvents.isEmpty()) {
            return;
        }
        Set<String> existingIds = eventRepository.findByClientEventIdIn(
                        bufferedEvents.stream().map(BufferedObservationBatch.Event::clientEventId).toList())
                .stream()
                .map(ClientObservationEvent::getClientEventId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> acceptedIds = new HashSet<>(existingIds);
        List<ClientObservationEvent> entities = new ArrayList<>();
        for (BufferedObservationBatch.Event buffered : bufferedEvents) {
            if (acceptedIds.add(buffered.clientEventId())) {
                entities.add(toEntity(buffered));
            }
        }
        if (!entities.isEmpty()) {
            eventRepository.saveAllAndFlush(entities);
        }
    }

    private ClientObservationEvent toEntity(BufferedObservationBatch.Event buffered) {
        ClientObservationEvent event = new ClientObservationEvent();
        event.setClientEventId(buffered.clientEventId());
        event.setTenantId(buffered.tenantId());
        event.setOrgUnitId(buffered.orgUnitId());
        event.setUserId(buffered.userId());
        event.setPlatform(buffered.platform());
        event.setEventType(buffered.eventType());
        event.setSeverity(buffered.severity());
        event.setCategory(buffered.category());
        event.setEventName(buffered.eventName());
        event.setMessage(buffered.message());
        event.setStackTrace(buffered.stackTrace());
        event.setRoute(buffered.route());
        event.setOperationName(buffered.operationName());
        event.setOutcome(buffered.outcome());
        event.setTraceId(buffered.traceId());
        event.setInstallationId(buffered.installationId());
        event.setSessionId(buffered.sessionId());
        event.setAppVersion(buffered.appVersion());
        event.setAppBuild(buffered.appBuild());
        event.setOsName(buffered.osName());
        event.setOsVersion(buffered.osVersion());
        event.setManufacturer(buffered.manufacturer());
        event.setDeviceModel(buffered.deviceModel());
        event.setUserAgent(buffered.userAgent());
        event.setNetworkType(buffered.networkType());
        event.setContextJson(buffered.contextJson());
        event.setBreadcrumbsJson(buffered.breadcrumbsJson());
        event.setClientOccurredAt(buffered.clientOccurredAt());
        event.setReceivedAt(buffered.receivedAt());
        event.setObservationSchemaVersion(buffered.observationSchemaVersion());
        event.setSignalType(buffered.signalType());
        event.setRequestId(buffered.requestId());
        String otelTraceId = ObservationTraceContext.traceId(buffered.otelTraceId());
        String otelSpanId = ObservationTraceContext.spanId(buffered.otelSpanId());
        String otelParentSpanId = ObservationTraceContext.spanId(buffered.otelParentSpanId());
        if ((buffered.otelTraceId() != null && otelTraceId == null)
                || (buffered.otelSpanId() != null && otelSpanId == null)
                || (buffered.otelParentSpanId() != null && otelParentSpanId == null)) {
            log.warn("运行诊断批次包含非法 W3C 标识符，已按缺失值写入。clientEventId={}",
                    buffered.clientEventId());
        }
        event.setOtelTraceId(otelTraceId);
        event.setOtelSpanId(otelSpanId);
        event.setOtelParentSpanId(otelParentSpanId);
        event.setOtelTraceFlags(buffered.otelTraceFlags());
        event.setSeverityNumber(buffered.severityNumber());
        event.setSeverityText(buffered.severityText());
        event.setBody(buffered.body());
        event.setScopeName(buffered.scopeName());
        event.setScopeVersion(buffered.scopeVersion());
        event.setResourceJson(buffered.resourceJson());
        event.setScopeAttributesJson(buffered.scopeAttributesJson());
        event.setAttributesJson(buffered.attributesJson());
        event.setSpanName(buffered.spanName());
        event.setSpanKind(buffered.spanKind());
        event.setSpanStatusCode(buffered.spanStatusCode());
        event.setSpanStatusMessage(buffered.spanStatusMessage());
        event.setDurationNanos(buffered.durationNanos());
        event.setSpanEventsJson(buffered.spanEventsJson());
        return event;
    }

    private void acknowledgeSafely(ObservationEventBuffer.ClaimedBatch claimed) {
        try {
            eventBuffer.acknowledge(claimed);
        } catch (Exception exception) {
            log.warn("运行诊断已写入数据库但 Redis 确认失败，将依靠幂等键消解重试。messageId={}",
                    claimed.batch().messageId(), exception);
        }
    }
}
