package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "work_order_department_participations", indexes = {
        @Index(name = "idx_work_order_dept_participation_order",
                columnList = "tenant_id,work_order_id,relation_type,active"),
        @Index(name = "idx_work_order_dept_participation_department",
                columnList = "tenant_id,department_id,active")
})
@Getter
@Setter
public class WorkOrderDepartmentParticipation extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "department_name_snapshot", nullable = false, length = 100)
    private String departmentNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 20)
    private WorkOrderDepartmentRelationType relationType;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_by_membership_id")
    private UUID createdByMembershipId;

    @Column(length = 1000)
    private String reason;
}
