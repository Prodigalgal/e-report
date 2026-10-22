package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WorkOrderSlaPolicyUpdateRequestDTO(
        @NotNull @Min(1) @Max(525600) Integer claimMinutes,
        @NotNull @Min(1) @Max(525600) Integer assignmentAcceptanceMinutes,
        @NotNull @Min(1) @Max(525600) Integer responseMinutes,
        @NotNull @Min(1) @Max(525600) Integer verificationMinutes,
        @NotNull @Min(1) @Max(525600) Integer reworkMinutes,
        @NotNull Boolean pauseOnHold,
        @NotNull @Min(0) @Max(525600) Integer maxSingleHoldMinutes,
        @NotNull @Min(0) @Max(525600) Integer maxTotalHoldMinutes
) {
}
