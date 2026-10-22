package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.TenantStatus;

import java.time.LocalDateTime;

public record TenantDTO(
        UUID id,
        UUID tenantGroupId,
        String code,
        String name,
        TenantStatus status,
        boolean defaultTenant,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}


