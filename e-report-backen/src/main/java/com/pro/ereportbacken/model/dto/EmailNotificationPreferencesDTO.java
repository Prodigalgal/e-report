package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotNull;

public record EmailNotificationPreferencesDTO(
        @NotNull Boolean workOrderCreated,
        @NotNull Boolean workOrderSlaBreached,
        @NotNull Boolean workOrderAssigned,
        @NotNull Boolean dailyOutstandingReport
) {
}
