package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "work_order_verifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_work_order_verification_response", columnNames = {"tenant_id", "response_id"})
)
@Getter
@Setter
public class WorkOrderVerification extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private java.util.UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "response_id", nullable = false)
    private WorkOrderResponse response;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verified_by_membership_id", nullable = false)
    private User verifiedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationDecision decision;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "verification_override", nullable = false)
    private Boolean verificationOverride = false;

    @Column(name = "override_reason", columnDefinition = "TEXT")
    private String overrideReason;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;
}
