package com.pro.ereportbacken.reporting;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.pojo.DailyWorkOrderReportDispatchStatus;
import com.pro.ereportbacken.model.pojo.WorkOrderStatus;
import com.pro.ereportbacken.notification.EmailNotificationRecipientResolver;
import com.pro.ereportbacken.notification.EmailNotificationCategory;
import com.pro.ereportbacken.notification.NotificationDeliveryGuard;
import com.pro.ereportbacken.repository.WorkOrderReportRepository;
import com.pro.ereportbacken.service.AppConfigService;
import com.pro.ereportbacken.service.OrganizationHierarchyService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.pro.ereportbacken.config.NotificationConstant.CENTER_ENABLED_KEY;
import static com.pro.ereportbacken.config.NotificationConstant.DAILY_OUTSTANDING_REPORT_ENABLED_KEY;
import static com.pro.ereportbacken.config.NotificationConstant.DAILY_OUTSTANDING_REPORT_SEND_TIME_KEY;
import static com.pro.ereportbacken.config.NotificationConstant.EMAIL_ENABLED_KEY;

@Service
@Log4j2
public class DailyOutstandingWorkOrderReportService {

    public static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalTime DEFAULT_SEND_TIME = LocalTime.of(18, 0);
    private static final DateTimeFormatter SEND_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> TERMINAL_STATUS_CODES = Set.of(
            WorkOrderStatus.CLOSED.getCode(),
            WorkOrderStatus.CANCELLED.getCode()
    );

    private final AppConfigService appConfigService;
    private final DailyWorkOrderReportDispatchLedger dispatchLedger;
    private final WorkOrderReportRepository reportRepository;
    private final OutstandingWorkOrderSpreadsheetExporter spreadsheetExporter;
    private final DailyOutstandingWorkOrderReportEmailSender emailSender;
    private final EmailNotificationRecipientResolver recipientResolver;
    private final NotificationDeliveryGuard deliveryGuard;
    private final Duration catchUpWindow;

    public DailyOutstandingWorkOrderReportService(
            AppConfigService appConfigService,
            DailyWorkOrderReportDispatchLedger dispatchLedger,
            WorkOrderReportRepository reportRepository,
            OutstandingWorkOrderSpreadsheetExporter spreadsheetExporter,
            DailyOutstandingWorkOrderReportEmailSender emailSender,
            EmailNotificationRecipientResolver recipientResolver,
            NotificationDeliveryGuard deliveryGuard,
            @Value("${app.daily-outstanding-report.catch-up-window-minutes:180}") int catchUpWindowMinutes) {
        this.appConfigService = appConfigService;
        this.dispatchLedger = dispatchLedger;
        this.reportRepository = reportRepository;
        this.spreadsheetExporter = spreadsheetExporter;
        this.emailSender = emailSender;
        this.recipientResolver = recipientResolver;
        this.deliveryGuard = deliveryGuard;
        this.catchUpWindow = Duration.ofMinutes(Math.max(0, catchUpWindowMinutes));
    }

    public void dispatchIfDue(UUID tenantId,
                              OrganizationHierarchyService.CountyScope countyScope,
                              ZonedDateTime now) {
        if (!enabled(tenantId, countyScope.county().id())) {
            return;
        }

        Optional<ZonedDateTime> deliveryTime = pendingDeliveryTime(now, sendTime());
        if (deliveryTime.isEmpty()) {
            return;
        }
        LocalDate reportDate = deliveryTime.orElseThrow().toLocalDate();

        LocalDateTime executionTime = now.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        UUID dispatchId;
        try {
            var claimed = dispatchLedger.claim(
                    tenantId,
                    countyScope.county().id(),
                    reportDate,
                    executionTime
            );
            if (claimed.isEmpty()) {
                return;
            }
            dispatchId = claimed.get();
        } catch (DataIntegrityViolationException duplicateClaim) {
            log.debug("日报执行权已由其他实例取得。tenantId={}, countyOrgUnitId={}, reportDate={}",
                    tenantId, countyScope.county().id(), reportDate);
            return;
        }

        processClaimedDispatch(dispatchId, tenantId, countyScope, reportDate, executionTime);
    }

