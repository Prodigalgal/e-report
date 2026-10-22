package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.DepartmentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record DepartmentDTO(
        UUID id,
        UUID tenantId,
        UUID orgUnitId,
        String orgUnitName,
        UUID parentId,
        String code,
        String name,
        String path,
        DepartmentStatus status,
        UUID mergedIntoDepartmentId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
