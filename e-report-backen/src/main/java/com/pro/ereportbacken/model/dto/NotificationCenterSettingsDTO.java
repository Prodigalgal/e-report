package com.pro.ereportbacken.model.dto;

import java.util.List;

public record NotificationCenterSettingsDTO(
        NotificationSwitchDTO center,
        List<NotificationSwitchDTO> channels,
        DailyOutstandingReportSettingsDTO dailyOutstandingReport,
        MailSenderSettingsDTO mailSender
) {
    public NotificationCenterSettingsDTO {
        channels = List.copyOf(channels);
    }
}
