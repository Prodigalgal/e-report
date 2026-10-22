package com.pro.ereportbacken.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record WorkOrderOrganizationParticipationDTO(
        UUID id,
        UUID orgUnitId,
        String organizationName,
        String relationType,
        boolean active,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        String reason
) {
}
