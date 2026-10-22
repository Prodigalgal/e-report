package com.pro.ereportbacken.model.dto;

public record UserTransferPreviewSummaryDTO(
        int userCount,
        int affectedUserCount,
        int blockedUserCount,
        int validCommandCount,
        int invalidCommandCount,
        int unchangedCommandCount,
        int fieldChangeCount,
        int addedPermissionCount,
        int updatedPermissionCount,
        int removedPermissionCount
) {
}
