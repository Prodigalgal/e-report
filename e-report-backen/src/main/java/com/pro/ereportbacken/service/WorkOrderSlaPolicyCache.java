package com.pro.ereportbacken.service;

import com.pro.ereportbacken.config.CacheNames;
import com.pro.ereportbacken.model.dto.WorkOrderSlaPolicyDTO;
import com.pro.ereportbacken.model.dto.WorkOrderSlaPolicyDirectory;
import com.pro.ereportbacken.model.pojo.WorkOrderPriority;
import com.pro.ereportbacken.model.pojo.WorkOrderSlaPolicy;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import com.pro.ereportbacken.repository.WorkOrderSlaPolicyRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Log4j2
public class WorkOrderSlaPolicyCache {

    private final WorkOrderSlaPolicyRepository repository;
    private final CacheManager cacheManager;
    private final CacheInvalidationService cacheInvalidationService;

    public WorkOrderSlaPolicyCache(WorkOrderSlaPolicyRepository repository,
                                   CacheManager cacheManager,
                                   CacheInvalidationService cacheInvalidationService) {
        this.repository = repository;
        this.cacheManager = cacheManager;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @Transactional(readOnly = true)
    public List<WorkOrderSlaPolicyDTO> findAll(UUID tenantId) {
        String cacheKey = tenantId.toString();
        WorkOrderSlaPolicyDirectory cached = getCached(cacheKey);
        if (cached != null) {
            return cached.policies();
        }
        WorkOrderSlaPolicyDirectory directory = new WorkOrderSlaPolicyDirectory(
                repository.findByTenantIdOrderByReportTypeAscPriorityAsc(tenantId).stream()
                        .map(WorkOrderSlaPolicyCache::toDTO)
                        .toList()
        );
        putCached(cacheKey, directory);
        return directory.policies();
    }

    public WorkOrderSlaPolicyDTO require(UUID tenantId,
                                         WorkOrderType reportType,
                                         WorkOrderPriority priority) {
        return findAll(tenantId).stream()
                .filter(policy -> policy.reportType() == reportType && policy.priority() == priority)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "当前市公司缺少故障时限策略，工单类型=" + reportType + "，优先级=" + priority
                ));
    }

    public void invalidateAfterCommit(UUID tenantId) {
        cacheInvalidationService.evictAfterCommit(CacheNames.SLA_POLICY_DIRECTORY, tenantId.toString());
    }

    static WorkOrderSlaPolicyDTO toDTO(WorkOrderSlaPolicy policy) {
        return new WorkOrderSlaPolicyDTO(
                policy.getId(),
                policy.getReportType(),
                policy.getReportType().getDisplayName(),
                policy.getPriority(),
                policy.getPriority().getDisplayName(),
                policy.getClaimMinutes(),
                policy.getAssignmentAcceptanceMinutes(),
                policy.getResponseMinutes(),
                policy.getVerificationMinutes(),
                policy.getReworkMinutes(),
                policy.getPauseOnHold(),
                policy.getMaxSingleHoldMinutes(),
                policy.getMaxTotalHoldMinutes(),
                policy.getUpdatedAt()
        );
    }

    private WorkOrderSlaPolicyDirectory getCached(String cacheKey) {
        try {
            Cache cache = cacheManager.getCache(CacheNames.SLA_POLICY_DIRECTORY);
            return cache == null ? null : cache.get(cacheKey, WorkOrderSlaPolicyDirectory.class);
        } catch (RuntimeException exception) {
            log.warn("SLA 策略缓存读取失败，将回源数据库。cacheKey={}", cacheKey, exception);
            return null;
        }
    }

    private void putCached(String cacheKey, WorkOrderSlaPolicyDirectory directory) {
        try {
            Cache cache = cacheManager.getCache(CacheNames.SLA_POLICY_DIRECTORY);
            if (cache != null) {
                cache.put(cacheKey, directory);
            }
        } catch (RuntimeException exception) {
            log.warn("SLA 策略缓存写入失败，本次继续使用数据库结果。cacheKey={}", cacheKey, exception);
        }
    }
}
