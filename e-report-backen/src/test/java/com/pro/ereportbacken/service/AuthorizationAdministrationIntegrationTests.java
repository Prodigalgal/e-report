package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.AuthorizationOperationRequestDTO;
import com.pro.ereportbacken.model.dto.AuthorizationPlanRequestDTO;
import com.pro.ereportbacken.model.pojo.AuthorizationOperationType;
import com.pro.ereportbacken.model.pojo.GrantIssuerType;
import com.pro.ereportbacken.model.pojo.PermissionGrantStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.repository.PermissionGrantRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AuthorizationAdministrationIntegrationTests {

    @Autowired
    private AuthorizationAdministrationService authorizationService;
    @Autowired
    private PermissionGrantChainService grantChainService;
    @Autowired
    private PermissionGrantRepository grantRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserAuthorizationCache authorizationCache;
    @Autowired
    private DefaultTenantService defaultTenantService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void previewsAppliesAndAdjustsAnAtomicGrantWithoutAssignmentOrDataScope() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User operator = user(tenantId, rootOrgId, "v4-operator-");
        User target = user(tenantId, rootOrgId, "v4-target-");
        rootGrant(operator, PermissionCatalog.USER_AUTHORIZATION_VIEW, false);
        rootGrant(operator, PermissionCatalog.USER_AUTHORIZATION_GRANT, false);
        rootGrant(operator, PermissionCatalog.USER_AUTHORIZATION_ADJUST, false);
        rootGrant(operator, PermissionCatalog.USER_AUTHORIZATION_REVOKE, false);
        rootGrant(operator, PermissionCatalog.NETWORK_FAULT_VIEW, true);
        authenticate(operator);

        var operation = new AuthorizationOperationRequestDTO(
                AuthorizationOperationType.GRANT_PERMISSION,
                null,
                null,
                null,
                PermissionCatalog.NETWORK_FAULT_VIEW,
                null,
                null,
                false,
                "授予网络故障查看权限");
        var plan = authorizationService.createPlan(
                target.getId(), new AuthorizationPlanRequestDTO(target.getAuthorizationVersion(), List.of(operation)));

        assertThat(plan.operations()).singleElement().satisfies(preview -> {
            assertThat(preview.permissionChanges()).singleElement().satisfies(change -> {
                assertThat(change.after().permissionName()).isEqualTo(PermissionCatalog.NETWORK_FAULT_VIEW);
                assertThat(change.after().expiresAt()).isNull();
                assertThat(change.after().grantOption()).isFalse();
            });
        });

        var applied = authorizationService.applyPlan(target.getId(), plan.planToken(), "grant-network-view");
        assertThat(applied.grants()).singleElement().satisfies(grant -> {
            assertThat(grant.permissionName()).isEqualTo(PermissionCatalog.NETWORK_FAULT_VIEW);
            assertThat(grant.parentGrantId()).isNotNull();
            assertThat(grant.expiresAt()).isNull();
            assertThat(grant.status()).isEqualTo(PermissionGrantStatus.ACTIVE);
        });
        var persisted = grantRepository.findById(applied.grants().getFirst().id()).orElseThrow();
        assertThat(persisted.getIssuerType()).isEqualTo(GrantIssuerType.USER);
        assertThat(persisted.getGrantedByMembershipId()).isEqualTo(operator.getId());
        assertThat(persisted.getParentGrantId()).isNotNull();

        var adjust = new AuthorizationOperationRequestDTO(
                AuthorizationOperationType.ADJUST_PERMISSION,
                persisted.getId(),
                null,
                null,
                null,
                null,
                LocalDateTime.now().plusDays(30),
                null,
                "调整授权有效期");
        var adjustPlan = authorizationService.createPlan(
                target.getId(), new AuthorizationPlanRequestDTO(applied.authorizationVersion(), List.of(adjust)));
        var adjusted = authorizationService.applyPlan(
                target.getId(), adjustPlan.planToken(), "adjust-network-view");
        assertThat(adjusted.grants()).singleElement().satisfies(grant ->
                assertThat(grant.expiresAt()).isAfter(LocalDateTime.now().plusDays(29)));
        assertThat(grantRepository.findById(persisted.getId()).orElseThrow().getStatus())
                .isEqualTo(PermissionGrantStatus.REPLACED);
    }

    private void rootGrant(User user, String permissionCode, boolean grantOption) {
        grantChainService.issueRootGrant(
                user.getTenantId(), user.getId(), permissionCode, grantOption,
                GrantIssuerType.SYSTEM_ROOT, null, "V4 集成测试根授权");
        user.setAuthorizationVersion(user.getAuthorizationVersion() + 1L);
        userRepository.saveAndFlush(user);
        authorizationCache.evictAfterCommit(user.getTenantId(), user.getId());
    }

    private User user(UUID tenantId, UUID orgUnitId, String prefix) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setOrgUnitId(orgUnitId);
        user.setUsername(prefix + System.nanoTime());
        user.setPassword("encoded-test-password");
        user.setName("授权 V4 集成测试用户");
        user.setPhone("18" + String.valueOf(Math.abs(System.nanoTime())).substring(0, 9));
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthorizationVersion(1L);
        return userRepository.saveAndFlush(user);
    }

    private void authenticate(User operator) {
        var snapshot = authorizationCache.snapshot(operator);
        List<SimpleGrantedAuthority> authorities = snapshot.authorities().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        TenantUserDetails principal = new TenantUserDetails(
                snapshot.membershipId(), snapshot.tenantId(), snapshot.orgUnitId(),
                snapshot.authorizationVersion(), snapshot.username(), "encoded", true,
                authorities, snapshot);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
    }
}
