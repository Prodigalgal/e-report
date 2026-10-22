package com.pro.ereportbacken.model.dto;

import java.util.UUID;

public record AuthorizationProfileSummaryDTO(
        UUID id,
        String code,
        String displayName,
        String description,
        boolean builtIn,
        boolean active,
        UUID currentVersionId,
        Integer currentVersionNo
) {
}
