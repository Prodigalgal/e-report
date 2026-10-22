package com.pro.ereportbacken.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.pojo.OutboxEvent;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderAssignment;
import com.pro.ereportbacken.notification.NotificationChannel;
import com.pro.ereportbacken.notification.NotificationChannelDescriptor;
import com.pro.ereportbacken.notification.NotificationChannelRegistry;
import com.pro.ereportbacken.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxServiceTests {

    @Test
    void createsOneIndependentEventPerNotificationChannel() throws Exception {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        NotificationChannel email = channel("email");
        NotificationChannel wecom = channel("wecom");
        NotificationChannel dingtalk = channel("dingtalk");
        AtomicReference<List<OutboxEvent>> savedEvents = new AtomicReference<>();
        when(repository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<OutboxEvent> source = invocation.getArgument(0);
            List<OutboxEvent> events = new ArrayList<>();
            source.forEach(events::add);
            savedEvents.set(events);
            return events;
        });
        OutboxService service = new OutboxService(
                repository,
                objectMapper,
                new NotificationChannelRegistry(List.of(wecom, email, dingtalk)),
                true
        );
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(com.pro.ereportbacken.TestIds.uuid(42));
        workOrder.setTenantId(com.pro.ereportbacken.TestIds.uuid(7));

        service.enqueueWorkOrderCreated(workOrder);

        assertThat(savedEvents.get())
                .extracting(OutboxEvent::getEventType)
                .containsExactly(
                        "WORK_ORDER_CREATED:dingtalk",
                        "WORK_ORDER_CREATED:email",
                        "WORK_ORDER_CREATED:wecom"
                );
        for (OutboxEvent event : savedEvents.get()) {
            assertThat(event.getTenantId()).isEqualTo(com.pro.ereportbacken.TestIds.uuid(7));
            assertThat(event.getAggregateId()).isEqualTo(com.pro.ereportbacken.TestIds.uuid(42));
            assertThat(event.getState()).isEqualTo("PENDING");
            assertThat(event.getAttempts()).isZero();
            JsonNode payload = objectMapper.readTree(event.getPayload());
            assertThat(payload.path("workOrderId").asText())
                    .isEqualTo(com.pro.ereportbacken.TestIds.uuid(42).toString());
            assertThat(event.getEventType()).endsWith(payload.path("channel").asText());
        }
    }

    @Test
    void doesNotPersistEventsWhenEnqueueIsDisabled() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxService service = new OutboxService(
                repository,
                new ObjectMapper(),
                new NotificationChannelRegistry(List.of(channel("email"))),
                false
        );
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(com.pro.ereportbacken.TestIds.uuid(43));
        workOrder.setTenantId(com.pro.ereportbacken.TestIds.uuid(7));

        service.enqueueWorkOrderCreated(workOrder);
        service.enqueueWorkOrderSlaBreached(workOrder);
        service.enqueueAssignmentReceived(workOrder, new WorkOrderAssignment());

        verify(repository, never()).saveAll(any());
        verify(repository, never()).save(any());
    }

    @Test
    void enqueuesOnlyEmailForTheSpecificReassignment() throws Exception {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<OutboxEvent> savedEvent = new AtomicReference<>();
        when(repository.save(any())).thenAnswer(invocation -> {
            OutboxEvent event = invocation.getArgument(0);
            savedEvent.set(event);
            return event;
        });
        OutboxService service = new OutboxService(
                repository,
                objectMapper,
                new NotificationChannelRegistry(List.of(channel("email"), channel("wecom"))),
                true
        );
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(com.pro.ereportbacken.TestIds.uuid(44));
        workOrder.setTenantId(com.pro.ereportbacken.TestIds.uuid(7));
        WorkOrderAssignment assignment = new WorkOrderAssignment();
        assignment.setId(com.pro.ereportbacken.TestIds.uuid(45));

        service.enqueueAssignmentReceived(workOrder, assignment);

        assertThat(savedEvent.get().getEventType()).isEqualTo("WORK_ORDER_ASSIGNMENT_RECEIVED:email");
        assertThat(savedEvent.get().getAggregateId()).isEqualTo(workOrder.getId());
        assertThat(objectMapper.readTree(savedEvent.get().getPayload()).path("assignmentId").asText())
                .isEqualTo(assignment.getId().toString());
        verify(repository, never()).saveAll(any());
    }

    private NotificationChannel channel(String name) {
        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.descriptor()).thenReturn(new NotificationChannelDescriptor(
                name, name, name + " notifications", "notification." + name + ".enabled", false
        ));
        return channel;
    }
}
