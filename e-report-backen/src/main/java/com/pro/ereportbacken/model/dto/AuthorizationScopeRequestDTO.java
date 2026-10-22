package com.pro.ereportbacken.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record AuthorizationScopeRequestDTO(
        @NotNull Boolean tenantWide,
        List<@Valid Node> nodes
) {
    public AuthorizationScopeRequestDTO {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    public record Node(
            @NotNull UUID orgUnitId,
            boolean includeDescendants
    ) {
    }
}
