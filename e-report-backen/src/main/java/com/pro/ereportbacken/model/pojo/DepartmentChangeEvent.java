package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "department_change_events", indexes = {
        @Index(name = "idx_department_events_department", columnList = "tenant_id,department_id,created_at"),
        @Index(name = "idx_department_events_org", columnList = "tenant_id,org_unit_id,created_at")
})
@Getter
@Setter
public class DepartmentChangeEvent extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "org_unit_id", nullable = false)
    private UUID orgUnitId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DepartmentChangeOperation operation;

    @Column(name = "old_parent_id")
    private UUID oldParentId;

    @Column(name = "new_parent_id")
    private UUID newParentId;

    @Column(name = "target_department_id")
    private UUID targetDepartmentId;

    @Column(name = "operator_membership_id")
    private UUID operatorMembershipId;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "affected_users", nullable = false)
    private Long affectedUsers = 0L;

    @Column(name = "affected_work_orders", nullable = false)
    private Long affectedWorkOrders = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
