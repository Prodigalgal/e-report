package com.pro.ereportbacken.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record WorkOrderDepartmentParticipationDTO(
        UUID id,
        UUID departmentId,
        String departmentName,
        String relationType,
        boolean active,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        String reason
) {
}
