package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.ApiTokenCreateRequestDTO;
import com.pro.ereportbacken.model.pojo.ApiToken;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.repository.ApiTokenRepository;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.SecurityHashingService;
import com.pro.ereportbacken.security.TenantUserDetails;
import com.pro.ereportbacken.security.UserAuthorizationSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiTokenServiceTests {

    private final ApiTokenRepository tokenRepository = mock(ApiTokenRepository.class);
    private final UserAuthorizationCache authorizationCache = mock(UserAuthorizationCache.class);
    private final SecurityHashingService hashingService = mock(SecurityHashingService.class);
    private final RedisFixedWindowRateLimiter rateLimiter = mock(RedisFixedWindowRateLimiter.class);
    private final ApiTokenService service = new ApiTokenService(
            tokenRepository, authorizationCache, hashingService, rateLimiter, 300);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsOneTimeTokenWithOnlySelectedCurrentPermissions() {
        PrincipalFixture fixture = authenticate(Set.of(
                PermissionCatalog.NETWORK_FAULT_VIEW,
                PermissionCatalog.NETWORK_FAULT_ATTACHMENT_READ));
        when(tokenRepository.countActiveByOwner(any(), any(), any())).thenReturn(0L);
        when(hashingService.protectedHash(anyString(), anyString())).thenReturn("a".repeat(64));
        AtomicReference<ApiToken> persisted = new AtomicReference<>();
        when(tokenRepository.save(any(ApiToken.class))).thenAnswer(invocation -> {
            ApiToken token = invocation.getArgument(0);
            token.setId(UUID.randomUUID());
            persisted.set(token);
            return token;
        });

        var created = service.create(new ApiTokenCreateRequestDTO(
                "LLM 查询",
                LocalDateTime.now().plusDays(30).toInstant(ZoneOffset.UTC),
                Set.of(PermissionCatalog.NETWORK_FAULT_VIEW)
        ));

        assertThat(created.token()).matches("^erpt_[A-Za-z0-9_-]{22}\\.[A-Za-z0-9_-]{43}$");
        assertThat(created.tokenInfo().permissionNames()).containsExactly(PermissionCatalog.NETWORK_FAULT_VIEW);
        assertThat(persisted.get().getPermissionNames()).containsExactly(PermissionCatalog.NETWORK_FAULT_VIEW);
        assertThat(persisted.get().getSecretHash()).isEqualTo("a".repeat(64)).isNotEqualTo(created.token());
        verify(hashingService).protectedHash("api-token", created.token());
        verify(authorizationCache).get(fixture.tenantId(), fixture.membershipId());
    }

    @Test
    void rejectsPermissionsOutsideTheCurrentAuthorizationSnapshot() {
        authenticate(Set.of(PermissionCatalog.NETWORK_FAULT_VIEW));

        assertThatThrownBy(() -> service.create(new ApiTokenCreateRequestDTO(
                "越权 Token",
                null,
                Set.of(PermissionCatalog.BOX_DOOR_OPEN_VIEW)
        )))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("尚未拥有");
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void rejectsSystemScopedReleasePermission() {
        authenticate(Set.of(PermissionCatalog.APP_UPDATE_DELETE));

        assertThatThrownBy(() -> service.create(new ApiTokenCreateRequestDTO(
                "系统发布 Token",
                null,
                Set.of(PermissionCatalog.APP_UPDATE_DELETE)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能授予 API Token");
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void omitsSystemAndTokenManagementPermissionsFromApiTokenOptions() {
        authenticate(Set.of(
                PermissionCatalog.NETWORK_FAULT_VIEW,
                PermissionCatalog.APP_UPDATE_VIEW,
                PermissionCatalog.API_TOKEN_VIEW,
                PermissionCatalog.API_TOKEN_CREATE,
                PermissionCatalog.API_TOKEN_REVOKE));

        assertThat(service.ownPermissionOptions())
                .extracting(option -> option.name())
                .containsExactly(PermissionCatalog.NETWORK_FAULT_VIEW);
    }

    @Test
    void rejectsEmbeddingApiTokenManagementPermissionInCreatedToken() {
        authenticate(Set.of(PermissionCatalog.API_TOKEN_CREATE));

        assertThatThrownBy(() -> service.create(new ApiTokenCreateRequestDTO(
                "Token 管理链",
                null,
                Set.of(PermissionCatalog.API_TOKEN_CREATE)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能授予 API Token");
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void authenticatesValidTokenAndRejectsExpiredOrRevokedTokens() {
        String rawToken = "erpt_abcdefghijklmnopqrstuv." + "x".repeat(43);
        ApiToken active = token("abcdefghijklmnopqrstuv", LocalDateTime.now().plusDays(1), null);
        when(tokenRepository.findByLookupKey(active.getLookupKey())).thenReturn(Optional.of(active));
        when(hashingService.protectedHash("api-token", rawToken)).thenReturn("b".repeat(64));
        when(hashingService.constantTimeEquals(active.getSecretHash(), "b".repeat(64))).thenReturn(true);

        var authenticated = service.authenticate(rawToken);

        assertThat(authenticated.tokenId()).isEqualTo(active.getId());
        assertThat(authenticated.permissionNames()).containsExactly(PermissionCatalog.NETWORK_FAULT_VIEW);

        active.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThatThrownBy(() -> service.authenticate(rawToken))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
        active.setExpiresAt(null);
        active.setRevokedAt(LocalDateTime.now());
        assertThatThrownBy(() -> service.authenticate(rawToken))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void apiTokenAuthenticationCannotCreateAnotherToken() {
        PrincipalFixture fixture = authenticate(Set.of(PermissionCatalog.NETWORK_FAULT_VIEW));
        var authentication = (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        authentication.setDetails(new com.pro.ereportbacken.security.ApiTokenAuthenticationDetails(
                UUID.randomUUID(), "已有 Token", null));

        assertThatThrownBy(service::ownPermissionOptions)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("不能创建或管理");
        verify(authorizationCache, never()).get(fixture.tenantId(), fixture.membershipId());
    }

    @Test
    void credentialChangeRevokesAllPreviouslyUnrevokedTokens() {
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        when(tokenRepository.revokeAllByOwner(
                eq(tenantId), eq(membershipId), any(LocalDateTime.class), eq("用户修改密码")))
                .thenReturn(2);

        int revoked = service.revokeAllForCredentialChange(tenantId, membershipId, "用户修改密码");

        assertThat(revoked).isEqualTo(2);
    }

    private PrincipalFixture authenticate(Set<String> permissions) {
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UserAuthorizationSnapshot snapshot = snapshot(tenantId, membershipId, permissions);
        when(authorizationCache.get(tenantId, membershipId)).thenReturn(snapshot);
        TenantUserDetails principal = new TenantUserDetails(
                membershipId,
                tenantId,
                snapshot.orgUnitId(),
                snapshot.authorizationVersion(),
                snapshot.username(),
                "",
                true,
                snapshot.authorities().stream().map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList(),
                snapshot
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return new PrincipalFixture(tenantId, membershipId);
    }

    private UserAuthorizationSnapshot snapshot(UUID tenantId, UUID membershipId, Set<String> permissions) {
        List<UserAuthorizationSnapshot.EffectivePermissionGrant> grants = permissions.stream()
                .map(permission -> new UserAuthorizationSnapshot.EffectivePermissionGrant(
                        UUID.randomUUID(),
                        permission,
                        false,
                        null,
                        null
                ))
                .toList();
        return new UserAuthorizationSnapshot(
                membershipId,
                tenantId,
                UUID.randomUUID(),
                7L,
                "token-owner",
                null,
                UserStatus.ACTIVE,
                grants
        );
    }

    private ApiToken token(String lookupKey, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        ApiToken token = new ApiToken();
        token.setId(UUID.randomUUID());
        token.setTenantId(UUID.randomUUID());
        token.setMembershipId(UUID.randomUUID());
        token.setName("LLM Token");
        token.setLookupKey(lookupKey);
        token.setSecretHash("b".repeat(64));
        token.setCreatedAt(LocalDateTime.now().minusDays(1));
        token.setExpiresAt(expiresAt);
        token.setRevokedAt(revokedAt);
        token.setPermissionNames(Set.of(PermissionCatalog.NETWORK_FAULT_VIEW));
        return token;
    }

    private record PrincipalFixture(UUID tenantId, UUID membershipId) {
    }
}
