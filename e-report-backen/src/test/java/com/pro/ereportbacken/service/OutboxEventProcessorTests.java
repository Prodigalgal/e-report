package com.pro.ereportbacken.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

import com.pro.ereportbacken.model.pojo.OutboxEvent;
import com.pro.ereportbacken.notification.NotificationCenterService;
import com.pro.ereportbacken.notification.WorkOrderNotificationKind;
import com.pro.ereportbacken.security.TenantExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

class OutboxEventProcessorTests {

    @Test
    void completesOnlyTheTargetedChannelEvent() {
        OutboxDeliveryStore deliveryStore = mock(OutboxDeliveryStore.class);
        NotificationCenterService notificationCenterService = mock(NotificationCenterService.class);
        OutboxEvent event = event(uuid(8), "WORK_ORDER_CREATED:wecom");
        when(deliveryStore.claim(1)).thenReturn(List.of(event));
        OutboxEventProcessor processor = processor(deliveryStore, notificationCenterService);

        processor.deliver();

        verify(notificationCenterService).notifyWorkOrder(
                uuid(3), uuid(19), WorkOrderNotificationKind.CREATED, "wecom"
        );
        verify(deliveryStore).markCompleted(uuid(8));
        verify(deliveryStore, never()).markFailed(eq(uuid(8)), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void retriesOnlyTheFailedChannelEvent() {
        OutboxDeliveryStore deliveryStore = mock(OutboxDeliveryStore.class);
        NotificationCenterService notificationCenterService = mock(NotificationCenterService.class);
        OutboxEvent event = event(uuid(9), "WORK_ORDER_CREATED:email");
        when(deliveryStore.claim(1)).thenReturn(List.of(event));
        doThrow(new IllegalStateException("smtp unavailable"))
                .when(notificationCenterService).notifyWorkOrder(
                        uuid(3), uuid(19), WorkOrderNotificationKind.CREATED, "email"
                );
        OutboxEventProcessor processor = processor(deliveryStore, notificationCenterService);

        processor.deliver();

        verify(deliveryStore, never()).markCompleted(uuid(9));
        verify(deliveryStore).markFailed(eq(uuid(9)), contains("smtp unavailable"));
    }

    @Test
    void dispatchesChannelAgnosticCreatedEvents() {
        OutboxDeliveryStore deliveryStore = mock(OutboxDeliveryStore.class);
        NotificationCenterService notificationCenterService = mock(NotificationCenterService.class);
        OutboxEvent event = event(uuid(10), "WORK_ORDER_CREATED");
        when(deliveryStore.claim(1)).thenReturn(List.of(event));
        OutboxEventProcessor processor = processor(deliveryStore, notificationCenterService);

        processor.deliver();

        verify(notificationCenterService).notifyWorkOrder(
                uuid(3), uuid(19), WorkOrderNotificationKind.CREATED
        );
        verify(deliveryStore).markCompleted(uuid(10));
    }

    @Test
    void dispatchesReassignmentNoticeToTheCapturedAssignment() {
        OutboxDeliveryStore deliveryStore = mock(OutboxDeliveryStore.class);
        NotificationCenterService notificationCenterService = mock(NotificationCenterService.class);
        OutboxEvent event = event(uuid(11), "WORK_ORDER_ASSIGNMENT_RECEIVED:email");
        event.setPayload("{\"assignmentId\":\"" + uuid(21) + "\",\"channel\":\"email\"}");
        when(deliveryStore.claim(1)).thenReturn(List.of(event));
        OutboxEventProcessor processor = processor(deliveryStore, notificationCenterService);

        processor.deliver();

        verify(notificationCenterService).notifyAssignmentReceived(uuid(3), uuid(19), uuid(21));
        verify(deliveryStore).markCompleted(uuid(11));
    }

    private OutboxEvent event(UUID id, String eventType) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setTenantId(uuid(3));
        event.setAggregateId(uuid(19));
        event.setEventType(eventType);
        event.setPayload("{}");
        event.setAttempts(1);
        return event;
    }

    private OutboxEventProcessor processor(OutboxDeliveryStore deliveryStore,
                                           NotificationCenterService notificationCenterService) {
        RedisDistributedLockService lockService = mock(RedisDistributedLockService.class);
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return true;
        }).when(lockService).runWithLock(anyString(), any(Duration.class), any(Runnable.class));
        return new OutboxEventProcessor(
                deliveryStore,
                notificationCenterService,
                lockService,
                new TenantExecutionContext(),
                new ObjectMapper(),
                1
        );
    }

    private UUID uuid(long seed) {
        return com.pro.ereportbacken.TestIds.uuid(seed);
    }
}
