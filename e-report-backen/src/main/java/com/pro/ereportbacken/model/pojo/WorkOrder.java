package com.pro.ereportbacken.model.pojo;

import com.pro.ereportbacken.time.UtcTime;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "fault_reports",
        indexes = {
                @Index(name = "idx_fault_reports_type_status", columnList = "report_type,status"),
                @Index(name = "idx_fault_reports_type_status_time", columnList = "report_type,status,report_time"),
                @Index(name = "idx_fault_reports_type_time", columnList = "report_type,report_time"),
                @Index(name = "idx_fault_reports_reported_by_time", columnList = "reported_by_user_id,report_time"),
                @Index(name = "idx_fault_reports_status_time", columnList = "status,report_time"),
                @Index(name = "idx_fault_reports_report_time", columnList = "report_time"),
                @Index(name = "idx_fault_reports_tenant_type_status_time", columnList = "tenant_id,report_type,status_code,submitted_at"),
                @Index(name = "idx_fault_reports_tenant_reporting_status", columnList = "tenant_id,reporting_org_id,status_code,submitted_at"),
                @Index(name = "idx_fault_reports_tenant_responsible_status", columnList = "tenant_id,responsible_org_id,status_code,submitted_at"),
                @Index(name = "idx_fault_reports_tenant_reporter_time", columnList = "tenant_id,reported_by_user_id,submitted_at"),
                @Index(name = "idx_fault_reports_tenant_sla_due", columnList = "tenant_id,sla_stage,sla_due_at"),
                @Index(name = "idx_fault_reports_tenant_sla_breach", columnList = "tenant_id,sla_ever_breached,status_code")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_fault_reports_tenant_no", columnNames = {"tenant_id", "work_order_no"})
)
@Getter
@Setter
public class WorkOrder extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private java.util.UUID tenantId;

    @Column(name = "work_order_no", nullable = false, length = 64)
    private String workOrderNo;

    @Column(name = "reporting_org_id")
    private java.util.UUID reportingOrgId;

    @Column(name = "responsible_org_id")
    private java.util.UUID responsibleOrgId;

    @Column(name = "owner_org_id", nullable = false)
    private java.util.UUID ownerOrgId;

    @Column(name = "reporting_department_id")
    private java.util.UUID reportingDepartmentId;

    @Column(name = "responsible_department_id")
    private java.util.UUID responsibleDepartmentId;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(nullable = false, length = 255)
    private String location;

    @Column(length = 100)
    private String gpsCoordinates;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String reason;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 100)
    private String contactPerson;

    @Column(length = 50)
    private String status = WorkOrderStatus.SUBMITTED.getValue();

    @Column(name = "status_code", nullable = false, length = 40)
    private String statusCode = WorkOrderStatus.SUBMITTED.getCode();

    @Column(name = "rework_count", nullable = false)
    private Integer reworkCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderPriority priority = WorkOrderPriority.ROUTINE;

    @Enumerated(EnumType.STRING)
    @Column(name = "sla_stage", nullable = false, length = 30)
    private WorkOrderSlaStage slaStage = WorkOrderSlaStage.CLAIM;

    @Column(name = "sla_due_at")
    private LocalDateTime slaDueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sla_resume_stage", length = 30)
    private WorkOrderSlaStage slaResumeStage;

    @Column(name = "sla_resume_due_at")
    private LocalDateTime slaResumeDueAt;

    @Column(name = "sla_resume_breached_at")
    private LocalDateTime slaResumeBreachedAt;

    @Column(name = "sla_paused_at")
    private LocalDateTime slaPausedAt;

    @Column(name = "total_hold_seconds", nullable = false)
    private Long totalHoldSeconds = 0L;

    @Column(name = "sla_breached_at")
    private LocalDateTime slaBreachedAt;

    @Column(name = "sla_ever_breached", nullable = false)
    private Boolean slaEverBreached = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private WorkOrderType reportType = WorkOrderType.NETWORK_FAULT;

    private Integer boxQuantity;

    private Boolean replacementRequired = false;

    private Boolean replacementCompleted;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column
    private String replacementResult;

    /**
     * 工单首次进入系统的时间。该字段是业务事实，不能随着工单流转更新。
     */
    @Column(name = "report_time", nullable = false, updatable = false)
    private LocalDateTime reportTime;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /**
     * 工单聚合最近一次发生持久化变更的时间。与 reportTime 严格分离。
     */
    @Column(name = "last_updated_time", nullable = false)
    private LocalDateTime lastUpdatedTime;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column
    private String resolutionDetails;

    @Column(length = 255)
    private String resolutionMethod;

    private LocalDateTime resolutionTime;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column
    private String closureReason;

    private LocalDateTime closureTime;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Attachment> attachments = new ArrayList<>();

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<WorkOrderMaterialUsage> materialUsages = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_by_user_id", nullable = false)
    private User reportedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_user_id")
    private User resolvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_user_id")
    private User closedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_assignment_id")
    private WorkOrderAssignment currentAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_assignment_id")
    private WorkOrderAssignment pendingAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_response_id")
    private WorkOrderResponse currentResponse;

    public void addAttachment(Attachment attachment) {
        attachments.add(attachment);
        attachment.setWorkOrder(this);
        attachment.setTenantId(tenantId);
    }

    public void removeAttachment(Attachment attachment) {
        attachments.remove(attachment);
        attachment.setWorkOrder(null);
    }

    public void addMaterialUsage(WorkOrderMaterialUsage materialUsage) {
        materialUsages.add(materialUsage);
        materialUsage.setWorkOrder(this);
        materialUsage.setTenantId(tenantId);
    }

    public void removeMaterialUsage(WorkOrderMaterialUsage materialUsage) {
        materialUsages.remove(materialUsage);
        materialUsage.setWorkOrder(null);
    }

    public WorkOrderType type() {
        return reportType != null ? reportType : WorkOrderType.NETWORK_FAULT;
    }

    @PrePersist
    void initializeLifecycleFields() {
        LocalDateTime createdAt = submittedAt != null ? submittedAt : UtcTime.now();
        if (submittedAt == null) {
            submittedAt = createdAt;
        }
        if (reportTime == null) {
            reportTime = createdAt;
        }
        if (lastUpdatedTime == null) {
            lastUpdatedTime = createdAt;
        }
        if (ownerOrgId == null) {
            ownerOrgId = responsibleOrgId != null ? responsibleOrgId : reportingOrgId;
        }
        if (ownerOrgId == null) {
            throw new IllegalStateException("工单必须具有唯一归属组织。");
        }
        if (workOrderNo == null || workOrderNo.isBlank()) {
            workOrderNo = "TMP-" + java.util.UUID.randomUUID().toString().replace("-", "").toUpperCase();
        }
        WorkOrderStatus lifecycleStatus = WorkOrderStatus.fromValue(
                statusCode != null ? statusCode : status
        );
        transitionTo(lifecycleStatus);
    }

    @PreUpdate
    void refreshLastUpdatedTime() {
        lastUpdatedTime = UtcTime.now();
    }

    public void markResolved(String details, String method, User operator) {
        if (WorkOrderStatus.PENDING_VERIFICATION.matches(statusCode) || lifecycleStatus().isTerminal()) {
            throw new IllegalStateException("工单 " + getId() + " 已完成回单或已关闭。");
        }
        resolutionDetails = details;
        resolutionMethod = method;
        resolutionTime = UtcTime.now();
        resolvedBy = operator;
        transitionTo(WorkOrderStatus.PENDING_VERIFICATION);
    }

    public void close(String reason, User operator) {
        if (lifecycleStatus() != WorkOrderStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("工单 " + getId() + " 只有待验收状态可以闭环。");
        }
        if (WorkOrderStatus.CLOSED.matches(statusCode)) {
            throw new IllegalStateException("工单 " + getId() + " 已关闭。");
        }
        closureReason = reason;
        closureTime = UtcTime.now();
        closedBy = operator;
        closedAt = closureTime;
        transitionTo(WorkOrderStatus.CLOSED);
    }

    public WorkOrderStatus lifecycleStatus() {
        return WorkOrderStatus.fromValue(statusCode != null ? statusCode : status);
    }

    public void transitionTo(WorkOrderStatus nextStatus) {
        if (nextStatus == null) {
            throw new IllegalArgumentException("工单状态不能为空。" );
        }
        if (getId() != null) {
            WorkOrderStatus currentStatus = lifecycleStatus();
            if (!currentStatus.canTransitionTo(nextStatus)) {
                throw new IllegalStateException(
                        "工单 " + getId() + " 不允许从 " + currentStatus.getLabel()
                                + " 转换到 " + nextStatus.getLabel() + "。"
                );
            }
        }
        statusCode = nextStatus.getCode();
        status = nextStatus.getValue();
    }
}
