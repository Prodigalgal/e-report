package com.pro.ereportbacken.service;

import java.util.UUID;

import com.pro.ereportbacken.model.dto.WorkOrderSlaPolicyDTO;
import com.pro.ereportbacken.model.dto.WorkOrderSlaPolicyUpdateRequestDTO;
import com.pro.ereportbacken.model.pojo.*;
import com.pro.ereportbacken.repository.WorkOrderSlaPolicyRepository;
import com.pro.ereportbacken.security.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WorkOrderSlaPolicyService {
    private static final int MAX_SINGLE_HOLD_MINUTES = 48 * 60;
    private static final int MAX_TOTAL_HOLD_MINUTES = 144 * 60;

    private final WorkOrderSlaPolicyRepository repository;
    private final TenantContext tenantContext;
    private final WorkOrderSlaPolicyCache policyCache;

    public WorkOrderSlaPolicyService(WorkOrderSlaPolicyRepository repository,
                                     TenantContext tenantContext,
                                     WorkOrderSlaPolicyCache policyCache) {
        this.repository = repository;
        this.tenantContext = tenantContext;
        this.policyCache = policyCache;
    }

    @Transactional(readOnly = true)
    public List<WorkOrderSlaPolicyDTO> findAll() {
        return policyCache.findAll(tenantContext.requireTenantId());
    }

    @Transactional
    public WorkOrderSlaPolicyDTO update(UUID id, WorkOrderSlaPolicyUpdateRequestDTO request) {
        WorkOrderSlaPolicy policy = repository.findById(id)
                .filter(candidate -> candidate.getTenantId().equals(tenantContext.requireTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("故障时限策略不存在：" + id));
        validateHoldLimits(request);
        policy.setClaimMinutes(request.claimMinutes());
        policy.setAssignmentAcceptanceMinutes(request.assignmentAcceptanceMinutes());
        policy.setResponseMinutes(request.responseMinutes());
        policy.setVerificationMinutes(request.verificationMinutes());
        policy.setReworkMinutes(request.reworkMinutes());
        policy.setPauseOnHold(request.pauseOnHold());
        policy.setMaxSingleHoldMinutes(request.maxSingleHoldMinutes());
        policy.setMaxTotalHoldMinutes(request.maxTotalHoldMinutes());
        WorkOrderSlaPolicyDTO result = WorkOrderSlaPolicyCache.toDTO(repository.save(policy));
        policyCache.invalidateAfterCommit(policy.getTenantId());
        return result;
    }

    @Transactional(readOnly = true)
    public WorkOrderSlaPolicyDTO requirePolicy(UUID tenantId, WorkOrderType reportType, WorkOrderPriority priority) {
        return policyCache.require(tenantId, reportType, priority);
    }

    @Transactional
    public void initializeDefaultsForTenant(UUID tenantId) {
        for (WorkOrderType reportType : WorkOrderType.values()) {
            for (WorkOrderPriority priority : WorkOrderPriority.values()) {
                repository.findByTenantIdAndReportTypeAndPriority(tenantId, reportType, priority)
                        .orElseGet(() -> repository.save(defaultPolicy(tenantId, reportType, priority)));
            }
        }
        policyCache.invalidateAfterCommit(tenantId);
    }

    private WorkOrderSlaPolicy defaultPolicy(UUID tenantId, WorkOrderType reportType, WorkOrderPriority priority) {
        WorkOrderSlaPolicy policy = new WorkOrderSlaPolicy();
        policy.setTenantId(tenantId);
        policy.setReportType(reportType);
        policy.setPriority(priority);
        policy.setClaimMinutes(defaultClaimMinutes(priority));
        policy.setAssignmentAcceptanceMinutes(defaultClaimMinutes(priority));
        policy.setResponseMinutes(defaultResponseMinutes(priority));
        policy.setVerificationMinutes(defaultVerificationMinutes(priority));
        policy.setReworkMinutes(defaultResponseMinutes(priority));
        policy.setPauseOnHold(true);
        policy.setMaxSingleHoldMinutes(MAX_SINGLE_HOLD_MINUTES);
        policy.setMaxTotalHoldMinutes(MAX_TOTAL_HOLD_MINUTES);
        return policy;
    }

    private int defaultClaimMinutes(WorkOrderPriority priority) {
        return switch (priority) {
            case URGENT -> 30;
            case HIGH -> 60;
            case ROUTINE -> 240;
            case LOW -> 480;
        };
    }

    private int defaultResponseMinutes(WorkOrderPriority priority) {
        return switch (priority) {
            case URGENT -> 240;
            case HIGH -> 480;
            case ROUTINE -> 2880;
            case LOW -> 5760;
        };
    }

    private int defaultVerificationMinutes(WorkOrderPriority priority) {
        return switch (priority) {
            case URGENT -> 120;
            case HIGH -> 240;
            case ROUTINE -> 960;
            case LOW -> 2880;
        };
    }

    private void validateHoldLimits(WorkOrderSlaPolicyUpdateRequestDTO request) {
        if (request.pauseOnHold() && request.maxSingleHoldMinutes() > request.maxTotalHoldMinutes()) {
            throw new IllegalArgumentException("单次最大挂起时长不能超过累计最大挂起时长。");
        }
    }

}


