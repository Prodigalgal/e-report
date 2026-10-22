package com.pro.ereportbacken.service;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderEvent;
import com.pro.ereportbacken.model.pojo.WorkOrderEventSource;
import com.pro.ereportbacken.model.pojo.WorkOrderEventType;
import com.pro.ereportbacken.model.pojo.WorkOrderSlaStage;
import com.pro.ereportbacken.config.CacheNames;
import com.pro.ereportbacken.repository.WorkOrderEventRepository;
import com.pro.ereportbacken.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class WorkOrderSlaBreachService {
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderEventRepository eventRepository;
    private final WorkOrderSlaService slaService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final CacheInvalidationService cacheInvalidationService;

    public WorkOrderSlaBreachService(WorkOrderRepository workOrderRepository,
                                     WorkOrderEventRepository eventRepository,
                                     WorkOrderSlaService slaService,
                                     OutboxService outboxService,
                                     ObjectMapper objectMapper,
                                     CacheInvalidationService cacheInvalidationService) {
        this.workOrderRepository = workOrderRepository;
        this.eventRepository = eventRepository;
        this.slaService = slaService;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @Transactional
    public boolean markBreachedIfDue(UUID tenantId, UUID workOrderId, LocalDateTime now) {
        WorkOrder workOrder = workOrderRepository.findForSlaUpdate(tenantId, workOrderId).orElse(null);
        if (workOrder == null
                || workOrder.lifecycleStatus().isTerminal()
                || workOrder.getSlaStage() == WorkOrderSlaStage.COMPLETED
                || workOrder.getSlaBreachedAt() != null) {
            return false;
        }
        LocalDateTime effectiveDueAt = slaService.effectiveDueAt(workOrder, now);
        if (effectiveDueAt == null || now.isBefore(effectiveDueAt)) {
            return false;
        }

        workOrder.setSlaBreachedAt(now);
        workOrder.setSlaEverBreached(true);
        workOrderRepository.saveAndFlush(workOrder);
        eventRepository.save(breachEvent(workOrder, effectiveDueAt, now));
        outboxService.enqueueWorkOrderSlaBreached(workOrder);
        clearStatisticsCache();
        return true;
    }

    private WorkOrderEvent breachEvent(WorkOrder workOrder, LocalDateTime effectiveDueAt, LocalDateTime occurredAt) {
        WorkOrderEvent event = new WorkOrderEvent();
        event.setTenantId(workOrder.getTenantId());
        event.setWorkOrder(workOrder);
        event.setEventType(WorkOrderEventType.SLA_BREACHED);
        event.setPreviousStatusCode(workOrder.lifecycleStatus().getCode());
        event.setNextStatusCode(workOrder.lifecycleStatus().getCode());
        event.setOperatorNameSnapshot("系统时限检查器");
        event.setSource(WorkOrderEventSource.SYSTEM);
        event.setReason("工单“" + workOrder.getSlaStage().getDisplayName() + "”阶段已超过截止时间。");
        event.setMetadataJson(toJson(Map.of(
                "priority", workOrder.getPriority().name(),
                "stage", workOrder.getSlaStage().name(),
                "effectiveDueAt", effectiveDueAt.toString(),
                "breachedAt", occurredAt.toString()
        )));
        event.setOccurredAt(occurredAt);
        return event;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法生成处理时限超时事件。", exception);
        }
    }

    private void clearStatisticsCache() {
        cacheInvalidationService.clearAfterCommit(CacheNames.WORK_ORDER_STATISTICS);
    }
}

