package com.pro.ereportbacken.reporting;

import com.pro.ereportbacken.model.pojo.DailyWorkOrderReportDispatchStatus;
import com.pro.ereportbacken.model.pojo.AppConfig;
import com.pro.ereportbacken.model.dto.OrgUnitDTO;
import com.pro.ereportbacken.model.pojo.OrgUnitType;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.notification.EmailNotificationRecipientResolver;
import com.pro.ereportbacken.notification.EmailNotificationCategory;
import com.pro.ereportbacken.notification.NotificationDeliveryGuard;
import com.pro.ereportbacken.repository.WorkOrderReportRepository;
import com.pro.ereportbacken.service.AppConfigService;
import com.pro.ereportbacken.service.OrganizationHierarchyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class DailyOutstandingWorkOrderReportServiceTests {

    private final UUID tenantId = uuid(1);
    private final UUID countyId = uuid(2);
    private final UUID childOrgId = uuid(3);
    private final UUID dispatchId = uuid(4);
    private final AppConfigService appConfigService = mock(AppConfigService.class);
    private final DailyWorkOrderReportDispatchLedger dispatchLedger = mock(DailyWorkOrderReportDispatchLedger.class);
    private final WorkOrderReportRepository reportRepository = mock(WorkOrderReportRepository.class);
    private final OutstandingWorkOrderSpreadsheetExporter exporter = mock(OutstandingWorkOrderSpreadsheetExporter.class);
    private final DailyOutstandingWorkOrderReportEmailSender emailSender = mock(DailyOutstandingWorkOrderReportEmailSender.class);
    private final EmailNotificationRecipientResolver recipientResolver = mock(EmailNotificationRecipientResolver.class);
    private final DailyOutstandingWorkOrderReportService service = new DailyOutstandingWorkOrderReportService(
            appConfigService, dispatchLedger, reportRepository, exporter, emailSender, recipientResolver,
            new NotificationDeliveryGuard(true), 180);

    @BeforeEach
    void enableReport() {
        when(recipientResolver.resolve(any(), any(), eq(EmailNotificationCategory.DAILY_OUTSTANDING_REPORT)))
                .thenReturn(new EmailNotificationRecipientResolver.RecipientSelection(List.of(), 0, 0));
        configure("notification.center.enabled", "true");
        configure("notification.email.enabled", "true");
        configure("notification.daily-outstanding-report.enabled", "true");
        configure("notification.daily-outstanding-report.send-time", "18:00");
    }

    @Test
    void doesNotClaimDispatchBeforeConfiguredTime() {
        service.dispatchIfDue(tenantId, countyScope(), at(17, 59));

        verify(dispatchLedger, never()).claim(any(), any(), any(), any());
    }

    @Test
    void recordsEmptyCountyWithoutSendingEmail() {
        ZonedDateTime scheduledTime = at(18, 0);
        LocalDateTime persistenceTime = scheduledTime
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        when(dispatchLedger.claim(
                tenantId,
                countyId,
                scheduledTime.toLocalDate(),
                persistenceTime
        )).thenReturn(Optional.of(dispatchId));
        when(reportRepository.findOutstandingRows(eq(tenantId), eq(Set.of(countyId, childOrgId)), any()))
                .thenReturn(List.of());

        service.dispatchIfDue(tenantId, countyScope(), scheduledTime);

        verify(dispatchLedger).claim(tenantId, countyId, scheduledTime.toLocalDate(), persistenceTime);
        verify(reportRepository).findOutstandingRows(
                eq(tenantId), eq(Set.of(countyId, childOrgId)), eq(Set.of("CLOSED", "CANCELLED")));
        verify(dispatchLedger).complete(
                eq(dispatchId), eq(DailyWorkOrderReportDispatchStatus.SKIPPED_EMPTY),
                eq(0), eq(0), eq(0), eq(0), eq(null), any(LocalDateTime.class));
        verify(emailSender, never()).send(any(), any(), any());
    }

    @Test
    void disabledNotificationCenterDoesNotClaimDispatch() {
        configure("notification.center.enabled", "false");

        service.dispatchIfDue(tenantId, countyScope(), at(18, 30));

        verify(dispatchLedger, never()).claim(any(), any(), any(), any());
    }

    @Test
    void globalDeliveryGuardDoesNotClaimDispatch() {
        DailyOutstandingWorkOrderReportService guardedService = new DailyOutstandingWorkOrderReportService(
                appConfigService, dispatchLedger, reportRepository, exporter, emailSender, recipientResolver,
                new NotificationDeliveryGuard(false), 180);

        guardedService.dispatchIfDue(tenantId, countyScope(), at(18, 30));

        verify(dispatchLedger, never()).claim(any(), any(), any(), any());
    }

    @Test
    void doesNotSendAStaleReportAfterCatchUpWindow() {
        service.dispatchIfDue(tenantId, countyScope(), at(21, 1));

        verify(dispatchLedger, never()).claim(any(), any(), any(), any());
    }

    @Test
    void closedEmailChannelPreventsBothDispatchAndScheduling() {
        configure("notification.email.enabled", "false");

        service.dispatchIfDue(tenantId, countyScope(), at(18, 0));

        assertThat(service.nextExecutionAt(tenantId, countyId, at(17, 0))).isEmpty();
        verify(dispatchLedger, never()).claim(any(), any(), any(), any());
    }

    @Test
    void disabledReportDoesNotCreateAnAppointment() {
        configure("notification.daily-outstanding-report.enabled", "false");

        assertThat(service.nextExecutionAt(tenantId, countyId, at(17, 0))).isEmpty();
    }

    @Test
    void schedulesTheConfiguredBeijingTimeAsAnInstant() {
        configure("notification.daily-outstanding-report.send-time", "19:25");

        assertThat(service.nextExecutionAt(tenantId, countyId, at(17, 59)))
                .contains(Instant.parse("2026-08-07T11:25:00Z"));
    }

    @Test
    void restartsInsideTheWindowCanDispatchImmediately() {
        ZonedDateTime now = at(18, 42);
        when(dispatchLedger.nextAttemptAt(tenantId, countyId, now.toLocalDate(), utc(now)))
                .thenReturn(Optional.of(utc(now)));

        assertThat(service.nextExecutionAt(tenantId, countyId, now)).contains(now.toInstant());
    }

    @Test
    void finishedReportsWaitUntilTheNextDay() {
        ZonedDateTime now = at(18, 42);
        when(dispatchLedger.nextAttemptAt(tenantId, countyId, now.toLocalDate(), utc(now)))
                .thenReturn(Optional.empty());

        assertThat(service.nextExecutionAt(tenantId, countyId, now))
                .contains(Instant.parse("2026-08-08T10:00:00Z"));
    }

    @Test
    void processingReportsWaitUntilTheirLeaseExpires() {
        ZonedDateTime now = at(18, 42);
        LocalDateTime leaseExpiresAt = utc(at(19, 0)).plusSeconds(1);
        when(dispatchLedger.nextAttemptAt(tenantId, countyId, now.toLocalDate(), utc(now)))
                .thenReturn(Optional.of(leaseExpiresAt));

        assertThat(service.nextExecutionAt(tenantId, countyId, now))
                .contains(leaseExpiresAt.toInstant(ZoneOffset.UTC));
    }

    @Test
    void doesNotRecoverALeaseOutsideTheDeliveryWindow() {
        ZonedDateTime now = at(20, 59);
        when(dispatchLedger.nextAttemptAt(tenantId, countyId, now.toLocalDate(), utc(now)))
                .thenReturn(Optional.of(utc(at(21, 59))));

        assertThat(service.nextExecutionAt(tenantId, countyId, now))
                .contains(Instant.parse("2026-08-08T10:00:00Z"));
    }

    @Test
    void restartOutsideTheWindowWaitsUntilTheNextDay() {
        assertThat(service.nextExecutionAt(tenantId, countyId, at(21, 1)))
                .contains(Instant.parse("2026-08-08T10:00:00Z"));
    }

    @Test
    void invalidTimeFallsBackToEighteenOClock() {
        configure("notification.daily-outstanding-report.send-time", "invalid");

        assertThat(service.nextExecutionAt(tenantId, countyId, at(17, 0)))
                .contains(Instant.parse("2026-08-07T10:00:00Z"));
    }

    @Test
    void catchUpAcrossMidnightUsesTheScheduledBeijingDate() {
        configure("notification.daily-outstanding-report.send-time", "23:30");
        ZonedDateTime now = at(0, 30).plusDays(1);
        LocalDateTime executionTime = utc(now);
        when(dispatchLedger.claim(tenantId, countyId, now.minusDays(1).toLocalDate(), executionTime))
                .thenReturn(Optional.of(dispatchId));
        when(reportRepository.findOutstandingRows(eq(tenantId), any(), any())).thenReturn(List.of());

        service.dispatchIfDue(tenantId, countyScope(), now.withZoneSameInstant(ZoneOffset.UTC));

        verify(dispatchLedger).claim(tenantId, countyId, now.minusDays(1).toLocalDate(), executionTime);
    }

    @Test
    void schedulesPreviousDaysPendingReportWithinTheMidnightCatchUpWindow() {
        configure("notification.daily-outstanding-report.send-time", "23:30");
        ZonedDateTime now = at(0, 30).plusDays(1);
        when(dispatchLedger.nextAttemptAt(tenantId, countyId, now.minusDays(1).toLocalDate(), utc(now)))
                .thenReturn(Optional.of(utc(now)));

        assertThat(service.nextExecutionAt(tenantId, countyId, now)).contains(now.toInstant());
    }

    @Test
    void reportSentAfterMidnightCarriesItsScheduledDateAndUtcGenerationTime() {
        configure("notification.daily-outstanding-report.send-time", "23:30");
        ZonedDateTime now = at(0, 30).plusDays(1);
        var recipients = List.of(new EmailNotificationRecipientResolver.EmailRecipient(uuid(5), "test@example.com", false));
        when(dispatchLedger.claim(tenantId, countyId, now.minusDays(1).toLocalDate(), utc(now)))
                .thenReturn(Optional.of(dispatchId));
        when(reportRepository.findOutstandingRows(eq(tenantId), any(), any())).thenReturn(List.of(row()));
        when(recipientResolver.resolve(tenantId, countyId, EmailNotificationCategory.DAILY_OUTSTANDING_REPORT))
                .thenReturn(new EmailNotificationRecipientResolver.RecipientSelection(recipients, recipients.size(), 0));
        when(exporter.export(any())).thenReturn(new byte[]{1, 2});
        when(emailSender.send(any(), any(), eq(recipients)))
                .thenReturn(new DailyOutstandingWorkOrderReportEmailSender.DeliveryResult(1, List.of(), null));

        service.dispatchIfDue(tenantId, countyScope(), now);

        ArgumentCaptor<OutstandingWorkOrderReport> report = ArgumentCaptor.forClass(OutstandingWorkOrderReport.class);
        verify(emailSender).send(report.capture(), any(), eq(recipients));
        assertThat(report.getValue().reportDate()).isEqualTo(now.minusDays(1).toLocalDate());
        assertThat(report.getValue().generatedAt()).isEqualTo(utc(now));
        assertThat(report.getValue().countyOrgUnitId()).isEqualTo(countyId);
        verify(dispatchLedger).complete(eq(dispatchId), eq(DailyWorkOrderReportDispatchStatus.SENT),
                eq(1), eq(1), eq(1), eq(0), eq(null), any());
    }

    @Test
    void competingInstancesDoNotBothExportOrSendTheReport() {
        when(dispatchLedger.claim(any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("scope and date already claimed"));

        service.dispatchIfDue(tenantId, countyScope(), at(18, 0));

        verify(exporter, never()).export(any());
        verify(emailSender, never()).send(any(), any(), any());
    }

    @Test
    void failedRecipientsAreRecordedWithoutLosingSuccessfulDeliveries() {
        ZonedDateTime now = at(18, 0);
        var recipients = List.of(
                new EmailNotificationRecipientResolver.EmailRecipient(uuid(5), "first@example.com", false),
                new EmailNotificationRecipientResolver.EmailRecipient(uuid(6), "second@example.com", false));
        when(dispatchLedger.claim(any(), any(), any(), any())).thenReturn(Optional.of(dispatchId));
        when(reportRepository.findOutstandingRows(eq(tenantId), any(), any())).thenReturn(List.of(row()));
        when(recipientResolver.resolve(tenantId, countyId, EmailNotificationCategory.DAILY_OUTSTANDING_REPORT))
                .thenReturn(new EmailNotificationRecipientResolver.RecipientSelection(recipients, recipients.size(), 0));
        when(exporter.export(any())).thenReturn(new byte[]{1, 2});
        when(emailSender.send(any(), any(), eq(recipients)))
                .thenReturn(new DailyOutstandingWorkOrderReportEmailSender.DeliveryResult(1,
                        List.of(new DailyOutstandingWorkOrderReportEmailSender.DeliveryFailure(uuid(6), "SMTP timeout")), null));

        service.dispatchIfDue(tenantId, countyScope(), now);

        verify(dispatchLedger).complete(eq(dispatchId), eq(DailyWorkOrderReportDispatchStatus.PARTIAL_FAILED),
                eq(1), eq(2), eq(1), eq(1), eq(uuid(6) + ": SMTP timeout"), any());
    }

    @Test
    void incompleteRecipientBatchIsRescheduledAtItsCooldownWithoutCompletingTheReport() {
        var now = at(18, 0);
        var retryAt = now.toInstant().plusSeconds(10);
        prepareRecipientBatch(retryAt);
        service.dispatchIfDue(tenantId, countyScope(), now);
        verify(dispatchLedger).defer(dispatchId, LocalDateTime.ofInstant(retryAt, ZoneOffset.UTC), 1, 2, 1, 0);
        verify(dispatchLedger, never()).complete(any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), any(), any());
        when(dispatchLedger.nextAttemptAt(tenantId, countyId, now.toLocalDate(), utc(now)))
                .thenReturn(Optional.of(LocalDateTime.ofInstant(retryAt, ZoneOffset.UTC)));
        assertThat(service.nextExecutionAt(tenantId, countyId, now)).contains(retryAt);
    }

    @Test
    void backoffBeyondTheDeliveryWindowFinishesTheReportWithItsExistingSuccesses() {
        var now = at(20, 59);
        prepareRecipientBatch(now.toInstant().plusSeconds(120));
        service.dispatchIfDue(tenantId, countyScope(), now);
        verify(dispatchLedger).complete(eq(dispatchId), eq(DailyWorkOrderReportDispatchStatus.PARTIAL_FAILED),
                eq(1), eq(2), eq(1), eq(1), org.mockito.ArgumentMatchers.contains("超过当日报告发送窗口"), any());
        verify(dispatchLedger, never()).defer(any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    private void prepareRecipientBatch(Instant retryAt) {
        var recipients = List.of(
                new EmailNotificationRecipientResolver.EmailRecipient(uuid(5), "first@example.com", false),
                new EmailNotificationRecipientResolver.EmailRecipient(uuid(6), "second@example.com", false));
        when(dispatchLedger.claim(any(), any(), any(), any())).thenReturn(Optional.of(dispatchId));
        when(reportRepository.findOutstandingRows(eq(tenantId), any(), any())).thenReturn(List.of(row()));
        when(recipientResolver.resolve(tenantId, countyId, EmailNotificationCategory.DAILY_OUTSTANDING_REPORT))
                .thenReturn(new EmailNotificationRecipientResolver.RecipientSelection(recipients, recipients.size(), 0));
        when(exporter.export(any())).thenReturn(new byte[]{1, 2});
        when(emailSender.send(any(), any(), eq(recipients)))
                .thenReturn(new DailyOutstandingWorkOrderReportEmailSender.DeliveryResult(1, List.of(), retryAt));
    }

    private OutstandingWorkOrderReportRow row() {
        return new OutstandingWorkOrderReportRow(uuid(7), "TEST-001", null, "SUBMITTED", null,
                "测试地点", "故障", null, null, "报单人", "reporter", null, null,
                utc(at(16, 0)), utc(at(17, 0)), null, null);
    }

    private void configure(String key, String value) {
        when(appConfigService.findConfigByKey(key)).thenReturn(Optional.of(new AppConfig(key, value, null)));
    }

    private LocalDateTime utc(ZonedDateTime time) {
        return time.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private OrganizationHierarchyService.CountyScope countyScope() {
        OrgUnitDTO county = new OrgUnitDTO(
                countyId,
                tenantId,
                null,
                "NANJING",
                "南靖县",
                OrgUnitType.COUNTY,
                "/" + countyId + "/",
              TenantStatus.ACTIVE,
              false
        );
        return new OrganizationHierarchyService.CountyScope(county, Set.of(countyId, childOrgId));
    }

    private ZonedDateTime at(int hour, int minute) {
        return ZonedDateTime.of(2026, 8, 7, hour, minute, 0, 0,
                DailyOutstandingWorkOrderReportService.REPORT_ZONE);
    }

    @Test
    void allDailyReportRecipientsCanUnsubscribeWithoutExportOrSmtp() {
        when(dispatchLedger.claim(any(), any(), any(), any())).thenReturn(Optional.of(dispatchId));
        when(reportRepository.findOutstandingRows(eq(tenantId), any(), any())).thenReturn(List.of(row()));
        when(recipientResolver.resolve(tenantId, countyId, EmailNotificationCategory.DAILY_OUTSTANDING_REPORT))
                .thenReturn(new EmailNotificationRecipientResolver.RecipientSelection(List.of(), 0, 2));

        service.dispatchIfDue(tenantId, countyScope(), at(18, 0));

        verify(exporter, never()).export(any());
        verify(emailSender, never()).send(any(), any(), any());
        verify(dispatchLedger).complete(eq(dispatchId), eq(DailyWorkOrderReportDispatchStatus.SKIPPED_NO_RECIPIENT),
                eq(1), eq(0), eq(0), eq(0), eq("符合收件范围的账号均已关闭每日汇总邮件订阅。"), any());
    }

    private UUID uuid(long seed) {
        return com.pro.ereportbacken.TestIds.uuid(seed);
    }
}
