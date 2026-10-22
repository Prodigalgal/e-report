package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.Tenant;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.security.TenantExecutionContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageRedactionReconciliationSchedulerTests {

    @Test
    void scansEachActiveTenantWithBoundedBatchConfiguration() {
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantExecutionContext tenantExecutionContext = new TenantExecutionContext();
        ImageRedactionReconciliationService reconciliationService = mock(ImageRedactionReconciliationService.class);
        ImageRedactionService imageRedactionService = mock(ImageRedactionService.class);
        RedisDistributedLockService lockService = mock(RedisDistributedLockService.class);
        ImageRedactionReconciliationCursorStore cursorStore = mock(ImageRedactionReconciliationCursorStore.class);
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);
        when(tenantRepository.findByStatusOrderByNameAsc(TenantStatus.ACTIVE)).thenReturn(List.of(tenant));
        when(cursorStore.load(tenantId)).thenReturn(Optional.empty());
        when(reconciliationService.reconcileTenant(eq(tenantId), eq(50), eq(null)))
                .thenReturn(new ImageRedactionReconciliationService.ReconciliationBatch(3, 3, 0, 0, 0));
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return true;
        }).when(lockService).runWithLock(anyString(), any(Duration.class), any(Runnable.class));

        ImageRedactionReconciliationScheduler scheduler = new ImageRedactionReconciliationScheduler(
                tenantRepository,
                tenantExecutionContext,
                reconciliationService,
                imageRedactionService,
                lockService,
                cursorStore,
                50,
                2
        );

        scheduler.reconcile();

        verify(reconciliationService).reconcileTenant(tenantId, 50, null);
        verify(cursorStore).clear(tenantId);
    }

    @Test
    void advancesAttachmentCursorWhenAFullBatchContainsUnmatchableRows() {
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantExecutionContext tenantExecutionContext = new TenantExecutionContext();
        ImageRedactionReconciliationService reconciliationService = mock(ImageRedactionReconciliationService.class);
        ImageRedactionService imageRedactionService = mock(ImageRedactionService.class);
        RedisDistributedLockService lockService = mock(RedisDistributedLockService.class);
        ImageRedactionReconciliationCursorStore cursorStore = mock(ImageRedactionReconciliationCursorStore.class);
        UUID tenantId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);
        when(tenantRepository.findByStatusOrderByNameAsc(TenantStatus.ACTIVE)).thenReturn(List.of(tenant));
        when(cursorStore.load(tenantId)).thenReturn(Optional.empty());
        when(reconciliationService.reconcileTenant(eq(tenantId), eq(50), eq(null)))
                .thenReturn(new ImageRedactionReconciliationService.ReconciliationBatch(50, 0, 50, 0, 0, cursor));
        when(reconciliationService.reconcileTenant(eq(tenantId), eq(50), eq(cursor)))
                .thenReturn(new ImageRedactionReconciliationService.ReconciliationBatch(1, 1, 0, 0, 0, UUID.randomUUID()));
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return true;
        }).when(lockService).runWithLock(anyString(), any(Duration.class), any(Runnable.class));

        ImageRedactionReconciliationScheduler scheduler = new ImageRedactionReconciliationScheduler(
                tenantRepository,
                tenantExecutionContext,
                reconciliationService,
                imageRedactionService,
                lockService,
                cursorStore,
                50,
                2
        );

        scheduler.reconcile();

        verify(reconciliationService).reconcileTenant(tenantId, 50, null);
        verify(reconciliationService).reconcileTenant(tenantId, 50, cursor);
    }
}
