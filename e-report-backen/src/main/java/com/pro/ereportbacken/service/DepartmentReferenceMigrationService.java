package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.Department;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderAssignment;
import com.pro.ereportbacken.model.pojo.WorkOrderDepartmentParticipation;
import com.pro.ereportbacken.repository.DepartmentRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.repository.WorkOrderAssignmentRepository;
import com.pro.ereportbacken.repository.WorkOrderDepartmentParticipationRepository;
import com.pro.ereportbacken.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DepartmentReferenceMigrationService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderAssignmentRepository assignmentRepository;
    private final WorkOrderDepartmentParticipationRepository participationRepository;
    private final UserAuthorizationCache userAuthorizationCache;

    public DepartmentReferenceMigrationService(
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            WorkOrderRepository workOrderRepository,
            WorkOrderAssignmentRepository assignmentRepository,
            WorkOrderDepartmentParticipationRepository participationRepository,
            UserAuthorizationCache userAuthorizationCache) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.workOrderRepository = workOrderRepository;
        this.assignmentRepository = assignmentRepository;
        this.participationRepository = participationRepository;
        this.userAuthorizationCache = userAuthorizationCache;
    }

    @Transactional(readOnly = true)
    public Impact preview(UUID tenantId, UUID departmentId) {
        Set<UUID> affectedWorkOrders = new LinkedHashSet<>(
                workOrderRepository.findDistinctIdsByDepartmentReference(tenantId, departmentId));
        affectedWorkOrders.addAll(participationRepository.findDistinctWorkOrderIdsByDepartment(
                tenantId, departmentId));
        affectedWorkOrders.addAll(assignmentRepository.findDistinctWorkOrderIdsByResponsibleDepartment(
                tenantId, departmentId));
        return new Impact(
                userRepository.countByTenantIdAndDepartmentId(tenantId, departmentId),
                affectedWorkOrders.size());
    }

    @Transactional
    public Impact migrate(Department source, Department target) {
        lockDepartments(source, target);
        Impact impact = preview(source.getTenantId(), source.getId());
        UUID targetId = target == null ? null : target.getId();
        userRepository.moveDepartmentMembers(source.getTenantId(), source.getId(), targetId);
        migrateWorkOrders(source, targetId);
        migrateAssignments(source, targetId);
        migrateParticipation(source, target);
        userAuthorizationCache.clearAfterCommit();
        return impact;
    }

    private void lockDepartments(Department source, Department target) {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(source.getId());
        if (target != null) {
            ids.add(target.getId());
        }
        List<Department> lockedDepartments = departmentRepository.findAllByTenantIdAndIdInForUpdate(
                source.getTenantId(), ids);
        if (lockedDepartments.size() != ids.size()) {
            throw new IllegalStateException("部门迁移目标在锁定期间已被删除或不属于当前租户。" );
        }
    }

    private void migrateWorkOrders(Department source, UUID targetId) {
        Set<UUID> ids = new LinkedHashSet<>(workOrderRepository.findDistinctIdsByDepartmentReference(
                source.getTenantId(), source.getId()));
        if (ids.isEmpty()) {
            return;
        }
        List<WorkOrder> workOrders = workOrderRepository.findAllByTenantIdAndIdInForUpdate(
                source.getTenantId(), ids);
        for (WorkOrder workOrder : workOrders) {
            if (source.getId().equals(workOrder.getReportingDepartmentId())) {
                workOrder.setReportingDepartmentId(targetId);
            }
            if (source.getId().equals(workOrder.getResponsibleDepartmentId())) {
                workOrder.setResponsibleDepartmentId(targetId);
            }
        }
        workOrderRepository.flush();
    }

    private void migrateAssignments(Department source, UUID targetId) {
        List<WorkOrderAssignment> assignments = assignmentRepository
                .findByTenantIdAndResponsibleDepartmentIdForUpdate(
                        source.getTenantId(), source.getId());
        assignments.forEach(assignment -> assignment.setResponsibleDepartmentId(targetId));
        assignmentRepository.flush();
    }

    private void migrateParticipation(Department source, Department target) {
        List<WorkOrderDepartmentParticipation> participations = participationRepository
                .findByTenantIdAndDepartmentIdForUpdate(source.getTenantId(), source.getId());
        String targetName = target == null ? source.getName() + "（已删除，归属组织域）" : target.getName();
        participations.forEach(participation -> {
            participation.setDepartmentId(target == null ? null : target.getId());
            participation.setDepartmentNameSnapshot(targetName);
        });
        participationRepository.flush();
    }

    public record Impact(long affectedUsers, long affectedWorkOrders) {
    }
}
