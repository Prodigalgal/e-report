package com.pro.ereportbacken.model.dto;

public record UserWorkOrderPerformanceDTO(
        long reportedTotal,
        long reportedLast30Days,
        long reportedOpen,
        long reportedClosed,
        long assignedTotal,
        long activeAssignments,
        long responsesSubmitted,
        long responsesLast30Days,
        long responsesWithin24Hours,
        long verificationsCompleted,
        long verificationsApproved,
        double averageResponseMinutes,
        double averageVerificationMinutes,
        double averageReportedClosureHours,
        double responseWithin24HourRate,
        double verificationApprovalRate
) {
}
