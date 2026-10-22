package com.pro.ereportbacken.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class ConfigurationScopeContext {

    private final ThreadLocal<UUID> executionOrgUnitId = new ThreadLocal<>();

    public UUID currentOrgUnitIdOrNull() {
        UUID backgroundOrgUnitId = executionOrgUnitId.get();
        if (backgroundOrgUnitId != null) {
            return backgroundOrgUnitId;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TenantUserDetails principal) {
            return principal.orgUnitId();
        }
        return null;
    }

    public <T> T runInOrgUnit(UUID orgUnitId, Supplier<T> action) {
        UUID previous = executionOrgUnitId.get();
        if (orgUnitId == null) {
            executionOrgUnitId.remove();
        } else {
            executionOrgUnitId.set(orgUnitId);
        }
        try {
            return action.get();
        } finally {
            if (previous == null) {
                executionOrgUnitId.remove();
            } else {
                executionOrgUnitId.set(previous);
            }
        }
    }
}

