package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "work_order_events", indexes = @Index(name = "idx_work_order_events_tenant_order_time", columnList = "tenant_id,work_order_id,occurred_at"))
@Getter
@Setter
public class WorkOrderEvent extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private java.util.UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private WorkOrderEventType eventType;

    @Column(name = "previous_status_code", length = 40)
    private String previousStatusCode;

    @Column(name = "next_status_code", length = 40)
    private String nextStatusCode;

    @Column(name = "assignment_id")
    private java.util.UUID assignmentId;

    @Column(name = "response_id")
    private java.util.UUID responseId;

    @Column(name = "verification_id")
    private java.util.UUID verificationId;

    @Column(name = "operator_membership_id")
    private java.util.UUID operatorMembershipId;

    @Column(name = "operator_name_snapshot", length = 100)
    private String operatorNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkOrderEventSource source;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
