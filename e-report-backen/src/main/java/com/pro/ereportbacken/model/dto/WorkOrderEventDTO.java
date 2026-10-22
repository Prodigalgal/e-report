package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import java.time.LocalDateTime;

public record WorkOrderEventDTO(
        UUID id,
        String eventType,
        String eventTypeLabel,
        String previousStatusCode,
        String nextStatusCode,
        UUID operatorMembershipId,
        String operatorName,
        String reason,
        LocalDateTime occurredAt
) {
}


