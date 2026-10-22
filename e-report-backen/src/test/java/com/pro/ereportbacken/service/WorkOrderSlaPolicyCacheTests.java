package com.pro.ereportbacken.service;

import com.pro.ereportbacken.config.CacheNames;
import com.pro.ereportbacken.model.pojo.WorkOrderPriority;
import com.pro.ereportbacken.model.pojo.WorkOrderSlaPolicy;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import com.pro.ereportbacken.repository.WorkOrderSlaPolicyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkOrderSlaPolicyCacheTests {

    @Test
    void resolvesMultipleStageReadsFromOneTenantPolicyQuery() {
        UUID tenantId = uuid(1);
        WorkOrderSlaPolicyRepository repository = mock(WorkOrderSlaPolicyRepository.class);
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(CacheNames.SLA_POLICY_DIRECTORY);
        WorkOrderSlaPolicyCache cache = new WorkOrderSlaPolicyCache(
                repository,
                cacheManager,
                new CacheInvalidationService(cacheManager, mock(ApplicationEventPublisher.class))
        );
        when(repository.findByTenantIdOrderByReportTypeAscPriorityAsc(tenantId))
                .thenReturn(List.of(policy(tenantId)));

        assertThat(cache.require(tenantId, WorkOrderType.NETWORK_FAULT, WorkOrderPriority.ROUTINE).claimMinutes())
                .isEqualTo(240);
        assertThat(cache.require(tenantId, WorkOrderType.NETWORK_FAULT, WorkOrderPriority.ROUTINE).responseMinutes())
                .isEqualTo(2880);
        verify(repository).findByTenantIdOrderByReportTypeAscPriorityAsc(tenantId);

        cache.invalidateAfterCommit(tenantId);
        cache.findAll(tenantId);

        verify(repository, times(2)).findByTenantIdOrderByReportTypeAscPriorityAsc(tenantId);
    }

    private WorkOrderSlaPolicy policy(UUID tenantId) {
        WorkOrderSlaPolicy policy = new WorkOrderSlaPolicy();
        policy.setId(uuid(2));
        policy.setTenantId(tenantId);
        policy.setReportType(WorkOrderType.NETWORK_FAULT);
        policy.setPriority(WorkOrderPriority.ROUTINE);
        policy.setClaimMinutes(240);
        policy.setAssignmentAcceptanceMinutes(240);
        policy.setResponseMinutes(2880);
        policy.setVerificationMinutes(960);
        policy.setReworkMinutes(2880);
        policy.setPauseOnHold(true);
        policy.setMaxSingleHoldMinutes(2880);
        policy.setMaxTotalHoldMinutes(8640);
        return policy;
    }

    private UUID uuid(long seed) {
        return com.pro.ereportbacken.TestIds.uuid(seed);
    }
}