    public Optional<Instant> nextExecutionAt(UUID tenantId, UUID countyOrgUnitId, ZonedDateTime now) {
        if (!enabled(tenantId, countyOrgUnitId)) {
            return Optional.empty();
        }
        LocalTime configuredSendTime = sendTime();
        Optional<ZonedDateTime> deliveryTime = pendingDeliveryTime(now, configuredSendTime);
        if (deliveryTime.isPresent()) {
            ZonedDateTime scheduledTime = deliveryTime.orElseThrow();
            Optional<Instant> nextAttempt = dispatchLedger.nextAttemptAt(
                            tenantId, countyOrgUnitId, scheduledTime.toLocalDate(),
                            now.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime())
                    .map(attempt -> attempt.toInstant(ZoneOffset.UTC))
                    .filter(attempt -> !attempt.isAfter(scheduledTime.plus(catchUpWindow).toInstant()));
            if (nextAttempt.isPresent()) {
                return nextAttempt;
            }
        }
        ZonedDateTime localNow = now.withZoneSameInstant(REPORT_ZONE);
        ZonedDateTime nextDailyTime = localNow.toLocalDate().atTime(configuredSendTime).atZone(REPORT_ZONE);
        if (!nextDailyTime.isAfter(localNow)) {
            nextDailyTime = nextDailyTime.plusDays(1);
        }
        return Optional.of(nextDailyTime.toInstant());
    }

    private void processClaimedDispatch(UUID dispatchId,
                                        UUID tenantId,
                                        OrganizationHierarchyService.CountyScope countyScope,
                                        LocalDate reportDate,
                                        LocalDateTime executionTime) {
        int workOrderCount = 0;
        int recipientCount = 0;
        try {
            List<OutstandingWorkOrderReportRow> rows = reportRepository.findOutstandingRows(
                    tenantId,
                    countyScope.descendantIds(),
                    TERMINAL_STATUS_CODES
            );
            workOrderCount = rows.size();
            if (rows.isEmpty()) {
                complete(dispatchId, DailyWorkOrderReportDispatchStatus.SKIPPED_EMPTY,
                        0, 0, 0, 0, null);
                return;
            }

            EmailNotificationRecipientResolver.RecipientSelection selection = recipientResolver.resolve(
                    tenantId, countyScope.county().id(), EmailNotificationCategory.DAILY_OUTSTANDING_REPORT);
            List<EmailNotificationRecipientResolver.EmailRecipient> recipients = selection.recipients();
            recipientCount = recipients.size();
            if (recipients.isEmpty()) {
                complete(dispatchId, DailyWorkOrderReportDispatchStatus.SKIPPED_NO_RECIPIENT,
                        workOrderCount, 0, 0, 0, selection.allUnsubscribed()
                                ? "符合收件范围的账号均已关闭每日汇总邮件订阅。"
                                : "区县管理员没有可用的邮件收件地址。");
                return;
            }

            OutstandingWorkOrderReport report = OutstandingWorkOrderReport.create(
                    tenantId,
                    countyScope.county(),
                    reportDate,
                    executionTime,
                    rows
            );
            byte[] workbook = spreadsheetExporter.export(report);
            DailyOutstandingWorkOrderReportEmailSender.DeliveryResult delivery =
                    emailSender.send(report, workbook, recipients);
            DailyWorkOrderReportDispatchStatus status = deliveryStatus(delivery, recipientCount);
            complete(dispatchId, status, workOrderCount, recipientCount,
                    delivery.successCount(), delivery.failureCount(), failureSummary(delivery));
            log.info("每日区县未处理工单汇总完成。tenantId={}, countyOrgUnitId={}, reportDate={}, status={}, workOrders={}, success={}/{}, workbookBytes={}",
                    tenantId, countyScope.county().id(), reportDate, status,
                    workOrderCount, delivery.successCount(), recipientCount, workbook.length);
        } catch (RuntimeException exception) {
            complete(dispatchId, DailyWorkOrderReportDispatchStatus.FAILED,
                    workOrderCount, recipientCount, 0, recipientCount, conciseReason(exception));
            log.error("每日区县未处理工单汇总失败。tenantId={}, countyOrgUnitId={}, reportDate={}",
                    tenantId, countyScope.county().id(), reportDate, exception);
        }
    }

