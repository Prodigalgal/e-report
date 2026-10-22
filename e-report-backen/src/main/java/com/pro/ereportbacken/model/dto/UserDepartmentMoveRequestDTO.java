package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UserDepartmentMoveRequestDTO(
        UUID targetDepartmentId,
        @NotNull(message = "授权版本不能为空") Long expectedAuthorizationVersion
) {
}
