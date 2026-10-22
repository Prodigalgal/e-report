package com.pro.ereportbacken.reporting;

import com.pro.ereportbacken.model.dto.OrgUnitDTO;
import com.pro.ereportbacken.model.pojo.OrgUnitType;
import com.pro.ereportbacken.model.pojo.Tenant;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.security.ConfigurationScopeContext;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.security.TenantExecutionContext;
import com.pro.ereportbacken.service.DefaultTenantService;
import com.pro.ereportbacken.service.OrganizationHierarchyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyOutstandingWorkOrderReportSchedulerTests {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID countyId = UUID.randomUUID();
    private final Instant startup = Instant.parse("2026-10-20T09:00:00Z");
    private final Instant due = Instant.parse("2026-10-20T10:00:00Z");
    private final ReportSchedulingTestTimer timer = new ReportSchedulingTestTimer(startup);
    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final OrganizationHierarchyService hierarchyService = mock(OrganizationHierarchyService.class);
    private final DailyOutstandingWorkOrderReportService reportService = mock(DailyOutstandingWorkOrderReportService.class);
    private final TenantContext tenantContext = new TenantContext(mock(DefaultTenantService.class), new TenantExecutionContext());
    private final ConfigurationScopeContext configurationScopeContext = new ConfigurationScopeContext();
    private final ArrayDeque<Runnable> workers = new ArrayDeque<>();
    private final DailyOutstandingWorkOrderReportScheduler scheduler = new DailyOutstandingWorkOrderReportScheduler(
            tenantRepository, hierarchyService, tenantContext, configurationScopeContext,
            reportService, timer.scheduler, workers::addLast);
    private final Tenant tenant = new Tenant();
    private final OrganizationHierarchyService.CountyScope countyScope = county(countyId);

    @BeforeEach
    void prepareCounty() {
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);
        when(tenantRepository.findByStatusOrderByNameAsc(TenantStatus.ACTIVE)).thenReturn(List.of(tenant));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(hierarchyService.activeCountyScopes(tenantId)).thenReturn(List.of(countyScope));
        when(reportService.nextExecutionAt(eq(tenantId), eq(countyId), any())).thenAnswer(invocation -> {
            assertThat(tenantContext.requireTenantId()).isEqualTo(tenantId);
            assertThat(configurationScopeContext.currentOrgUnitIdOrNull()).isEqualTo(countyId);
            ZonedDateTime now = invocation.getArgument(2);
            ZonedDateTime next = now.toLocalDate().atTime(LocalTime.of(18, 0)).atZone(now.getZone());
            return Optional.of((next.isAfter(now) ? next : next.plusDays(1)).toInstant());
        });
    }

    @Test
    void waitsUntilApplicationReadyThenAppointsTheExactTime() {
        scheduler.requestRefresh();
        assertThat(timer.pendingTimes()).isEmpty();

        start();

        assertThat(timer.pendingTimes()).containsExactly(due);
        verify(reportService, never()).dispatchIfDue(any(), any(), any());
    }

    @Test
    void triggerSubmitsEmailWorkAndThenAppointsTheFollowingDay() {
        start();
        timer.runDueAt(due);

        assertThat(workers).hasSize(1);
        verify(reportService, never()).dispatchIfDue(any(), any(), any());
        workers.removeFirst().run();

        verify(reportService).dispatchIfDue(tenantId, countyScope,
                due.atZone(DailyOutstandingWorkOrderReportService.REPORT_ZONE));
        assertThat(timer.pendingTimes()).containsExactly(due.plusSeconds(86400));
        assertThat(configurationScopeContext.currentOrgUnitIdOrNull()).isNull();
    }

    @Test
    void changedTimeCancelsThePreviousAppointmentAndIgnoresItsCallback() {
        start();
        Runnable previous = timer.firstTaskAt(due);
        Instant changed = due.plusSeconds(4500);
        when(reportService.nextExecutionAt(eq(tenantId), eq(countyId), any())).thenReturn(Optional.of(changed));

        scheduler.requestRefresh();
        timer.runDueAt(startup);
        previous.run();
        timer.runDueAt(due);

        assertThat(workers).isEmpty();
        assertThat(timer.pendingTimes()).containsExactly(changed);
        timer.runDueAt(changed);
        assertThat(workers).hasSize(1);
    }

    @Test
    void closingTheChannelCancelsThePendingReport() {
        start();
        when(reportService.nextExecutionAt(eq(tenantId), eq(countyId), any())).thenReturn(Optional.empty());

        scheduler.requestRefresh();
        timer.runDueAt(startup);
        timer.runDueAt(due);

        assertThat(timer.pendingTimes()).isEmpty();
        assertThat(workers).isEmpty();
    }

    @Test
    void removedCountyCancelsItsAppointment() {
        start();
        when(hierarchyService.activeCountyScopes(tenantId)).thenReturn(List.of());

        scheduler.requestRefresh();
        timer.runDueAt(startup);

        assertThat(timer.pendingTimes()).isEmpty();
    }

    @Test
    void inactiveTenantsDoNotDispatchEvenIfTheirTimerWasAlreadyTriggered() {
        start();
        timer.runDueAt(due);
        tenant.setStatus(TenantStatus.SUSPENDED);

        workers.removeFirst().run();

        verify(reportService, never()).dispatchIfDue(any(), any(), any());
        assertThat(timer.pendingTimes()).isEmpty();
    }

    @Test
    void oneCountyPlanningFailureDoesNotBlockAnotherCounty() {
        UUID otherCountyId = UUID.randomUUID();
        when(hierarchyService.activeCountyScopes(tenantId)).thenReturn(List.of(countyScope, county(otherCountyId)));
        when(reportService.nextExecutionAt(eq(tenantId), eq(countyId), any()))
                .thenThrow(new IllegalStateException("configuration unavailable"));
        when(reportService.nextExecutionAt(eq(tenantId), eq(otherCountyId), any())).thenReturn(Optional.of(due));

        start();

        assertThat(timer.pendingTimes()).containsExactly(startup.plusSeconds(60), due);
    }

    @Test
    void triggerFailureAppointsASingleRetryWithinTheWindow() {
        start();
        when(reportService.nextExecutionAt(eq(tenantId), eq(countyId), any()))
                .thenAnswer(invocation -> Optional.of(((ZonedDateTime) invocation.getArgument(2)).toInstant()));
        doThrow(new IllegalStateException("database unavailable"))
                .when(reportService).dispatchIfDue(any(), any(), any());
        timer.runDueAt(due);

        workers.removeFirst().run();

        assertThat(timer.pendingTimes()).containsExactly(due.plusSeconds(60));
    }

    @Test
    void refreshWhileRunningDoesNotSubmitTheSameCountyTwice() {
        start();
        timer.runDueAt(due);

        scheduler.requestRefresh();
        timer.runDueAt(due);

        assertThat(workers).hasSize(1);
        assertThat(timer.pendingTimes()).isEmpty();
        workers.removeFirst().run();
        assertThat(timer.pendingTimes()).containsExactly(due.plusSeconds(86400));
    }

    @Test
    void shutdownCancelsPendingAppointments() {
        start();

        scheduler.close();
        scheduler.requestRefresh();
        timer.runDueAt(due);

        assertThat(workers).isEmpty();
        assertThat(timer.pendingTimes()).isEmpty();
    }

    private void start() {
        scheduler.onApplicationReady();
        timer.runDueAt(startup);
    }

    private OrganizationHierarchyService.CountyScope county(UUID orgUnitId) {
        OrgUnitDTO county = new OrgUnitDTO(orgUnitId, tenantId, null, "COUNTY", "区县",
                OrgUnitType.COUNTY, "/" + orgUnitId + "/", TenantStatus.ACTIVE, false);
        return new OrganizationHierarchyService.CountyScope(county, Set.of(orgUnitId));
    }
}
