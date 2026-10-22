package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.model.pojo.UserSystemKey;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.SystemAuthority;
import com.pro.ereportbacken.security.TenantUserDetails;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.UserAuthorizationSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserDetailsServiceImplTests {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserAuthorizationCache authorizationCache = mock(UserAuthorizationCache.class);
    private final UserDetailsServiceImpl service = new UserDetailsServiceImpl(
            userRepository,
            authorizationCache,
            new com.pro.ereportbacken.security.TenantContext(
                    mock(DefaultTenantService.class),
                    new com.pro.ereportbacken.security.TenantExecutionContext()
            )
    );

    @Test
    void restoresSessionDirectlyFromAuthorizationSnapshotCache() {
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UserAuthorizationSnapshot snapshot = snapshot(tenantId, membershipId, "worker");
        when(authorizationCache.get(tenantId, membershipId)).thenReturn(snapshot);

        var details = service.loadForSession(tenantId, membershipId);

        assertThat(details.getUsername()).isEqualTo("worker");
        assertThat(details.authorizationVersion()).isEqualTo(7L);
        verifyNoInteractions(userRepository);
    }

    @Test
    void loginUsesCurrentCachedAuthorizationAfterLoadingPasswordRecord() {
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        User user = new User();
        user.setId(membershipId);
        user.setTenantId(tenantId);
        user.setUsername("worker");
        user.setPassword("{argon2id}encoded");
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthorizationVersion(7L);
        UserAuthorizationSnapshot cached = snapshot(tenantId, membershipId, "worker");
        user.setOrgUnitId(cached.orgUnitId());
        when(userRepository.findByUsername("worker")).thenReturn(Optional.of(user));
        when(authorizationCache.get(tenantId, membershipId))
                .thenReturn(cached);

        var details = service.loadUserByUsername(" worker ");

        assertThat(details.getPassword()).isEqualTo("{argon2id}encoded");
        verify(authorizationCache).get(tenantId, membershipId);
        verify(authorizationCache, never()).snapshot(user);
    }

    @Test
    void loginRefreshesStaleAuthorizationSnapshotBeforeCreatingSession() {
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        User user = new User();
        user.setId(membershipId);
        user.setTenantId(tenantId);
        user.setOrgUnitId(UUID.randomUUID());
        user.setUsername("worker");
        user.setPassword("{argon2id}encoded");
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthorizationVersion(8L);
        UserAuthorizationSnapshot stale = snapshot(tenantId, membershipId, "worker");
        UserAuthorizationSnapshot refreshed = new UserAuthorizationSnapshot(
                membershipId,
                tenantId,
                user.getOrgUnitId(),
                8L,
                "worker",
                null,
                UserStatus.ACTIVE,
                List.of()
        );
        when(userRepository.findByUsername("worker")).thenReturn(Optional.of(user));
        when(authorizationCache.get(tenantId, membershipId)).thenReturn(stale);
        when(authorizationCache.snapshot(user)).thenReturn(refreshed);

        var details = service.loadUserByUsername("worker");

        assertThat(((TenantUserDetails) details).authorizationVersion()).isEqualTo(8L);
        verify(authorizationCache).snapshot(user);
    }

    @Test
    void apiTokenKeepsOnlySelectedGrantsWithoutChangingTheirDataBoundary() {
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID orgUnitId = UUID.randomUUID();
        UserAuthorizationSnapshot snapshot = new UserAuthorizationSnapshot(
                membershipId,
                tenantId,
                orgUnitId,
                7L,
                "worker",
                UserSystemKey.SUPER_ADMINISTRATOR,
                UserStatus.ACTIVE,
                List.of(
                        new UserAuthorizationSnapshot.EffectivePermissionGrant(
                                UUID.randomUUID(), PermissionCatalog.NETWORK_FAULT_VIEW, false,
                                null, null),
                        new UserAuthorizationSnapshot.EffectivePermissionGrant(
                                UUID.randomUUID(), PermissionCatalog.APP_UPDATE_DELETE, true,
                                null, null)
                )
        );
        when(authorizationCache.get(tenantId, membershipId)).thenReturn(snapshot);

        var details = service.loadForApiToken(
                tenantId, membershipId, Set.of(PermissionCatalog.NETWORK_FAULT_VIEW));

        assertThat(details.getAuthorities()).extracting("authority")
                .containsExactly(PermissionCatalog.NETWORK_FAULT_VIEW);
        assertThat(details.authorizationSnapshot().grants()).singleElement().satisfies(grant -> {
            assertThat(grant.permissionName()).isEqualTo(PermissionCatalog.NETWORK_FAULT_VIEW);
            assertThat(grant.grantOption()).isFalse();
        });
    }

    @Test
    void interactiveSessionDerivesNonDelegableSuperAdministratorAuthority() {
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UserAuthorizationSnapshot snapshot = new UserAuthorizationSnapshot(
                membershipId,
                tenantId,
                UUID.randomUUID(),
                9L,
                "superAdmin",
                UserSystemKey.SUPER_ADMINISTRATOR,
                UserStatus.ACTIVE,
                List.of()
        );
        when(authorizationCache.get(tenantId, membershipId)).thenReturn(snapshot);

        var details = service.loadForSession(tenantId, membershipId);

        assertThat(details.getAuthorities()).extracting("authority")
                .containsExactly(SystemAuthority.SUPER_ADMINISTRATOR);
    }

    private UserAuthorizationSnapshot snapshot(UUID tenantId, UUID membershipId, String username) {
        return new UserAuthorizationSnapshot(
                membershipId,
                tenantId,
                UUID.randomUUID(),
                7L,
                username,
                null,
                UserStatus.ACTIVE,
                List.of()
        );
    }
}
