package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "work_order_sla_policies",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sla_policy_tenant_type_priority",
                columnNames = {"tenant_id", "report_type", "priority"}
        ),
        indexes = @Index(name = "idx_sla_policy_tenant_type", columnList = "tenant_id,report_type")
)
@Getter
@Setter
public class WorkOrderSlaPolicy extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private java.util.UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 50)
    private WorkOrderType reportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderPriority priority;

    @Column(name = "claim_minutes", nullable = false)
    private Integer claimMinutes;

    @Column(name = "assignment_acceptance_minutes", nullable = false)
    private Integer assignmentAcceptanceMinutes;

    @Column(name = "response_minutes", nullable = false)
    private Integer responseMinutes;

    @Column(name = "verification_minutes", nullable = false)
    private Integer verificationMinutes;

    @Column(name = "rework_minutes", nullable = false)
    private Integer reworkMinutes;

    @Column(name = "pause_on_hold", nullable = false)
    private Boolean pauseOnHold;

    @Column(name = "max_single_hold_minutes", nullable = false)
    private Integer maxSingleHoldMinutes;

    @Column(name = "max_total_hold_minutes", nullable = false)
    private Integer maxTotalHoldMinutes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
