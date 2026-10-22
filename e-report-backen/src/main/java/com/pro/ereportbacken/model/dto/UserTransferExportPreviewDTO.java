package com.pro.ereportbacken.model.dto;

import java.util.List;
import java.time.LocalDateTime;

public record UserTransferExportPreviewDTO(
        long userCount,
        long grantCount,
        long estimatedFileSize,
        int maximumUserCount,
        long maximumDetailRows,
        long maximumFileSize,
        String previewToken,
        LocalDateTime previewExpiresAt,
        FilterPreview filters,
        List<SheetPreview> sheets
) {
    public UserTransferExportPreviewDTO {
        sheets = List.copyOf(sheets);
    }

    public record FilterPreview(
            String searchTerm,
            String organizationName,
            String departmentName,
            String statusLabel,
            boolean includesDeleted
    ) {
    }

    public record SheetPreview(
            String name,
            List<String> fields
    ) {
        public SheetPreview {
            fields = List.copyOf(fields);
        }
    }
}
