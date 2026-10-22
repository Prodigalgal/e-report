package com.pro.ereportbacken.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AuthorizationPlanRequestDTO(
        @NotNull Long expectedAuthorizationVersion,
        @NotEmpty @Size(max = 200) List<@Valid AuthorizationOperationRequestDTO> operations
) {
    public AuthorizationPlanRequestDTO {
        operations = operations == null ? List.of() : List.copyOf(operations);
    }
}
