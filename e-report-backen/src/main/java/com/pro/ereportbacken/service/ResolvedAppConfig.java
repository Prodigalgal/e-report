package com.pro.ereportbacken.service;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.AppConfig;

public record ResolvedAppConfig(
        AppConfig config,
        UUID requestedOrgUnitId,
        String requestedOrgUnitName,
        SourceScope sourceScope,
        UUID sourceOrgUnitId,
        String sourceOrgUnitName,
        boolean inherited
) {
    public enum SourceScope {
        PLATFORM_DEFAULT,
        TENANT_OVERRIDE,
        ORGANIZATION_OVERRIDE
    }
}

