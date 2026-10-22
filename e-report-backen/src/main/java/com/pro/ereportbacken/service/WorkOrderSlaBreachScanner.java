package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import java.util.UUID;

import com.pro.ereportbacken.model.pojo.Tenant;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.repository.WorkOrderRepository;
import com.pro.ereportbacken.security.TenantExecutionContext;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "app.work-order-sla.scanner-enabled", havingValue = "true", matchIfMissing = true)
@Log4j2
public class WorkOrderSlaBreachScanner {
    private static final int BATCH_SIZE = 500;

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderSlaBreachService breachService;
    private final RedisDistributedLockService lockService;
    private final TenantRepository tenantRepository;
    private final TenantExecutionContext tenantExecutionContext;

    public WorkOrderSlaBreachScanner(WorkOrderRepository workOrderRepository,
                                     WorkOrderSlaBreachService breachService,
                                     RedisDistributedLockService lockService,
                                     TenantRepository tenantRepository,
                                     TenantExecutionContext tenantExecutionContext) {
        this.workOrderRepository = workOrderRepository;
        this.breachService = breachService;
        this.lockService = lockService;
        this.tenantRepository = tenantRepository;
        this.tenantExecutionContext = tenantExecutionContext;
    }

    @Scheduled(initialDelayString = "${app.work-order-sla.initial-delay-ms:30000}",
            fixedDelayString = "${app.work-order-sla.scan-interval-ms:60000}")
    public void scan() {
        lockService.runWithLock("work-order:sla-scan", Duration.ofSeconds(55), this::scanDueWorkOrders);
    }

    private void scanDueWorkOrders() {
        for (Tenant tenant : tenantRepository.findByStatusOrderByNameAsc(TenantStatus.ACTIVE)) {
            tenantExecutionContext.runInTenant(tenant.getId(), () -> scanTenantDueWorkOrders(tenant.getId()));
        }
    }

    private void scanTenantDueWorkOrders(UUID tenantId) {
        LocalDateTime now = UtcTime.now();
        List<UUID> candidateIds = workOrderRepository.findSlaBreachCandidateIds(
                tenantId,
                now,
                PageRequest.of(0, BATCH_SIZE)
        );
        int marked = 0;
        for (UUID candidateId : candidateIds) {
            try {
                if (breachService.markBreachedIfDue(tenantId, candidateId, now)) {
                    marked++;
                }
            } catch (RuntimeException exception) {
                log.error("SLA breach evaluation failed. tenantId={}, workOrderId={}",
                        tenantId, candidateId, exception);
            }
        }
        if (marked > 0) {
            log.info("SLA breach scan completed. tenantId={}, candidates={}, marked={}",
                    tenantId, candidateIds.size(), marked);
        }
    }
}

