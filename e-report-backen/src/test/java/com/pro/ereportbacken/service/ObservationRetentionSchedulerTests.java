package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.ClientObservationEvent;
import com.pro.ereportbacken.model.pojo.ObservationEventType;
import com.pro.ereportbacken.model.pojo.ObservationSeverity;
import com.pro.ereportbacken.model.pojo.Tenant;
import com.pro.ereportbacken.repository.ClientObservationEventRepository;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.security.TenantContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObservationRetentionSchedulerTests {

    @Test
    void cleansSuccessfulOperationsWithShorterRetentionInsideTenantContext() {
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantContext tenantContext = mock(TenantContext.class);
        ObservationSettingsService settingsService = mock(ObservationSettingsService.class);
        ClientObservationEventRepository eventRepository = mock(ClientObservationEventRepository.class);
        RedisDistributedLockService lockService = mock(RedisDistributedLockService.class);
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        ClientObservationEvent operation = new ClientObservationEvent();
        operation.setId(UUID.randomUUID());

        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(settingsService.operationRetentionDays()).thenReturn(14);
        when(settingsService.retentionDays()).thenReturn(30);
        when(eventRepository.findTop500ByTenantIdAndEventTypeAndSeverityAndReceivedAtBeforeOrderByReceivedAtAsc(
                eq(tenantId), eq(ObservationEventType.OPERATION), eq(ObservationSeverity.INFO), any(LocalDateTime.class)
        )).thenReturn(List.of(operation));
        when(eventRepository.findTop500ByTenantIdAndReceivedAtBeforeOrderByReceivedAtAsc(
                eq(tenantId), any(LocalDateTime.class)
        )).thenReturn(List.of());
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(lockService).runWithLock(any(), any(), any());
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(tenantContext).runInTenant(eq(tenantId), any());

        new ObservationRetentionScheduler(
                tenantRepository, tenantContext, settingsService, eventRepository, lockService
        ).cleanExpiredEvents();

        verify(eventRepository).deleteAllByIdInBatch(List.of(operation.getId()));
        verify(tenantContext).runInTenant(eq(tenantId), any());
    }
}
