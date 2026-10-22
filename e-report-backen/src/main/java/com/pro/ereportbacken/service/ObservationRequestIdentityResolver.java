package com.pro.ereportbacken.service;

import com.pro.ereportbacken.security.TenantUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ObservationRequestIdentityResolver {

    private final DefaultTenantService defaultTenantService;

    public ObservationRequestIdentityResolver(DefaultTenantService defaultTenantService) {
        this.defaultTenantService = defaultTenantService;
    }

    public ObservationIdentity resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof TenantUserDetails principal) {
            return new ObservationIdentity(principal.tenantId(), principal.orgUnitId(), principal.membershipId(), true);
        }
        return new ObservationIdentity(defaultTenantService.requireDefaultTenant().getId(), null, null, false);
    }

    public record ObservationIdentity(UUID tenantId, UUID orgUnitId, UUID userId, boolean authenticated) {
    }
}
