package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.repository.AuthorizationProfileRepository;
import com.pro.ereportbacken.repository.AuthorizationProfileVersionRepository;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.repository.UserWorkOrderPerformanceRepository;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.AdministrativeControlService;
import com.pro.ereportbacken.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserCredentialRevocationTests {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final TenantContext tenantContext = mock(TenantContext.class);
    private final OrganizationScopeAuthorizationService scopeAuthorizationService =
            mock(OrganizationScopeAuthorizationService.class);
    private final CredentialRevocationService credentialRevocationService =
            mock(CredentialRevocationService.class);
    private final UserService userService = new UserService(
            userRepository,
            passwordEncoder,
            mock(AuthorizationProfileRepository.class),
            mock(AuthorizationProfileVersionRepository.class),
            tenantContext,
            mock(DefaultTenantService.class),
            mock(OrgUnitRepository.class),
            scopeAuthorizationService,
            mock(UserAuthorizationCache.class),
            mock(UserWorkOrderPerformanceRepository.class),
            mock(PasswordPolicy.class),
            credentialRevocationService,
            mock(AuthorizationAdministrationService.class),
            mock(OrganizationHierarchyService.class),
            mock(AdministrativeControlService.class),
            mock(SystemGrantProvisioningService.class),
            mock(UserOrganizationMoveAuthorizationPlanner.class),
            mock(DepartmentService.class)
    );

    @Test
    void selfPasswordChangeUsesUnifiedCredentialRevocation() {
        User user = user();
        UUID currentSessionId = UUID.randomUUID();
        when(userRepository.findByTenantIdAndId(user.getTenantId(), user.getId()))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", "stored-password")).thenReturn(true);
        when(passwordEncoder.matches("new-password", "stored-password")).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new-password");

        userService.changeCurrentPassword(
                user.getTenantId(), user.getId(), currentSessionId, "current-password", "new-password");

        verify(credentialRevocationService).afterSelfPasswordChange(
                currentSessionId, user.getTenantId(), user.getId());
        assertThat(user.getPassword()).isEqualTo("encoded-new-password");
        assertThat(user.getPasswordChangeRequired()).isFalse();
    }

    @Test
    void adminPasswordResetUsesUnifiedCredentialRevocation() {
        User user = user();
        when(tenantContext.currentTenantIdOrDefault()).thenReturn(user.getTenantId());
        when(userRepository.findByTenantIdAndId(user.getTenantId(), user.getId()))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.encode("reset-password")).thenReturn("encoded-reset-password");

        userService.adminResetPassword(user.getId(), "reset-password");

        verify(credentialRevocationService).afterAdminPasswordReset(user.getTenantId(), user.getId());
        assertThat(user.getPassword()).isEqualTo("encoded-reset-password");
        assertThat(user.getPasswordChangeRequired()).isTrue();
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(UUID.randomUUID());
        user.setOrgUnitId(UUID.randomUUID());
        user.setUsername("credential-user");
        user.setPassword("stored-password");
        user.setName("凭据测试用户");
        user.setPhone("13000000095");
        user.setAuthorizationVersion(7L);
        return user;
    }
}
