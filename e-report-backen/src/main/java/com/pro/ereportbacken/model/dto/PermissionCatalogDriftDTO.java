package com.pro.ereportbacken.model.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PermissionCatalogDriftDTO(
        LocalDateTime scannedAt,
        Status status,
        int permissionCatalogDriftCount,
        List<String> missingDatabasePermissions,
        List<DatabaseOnlyPermission> databaseOnlyPermissions
) {
    public PermissionCatalogDriftDTO {
        missingDatabasePermissions = List.copyOf(missingDatabasePermissions);
        databaseOnlyPermissions = List.copyOf(databaseOnlyPermissions);
    }

    public enum Status {
        HEALTHY,
        WARNING,
        CRITICAL
    }

    public record DatabaseOnlyPermission(
            String permissionName,
            long profileRuleReferences,
            long permissionGrantReferences,
            long apiTokenReferences,
            long totalReferences
    ) {
    }
}
