package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.BufferedObservationBatch;
import com.pro.ereportbacken.model.pojo.ClientObservationEvent;
import com.pro.ereportbacken.model.pojo.ObservationEventType;
import com.pro.ereportbacken.model.pojo.ObservationOutcome;
import com.pro.ereportbacken.model.pojo.ObservationPlatform;
import com.pro.ereportbacken.model.pojo.ObservationSeverity;
import com.pro.ereportbacken.repository.ClientObservationEventRepository;
import com.pro.ereportbacken.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class ObservationBufferWriterTests {

    @Mock
    private ObservationEventBuffer eventBuffer;
    @Mock
    private ClientObservationEventRepository eventRepository;
    @Mock
    private RedisDistributedLockService lockService;
    @Mock
    private TenantContext tenantContext;

    @Test
    void mergesRedisBatchesIntoOneDatabaseWriteAndDeduplicatesClientEvents() {
        UUID tenantId = UUID.randomUUID();
        ObservationEventBuffer.ClaimedBatch first = claimed(tenantId, "message-1", "event-1");
        ObservationEventBuffer.ClaimedBatch second = claimed(tenantId, "message-2", "event-1");
        when(eventBuffer.claim(10)).thenReturn(List.of(first, second));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(lockService).runWithLock(any(), any(), any());
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(tenantContext).runInTenant(any(), any());
        when(eventRepository.findByClientEventIdIn(anyCollection())).thenReturn(List.of());
        when(eventRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        new ObservationBufferWriter(eventBuffer, eventRepository, lockService, tenantContext).flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClientObservationEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventRepository, times(1)).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue()).singleElement()
                .extracting(ClientObservationEvent::getClientEventId)
                .isEqualTo("event-1");
        verify(eventBuffer).acknowledge(first);
        verify(eventBuffer).acknowledge(second);
    }

    private ObservationEventBuffer.ClaimedBatch claimed(UUID tenantId, String messageId, String clientEventId) {
        BufferedObservationBatch.Event event = new BufferedObservationBatch.Event(
                clientEventId, tenantId, null, null, ObservationPlatform.WEB,
                ObservationEventType.ERROR, ObservationSeverity.ERROR, "SYSTEM", "TEST_ERROR", "测试", null,
                "/test", null, ObservationOutcome.FAILURE, "request-1", UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), "1.0", "1", "Web", "1", null, null, "test", "wifi",
                "{}", "[]", LocalDateTime.now(), LocalDateTime.now()
                ,1, null, "request-1", null, null, null, null, 17, "ERROR", "测试",
                "e-report-web", "1.0", "{}", "{}", "{}", null, null, null, null, null, "[]"
        );
        BufferedObservationBatch batch = new BufferedObservationBatch(
                BufferedObservationBatch.CURRENT_SCHEMA_VERSION, messageId, List.of(event)
        );
        return new ObservationEventBuffer.ClaimedBatch("serialized-" + messageId, batch);
    }
}
