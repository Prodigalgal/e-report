package com.pro.ereportbacken.repository;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.AssignmentSlot;
import com.pro.ereportbacken.model.pojo.WorkOrderAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface WorkOrderAssignmentRepository extends JpaRepository<WorkOrderAssignment, UUID> {
    @EntityGraph(attributePaths = {"assignee", "assignedBy"})
    Optional<WorkOrderAssignment> findByTenantIdAndWorkOrderIdAndActiveSlot(UUID tenantId, UUID workOrderId, AssignmentSlot slot);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"assignee", "assignedBy"})
    @Query("""
            SELECT assignment FROM WorkOrderAssignment assignment
            WHERE assignment.tenantId = :tenantId
              AND assignment.workOrder.id = :workOrderId
              AND assignment.activeSlot IN :slots
            ORDER BY assignment.activeSlot
            """)
    List<WorkOrderAssignment> findByTenantIdAndWorkOrderIdAndActiveSlotInForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("workOrderId") UUID workOrderId,
            @Param("slots") Collection<AssignmentSlot> slots);

    @EntityGraph(attributePaths = {"assignee", "assignedBy"})
    @Query("""
            SELECT assignment FROM WorkOrderAssignment assignment
            WHERE assignment.tenantId = :tenantId
              AND assignment.workOrder.id IN :workOrderIds
              AND ((assignment.state = com.pro.ereportbacken.model.pojo.AssignmentState.ACCEPTED
                    AND assignment.activeSlot = com.pro.ereportbacken.model.pojo.AssignmentSlot.CURRENT)
                   OR (assignment.state = com.pro.ereportbacken.model.pojo.AssignmentState.PENDING_ACCEPTANCE
                    AND assignment.activeSlot = com.pro.ereportbacken.model.pojo.AssignmentSlot.PENDING))
            ORDER BY assignment.assignedAt DESC
            """)
    List<WorkOrderAssignment> findActiveByTenantIdAndWorkOrderIdIn(
            @Param("tenantId") UUID tenantId,
            @Param("workOrderIds") Collection<UUID> workOrderIds);

    @EntityGraph(attributePaths = {"assignee", "assignedBy"})
    Optional<WorkOrderAssignment> findByTenantIdAndWorkOrderIdAndId(UUID tenantId, UUID workOrderId, UUID id);

    List<WorkOrderAssignment> findByTenantIdAndWorkOrderIdOrderByAssignedAtAsc(UUID tenantId, UUID workOrderId);

    long countByTenantIdAndAssigneeIdAndState(UUID tenantId, UUID assigneeId, com.pro.ereportbacken.model.pojo.AssignmentState state);

    boolean existsByTenantIdAndWorkOrderIdAndAssigneeIdAndAcceptedAtIsNotNull(
            UUID tenantId,
            UUID workOrderId,
            UUID assigneeId
    );

    @Query("""
            SELECT DISTINCT assignment.workOrder.id
            FROM WorkOrderAssignment assignment
            WHERE assignment.tenantId = :tenantId
              AND assignment.workOrder.id IN :workOrderIds
              AND assignment.assignee.id = :assigneeId
              AND assignment.acceptedAt IS NOT NULL
            """)
    List<UUID> findAcceptedWorkOrderIds(
            @Param("tenantId") UUID tenantId,
            @Param("workOrderIds") Collection<UUID> workOrderIds,
            @Param("assigneeId") UUID assigneeId
    );

    @Query("""
            SELECT a.assignee.id AS assigneeId, COUNT(a.id) AS total
            FROM WorkOrderAssignment a
            WHERE a.tenantId = :tenantId
              AND a.assignee.id IN :assigneeIds
              AND a.state = com.pro.ereportbacken.model.pojo.AssignmentState.ACCEPTED
              AND a.activeSlot = com.pro.ereportbacken.model.pojo.AssignmentSlot.CURRENT
            GROUP BY a.assignee.id
            """)
    List<ActiveAssignmentCount> countActiveAssignments(@Param("tenantId") UUID tenantId,
                                                        @Param("assigneeIds") Collection<UUID> assigneeIds);

    @Query("""
            SELECT DISTINCT assignment.workOrder.id
            FROM WorkOrderAssignment assignment
            WHERE assignment.tenantId = :tenantId
              AND assignment.responsibleDepartmentId = :departmentId
            """)
    List<UUID> findDistinctWorkOrderIdsByResponsibleDepartment(
            @Param("tenantId") UUID tenantId, @Param("departmentId") UUID departmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT assignment FROM WorkOrderAssignment assignment
            WHERE assignment.tenantId = :tenantId
              AND assignment.responsibleDepartmentId = :departmentId
            ORDER BY assignment.id
            """)
    List<WorkOrderAssignment> findByTenantIdAndResponsibleDepartmentIdForUpdate(
            @Param("tenantId") UUID tenantId, @Param("departmentId") UUID departmentId);

    interface ActiveAssignmentCount {
        UUID getAssigneeId();

        long getTotal();
    }
}


