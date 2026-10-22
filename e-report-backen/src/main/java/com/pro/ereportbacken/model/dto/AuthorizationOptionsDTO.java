package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.security.PermissionRiskLevel;

import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;

public record AuthorizationOptionsDTO(
        List<TemplateOption> templates,
        List<PermissionOption> permissions,
        List<ParentGrantOption> parentGrants
) {
    public AuthorizationOptionsDTO {
        templates = List.copyOf(templates);
        permissions = List.copyOf(permissions);
        parentGrants = List.copyOf(parentGrants);
    }

    public record TemplateOption(
            UUID id,
            String code,
            String displayName,
            String description,
            boolean builtIn,
            boolean enabled,
            List<String> permissionNames
    ) {
        public TemplateOption {
            permissionNames = List.copyOf(permissionNames);
        }
    }

    public record PermissionOption(
            String name,
            String displayName,
            String description,
            PermissionRiskLevel operationRiskLevel,
            boolean systemScoped,
            boolean apiTokenAssignable
    ) {
    }

    public record ParentGrantOption(
            UUID grantId,
            String permissionName,
            String permissionDisplayName,
            LocalDateTime expiresAt
    ) {
    }
}
