package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.time.DisplayTime;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;

final class NotificationMarkdownFormatter {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private NotificationMarkdownFormatter() {
    }

    static String workOrderCreated(WorkOrderNotification notification) {
        StringBuilder content = new StringBuilder();
        content.append("### 新工单通知 #").append(notification.id()).append("\n\n");
        appendLine(content, "工单类型", notification.reportTypeName());
        appendLine(content, "故障地点", notification.location());
        appendLine(content, "定位坐标", notification.gpsCoordinates());
        appendLine(content, "联系人", notification.contactPerson());
        appendLine(content, "联系电话", notification.phone());
        appendLine(content, "上报时间", DisplayTime.formatUtc(notification.reportTime(), DATE_TIME_FORMATTER));
        appendLine(content, "问题描述", notification.reason());
        return content.toString();
    }

    static String format(WorkOrderNotification notification) {
        return notification.isSlaBreach() ? workOrderSlaBreached(notification) : workOrderCreated(notification);
    }

    private static String workOrderSlaBreached(WorkOrderNotification notification) {
        StringBuilder content = new StringBuilder();
        content.append("### 工单处理时限超时 #").append(notification.id()).append("\n\n");
        appendLine(content, "工单类型", notification.reportTypeName());
        appendLine(content, "优先级", notification.priorityName());
        appendLine(content, "超时阶段", notification.slaStageName());
        appendLine(content, "阶段截止", format(notification.slaDueAt()));
        appendLine(content, "超时确认", format(notification.slaBreachedAt()));
        appendLine(content, "故障地点", notification.location());
        return content.toString();
    }

    private static String format(java.time.LocalDateTime value) {
        return DisplayTime.formatUtc(value, DATE_TIME_FORMATTER);
    }

    private static void appendLine(StringBuilder content, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        content.append("- **").append(label).append("**：").append(value).append("\n");
    }
}
