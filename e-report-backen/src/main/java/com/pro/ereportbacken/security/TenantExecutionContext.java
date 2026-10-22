package com.pro.ereportbacken.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class TenantExecutionContext {

    private final ThreadLocal<UUID> explicitTenantId = new ThreadLocal<>();

    public Optional<UUID> currentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof TenantUserDetails principal) {
            return Optional.of(principal.tenantId());
        }
        return Optional.ofNullable(explicitTenantId.get());
    }

    public void runInTenant(UUID tenantId, Runnable action) {
        callInTenant(tenantId, () -> {
            action.run();
            return null;
        });
    }

    public <T> T callInTenant(UUID tenantId, Supplier<T> action) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Background tenant ID is required.");
        }
        UUID previous = explicitTenantId.get();
        explicitTenantId.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                explicitTenantId.remove();
            } else {
                explicitTenantId.set(previous);
            }
        }
    }
}
