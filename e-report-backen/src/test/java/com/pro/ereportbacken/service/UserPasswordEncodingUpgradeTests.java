package com.pro.ereportbacken.service;

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

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPasswordEncodingUpgradeTests {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserService userService = new UserService(
            userRepository,
            passwordEncoder,
            mock(AuthorizationProfileRepository.class),
            mock(AuthorizationProfileVersionRepository.class),
            mock(TenantContext.class),
            mock(DefaultTenantService.class),
            mock(OrgUnitRepository.class),
            mock(OrganizationScopeAuthorizationService.class),
            mock(UserAuthorizationCache.class),
            mock(UserWorkOrderPerformanceRepository.class),
            mock(PasswordPolicy.class),
            mock(CredentialRevocationService.class),
            mock(AuthorizationAdministrationService.class),
            mock(OrganizationHierarchyService.class),
            mock(AdministrativeControlService.class),
            mock(SystemGrantProvisioningService.class),
            mock(UserOrganizationMoveAuthorizationPlanner.class),
            mock(DepartmentService.class)
    );

    @Test
    void upgradesUsingAuthenticatedHashWithoutMatchingBcryptAgain() {
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        String authenticatedPassword = "$2a$10$authenticated-bcrypt";
        when(passwordEncoder.upgradeEncoding(authenticatedPassword)).thenReturn(true);
        when(passwordEncoder.encode("raw-password")).thenReturn("{argon2id}upgraded");

        userService.upgradeAuthenticatedPasswordEncodingIfNeeded(
                tenantId, membershipId, authenticatedPassword, "raw-password");

        verify(passwordEncoder, never()).matches("raw-password", authenticatedPassword);
        verify(userRepository).upgradePasswordIfCurrent(
                tenantId, membershipId, authenticatedPassword, "{argon2id}upgraded");
    }

    @Test
    void skipsEncodingWhenAuthenticatedPasswordAlreadyUsesCurrentEncoder() {
        String authenticatedPassword = "{argon2id}current";
        when(passwordEncoder.upgradeEncoding(authenticatedPassword)).thenReturn(false);

        userService.upgradeAuthenticatedPasswordEncodingIfNeeded(
                UUID.randomUUID(), UUID.randomUUID(), authenticatedPassword, "raw-password");

        verify(passwordEncoder, never()).encode("raw-password");
        verify(userRepository, never()).upgradePasswordIfCurrent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
