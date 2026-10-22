package com.pro.ereportbacken.reporting;

import com.pro.ereportbacken.model.pojo.Tenant;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.security.ConfigurationScopeContext;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.service.OrganizationHierarchyService;
import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "app.daily-outstanding-report.scheduler-enabled", havingValue = "true", matchIfMissing = true)
@Log4j2
public class DailyOutstandingWorkOrderReportScheduler {

    private static final Duration FAILURE_RETRY_DELAY = Duration.ofMinutes(1);

    private final TenantRepository tenantRepository;
    private final OrganizationHierarchyService hierarchyService;
    private final TenantContext tenantContext;
    private final ConfigurationScopeContext configurationScopeContext;
    private final DailyOutstandingWorkOrderReportService reportService;
    private final TaskScheduler taskScheduler;
    private final Executor reportExecutor;
    private final Map<ReportScope, Appointment> appointments = new HashMap<>();
    private final Set<ReportScope> runningScopes = new HashSet<>();
    private final AtomicBoolean refreshQueued = new AtomicBoolean();
    private volatile boolean ready;
    private volatile boolean closed;

    public DailyOutstandingWorkOrderReportScheduler(
            TenantRepository tenantRepository,
            OrganizationHierarchyService hierarchyService,
            TenantContext tenantContext,
            ConfigurationScopeContext configurationScopeContext,
            DailyOutstandingWorkOrderReportService reportService,
            TaskScheduler taskScheduler,
            @Qualifier("dailyOutstandingReportTaskExecutor") Executor reportExecutor) {
        this.tenantRepository = tenantRepository;
        this.hierarchyService = hierarchyService;
        this.tenantContext = tenantContext;
        this.configurationScopeContext = configurationScopeContext;
        this.reportService = reportService;
        this.taskScheduler = taskScheduler;
        this.reportExecutor = reportExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        ready = true;
        requestRefresh();
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Shanghai")
    public void reconcileDailySchedules() {
        requestRefresh();
    }

    public void requestRefresh() {
        if (!ready || closed || !refreshQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            taskScheduler.schedule(() -> {
                refreshQueued.set(false);
                refreshSchedules();
            }, taskScheduler.getClock().instant());
        } catch (RuntimeException exception) {
            refreshQueued.set(false);
            log.warn("日报预约重载未能提交。closed={}", closed, exception);
        }
    }

    private synchronized void refreshSchedules() {
        if (closed) {
            return;
        }
        try {
            Set<ReportScope> activeScopes = new HashSet<>();
            Set<UUID> failedTenants = new HashSet<>();
            for (Tenant tenant : tenantRepository.findByStatusOrderByNameAsc(TenantStatus.ACTIVE)) {
                try {
                    tenantContext.runInTenant(tenant.getId(), () -> {
                        for (var countyScope : hierarchyService.activeCountyScopes(tenant.getId())) {
                            ReportScope scope = new ReportScope(tenant.getId(), countyScope.county().id());
                            activeScopes.add(scope);
                            planNext(scope, now());
                        }
                    });
                } catch (RuntimeException exception) {
                    failedTenants.add(tenant.getId());
                    log.error("租户日报预约加载失败，将重试加载。tenantId={}", tenant.getId(), exception);
                }
            }
            for (ReportScope scope : Set.copyOf(appointments.keySet())) {
                if (!activeScopes.contains(scope) && !failedTenants.contains(scope.tenantId())) {
                    cancel(scope);
                }
            }
            if (!failedTenants.isEmpty()) {
                retryRefresh();
            }
        } catch (RuntimeException exception) {
            log.error("每日工单汇总预约加载失败，将重试加载。", exception);
            retryRefresh();
        }
    }

    private void retryRefresh() {
        taskScheduler.schedule(this::requestRefresh,
                taskScheduler.getClock().instant().plus(FAILURE_RETRY_DELAY));
    }

    private void planNext(ReportScope scope, ZonedDateTime notBefore) {
        if (closed || runningScopes.contains(scope)) {
            return;
        }
        cancel(scope);
        try {
            Optional<Instant> nextExecution = tenantContext.callInTenant(scope.tenantId(), () ->
                    configurationScopeContext.runInOrgUnit(scope.countyOrgUnitId(), () ->
                            reportService.nextExecutionAt(scope.tenantId(), scope.countyOrgUnitId(), notBefore)));
            if (nextExecution.isPresent()) {
                appoint(scope, nextExecution.orElseThrow());
            }
        } catch (RuntimeException exception) {
            log.error("区县日报预约失败，将重试该区县。tenantId={}, countyOrgUnitId={}",
                    scope.tenantId(), scope.countyOrgUnitId(), exception);
            appoint(scope, taskScheduler.getClock().instant().plus(FAILURE_RETRY_DELAY));
        }
    }

    private void appoint(ReportScope scope, Instant executionAt) {
        Appointment appointment = new Appointment();
        appointments.put(scope, appointment);
        appointment.future = taskScheduler.schedule(() -> trigger(scope, appointment), executionAt);
        log.info("已预约每日区县工单汇总。tenantId={}, countyOrgUnitId={}, executionAt={}",
                scope.tenantId(), scope.countyOrgUnitId(), executionAt.atZone(DailyOutstandingWorkOrderReportService.REPORT_ZONE));
    }

    private synchronized void trigger(ReportScope scope, Appointment appointment) {
        if (closed || appointments.get(scope) != appointment) {
            return;
        }
        appointments.remove(scope);
        if (!runningScopes.add(scope)) {
            return;
        }
        try {
            reportExecutor.execute(() -> dispatch(scope));
        } catch (RuntimeException exception) {
            runningScopes.remove(scope);
            log.error("日报执行线程池拒绝任务，将重试该区县。tenantId={}, countyOrgUnitId={}",
                    scope.tenantId(), scope.countyOrgUnitId(), exception);
            appoint(scope, taskScheduler.getClock().instant().plus(FAILURE_RETRY_DELAY));
        }
    }

    private void dispatch(ReportScope scope) {
        boolean failed = false;
        boolean active = false;
        try {
            active = tenantContext.callInTenant(scope.tenantId(), () -> {
                if (tenantRepository.findById(scope.tenantId())
                        .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE).isEmpty()) {
                    return false;
                }
                var countyScope = hierarchyService.activeCountyScopes(scope.tenantId()).stream()
                        .filter(county -> county.county().id().equals(scope.countyOrgUnitId()))
                        .findFirst();
                if (countyScope.isEmpty()) {
                    return false;
                }
                configurationScopeContext.runInOrgUnit(scope.countyOrgUnitId(), () -> {
                    reportService.dispatchIfDue(scope.tenantId(), countyScope.orElseThrow(), now());
                    return null;
                });
                return true;
            });
        } catch (RuntimeException exception) {
            failed = true;
            log.error("区县日报触发失败，将在投递窗口内重试。tenantId={}, countyOrgUnitId={}",
                    scope.tenantId(), scope.countyOrgUnitId(), exception);
        } finally {
            synchronized (this) {
                runningScopes.remove(scope);
                if (active || failed) {
                    planNext(scope, failed ? now().plus(FAILURE_RETRY_DELAY) : now());
                }
            }
        }
    }

    private ZonedDateTime now() {
        return ZonedDateTime.ofInstant(taskScheduler.getClock().instant(), DailyOutstandingWorkOrderReportService.REPORT_ZONE);
    }

    private void cancel(ReportScope scope) {
        Appointment previous = appointments.remove(scope);
        if (previous != null && previous.future != null) {
            previous.future.cancel(false);
        }
    }

    @PreDestroy
    public synchronized void close() {
        closed = true;
        for (ReportScope scope : Set.copyOf(appointments.keySet())) {
            cancel(scope);
        }
    }

    private record ReportScope(UUID tenantId, UUID countyOrgUnitId) {
    }

    private static final class Appointment {
        private ScheduledFuture<?> future;
    }
}
