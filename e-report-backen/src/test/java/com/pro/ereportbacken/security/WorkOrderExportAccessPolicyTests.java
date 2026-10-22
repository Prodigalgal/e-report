package com.pro.ereportbacken.security;

import com.pro.ereportbacken.model.dto.WorkOrderFilterDTO;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.repository.WorkOrderAssignmentRepository;
import com.pro.ereportbacken.repository.WorkOrderOrganizationParticipationRepository;
import com.pro.ereportbacken.service.UserAuthorizationCache;
import com.pro.ereportbacken.service.OrganizationHierarchyService;
import com.pro.ereportbacken.service.WorkOrderDepartmentParticipationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class WorkOrderExportAccessPolicyTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void compilesExportPermissionsIntoTheUsersNaturalOrganizationSubtree() {
        UUID tenantId = uuid(1);
        UUID membershipId = uuid(2);
        UUID membershipOrgId = uuid(3);
        UUID countyOrgId = uuid(4);
        UserAuthorizationSnapshot snapshot = new UserAuthorizationSnapshot(
                membershipId, tenantId, membershipOrgId, 1L, "exporter", null, UserStatus.ACTIVE,
                List.of(
                        grant(exportPermission(WorkOrderType.NETWORK_FAULT)),
                        grant(exportPermission(WorkOrderType.DAMAGED_BOX_REPLACEMENT))
                ));
        authenticate(snapshot);
        WorkOrderAccessPolicy policy = policy(Set.of(membershipOrgId, countyOrgId));

        WorkOrderFilterDTO scoped = policy.applyExportScope(new WorkOrderFilterDTO());

        assertThat(scoped.getTenantId()).isEqualTo(tenantId);
        assertThat(scoped.getAccessMembershipId()).isEqualTo(membershipId);
        assertThat(scoped.getAccessibleOrgUnitIdsByType())
                .containsEntry(WorkOrderType.NETWORK_FAULT, Set.of(membershipOrgId, countyOrgId))
                .containsEntry(WorkOrderType.DAMAGED_BOX_REPLACEMENT, Set.of(membershipOrgId, countyOrgId));
        assertThat(scoped.getTenantWideReporterTypes())
                .containsExactlyInAnyOrder(WorkOrderType.NETWORK_FAULT, WorkOrderType.DAMAGED_BOX_REPLACEMENT);
        assertThat(scoped.getAccessibleReportTypes()).isEmpty();
    }

    @Test
    void viewPermissionAloneCannotExport() {
        UUID tenantId = uuid(1);
        UserAuthorizationSnapshot snapshot = new UserAuthorizationSnapshot(
                uuid(2), tenantId, uuid(3), 1L, "viewer", null, UserStatus.ACTIVE,
                List.of(grant(PermissionCatalog.NETWORK_FAULT_VIEW)));
        authenticate(snapshot);

        assertThatThrownBy(() -> policy(Set.of(snapshot.orgUnitId())).applyExportScope(new WorkOrderFilterDTO()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("没有导出工单的权限");
    }

    private WorkOrderAccessPolicy policy(Set<UUID> organizationSubtree) {
        OrganizationHierarchyService hierarchyService = mock(OrganizationHierarchyService.class);
        when(hierarchyService.descendantIds(any(), any())).thenReturn(organizationSubtree);
        return new WorkOrderAccessPolicy(
                mock(UserRepository.class), mock(TenantContext.class),
                mock(WorkOrderAssignmentRepository.class), mock(UserAuthorizationCache.class), hierarchyService,
                mock(WorkOrderOrganizationParticipationRepository.class),
                mock(WorkOrderDepartmentParticipationService.class));
    }

    private String exportPermission(WorkOrderType type) {
        return PermissionCatalog.requireWorkOrderEntry(type).exportPermission();
    }

    private UserAuthorizationSnapshot.EffectivePermissionGrant grant(String permission) {
        return new UserAuthorizationSnapshot.EffectivePermissionGrant(
                uuid(permission.hashCode()), permission, false, null, null);
    }

    private void authenticate(UserAuthorizationSnapshot snapshot) {
        TenantUserDetails principal = new TenantUserDetails(
                snapshot.membershipId(), snapshot.tenantId(), snapshot.orgUnitId(), snapshot.authorizationVersion(),
                snapshot.username(), "", true, List.of(), snapshot);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));
    }

    private UUID uuid(long seed) {
        return com.pro.ereportbacken.TestIds.uuid(seed);
    }
}
