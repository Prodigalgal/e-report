package com.pro.ereportbacken.model.dto;

import java.util.List;

public record ConfigScopeDTO(
        boolean tenantWide,
        List<OrgUnitDTO> organizations
) {
    public ConfigScopeDTO {
        organizations = List.copyOf(organizations);
    }
}
