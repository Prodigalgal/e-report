package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.UserStatus;

import java.util.UUID;

public record UserTransferExportRequestDTO(
        String searchTerm,
        UUID orgUnitId,
        UUID departmentId,
        UserStatus status,
        Boolean includeDeleted,
        String previewToken
) {

    public UserTransferExportRequestDTO(String searchTerm,
                                        UUID orgUnitId,
                                        UserStatus status,
                                        Boolean includeDeleted) {
        this(searchTerm, orgUnitId, null, status, includeDeleted, null);
    }

    public UserTransferExportRequestDTO(String searchTerm,
                                        UUID orgUnitId,
                                        UserStatus status,
                                        Boolean includeDeleted,
                                        String previewToken) {
        this(searchTerm, orgUnitId, null, status, includeDeleted, previewToken);
    }

    public boolean shouldIncludeDeleted() {
        return Boolean.TRUE.equals(includeDeleted);
    }

    public UserTransferExportRequestDTO withoutPreviewToken() {
        return new UserTransferExportRequestDTO(
                searchTerm, orgUnitId, departmentId, status, includeDeleted, null);
    }
}
