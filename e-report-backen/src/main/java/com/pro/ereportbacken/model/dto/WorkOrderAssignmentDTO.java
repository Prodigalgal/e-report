package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import java.time.LocalDateTime;

public record WorkOrderAssignmentDTO(
        UUID id,
        String state,
        String assignmentType,
        UUID membershipId,
        String username,
        String name,
        UUID responsibleOrgId,
        UUID responsibleDepartmentId,
        String reason,
        LocalDateTime assignedAt,
        LocalDateTime acceptedAt
) {
}


