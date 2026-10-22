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
@Table(name = "work_order_organization_transfers", indexes = {
        @Index(name = "idx_work_order_transfers_order_status",
                columnList = "tenant_id,work_order_id,status,requested_at"),
        @Index(name = "idx_work_order_transfers_target_status",
                columnList = "tenant_id,target_org_unit_id,status,requested_at")
})
@Getter
@Setter
public class WorkOrderOrganizationTransfer extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;

    @Column(name = "source_org_unit_id", nullable = false)
    private UUID sourceOrgUnitId;

    @Column(name = "target_org_unit_id", nullable = false)
    private UUID targetOrgUnitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderOrganizationTransferStatus status = WorkOrderOrganizationTransferStatus.PENDING;

    @Column(name = "expected_work_order_version", nullable = false)
    private Long expectedWorkOrderVersion;

    @Column(name = "requested_by_membership_id", nullable = false)
    private UUID requestedByMembershipId;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "request_reason", nullable = false, length = 2000)
    private String requestReason;

    @Column(name = "decided_by_membership_id")
    private UUID decidedByMembershipId;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_comment", length = 2000)
    private String decisionComment;
}
