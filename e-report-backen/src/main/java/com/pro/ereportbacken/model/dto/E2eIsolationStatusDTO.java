package com.pro.ereportbacken.model.dto;

public record E2eIsolationStatusDTO(
        boolean ready,
        boolean notificationDeliveryEnabled,
        boolean notificationCenterEnabled,
        boolean emailEnabled,
        boolean weComEnabled,
        boolean dingTalkEnabled,
        boolean dailyOutstandingReportEnabled,
        boolean outboxEnqueueEnabled,
        boolean outboxDeliveryEnabled,
        boolean dailyOutstandingReportSchedulerEnabled
) {
}
