package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "daily_work_order_report_dispatches",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_report_dispatch_scope_date",
                columnNames = {"tenant_id", "org_unit_id", "report_date"}
        ),
        indexes = @Index(
                name = "idx_daily_report_dispatch_status",
                columnList = "tenant_id,status,report_date"
        )
)
@Getter
@Setter
public class DailyWorkOrderReportDispatch extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "org_unit_id", nullable = false)
    private UUID orgUnitId;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DailyWorkOrderReportDispatchStatus status;

    @Column(name = "work_order_count", nullable = false)
    private Integer workOrderCount = 0;

    @Column(name = "recipient_count", nullable = false)
    private Integer recipientCount = 0;

    @Column(name = "success_count", nullable = false)
    private Integer successCount = 0;

    @Column(name = "failure_count", nullable = false)
    private Integer failureCount = 0;

    @Column(name = "error_summary", length = 1000)
    private String errorSummary;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
