package com.pro.ereportbacken.model.dto;

import java.util.List;
import java.util.UUID;

public record DepartmentMutationPreviewDTO(
        String operation,
        UUID sourceDepartmentId,
        String sourceDepartmentName,
        UUID targetDepartmentId,
        String targetDepartmentName,
        UUID fallbackDepartmentId,
        String fallbackDepartmentName,
        long directChildDepartmentCount,
        long affectedUserCount,
        long affectedWorkOrderCount,
        List<String> changes,
        List<String> warnings
) {
    public DepartmentMutationPreviewDTO {
        changes = List.copyOf(changes);
        warnings = List.copyOf(warnings);
    }
}
