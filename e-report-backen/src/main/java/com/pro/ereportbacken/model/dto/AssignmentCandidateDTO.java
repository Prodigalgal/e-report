package com.pro.ereportbacken.model.dto;

import java.util.UUID;

public record AssignmentCandidateDTO(
        UUID membershipId,
        String username,
        String name,
        UUID orgUnitId,
        String orgName,
        long activeWorkOrderCount
) {
}

