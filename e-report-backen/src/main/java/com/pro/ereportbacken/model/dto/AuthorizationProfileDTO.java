package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.AuthorizationProfileVersionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AuthorizationProfileDTO(
        UUID id,
        String code,
        String displayName,
        String description,
        boolean builtIn,
        boolean active,
        Version currentVersion,
        Version draftVersion
) {
    public record Version(
            UUID id,
            int versionNo,
            AuthorizationProfileVersionStatus status,
            String changeSummary,
            LocalDateTime createdAt,
            LocalDateTime publishedAt,
            List<Rule> rules
    ) {
        public Version {
            rules = List.copyOf(rules);
        }
    }

    public record Rule(
            UUID id,
            String permissionName,
            String permissionDisplayName,
            int sortOrder
    ) {
    }
}
