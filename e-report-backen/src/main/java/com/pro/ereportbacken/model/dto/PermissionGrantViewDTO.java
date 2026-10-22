package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.GrantIssuerType;
import com.pro.ereportbacken.model.pojo.PermissionGrantStatus;
import com.pro.ereportbacken.security.PermissionRiskLevel;

import java.time.LocalDateTime;
import java.util.UUID;

public record PermissionGrantViewDTO(
        UUID id,
        UUID parentGrantId,
        String permissionName,
        String permissionDisplayName,
        String description,
        PermissionRiskLevel operationRiskLevel,
        PermissionGrantStatus status,
        boolean effective,
        boolean grantOption,
        LocalDateTime validFrom,
        LocalDateTime expiresAt,
        GrantIssuerType issuerType,
        UUID grantedByMembershipId,
        LocalDateTime grantedAt,
        String reason,
        boolean manageable,
        String manageabilityReason
) {
}
