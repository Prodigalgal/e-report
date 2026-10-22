package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.security.ApiTokenAuthenticationDetails;
import com.pro.ereportbacken.security.ApiTokenAuthenticationFilter;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantUserDetails;
import com.pro.ereportbacken.security.UserAuthorizationSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpCapabilityCatalogServiceTests {

    private final McpCapabilityCatalogService capabilityCatalog = new McpCapabilityCatalogService();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsOnlyMcpToolsAllowedByCurrentApiTokenPermissions() {
        authenticateWithPermissions(PermissionCatalog.NETWORK_FAULT_VIEW);

        assertThat(capabilityCatalog.currentCapabilities())
                .extracting("id")
                .containsExactlyInAnyOrder("work-order.search", "work-order.detail");
        assertThat(capabilityCatalog.capabilityCatalog())
                .extracting("id")
                .contains("work-order.search", "work-order.detail")
                .doesNotContain("work-order.export", "work-order.attachment", "user.list");
    }

    @Test
    void ignoresRetiredPermissionsInsteadOfFailingToolDiscovery() {
        authenticateWithPermissions("retired:permission", PermissionCatalog.NETWORK_FAULT_VIEW);

        assertThat(capabilityCatalog.currentCapabilities())
                .extracting("id")
                .containsExactlyInAnyOrder("work-order.search", "work-order.detail");
    }

    @Test
    void requiresApiTokenAuthenticationContext() {
        authenticateWithPermissions(PermissionCatalog.NETWORK_FAULT_VIEW);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                authentication.getPrincipal(), null, List.of(new SimpleGrantedAuthority(PermissionCatalog.NETWORK_FAULT_VIEW))));

        assertThatThrownBy(capabilityCatalog::currentCapabilities)
                .isInstanceOf(AccessDeniedException.class);
    }

    private void authenticateWithPermissions(String... permissions) {
        UUID membershipId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        List<UserAuthorizationSnapshot.EffectivePermissionGrant> grants = java.util.Arrays.stream(permissions)
                .map(permission -> new UserAuthorizationSnapshot.EffectivePermissionGrant(
                        UUID.randomUUID(), permission, false, null, null))
                .toList();
        UserAuthorizationSnapshot snapshot = new UserAuthorizationSnapshot(
                membershipId,
                tenantId,
                UUID.randomUUID(),
                3L,
                "mcp-user",
                null,
                UserStatus.ACTIVE,
                grants
        );
        TenantUserDetails principal = new TenantUserDetails(
                membershipId,
                tenantId,
                snapshot.orgUnitId(),
                snapshot.authorizationVersion(),
                snapshot.username(),
                "",
                true,
                java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList(),
                snapshot
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(
                        new SimpleGrantedAuthority(ApiTokenAuthenticationFilter.API_TOKEN_AUTHORITY),
                        new SimpleGrantedAuthority(PermissionCatalog.NETWORK_FAULT_VIEW)
                )
        );
        authentication.setDetails(new ApiTokenAuthenticationDetails(UUID.randomUUID(), "MCP", null));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
