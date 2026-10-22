package com.pro.ereportbacken.reporting;

import com.pro.ereportbacken.notification.ConfiguredMailSenderFactory;
import com.pro.ereportbacken.notification.EmailNotificationRecipientResolver;
import com.pro.ereportbacken.notification.delivery.EmailDeliveryCategory;
import com.pro.ereportbacken.notification.delivery.EmailDeliveryContext;
import com.pro.ereportbacken.notification.delivery.EmailDeliveryTracker;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Log4j2
public class DailyOutstandingWorkOrderReportEmailSender {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    private final ConfiguredMailSenderFactory mailSenderFactory;
    private final EmailDeliveryTracker deliveryTracker;

    public DailyOutstandingWorkOrderReportEmailSender(ConfiguredMailSenderFactory mailSenderFactory,
                                                     EmailDeliveryTracker deliveryTracker) {
        this.mailSenderFactory = mailSenderFactory;
        this.deliveryTracker = deliveryTracker;
    }

    public DeliveryResult send(OutstandingWorkOrderReport report,
                               byte[] workbook,
                               List<EmailNotificationRecipientResolver.EmailRecipient> recipients) {
        List<DeliveryFailure> failures = new ArrayList<>();
        int successCount = 0;
        ConfiguredMailSenderFactory.MailSenderContext mailContext;
        try {
            mailContext = mailSenderFactory.next();
        } catch (RuntimeException exception) {
            recipients.forEach(recipient -> {
                deliveryTracker.configurationFailed(deliveryContext(report, recipient));
                failures.add(new DeliveryFailure(recipient.userId(), conciseReason(exception)));
            });
            return new DeliveryResult(0, failures);
        }

        String fileName = report.countyName() + "_未处理工单_" + report.reportDate() + ".xlsx";
        for (EmailNotificationRecipientResolver.EmailRecipient recipient : recipients) {
            try {
                deliveryTracker.send(deliveryContext(report, recipient), mailContext.fromAddress(),
                        () -> sendToRecipient(mailContext, report, workbook, fileName, recipient));
                successCount++;
            } catch (MessagingException | MailException exception) {
                failures.add(new DeliveryFailure(recipient.userId(), conciseReason(exception)));
                log.error("每日工单汇总邮件投递失败。tenantId={}, countyOrgUnitId={}, reportDate={}, userId={}, address={}, reason={}",
                        report.tenantId(), report.countyOrgUnitId(), report.reportDate(), recipient.userId(),
                        EmailNotificationRecipientResolver.maskAddress(recipient.address()), exception.getMessage(), exception);
            }
        }
        return new DeliveryResult(successCount, failures);
    }

    private EmailDeliveryContext deliveryContext(OutstandingWorkOrderReport report,
                                                 EmailNotificationRecipientResolver.EmailRecipient recipient) {
        return new EmailDeliveryContext(report.tenantId(), report.countyOrgUnitId(),
                EmailDeliveryCategory.DAILY_OUTSTANDING_REPORT, null, recipient.userId(), recipient.address(),
                "【每日工单汇总】" + report.countyName() + "未处理工单 " + report.rows().size() + " 单");
    }

    private void sendToRecipient(ConfiguredMailSenderFactory.MailSenderContext mailContext,
                                 OutstandingWorkOrderReport report,
                                 byte[] workbook,
                                 String fileName,
                                 EmailNotificationRecipientResolver.EmailRecipient recipient)
            throws MessagingException {
        JavaMailSender mailSender = mailContext.mailSender();
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(mailContext.fromAddress());
        helper.setTo(recipient.address());
        helper.setSubject("【每日工单汇总】" + report.countyName() + "未处理工单 " + report.rows().size() + " 单");
        helper.setText(buildHtml(report), true);
        helper.addAttachment(fileName, new ByteArrayResource(workbook), XLSX_MEDIA_TYPE);
        mailSender.send(message);
    }

    private String buildHtml(OutstandingWorkOrderReport report) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,Microsoft YaHei,sans-serif;line-height:1.6;color:#222'>")
                .append("<h2>").append(escapeHtml(report.countyName())).append("未处理工单日报</h2>")
                .append("<p>统计日期：").append(DATE_FORMATTER.format(report.reportDate())).append("</p>")
                .append("<table border='1' cellpadding='8' style='border-collapse:collapse;max-width:760px;width:100%'>");
        appendSummaryRow(html, "未处理工单", report.rows().size() + " 单");
        appendSummaryRow(html, "当前超时", report.currentOverdueCount() + " 单");
        appendSummaryRow(html, "最长历时", formatDuration(report.oldestElapsed()));
        appendSummaryRow(html, "状态分布", formatCounts(report.statusCounts()));
        appendSummaryRow(html, "类型分布", formatCounts(report.typeCounts()));
        html.append("</table>")
                .append("<p>逐单明细见附件。请登录系统及时接单、处理或调度。</p>")
                .append("<p style='font-size:12px;color:#666'>此邮件由系统自动发送，请勿直接回复。</p>")
                .append("</body></html>");
        return html.toString();
    }

    private void appendSummaryRow(StringBuilder html, String label, String value) {
        html.append("<tr><th align='left' style='width:160px;background:#f3f6fa'>")
                .append(escapeHtml(label))
                .append("</th><td>")
                .append(escapeHtml(value))
                .append("</td></tr>");
    }

    private String formatCounts(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue() + " 单")
                .reduce((left, right) -> left + "；" + right)
                .orElse("无");
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.minusDays(days).toHours();
        long minutes = duration.minusDays(days).minusHours(hours).toMinutes();
        return days > 0 ? days + "天" + hours + "小时" : hours + "小时" + minutes + "分钟";
    }

    private String conciseReason(Exception exception) {
        String message = exception.getMessage();
        String reason = exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
        return reason.length() <= 240 ? reason : reason.substring(0, 240);
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public record DeliveryResult(int successCount, List<DeliveryFailure> failures) {
        public DeliveryResult {
            failures = List.copyOf(failures);
        }

        public int failureCount() {
            return failures.size();
        }
    }

    public record DeliveryFailure(UUID userId, String reason) {
    }
}
