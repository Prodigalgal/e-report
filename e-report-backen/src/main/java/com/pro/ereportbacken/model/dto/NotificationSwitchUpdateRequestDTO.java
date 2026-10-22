package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotNull;

public record NotificationSwitchUpdateRequestDTO(
        @NotNull(message = "通知开关值不能为空") Boolean enabled
) {
}
