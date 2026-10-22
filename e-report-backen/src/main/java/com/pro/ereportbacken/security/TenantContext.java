package com.pro.ereportbacken.security;

import java.util.UUID;
import java.util.function.Supplier;

import com.pro.ereportbacken.service.DefaultTenantService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class TenantContext {

    private final DefaultTenantService defaultTenantService;
    private final TenantExecutionContext tenantExecutionContext;

    public TenantContext(DefaultTenantService defaultTenantService,
                         TenantExecutionContext tenantExecutionContext) {
        this.defaultTenantService = defaultTenantService;
        this.tenantExecutionContext = tenantExecutionContext;
    }

    public UUID requireTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof TenantUserDetails principal) {
            return principal.tenantId();
        }
        var executionTenantId = tenantExecutionContext.currentTenantId();
        if (executionTenantId.isPresent()) {
            return executionTenantId.orElseThrow();
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authenticated tenant context is required.");
        }
        return defaultTenantService.requireDefaultTenant().getId();
    }

    public UUID currentTenantIdOrDefault() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TenantUserDetails principal) {
            return principal.tenantId();
        }
        var executionTenantId = tenantExecutionContext.currentTenantId();
        if (executionTenantId.isPresent()) {
            return executionTenantId.orElseThrow();
        }
        return defaultTenantService.requireDefaultTenant().getId();
    }

    public void runInTenant(UUID tenantId, Runnable action) {
        tenantExecutionContext.runInTenant(tenantId, action);
    }

    public <T> T callInTenant(UUID tenantId, Supplier<T> action) {
        return tenantExecutionContext.callInTenant(tenantId, action);
    }
}

