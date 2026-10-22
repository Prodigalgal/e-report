package com.pro.ereportbacken.model.dto;

import java.util.UUID;

public record DailyOutstandingReportSettingsDTO(
        NotificationSwitchDTO switchSetting,
        String sendTime,
        String sourceScope,
        UUID sourceOrgUnitId,
        String sourceOrgUnitName,
        boolean inherited
) {
}
