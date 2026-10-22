package com.pro.ereportbacken.model.dto;

import java.util.UUID;

public record NotificationSwitchDTO(
        String name,
        String displayName,
        String description,
        boolean enabled,
        String sourceScope,
        UUID sourceOrgUnitId,
        String sourceOrgUnitName,
        boolean inherited
) {
}
