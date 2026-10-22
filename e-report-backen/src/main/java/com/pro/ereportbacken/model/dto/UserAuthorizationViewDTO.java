package com.pro.ereportbacken.model.dto;

import java.util.List;
import java.util.UUID;

public record UserAuthorizationViewDTO(
        UUID membershipId,
        Long authorizationVersion,
        List<PermissionGrantViewDTO> grants,
        int effectivePermissionCount
) {
    public UserAuthorizationViewDTO {
        grants = List.copyOf(grants);
    }
}
