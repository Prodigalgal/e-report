package com.pro.ereportbacken.model.dto;

import java.util.List;

public record OrganizationDirectorySnapshot(List<OrgUnitDTO> orgUnits) {

    public OrganizationDirectorySnapshot {
        orgUnits = List.copyOf(orgUnits);
    }
}
