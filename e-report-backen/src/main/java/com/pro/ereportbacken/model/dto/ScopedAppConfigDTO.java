package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import java.time.LocalDateTime;

public record ScopedAppConfigDTO(
        String configKey,
        String configValue,
        String description,
        LocalDateTime lastUpdatedTime,
        boolean sensitive,
        boolean configured,
        String displayValue,
        UUID requestedOrgUnitId,
        String requestedOrgUnitName,
        String sourceScope,
        UUID sourceOrgUnitId,
        String sourceOrgUnitName,
        boolean inherited
) {
}

