package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.GrantIssuerType;
import com.pro.ereportbacken.model.pojo.Permission;
import com.pro.ereportbacken.model.pojo.PermissionGrant;
import com.pro.ereportbacken.model.pojo.PermissionGrantStatus;
import com.pro.ereportbacken.repository.PermissionGrantRepository;
import com.pro.ereportbacken.repository.PermissionRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.PermissionCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionGrantChainServiceTests {

    private PermissionGrantRepository grantRepository;
    private PermissionRepository permissionRepository;
    private UserRepository userRepository;
    private OrganizationHierarchyService hierarchyService;
    private AuthorizationVersionInvalidationService versionInvalidationService;
    private PermissionGrantChainService service;

    @BeforeEach
    void setUp() {
        grantRepository = mock(PermissionGrantRepository.class);
        permissionRepository = mock(PermissionRepository.class);
        userRepository = mock(UserRepository.class);
        hierarchyService = mock(OrganizationHierarchyService.class);
        versionInvalidationService = mock(AuthorizationVersionInvalidationService.class);
        service = new PermissionGrantChainService(
                grantRepository,
                permissionRepository,
                userRepository,
                hierarchyService,
                versionInvalidationService);
    }

    @Test
    void resolvesAnUnlimitedParentChainWithoutFlatteningScopesOrRelations() {
        UUID tenantId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        PermissionGrant root = grant(tenantId, UUID.randomUUID(), permissionId, null, true,
                now.minusDays(1), null);
        PermissionGrant middle = grant(tenantId, UUID.randomUUID(), permissionId, root.getId(), true,
                now.minusHours(1), now.plusDays(10));
        PermissionGrant leaf = grant(tenantId, UUID.randomUUID(), permissionId, middle.getId(), false,
                now.minusMinutes(5), now.plusDays(1));
        UUID sharedOrgUnitId = UUID.randomUUID();
        stubMembershipOrganizations(tenantId, Map.of(
                root.getMembershipId(), sharedOrgUnitId,
                middle.getMembershipId(), sharedOrgUnitId,
                leaf.getMembershipId(), sharedOrgUnitId));
        when(grantRepository.findById(root.getId())).thenReturn(Optional.of(root));
        when(grantRepository.findById(middle.getId())).thenReturn(Optional.of(middle));

        assertThat(service.isEffectivelyActive(leaf, now)).isTrue();

        middle.setStatus(PermissionGrantStatus.REVOKED);
        assertThat(service.isEffectivelyActive(leaf, now)).isFalse();
    }

    @Test
    void rejectsGrantChainsThatCrossOutsideParentOrganizationScope() {
        UUID tenantId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        UUID parentOrganizationId = UUID.randomUUID();
        UUID siblingOrganizationId = UUID.randomUUID();
        PermissionGrant parent = grant(tenantId, UUID.randomUUID(), permissionId, null, true,
                now.minusDays(1), null);
        PermissionGrant child = grant(tenantId, UUID.randomUUID(), permissionId, parent.getId(), false,
                now.minusHours(1), null);
        stubMembershipOrganizations(tenantId, Map.of(
                parent.getMembershipId(), parentOrganizationId,
                child.getMembershipId(), siblingOrganizationId));
        when(grantRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(hierarchyService.treeContains(tenantId, parentOrganizationId, siblingOrganizationId))
                .thenReturn(false);

        assertThat(service.isEffectivelyActive(child, now)).isFalse();
    }

    @Test
    void allowsGrantChainsWithinTheParentOrganizationSubtree() {
        UUID tenantId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        UUID parentOrganizationId = UUID.randomUUID();
        UUID childOrganizationId = UUID.randomUUID();
        PermissionGrant parent = grant(tenantId, UUID.randomUUID(), permissionId, null, true,
                now.minusDays(1), null);
        PermissionGrant child = grant(tenantId, UUID.randomUUID(), permissionId, parent.getId(), false,
                now.minusHours(1), null);
        stubMembershipOrganizations(tenantId, Map.of(
                parent.getMembershipId(), parentOrganizationId,
                child.getMembershipId(), childOrganizationId));
        when(grantRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(hierarchyService.treeContains(tenantId, parentOrganizationId, childOrganizationId))
                .thenReturn(true);

        assertThat(service.isEffectivelyActive(child, now)).isTrue();
    }

    @Test
    void rejectsAChildGrantThatOutlivesItsParent() {
        UUID tenantId = UUID.randomUUID();
        UUID grantorId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Permission permission = new Permission(
                PermissionCatalog.NETWORK_FAULT_VIEW, "网络故障查看", "查看网络故障");
        permission.setId(permissionId);
        PermissionGrant parent = grant(tenantId, grantorId, permissionId, null, true,
                now.minusDays(1), now.plusDays(2));
        stubMembershipOrganizations(tenantId, Map.of(parent.getMembershipId(), UUID.randomUUID()));
        when(permissionRepository.findByName(PermissionCatalog.NETWORK_FAULT_VIEW))
                .thenReturn(Optional.of(permission));
        when(grantRepository.lockActiveGrant(tenantId, grantorId, permissionId))
                .thenReturn(List.of(parent));

        assertThatThrownBy(() -> service.issueChildGrant(
                tenantId,
                grantorId,
                UUID.randomUUID(),
                PermissionCatalog.NETWORK_FAULT_VIEW,
                now,
                now.plusDays(3),
                false,
                "测试授权上限"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能超过上游授权");
        verify(grantRepository, never()).save(any(PermissionGrant.class));
    }

    @Test
    void blocksRemovingGrantOptionWhenActiveChildrenExist() {
        UUID tenantId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        PermissionGrant parent = grant(tenantId, UUID.randomUUID(), permissionId, null, true,
                now.minusDays(1), null);
        PermissionGrant child = grant(tenantId, UUID.randomUUID(), permissionId, parent.getId(), false,
                now.minusHours(1), null);
        when(grantRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(grantRepository.findByTenantIdAndParentGrantIdInAndStatusIn(
                tenantId,
                List.of(parent.getId()),
                List.of(PermissionGrantStatus.ACTIVE, PermissionGrantStatus.SUSPENDED_UPSTREAM)))
                .thenReturn(List.of(child));

        assertThatThrownBy(() -> service.replace(
                tenantId,
                parent.getId(),
                null,
                UUID.randomUUID(),
                now,
                null,
                false,
                "取消转授权能力"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须显式处理下游授权");
    }

    @Test
    void revokingAnUpstreamGrantInvalidatesEveryAffectedMembership() {
        UUID tenantId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        PermissionGrant root = grant(tenantId, UUID.randomUUID(), permissionId, null, true,
                now.minusDays(1), null);
        PermissionGrant child = grant(tenantId, UUID.randomUUID(), permissionId, root.getId(), true,
                now.minusHours(2), null);
        PermissionGrant leaf = grant(tenantId, UUID.randomUUID(), permissionId, child.getId(), false,
                now.minusHours(1), null);
        when(grantRepository.findById(root.getId())).thenReturn(Optional.of(root));
        when(grantRepository.findByTenantIdAndParentGrantIdInAndStatusIn(
                tenantId, List.of(root.getId()),
                List.of(PermissionGrantStatus.ACTIVE, PermissionGrantStatus.SUSPENDED_UPSTREAM)))
                .thenReturn(List.of(child));
        when(grantRepository.findByTenantIdAndParentGrantIdInAndStatusIn(
                tenantId, List.of(child.getId()),
                List.of(PermissionGrantStatus.ACTIVE, PermissionGrantStatus.SUSPENDED_UPSTREAM)))
                .thenReturn(List.of(leaf));
        when(grantRepository.findByTenantIdAndParentGrantIdInAndStatusIn(
                tenantId, List.of(leaf.getId()),
                List.of(PermissionGrantStatus.ACTIVE, PermissionGrantStatus.SUSPENDED_UPSTREAM)))
                .thenReturn(List.of());

        service.revoke(tenantId, root.getId(), UUID.randomUUID(), "撤销上游授权");

        assertThat(root.getStatus()).isEqualTo(PermissionGrantStatus.REVOKED);
        assertThat(child.getStatus()).isEqualTo(PermissionGrantStatus.SUSPENDED_UPSTREAM);
        assertThat(leaf.getStatus()).isEqualTo(PermissionGrantStatus.SUSPENDED_UPSTREAM);
        verify(versionInvalidationService).markChanged(eq(tenantId),
                org.mockito.ArgumentMatchers.<java.util.Collection<UUID>>argThat(ids ->
                ids.containsAll(List.of(root.getMembershipId(), child.getMembershipId(), leaf.getMembershipId()))
                        && ids.size() == 3));
    }

    private PermissionGrant grant(UUID tenantId,
                                  UUID membershipId,
                                  UUID permissionId,
                                  UUID parentGrantId,
                                  boolean grantOption,
                                  LocalDateTime validFrom,
                                  LocalDateTime expiresAt) {
        PermissionGrant grant = new PermissionGrant();
        grant.setId(UUID.randomUUID());
        grant.setTenantId(tenantId);
        grant.setMembershipId(membershipId);
        grant.setPermissionId(permissionId);
        grant.setParentGrantId(parentGrantId);
        grant.setGrantOption(grantOption);
        grant.setIssuerType(parentGrantId == null ? GrantIssuerType.SYSTEM_ROOT : GrantIssuerType.USER);
        grant.setStatus(PermissionGrantStatus.ACTIVE);
        grant.setValidFrom(validFrom);
        grant.setExpiresAt(expiresAt);
        grant.setGrantedAt(validFrom);
        grant.setStatusChangedAt(validFrom);
        return grant;
    }

    private void stubMembershipOrganizations(UUID tenantId, Map<UUID, UUID> orgUnitIdsByMembership) {
        List<UserRepository.MembershipOrganization> memberships = orgUnitIdsByMembership.entrySet().stream()
                .map(entry -> (UserRepository.MembershipOrganization) new UserRepository.MembershipOrganization() {
                    @Override
                    public UUID getMembershipId() {
                        return entry.getKey();
                    }

                    @Override
                    public UUID getOrgUnitId() {
                        return entry.getValue();
                    }
                })
                .toList();
        when(userRepository.findMembershipOrganizationsByTenantIdAndMembershipIds(
                eq(tenantId), org.mockito.ArgumentMatchers.<Set<UUID>>any()))
                .thenReturn(memberships);
    }
}
