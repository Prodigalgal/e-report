package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.OrgUnitType;
import com.pro.ereportbacken.model.pojo.TenantStatus;

public record OrgUnitDTO(
        UUID id,
        UUID tenantId,
        UUID parentId,
        String code,
        String name,
        OrgUnitType unitType,
        String path,
        TenantStatus status,
        boolean tenantRoot
) {
}


