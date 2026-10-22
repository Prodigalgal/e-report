package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.AuthorizationOperationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuthorizationOperationRequestDTO(
        @NotNull AuthorizationOperationType type,
        UUID grantId,
        UUID parentGrantId,
        UUID templateId,
        String permissionName,
        LocalDateTime validFrom,
        LocalDateTime expiresAt,
        Boolean grantOption,
        @Size(max = 1000) String reason
) {
}
