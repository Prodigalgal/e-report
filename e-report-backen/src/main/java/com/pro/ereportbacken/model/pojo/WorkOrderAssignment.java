package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "work_order_assignments",
        uniqueConstraints = @UniqueConstraint(name = "uk_work_order_assignment_slot", columnNames = {"tenant_id", "work_order_id", "active_slot"}),
        indexes = @Index(name = "idx_assignments_assignee_state", columnList = "tenant_id,assignee_membership_id,state,assigned_at")
)
@Getter
@Setter
public class WorkOrderAssignment extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private java.util.UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignee_membership_id", nullable = false)
    private User assignee;

    @Column(name = "responsible_org_id", nullable = false)
    private java.util.UUID responsibleOrgId;

    @Column(name = "responsible_department_id")
    private java.util.UUID responsibleDepartmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by_membership_id", nullable = false)
    private User assignedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false, length = 20)
    private AssignmentType assignmentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AssignmentState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "active_slot", length = 20)
    private AssignmentSlot activeSlot;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /**
     * 当前责任槽位必须同时满足状态和槽位约束。仅判断工单快照上的指针会把历史派单误认为当前责任人。
     */
    public boolean isCurrent() {
        return state == AssignmentState.ACCEPTED && activeSlot == AssignmentSlot.CURRENT;
    }

    /**
     * 待接收槽位必须同时满足状态和槽位约束，避免已拒绝/已撤回记录继续出现在待接收队列。
     */
    public boolean isPending() {
        return state == AssignmentState.PENDING_ACCEPTANCE && activeSlot == AssignmentSlot.PENDING;
    }

    @PrePersist
    @PreUpdate
    void validateInvariant() {
        if (tenantId == null || workOrder == null || !tenantId.equals(workOrder.getTenantId())) {
            throw new IllegalStateException("派单记录与工单必须属于同一租户。" );
        }
        if (assignee == null || !tenantId.equals(assignee.getTenantId())
                || assignedBy == null || !tenantId.equals(assignedBy.getTenantId())) {
            throw new IllegalStateException("派单人员、操作人员与工单必须属于同一租户。" );
        }
        if (activeSlot != null && !isCurrent() && !isPending()) {
            throw new IllegalStateException("派单活动槽位与状态不一致。" );
        }
    }
}
