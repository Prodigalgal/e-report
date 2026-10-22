package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.ApiTokenStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ApiTokenDTO(
        UUID id,
        String name,
        String tokenPrefix,
        Instant createdAt,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant revokedAt,
        ApiTokenStatus status,
        Set<String> permissionNames
) {
    public ApiTokenDTO {
        permissionNames = Set.copyOf(permissionNames);
    }
}
