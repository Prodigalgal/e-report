package com.pro.ereportbacken.security;

import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public final class TenantUserDetails implements UserDetails {

    private final UUID membershipId;
    private final UUID tenantId;
    private final UUID orgUnitId;
    private final Long authorizationVersion;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;
    private final UserAuthorizationSnapshot authorizationSnapshot;

    public TenantUserDetails(UUID membershipId,
                             UUID tenantId,
                             UUID orgUnitId,
                             Long authorizationVersion,
                             String username,
                             String password,
                             boolean enabled,
                             Collection<? extends GrantedAuthority> authorities,
                             UserAuthorizationSnapshot authorizationSnapshot) {
        this.membershipId = membershipId;
        this.tenantId = tenantId;
        this.orgUnitId = orgUnitId;
        this.authorizationVersion = authorizationVersion;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.authorities = authorities;
        this.authorizationSnapshot = authorizationSnapshot;
    }

    public UUID membershipId() {
        return membershipId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID orgUnitId() {
        return orgUnitId;
    }

    public Long authorizationVersion() {
        return authorizationVersion;
    }

    public UserAuthorizationSnapshot authorizationSnapshot() {
        return authorizationSnapshot;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

