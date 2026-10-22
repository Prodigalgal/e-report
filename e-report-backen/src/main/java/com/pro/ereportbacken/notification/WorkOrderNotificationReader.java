package com.pro.ereportbacken.notification;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderAssignment;
import com.pro.ereportbacken.repository.WorkOrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
public class WorkOrderNotificationReader {

    private final WorkOrderRepository workOrderRepository;

    public WorkOrderNotificationReader(WorkOrderRepository workOrderRepository) {
        this.workOrderRepository = workOrderRepository;
    }

    @Transactional(readOnly = true)
    public WorkOrderNotification load(UUID tenantId, UUID workOrderId, WorkOrderNotificationKind kind) {
        WorkOrder workOrder = workOrderRepository.findByTenantIdAndId(tenantId, workOrderId)
                .orElseThrow(() -> new EntityNotFoundException("WorkOrder not found with id: " + workOrderId));
        return switch (kind) {
            case CREATED -> WorkOrderNotification.created(workOrder);
            case SLA_BREACHED -> WorkOrderNotification.slaBreached(workOrder);
            case ASSIGNMENT_RECEIVED -> throw new IllegalArgumentException("派单通知必须指定派单记录。");
        };
    }

    @Transactional(readOnly = true)
    public Optional<WorkOrderNotification> loadAssignmentReceived(UUID tenantId,
                                                                  UUID workOrderId,
                                                                  UUID assignmentId) {
        WorkOrder workOrder = workOrderRepository.findByTenantIdAndId(tenantId, workOrderId)
                .orElseThrow(() -> new EntityNotFoundException("WorkOrder not found with id: " + workOrderId));
        WorkOrderAssignment assignment = workOrder.getPendingAssignment();
        if (assignment == null || !assignment.isPending() || !Objects.equals(assignment.getId(), assignmentId)) {
            assignment = workOrder.getCurrentAssignment();
        }
        if (assignment == null
                || (!assignment.isCurrent() && !assignment.isPending())
                || !Objects.equals(assignment.getId(), assignmentId)) {
            return Optional.empty();
        }
        return Optional.of(WorkOrderNotification.assignmentReceived(workOrder, assignment));
    }
}
