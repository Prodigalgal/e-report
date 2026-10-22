package com.pro.ereportbacken.security;

import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.model.pojo.UserSystemKey;
import com.pro.ereportbacken.service.OrganizationHierarchyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdministrativeControlServiceTests {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID ROOT_ORG_ID = UUID.fromString("00000000-0000-7000-8000-000000000002");
    private static final UUID COUNTY_ORG_ID = UUID.fromString("00000000-0000-7000-8000-000000000003");
    private static final UUID OTHER_COUNTY_ORG_ID = UUID.fromString("00000000-0000-7000-8000-000000000004");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-7000-8000-000000000005");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-7000-8000-000000000006");

    @Mock
    private OrganizationHierarchyService hierarchyService;

    private AdministrativeControlService service;
    private User target;

    @BeforeEach
    void setUp() {
        service = new AdministrativeControlService(hierarchyService);
        target = user(TARGET_ID, COUNTY_ORG_ID, null);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsSameOrganizationPeerManagementWithoutTargetSensitivityClassification() {
        authenticate(ACTOR_ID, COUNTY_ORG_ID, null);

        AdministrativeControlDecision decision = service.decide(
                target, AdministrativeControlAction.MANAGE_ACCESS);

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void allowsStrictAncestorWithItsOwnAtomicActionPermission() {
        authenticate(ACTOR_ID, ROOT_ORG_ID, null);
        when(hierarchyService.treeContains(TENANT_ID, ROOT_ORG_ID, COUNTY_ORG_ID)).thenReturn(true);

        AdministrativeControlDecision decision = service.decide(
                target, AdministrativeControlAction.MANAGE_ACCESS);

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void deniesSensitiveSelfMutation() {
        target = user(ACTOR_ID, COUNTY_ORG_ID, null);
        authenticate(ACTOR_ID, COUNTY_ORG_ID, null);

        AdministrativeControlDecision decision = service.decide(
                target, AdministrativeControlAction.RESET_PASSWORD);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("SELF_PRIVILEGED_ACTION_DENIED");
    }

    @Test
    void deniesSiblingOrganizationManagement() {
        authenticate(ACTOR_ID, OTHER_COUNTY_ORG_ID, null);
        when(hierarchyService.treeContains(
                TENANT_ID, OTHER_COUNTY_ORG_ID, COUNTY_ORG_ID)).thenReturn(false);

        AdministrativeControlDecision decision = service.decide(
                target, AdministrativeControlAction.UPDATE_DETAILS);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("ORGANIZATION_RELATION_DENIED");
    }

    @Test
    void superAdministratorCanCrossOrganizationLevelsButNotTenantBoundaries() {
        authenticate(ACTOR_ID, ROOT_ORG_ID, UserSystemKey.SUPER_ADMINISTRATOR);

        assertThat(service.decide(target, AdministrativeControlAction.MANAGE_ACCESS).allowed()).isTrue();

        target.setTenantId(UUID.fromString("00000000-0000-7000-8000-000000000099"));
        assertThat(service.decide(target, AdministrativeControlAction.MANAGE_ACCESS).reasonCode())
                .isEqualTo("TARGET_TENANT_MISMATCH");
    }

    private void authenticate(UUID membershipId, UUID orgUnitId, UserSystemKey systemKey) {
        UserAuthorizationSnapshot snapshot = new UserAuthorizationSnapshot(
                membershipId,
                TENANT_ID,
                orgUnitId,
                1L,
                "actor",
                systemKey,
                UserStatus.ACTIVE,
                List.of());
        TenantUserDetails principal = new TenantUserDetails(
                membershipId, TENANT_ID, orgUnitId, 1L, "actor", "", true, List.of(), snapshot);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private User user(UUID id, UUID orgUnitId, UserSystemKey systemKey) {
        User user = new User();
        user.setId(id);
        user.setTenantId(TENANT_ID);
        user.setOrgUnitId(orgUnitId);
        user.setUsername("user-" + id);
        user.setSystemKey(systemKey);
        return user;
    }
}