    private boolean enabled(UUID tenantId, UUID countyOrgUnitId) {
        if (!deliveryGuard.deliveryEnabled()) {
            log.info("日报投递已被环境总闸关闭。tenantId={}, countyOrgUnitId={}", tenantId, countyOrgUnitId);
            return false;
        }
        for (String configKey : List.of(CENTER_ENABLED_KEY, EMAIL_ENABLED_KEY, DAILY_OUTSTANDING_REPORT_ENABLED_KEY)) {
            if (!enabled(configKey)) {
                log.info("日报投递未启用。tenantId={}, countyOrgUnitId={}, disabledSetting={}",
                        tenantId, countyOrgUnitId, configKey);
                return false;
            }
        }
        return true;
    }

    private boolean enabled(String configKey) {
        String value = configValue(configKey, "false");
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    private String configValue(String configKey, String defaultValue) {
        return appConfigService.findConfigByKey(configKey)
                .map(config -> config.getConfigValue())
                .orElse(defaultValue);
    }

    private LocalTime sendTime() {
        String configured = configValue(
                DAILY_OUTSTANDING_REPORT_SEND_TIME_KEY,
                DEFAULT_SEND_TIME.format(SEND_TIME_FORMATTER)
        );
        try {
            return LocalTime.parse(configured == null ? "" : configured.trim(), SEND_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            log.error("每日未处理工单汇总发送时间配置无效，已回退到18:00。value={}", configured);
            return DEFAULT_SEND_TIME;
        }
    }

    private Optional<ZonedDateTime> pendingDeliveryTime(ZonedDateTime now, LocalTime configuredSendTime) {
        ZonedDateTime localNow = now.withZoneSameInstant(REPORT_ZONE);
        ZonedDateTime scheduledTime = localNow.toLocalDate().atTime(configuredSendTime).atZone(REPORT_ZONE);
        if (localNow.isBefore(scheduledTime)) {
            scheduledTime = scheduledTime.minusDays(1);
        }
        return Duration.between(scheduledTime, localNow).compareTo(catchUpWindow) <= 0
                ? Optional.of(scheduledTime)
                : Optional.empty();
    }

    private DailyWorkOrderReportDispatchStatus deliveryStatus(
            DailyOutstandingWorkOrderReportEmailSender.DeliveryResult delivery,
            int recipientCount) {
        if (delivery.successCount() == recipientCount) {
            return DailyWorkOrderReportDispatchStatus.SENT;
        }
        return delivery.successCount() == 0
                ? DailyWorkOrderReportDispatchStatus.FAILED
                : DailyWorkOrderReportDispatchStatus.PARTIAL_FAILED;
    }

    private String failureSummary(DailyOutstandingWorkOrderReportEmailSender.DeliveryResult delivery) {
        if (delivery.failures().isEmpty()) {
            return null;
        }
        return delivery.failures().stream()
                .map(failure -> failure.userId() + ": " + failure.reason())
                .collect(Collectors.joining("；"));
    }

    private String conciseReason(RuntimeException exception) {
        String message = exception.getMessage();
        String reason = exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }

    private void complete(UUID dispatchId,
                          DailyWorkOrderReportDispatchStatus status,
                          int workOrderCount,
                          int recipientCount,
                          int successCount,
                          int failureCount,
                          String errorSummary) {
        dispatchLedger.complete(
                dispatchId,
                status,
                workOrderCount,
                recipientCount,
                successCount,
                failureCount,
                errorSummary,
                UtcTime.now()
        );
    }
}
