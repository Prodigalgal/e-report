package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "work_order_responses",
        uniqueConstraints = @UniqueConstraint(name = "uk_work_order_response_attempt", columnNames = {"tenant_id", "work_order_id", "attempt_no"})
)
@Getter
@Setter
public class WorkOrderResponse extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private java.util.UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private WorkOrderAssignment assignment;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by_membership_id", nullable = false)
    private User submittedBy;

    @Column(name = "resolution_details", nullable = false, columnDefinition = "TEXT")
    private String resolutionDetails;

    @Column(name = "resolution_method", nullable = false, length = 255)
    private String resolutionMethod;

    @Column(name = "replacement_completed")
    private Boolean replacementCompleted;

    @Column(name = "replacement_result", columnDefinition = "TEXT")
    private String replacementResult;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
}
