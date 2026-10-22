package com.pro.ereportbacken.model.dto;

import java.util.List;

public record UserTransferAuthorizationDiffDTO(
        String summary,
        List<AuthorizationPlanResponseDTO.PermissionChangePreview> changes
) {
    public UserTransferAuthorizationDiffDTO {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
