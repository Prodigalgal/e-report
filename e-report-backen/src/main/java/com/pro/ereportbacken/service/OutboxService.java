package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.pojo.OutboxEvent;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderAssignment;
import com.pro.ereportbacken.notification.EmailNotificationChannel;
import com.pro.ereportbacken.notification.NotificationChannelRegistry;
import com.pro.ereportbacken.repository.OutboxEventRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Log4j2
public class OutboxService {

    public static final String WORK_ORDER_CREATED = "WORK_ORDER_CREATED";
    public static final String WORK_ORDER_CREATED_CHANNEL_PREFIX = WORK_ORDER_CREATED + ":";
    public static final String WORK_ORDER_SLA_BREACHED = "WORK_ORDER_SLA_BREACHED";
    public static final String WORK_ORDER_SLA_BREACHED_CHANNEL_PREFIX = WORK_ORDER_SLA_BREACHED + ":";
    public static final String WORK_ORDER_ASSIGNMENT_RECEIVED_CHANNEL_PREFIX = "WORK_ORDER_ASSIGNMENT_RECEIVED:";

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final List<String> notificationChannelNames;
    private final boolean enqueueEnabled;

    public OutboxService(OutboxEventRepository repository,
                         ObjectMapper objectMapper,
                         NotificationChannelRegistry notificationChannelRegistry,
                         @Value("${app.outbox.enqueue-enabled:true}") boolean enqueueEnabled) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.notificationChannelNames = notificationChannelRegistry.channelNames();
        this.enqueueEnabled = enqueueEnabled;
    }

    public void enqueueWorkOrderCreated(WorkOrder workOrder) {
        if (!enqueueEnabled) {
            log.debug("Outbox enqueue is disabled. Skip work order created event for {}.", workOrder.getId());
            return;
        }
        List<OutboxEvent> events = notificationChannelNames.stream()
                .map(channelName -> newWorkOrderEvent(workOrder, WORK_ORDER_CREATED_CHANNEL_PREFIX, channelName))
                .toList();
        repository.saveAll(events);
    }

    public void enqueueWorkOrderSlaBreached(WorkOrder workOrder) {
        if (!enqueueEnabled) {
            log.debug("Outbox enqueue is disabled. Skip work order SLA breach event for {}.", workOrder.getId());
            return;
        }
        List<OutboxEvent> events = notificationChannelNames.stream()
                .map(channelName -> newWorkOrderEvent(workOrder, WORK_ORDER_SLA_BREACHED_CHANNEL_PREFIX, channelName))
                .toList();
        repository.saveAll(events);
    }

    public void enqueueAssignmentReceived(WorkOrder workOrder, WorkOrderAssignment assignment) {
        if (!enqueueEnabled) {
            log.debug("Outbox enqueue is disabled. Skip work order assignment notice for {}.", workOrder.getId());
            return;
        }
        if (!notificationChannelNames.contains(EmailNotificationChannel.CHANNEL_NAME)) {
            log.warn("Email notification channel is unavailable; assignment notice was not enqueued. workOrderId={}",
                    workOrder.getId());
            return;
        }
        OutboxEvent event = newWorkOrderEvent(
                workOrder,
                WORK_ORDER_ASSIGNMENT_RECEIVED_CHANNEL_PREFIX + EmailNotificationChannel.CHANNEL_NAME,
                Map.of("assignmentId", assignment.getId(), "channel", EmailNotificationChannel.CHANNEL_NAME)
        );
        repository.save(event);
    }

    private OutboxEvent newWorkOrderEvent(WorkOrder workOrder, String eventPrefix, String channelName) {
        return newWorkOrderEvent(
                workOrder,
                eventPrefix + channelName,
                Map.of("workOrderId", workOrder.getId(), "channel", channelName)
        );
    }

    private OutboxEvent newWorkOrderEvent(WorkOrder workOrder, String eventType, Map<String, ?> payload) {
        LocalDateTime now = UtcTime.now();
        OutboxEvent event = new OutboxEvent();
        event.setTenantId(workOrder.getTenantId());
        event.setAggregateType("WORK_ORDER");
        event.setAggregateId(workOrder.getId());
        event.setEventType(eventType);
        event.setPayload(toJson(payload));
        event.setState("PENDING");
        event.setAttempts(0);
        event.setAvailableAt(now);
        event.setCreatedAt(now);
        return event;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化 Outbox 事件。", exception);
        }
    }
}
