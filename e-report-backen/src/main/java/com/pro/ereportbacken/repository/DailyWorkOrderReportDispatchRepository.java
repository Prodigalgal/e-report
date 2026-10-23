package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.DailyWorkOrderReportDispatch;
import com.pro.ereportbacken.model.pojo.DailyWorkOrderReportDispatchStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface DailyWorkOrderReportDispatchRepository
        extends JpaRepository<DailyWorkOrderReportDispatch, UUID> {

    Optional<DailyWorkOrderReportDispatch> findByTenantIdAndOrgUnitIdAndReportDate(
            UUID tenantId,
            UUID orgUnitId,
            LocalDate reportDate);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE DailyWorkOrderReportDispatch dispatch
            SET dispatch.startedAt = :reclaimedAt,
                dispatch.nextAttemptAt = NULL,
                dispatch.completedAt = NULL,
                dispatch.workOrderCount = 0,
                dispatch.recipientCount = 0,
                dispatch.successCount = 0,
                dispatch.failureCount = 0,
                dispatch.errorSummary = NULL
            WHERE dispatch.id = :dispatchId
              AND dispatch.status = :processingStatus
              AND ((dispatch.nextAttemptAt IS NOT NULL AND dispatch.nextAttemptAt <= :reclaimedAt)
                OR (dispatch.nextAttemptAt IS NULL AND dispatch.startedAt < :staleBefore))
            """)
    int reclaimStale(
            @Param("dispatchId") UUID dispatchId,
            @Param("processingStatus") DailyWorkOrderReportDispatchStatus processingStatus,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("reclaimedAt") LocalDateTime reclaimedAt);
}
