package com.pro.ereportbacken.reporting;

import com.pro.ereportbacken.model.pojo.DailyWorkOrderReportDispatch;
import com.pro.ereportbacken.model.pojo.DailyWorkOrderReportDispatchStatus;
import com.pro.ereportbacken.repository.DailyWorkOrderReportDispatchRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Log4j2
public class DailyWorkOrderReportDispatchLedger {

    private static final int ERROR_SUMMARY_LIMIT = 1000;

    private final DailyWorkOrderReportDispatchRepository repository;
    private final Duration claimTimeout;

    public DailyWorkOrderReportDispatchLedger(
            DailyWorkOrderReportDispatchRepository repository,
            @Value("${app.daily-outstanding-report.claim-timeout-minutes:60}") int claimTimeoutMinutes) {
        this.repository = repository;
        this.claimTimeout = Duration.ofMinutes(Math.max(1, claimTimeoutMinutes));
    }

    @Transactional
    public Optional<UUID> claim(UUID tenantId,
                                UUID orgUnitId,
                                LocalDate reportDate,
                                LocalDateTime startedAt) {
        Optional<DailyWorkOrderReportDispatch> existing = repository
                .findByTenantIdAndOrgUnitIdAndReportDate(tenantId, orgUnitId, reportDate);
        if (existing.isPresent()) {
            return reclaimIfStale(existing.get(), startedAt);
        }

        DailyWorkOrderReportDispatch dispatch = new DailyWorkOrderReportDispatch();
        dispatch.setTenantId(tenantId);
        dispatch.setOrgUnitId(orgUnitId);
        dispatch.setReportDate(reportDate);
        dispatch.setStatus(DailyWorkOrderReportDispatchStatus.PROCESSING);
        dispatch.setStartedAt(startedAt);
        return Optional.of(repository.saveAndFlush(dispatch).getId());
    }

    public Optional<LocalDateTime> nextAttemptAt(UUID tenantId,
                                               UUID orgUnitId,
                                               LocalDate reportDate,
                                               LocalDateTime now) {
        return repository.findByTenantIdAndOrgUnitIdAndReportDate(tenantId, orgUnitId, reportDate)
                .map(dispatch -> {
                    if (dispatch.getStatus() != DailyWorkOrderReportDispatchStatus.PROCESSING) {
                        return Optional.<LocalDateTime>empty();
                    }
                    if (dispatch.getNextAttemptAt() != null) {
                        return Optional.of(dispatch.getNextAttemptAt().isAfter(now) ? dispatch.getNextAttemptAt() : now);
                    }
                    // 回收使用严格小于条件，避开数据库时间精度和租约边界上的立即重入。
                    LocalDateTime leaseExpiresAt = dispatch.getStartedAt().plus(claimTimeout).plusSeconds(1);
                    return Optional.of(leaseExpiresAt.isAfter(now) ? leaseExpiresAt : now);
                })
                .orElseGet(() -> Optional.of(now));
    }

    private Optional<UUID> reclaimIfStale(DailyWorkOrderReportDispatch dispatch,
                                           LocalDateTime reclaimedAt) {
        LocalDateTime staleBefore = reclaimedAt.minus(claimTimeout);
        if (dispatch.getStatus() != DailyWorkOrderReportDispatchStatus.PROCESSING
                || dispatch.getStartedAt() == null
                || (dispatch.getNextAttemptAt() != null
                    ? dispatch.getNextAttemptAt().isAfter(reclaimedAt)
                    : !dispatch.getStartedAt().isBefore(staleBefore))) {
            return Optional.empty();
        }

        int reclaimed = repository.reclaimStale(
                dispatch.getId(),
                DailyWorkOrderReportDispatchStatus.PROCESSING,
                staleBefore,
                reclaimedAt
        );
        if (reclaimed == 1) {
            log.warn("回收超时的日报执行权。dispatchId={}, previousStartedAt={}, reclaimedAt={}",
                    dispatch.getId(), dispatch.getStartedAt(), reclaimedAt);
            return Optional.of(dispatch.getId());
        }
        return Optional.empty();
    }

    @Transactional
    public void defer(UUID dispatchId, LocalDateTime retryAt, int workOrderCount,
                      int recipientCount, int successCount, int failureCount) {
        DailyWorkOrderReportDispatch dispatch = repository.findById(dispatchId).orElseThrow();
        dispatch.setNextAttemptAt(retryAt);
        dispatch.setWorkOrderCount(workOrderCount);
        dispatch.setRecipientCount(recipientCount);
        dispatch.setSuccessCount(successCount);
        dispatch.setFailureCount(failureCount);
        repository.save(dispatch);
    }

    @Transactional
    public void complete(UUID dispatchId,
                         DailyWorkOrderReportDispatchStatus status,
                         int workOrderCount,
                         int recipientCount,
                         int successCount,
                         int failureCount,
                         String errorSummary,
                         LocalDateTime completedAt) {
        DailyWorkOrderReportDispatch dispatch = repository.findById(dispatchId)
                .orElseThrow(() -> new EntityNotFoundException("日报执行台账不存在: " + dispatchId));
        dispatch.setStatus(status);
        dispatch.setNextAttemptAt(null);
        dispatch.setWorkOrderCount(workOrderCount);
        dispatch.setRecipientCount(recipientCount);
        dispatch.setSuccessCount(successCount);
        dispatch.setFailureCount(failureCount);
        dispatch.setErrorSummary(limit(errorSummary));
        dispatch.setCompletedAt(completedAt);
        repository.save(dispatch);
    }

    private String limit(String value) {
        if (value == null || value.length() <= ERROR_SUMMARY_LIMIT) {
            return value;
        }
        return value.substring(0, ERROR_SUMMARY_LIMIT - 3) + "...";
    }
}
