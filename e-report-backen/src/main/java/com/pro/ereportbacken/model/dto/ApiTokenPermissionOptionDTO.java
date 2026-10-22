package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.AccessMode;
import com.pro.ereportbacken.security.PermissionRiskLevel;

public record ApiTokenPermissionOptionDTO(
        String name,
        String displayName,
        String description,
        AccessMode accessMode,
        PermissionRiskLevel riskLevel
) {
}
