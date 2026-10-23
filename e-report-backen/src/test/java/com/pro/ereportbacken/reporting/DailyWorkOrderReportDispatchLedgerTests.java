package com.pro.ereportbacken.reporting;

import com.pro.ereportbacken.model.pojo.DailyWorkOrderReportDispatch;
import com.pro.ereportbacken.model.pojo.DailyWorkOrderReportDispatchStatus;
import com.pro.ereportbacken.repository.DailyWorkOrderReportDispatchRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyWorkOrderReportDispatchLedgerTests {

    private final DailyWorkOrderReportDispatchRepository repository =
            mock(DailyWorkOrderReportDispatchRepository.class);
    private final DailyWorkOrderReportDispatchLedger ledger =
            new DailyWorkOrderReportDispatchLedger(repository, 60);
    private final UUID tenantId = com.pro.ereportbacken.TestIds.uuid(1);
    private final UUID orgUnitId = com.pro.ereportbacken.TestIds.uuid(2);
    private final UUID dispatchId = com.pro.ereportbacken.TestIds.uuid(3);
    private final LocalDate reportDate = LocalDate.of(2026, 8, 8);
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 8, 19, 30);

    @Test
    void reclaimsAProcessingDispatchOnlyAfterTheTimeout() {
        DailyWorkOrderReportDispatch existing = dispatch(
                DailyWorkOrderReportDispatchStatus.PROCESSING,
                now.minusMinutes(61)
        );
        when(repository.findByTenantIdAndOrgUnitIdAndReportDate(tenantId, orgUnitId, reportDate))
                .thenReturn(Optional.of(existing));
        when(repository.reclaimStale(
                dispatchId,
                DailyWorkOrderReportDispatchStatus.PROCESSING,
                now.minusMinutes(60),
                now
        )).thenReturn(1);

        assertThat(ledger.claim(tenantId, orgUnitId, reportDate, now)).contains(dispatchId);
    }

    @Test
    void doesNotReclaimAFreshProcessingDispatch() {
        DailyWorkOrderReportDispatch existing = dispatch(
                DailyWorkOrderReportDispatchStatus.PROCESSING,
                now.minusMinutes(30)
        );
        when(repository.findByTenantIdAndOrgUnitIdAndReportDate(tenantId, orgUnitId, reportDate))
                .thenReturn(Optional.of(existing));

        assertThat(ledger.claim(tenantId, orgUnitId, reportDate, now)).isEmpty();
        verify(repository, never()).reclaimStale(
                dispatchId,
                DailyWorkOrderReportDispatchStatus.PROCESSING,
                now.minusMinutes(60),
                now
        );
    }

    @Test
    void neverReclaimsACompletedDispatch() {
        DailyWorkOrderReportDispatch existing = dispatch(
                DailyWorkOrderReportDispatchStatus.SENT,
                now.minusHours(2)
        );
        when(repository.findByTenantIdAndOrgUnitIdAndReportDate(tenantId, orgUnitId, reportDate))
                .thenReturn(Optional.of(existing));

        assertThat(ledger.claim(tenantId, orgUnitId, reportDate, now)).isEmpty();
        verify(repository, never()).reclaimStale(
                dispatchId,
                DailyWorkOrderReportDispatchStatus.PROCESSING,
                now.minusMinutes(60),
                now
        );
    }

    @Test
    void anUnclaimedReportCanRunImmediately() {
        when(repository.findByTenantIdAndOrgUnitIdAndReportDate(tenantId, orgUnitId, reportDate))
                .thenReturn(Optional.empty());

        assertThat(ledger.nextAttemptAt(tenantId, orgUnitId, reportDate, now)).contains(now);
    }

    @Test
    void freshProcessingSchedulesRecoveryJustAfterTheLeaseBoundary() {
        DailyWorkOrderReportDispatch existing = dispatch(
                DailyWorkOrderReportDispatchStatus.PROCESSING, now.minusMinutes(30));
        when(repository.findByTenantIdAndOrgUnitIdAndReportDate(tenantId, orgUnitId, reportDate))
                .thenReturn(Optional.of(existing));

        assertThat(ledger.nextAttemptAt(tenantId, orgUnitId, reportDate, now))
                .contains(now.plusMinutes(30).plusSeconds(1));
    }

    @Test
    void expiredProcessingCanRunImmediately() {
        DailyWorkOrderReportDispatch existing = dispatch(
                DailyWorkOrderReportDispatchStatus.PROCESSING, now.minusMinutes(61));
        when(repository.findByTenantIdAndOrgUnitIdAndReportDate(tenantId, orgUnitId, reportDate))
                .thenReturn(Optional.of(existing));

        assertThat(ledger.nextAttemptAt(tenantId, orgUnitId, reportDate, now)).contains(now);
    }

    @Test
    void coolingReportUsesItsExplicitRetryDeadlineAndCanBeClaimedAtThatInstant() {
        var existing = dispatch(DailyWorkOrderReportDispatchStatus.PROCESSING, now.minusSeconds(10));
        existing.setNextAttemptAt(now.plusSeconds(10));
        when(repository.findByTenantIdAndOrgUnitIdAndReportDate(tenantId, orgUnitId, reportDate))
                .thenReturn(Optional.of(existing));
        assertThat(ledger.nextAttemptAt(tenantId, orgUnitId, reportDate, now)).contains(now.plusSeconds(10));
        assertThat(ledger.claim(tenantId, orgUnitId, reportDate, now)).isEmpty();
        when(repository.reclaimStale(dispatchId, DailyWorkOrderReportDispatchStatus.PROCESSING,
                now.plusSeconds(10).minusMinutes(60), now.plusSeconds(10))).thenReturn(1);
        assertThat(ledger.claim(tenantId, orgUnitId, reportDate, now.plusSeconds(10))).contains(dispatchId);
    }

    @Test
    void finishedAndPartiallyFailedReportsAreNotAutomaticallyResent() {
        for (var status : DailyWorkOrderReportDispatchStatus.values()) {
            if (status == DailyWorkOrderReportDispatchStatus.PROCESSING) {
                continue;
            }
            when(repository.findByTenantIdAndOrgUnitIdAndReportDate(tenantId, orgUnitId, reportDate))
                    .thenReturn(Optional.of(dispatch(status, now.minusHours(2))));

            assertThat(ledger.nextAttemptAt(tenantId, orgUnitId, reportDate, now)).isEmpty();
        }
    }

    private DailyWorkOrderReportDispatch dispatch(DailyWorkOrderReportDispatchStatus status,
                                                   LocalDateTime startedAt) {
        DailyWorkOrderReportDispatch dispatch = new DailyWorkOrderReportDispatch();
        dispatch.setId(dispatchId);
        dispatch.setTenantId(tenantId);
        dispatch.setOrgUnitId(orgUnitId);
        dispatch.setReportDate(reportDate);
        dispatch.setStatus(status);
        dispatch.setStartedAt(startedAt);
        return dispatch;
    }
}
