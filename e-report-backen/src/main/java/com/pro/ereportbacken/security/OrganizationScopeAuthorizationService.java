package com.pro.ereportbacken.security;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.pojo.UserSystemKey;
import com.pro.ereportbacken.service.OrganizationHierarchyService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class OrganizationScopeAuthorizationService {

    private final TenantContext tenantContext;
    private final OrganizationHierarchyService hierarchyService;

    public OrganizationScopeAuthorizationService(TenantContext tenantContext,
                                                 OrganizationHierarchyService hierarchyService) {
        this.tenantContext = tenantContext;
        this.hierarchyService = hierarchyService;
    }

    public OrganizationAccess accessFor(String... permissions) {
        UserAuthorizationSnapshot snapshot = currentSnapshot();
        if (isSystemAdministrator(snapshot)) {
            return new OrganizationAccess(true, Set.copyOf(hierarchyService.allIds(snapshot.tenantId())));
        }
        Set<String> requestedPermissions = Set.copyOf(Arrays.asList(permissions));
        boolean allowed = snapshot.authorities().stream().anyMatch(requestedPermissions::contains);
        Set<UUID> orgUnitIds = allowed
                ? hierarchyService.descendantIds(snapshot.tenantId(), snapshot.orgUnitId())
                : Set.of();
        boolean tenantWide = allowed && orgUnitIds.equals(hierarchyService.allIds(snapshot.tenantId()));
        return new OrganizationAccess(tenantWide, orgUnitIds);
    }

    public void requireOrganizationAccess(String permission, UUID orgUnitId) {
        if (orgUnitId == null || !accessFor(permission).orgUnitIds().contains(orgUnitId)) {
            throw new AccessDeniedException("权限 '" + permission + "' 未覆盖目标组织。" );
        }
    }

    public void requireOrganizationAccessIfAuthenticated(String permission, UUID orgUnitId) {
        if (hasTenantPrincipal()) {
            requireOrganizationAccess(permission, orgUnitId);
        }
    }

    public void requireTenantWideAccess(String permission) {
        if (!accessFor(permission).tenantWide()) {
            throw new AccessDeniedException("操作需要全租户范围权限：" + permission);
        }
    }

    public void requireTenantWideAccessIfAuthenticated(String permission) {
        if (hasTenantPrincipal()) {
            requireTenantWideAccess(permission);
        }
    }

    public boolean hasAuthority(String permission) {
        if (!hasTenantPrincipal()) {
            return true;
        }
        UserAuthorizationSnapshot snapshot = currentSnapshot();
        return isSystemAdministrator(snapshot) || snapshot.authorities().contains(permission);
    }

    public void requireAuthorityIfAuthenticated(String permission) {
        if (hasTenantPrincipal() && !hasAuthority(permission)) {
            throw new AccessDeniedException("缺少权限：" + permission);
        }
    }

    public boolean canGrantPermission(String permissionName, LocalDateTime targetExpiresAt) {
        if (!hasTenantPrincipal()) {
            return true;
        }
        LocalDateTime now = UtcTime.now();
        return currentSnapshot().activeGrants(permissionName, now).stream()
                .filter(UserAuthorizationSnapshot.EffectivePermissionGrant::grantOption)
                .anyMatch(grant -> grant.expiresAt() == null
                        || targetExpiresAt != null && !targetExpiresAt.isAfter(grant.expiresAt()));
    }

    public boolean canGrantPermission(String permissionName) {
        if (!hasTenantPrincipal()) {
            return true;
        }
        LocalDateTime now = UtcTime.now();
        return currentSnapshot().activeGrants(permissionName, now).stream()
                .anyMatch(UserAuthorizationSnapshot.EffectivePermissionGrant::grantOption);
    }

    public LocalDateTime maximumGrantExpiry(String permissionName) {
        if (!hasTenantPrincipal()) {
            return null;
        }
        List<UserAuthorizationSnapshot.EffectivePermissionGrant> grants = currentSnapshot()
                .activeGrants(permissionName, UtcTime.now()).stream()
                .filter(UserAuthorizationSnapshot.EffectivePermissionGrant::grantOption)
                .toList();
        if (grants.isEmpty()) {
            throw new AccessDeniedException("当前账号没有该原子权限的转授权能力：" + permissionName);
        }
        if (grants.stream().anyMatch(grant -> grant.expiresAt() == null)) {
            return null;
        }
        return grants.stream().map(UserAuthorizationSnapshot.EffectivePermissionGrant::expiresAt)
                .max(LocalDateTime::compareTo).orElseThrow();
    }

    private boolean isSystemAdministrator(UserAuthorizationSnapshot snapshot) {
        return snapshot.systemKey() == UserSystemKey.SUPER_ADMINISTRATOR;
    }

    public boolean hasTenantPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof TenantUserDetails;
    }

    private UserAuthorizationSnapshot currentSnapshot() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantUserDetails principal)) {
            throw new AccessDeniedException("需要已认证的租户用户。" );
        }
        UserAuthorizationSnapshot snapshot = principal.authorizationSnapshot();
        if (snapshot == null || !snapshot.tenantId().equals(tenantContext.requireTenantId())) {
            throw new AccessDeniedException("当前授权快照不可用。" );
        }
        return snapshot;
    }

    public record OrganizationAccess(boolean tenantWide, Set<UUID> orgUnitIds) {
        public OrganizationAccess {
            orgUnitIds = Set.copyOf(orgUnitIds);
        }
    }

}
