package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.Tenant;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.repository.WorkOrderRepository;
import com.pro.ereportbacken.security.TenantExecutionContext;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkOrderSlaBreachScannerTests {

    @Test
    void scansEachActiveTenantInsideItsDatabaseExecutionContext() {
        WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
        WorkOrderSlaBreachService breachService = mock(WorkOrderSlaBreachService.class);
        RedisDistributedLockService lockService = mock(RedisDistributedLockService.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantExecutionContext executionContext = new TenantExecutionContext();
        UUID firstTenantId = com.pro.ereportbacken.TestIds.uuid(41);
        UUID secondTenantId = com.pro.ereportbacken.TestIds.uuid(42);
        when(tenantRepository.findByStatusOrderByNameAsc(TenantStatus.ACTIVE))
                .thenReturn(List.of(tenant(firstTenantId), tenant(secondTenantId)));
        List<UUID> observedTenantIds = new ArrayList<>();
        when(workOrderRepository.findSlaBreachCandidateIds(any(UUID.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    UUID queryTenantId = invocation.getArgument(0, UUID.class);
                    observedTenantIds.add(queryTenantId);
                    assertThat(queryTenantId).isEqualTo(executionContext.currentTenantId().orElseThrow());
                    return List.of();
                });
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return true;
        }).when(lockService).runWithLock(anyString(), any(Duration.class), any(Runnable.class));
        WorkOrderSlaBreachScanner scanner = new WorkOrderSlaBreachScanner(
                workOrderRepository,
                breachService,
                lockService,
                tenantRepository,
                executionContext
        );

        scanner.scan();

        assertThat(observedTenantIds).containsExactly(firstTenantId, secondTenantId);
        assertThat(executionContext.currentTenantId()).isEmpty();
    }

    private Tenant tenant(UUID id) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setStatus(TenantStatus.ACTIVE);
        return tenant;
    }
}
