package com.pro.ereportbacken.model.dto;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record WorkOrderTransferDTO(
        UUID id,
        UUID workOrderId,
        UUID sourceOrgUnitId,
        String sourceOrgUnitName,
        UUID targetOrgUnitId,
        String targetOrgUnitName,
        String status,
        Long expectedWorkOrderVersion,
        UUID requestedByMembershipId,
        LocalDateTime requestedAt,
        String requestReason,
        UUID decidedByMembershipId,
        LocalDateTime decidedAt,
        String decisionComment,
        Set<String> allowedActions
) {
    public WorkOrderTransferDTO {
        allowedActions = Set.copyOf(allowedActions);
    }
}
