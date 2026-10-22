package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.WorkOrderPriority;
import com.pro.ereportbacken.model.pojo.WorkOrderType;

import java.time.LocalDateTime;

public record WorkOrderSlaPolicyDTO(
        UUID id,
        WorkOrderType reportType,
        String reportTypeName,
        WorkOrderPriority priority,
        String priorityName,
        Integer claimMinutes,
        Integer assignmentAcceptanceMinutes,
        Integer responseMinutes,
        Integer verificationMinutes,
        Integer reworkMinutes,
        Boolean pauseOnHold,
        Integer maxSingleHoldMinutes,
        Integer maxTotalHoldMinutes,
        LocalDateTime updatedAt
) {
}

