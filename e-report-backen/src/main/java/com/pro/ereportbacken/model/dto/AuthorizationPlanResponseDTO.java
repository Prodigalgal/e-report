package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.AuthorizationOperationType;
import com.pro.ereportbacken.model.pojo.PermissionGrantStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

public record AuthorizationPlanResponseDTO(
        String planToken,
        LocalDateTime expiresAt,
        Long expectedAuthorizationVersion,
        List<OperationPreview> operations,
        int resultingActiveGrantCount
) {
    public AuthorizationPlanResponseDTO {
        operations = List.copyOf(operations);
    }

    public record OperationPreview(
            AuthorizationOperationType type,
            String title,
            String detail,
            boolean increasesAccess,
            boolean reasonRequired,
            List<PermissionChangePreview> permissionChanges
    ) {
        public OperationPreview {
            permissionChanges = permissionChanges == null ? List.of() : List.copyOf(permissionChanges);
        }
    }

    public record PermissionChangePreview(ChangeKind kind, GrantSnapshot before, GrantSnapshot after) {
        public PermissionChangePreview {
            if (kind == null) {
                throw new IllegalArgumentException("权限变更类型不能为空。");
            }
            if (kind == ChangeKind.ADD && (before != null || after == null)) {
                throw new IllegalArgumentException("新增权限必须只包含变更后数据。");
            }
            if (kind == ChangeKind.REMOVE && (before == null || after != null)) {
                throw new IllegalArgumentException("撤销权限必须只包含变更前数据。");
            }
            if (kind == ChangeKind.UPDATE && (before == null || after == null)) {
                throw new IllegalArgumentException("调整权限必须同时包含变更前后数据。");
            }
        }
    }

    public record GrantSnapshot(
            UUID grantId,
            UUID parentGrantId,
            String permissionName,
            String permissionDisplayName,
            PermissionGrantStatus status,
            boolean grantOption,
            LocalDateTime validFrom,
            LocalDateTime expiresAt
    ) {
        public GrantSnapshot {
            validFrom = validFrom == null ? null : validFrom.truncatedTo(ChronoUnit.MILLIS);
            expiresAt = expiresAt == null ? null : expiresAt.truncatedTo(ChronoUnit.MILLIS);
        }
    }

    public enum ChangeKind {
        ADD,
        UPDATE,
        REMOVE
    }
}
