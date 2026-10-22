package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.UserOrganizationMoveStrategy;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record UserOrganizationMovePreviewRequestDTO(
        @NotNull UUID targetOrgUnitId,
        @NotNull UserOrganizationMoveStrategy strategy,
        @NotNull @PositiveOrZero Long expectedAuthorizationVersion
) {
}
