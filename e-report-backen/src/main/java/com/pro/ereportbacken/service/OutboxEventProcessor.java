package com.pro.ereportbacken.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.pojo.OutboxEvent;
import com.pro.ereportbacken.notification.EmailNotificationChannel;
import com.pro.ereportbacken.notification.NotificationCenterService;
import com.pro.ereportbacken.notification.WorkOrderNotificationKind;
import com.pro.ereportbacken.security.TenantExecutionContext;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.pro.ereportbacken.service.OutboxService.WORK_ORDER_CREATED;
import static com.pro.ereportbacken.service.OutboxService.WORK_ORDER_CREATED_CHANNEL_PREFIX;
import static com.pro.ereportbacken.service.OutboxService.WORK_ORDER_ASSIGNMENT_RECEIVED_CHANNEL_PREFIX;
import static com.pro.ereportbacken.service.OutboxService.WORK_ORDER_SLA_BREACHED;
import static com.pro.ereportbacken.service.OutboxService.WORK_ORDER_SLA_BREACHED_CHANNEL_PREFIX;

import java.time.Duration;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
@Log4j2
public class OutboxEventProcessor {

    private static final int MAX_BATCH_SIZE = 100;

    private final OutboxDeliveryStore deliveryStore;
    private final NotificationCenterService notificationCenterService;
    private final int batchSize;
    private final RedisDistributedLockService lockService;
    private final TenantExecutionContext tenantExecutionContext;
    private final ObjectMapper objectMapper;

    public OutboxEventProcessor(OutboxDeliveryStore deliveryStore,
                                NotificationCenterService notificationCenterService,
                                RedisDistributedLockService lockService,
                                TenantExecutionContext tenantExecutionContext,
                                ObjectMapper objectMapper,
                                @Value("${app.outbox.batch-size:1}") int batchSize) {
        this.deliveryStore = deliveryStore;
        this.notificationCenterService = notificationCenterService;
        this.lockService = lockService;
        this.tenantExecutionContext = tenantExecutionContext;
        this.objectMapper = objectMapper;
        this.batchSize = Math.clamp(batchSize, 1, MAX_BATCH_SIZE);
    }

    @Scheduled(initialDelayString = "${app.outbox.initial-delay-ms:10000}",
            fixedDelayString = "${app.outbox.poll-interval-ms:20000}")
    public void deliver() {
        lockService.runWithLock("outbox:delivery", Duration.ofMinutes(4), this::deliverClaimedEvents);
    }

    private void deliverClaimedEvents() {
        for (OutboxEvent event : deliveryStore.claim(batchSize)) {
            try {
                tenantExecutionContext.runInTenant(event.getTenantId(), () -> dispatch(event));
                deliveryStore.markCompleted(event.getId());
            } catch (RuntimeException exception) {
                log.error("Outbox delivery failed. eventId={}, type={}, attempt={}",
                        event.getId(), event.getEventType(), event.getAttempts(), exception);
                deliveryStore.markFailed(event.getId(), exception.getMessage());
            }
        }
    }

    private void dispatch(OutboxEvent event) {
        if (WORK_ORDER_CREATED.equals(event.getEventType())) {
            notificationCenterService.notifyWorkOrder(
                    event.getTenantId(), event.getAggregateId(), WorkOrderNotificationKind.CREATED
            );
            return;
        }
        if (event.getEventType().startsWith(WORK_ORDER_CREATED_CHANNEL_PREFIX)) {
            String channelName = event.getEventType().substring(WORK_ORDER_CREATED_CHANNEL_PREFIX.length());
            if (channelName.isBlank()) {
                throw new IllegalArgumentException("Outbox notification channel is missing.");
            }
            notificationCenterService.notifyWorkOrder(
                    event.getTenantId(),
                    event.getAggregateId(),
                    WorkOrderNotificationKind.CREATED,
                    channelName
            );
            return;
        }
        if (WORK_ORDER_SLA_BREACHED.equals(event.getEventType())) {
            notificationCenterService.notifyWorkOrder(
                    event.getTenantId(), event.getAggregateId(), WorkOrderNotificationKind.SLA_BREACHED
            );
            return;
        }
        if (event.getEventType().startsWith(WORK_ORDER_SLA_BREACHED_CHANNEL_PREFIX)) {
            String channelName = event.getEventType().substring(WORK_ORDER_SLA_BREACHED_CHANNEL_PREFIX.length());
            if (channelName.isBlank()) {
                throw new IllegalArgumentException("Outbox notification channel is missing.");
            }
            notificationCenterService.notifyWorkOrder(
                    event.getTenantId(),
                    event.getAggregateId(),
                    WorkOrderNotificationKind.SLA_BREACHED,
                    channelName
            );
            return;
        }
        if (event.getEventType().startsWith(WORK_ORDER_ASSIGNMENT_RECEIVED_CHANNEL_PREFIX)) {
            String channelName = event.getEventType().substring(WORK_ORDER_ASSIGNMENT_RECEIVED_CHANNEL_PREFIX.length());
            if (!EmailNotificationChannel.CHANNEL_NAME.equals(channelName)) {
                throw new IllegalArgumentException("Unsupported assignment notification channel: " + channelName);
            }
            notificationCenterService.notifyAssignmentReceived(
                    event.getTenantId(), event.getAggregateId(), assignmentId(event.getPayload()));
            return;
        }
        throw new IllegalArgumentException("Unsupported outbox event type: " + event.getEventType());
    }

    private UUID assignmentId(String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            String value = json == null ? null : json.path("assignmentId").asText(null);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Assignment outbox payload has no assignmentId.");
            }
            return UUID.fromString(value);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Assignment outbox payload is invalid.", exception);
        }
    }
}
