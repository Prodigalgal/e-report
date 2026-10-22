package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import java.time.LocalDateTime;

public record WorkOrderResponseHistoryDTO(
        UUID id,
        Integer attemptNo,
        String resolutionDetails,
        String resolutionMethod,
        UserBasicInfoDTO submittedBy,
        LocalDateTime submittedAt,
        String verificationDecision,
        String verificationDecisionLabel,
        String verificationComment,
        UserBasicInfoDTO verifiedBy,
        LocalDateTime verifiedAt
) {
}

