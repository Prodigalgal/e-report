package com.pro.ereportbacken.security;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.model.pojo.UserSystemKey;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UserAuthorizationSnapshot(
        UUID membershipId,
        UUID tenantId,
        UUID orgUnitId,
        Long authorizationVersion,
        String username,
        UserSystemKey systemKey,
        UserStatus status,
        List<EffectivePermissionGrant> grants
) {

    public UserAuthorizationSnapshot {
        grants = List.copyOf(grants);
    }

    public Set<String> authorities() {
        return authorities(true);
    }

    public Set<String> authorities(boolean includeSystemAuthorities) {
        LocalDateTime now = UtcTime.now();
        Set<String> authorities = new LinkedHashSet<>();
        activeGrants(now).forEach(grant -> authorities.add(grant.permissionName()));
        if (includeSystemAuthorities && systemKey == UserSystemKey.SUPER_ADMINISTRATOR) {
            authorities.add(SystemAuthority.SUPER_ADMINISTRATOR);
        }
        return Set.copyOf(authorities);
    }

    public List<EffectivePermissionGrant> activeGrants(LocalDateTime now) {
        return grants.stream().filter(grant -> grant.activeAt(now)).toList();
    }

    public List<EffectivePermissionGrant> activeGrants(String permissionName, LocalDateTime now) {
        return grants.stream()
                .filter(grant -> grant.permissionName().equals(permissionName) && grant.activeAt(now))
                .toList();
    }

    public UserAuthorizationSnapshot restrictedTo(Set<String> permissionNames) {
        Set<String> allowed = Set.copyOf(permissionNames);
        return new UserAuthorizationSnapshot(
                membershipId,
                tenantId,
                orgUnitId,
                authorizationVersion,
                username,
                systemKey,
                status,
                grants.stream().filter(grant -> allowed.contains(grant.permissionName())).toList()
        );
    }

    public record EffectivePermissionGrant(
            UUID grantId,
            String permissionName,
            boolean grantOption,
            LocalDateTime validFrom,
            LocalDateTime expiresAt
    ) {
        public boolean activeAt(LocalDateTime instant) {
            return (validFrom == null || !validFrom.isAfter(instant))
                    && (expiresAt == null || expiresAt.isAfter(instant));
        }
    }
}
