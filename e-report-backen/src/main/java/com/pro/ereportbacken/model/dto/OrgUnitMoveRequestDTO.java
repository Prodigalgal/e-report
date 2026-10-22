package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record OrgUnitMoveRequestDTO(
        @NotNull UUID targetParentId,
        @NotBlank @Size(max = 1000) String reason
) {
}
