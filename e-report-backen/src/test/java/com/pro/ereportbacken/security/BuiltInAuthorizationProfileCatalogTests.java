package com.pro.ereportbacken.security;

import com.pro.ereportbacken.model.pojo.WorkOrderDuty;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltInAuthorizationProfileCatalogTests {

    @Test
    void builtInProfilesNeverUsePermissionlessIdentityMarkers() {
        assertThat(BuiltInAuthorizationProfileCatalog.templates(true))
                .allSatisfy(profile -> assertThat(profile.rules())
                        .as("profile=%s", profile.systemKey())
                        .isNotEmpty());
    }

    @Test
    void countyAdministratorProfileContainsEveryUserImportAction() {
        BuiltInAuthorizationProfileCatalog.Template profile = BuiltInAuthorizationProfileCatalog.templates(false)
                .stream()
                .filter(candidate -> BuiltInAuthorizationProfileCatalog.COUNTY_ADMINISTRATOR.equals(candidate.systemKey()))
                .findFirst()
                .orElseThrow();

        Set<String> permissions = profile.rules().stream()
                .map(BuiltInAuthorizationProfileCatalog.Rule::permissionName)
                .collect(Collectors.toSet());
        assertThat(permissions).contains(
                PermissionCatalog.USER_IMPORT_TEMPLATE_DOWNLOAD,
                PermissionCatalog.USER_IMPORT_PREVIEW,
                PermissionCatalog.USER_IMPORT_EXECUTE,
                PermissionCatalog.USER_IMPORT_CANCEL,
                PermissionCatalog.USER_EXPORT_CREATE,
                PermissionCatalog.USER_EXPORT_DOWNLOAD,
                PermissionCatalog.NOTIFICATION_SENDER_UPDATE,
                PermissionCatalog.API_TOKEN_VIEW,
                PermissionCatalog.API_TOKEN_CREATE,
                PermissionCatalog.API_TOKEN_REVOKE);
        assertThat(permissions).containsAll(PermissionCatalog.exportPermissions());
    }

    @Test
    void apiTokenManagerIsAnAtomicProfileIncludedInBothDefaultAdministratorProfiles() {
        Map<String, Set<String>> rulesByProfile = BuiltInAuthorizationProfileCatalog.templates(true).stream()
                .collect(Collectors.toMap(
                        BuiltInAuthorizationProfileCatalog.Template::systemKey,
                        profile -> profile.rules().stream()
                                .map(BuiltInAuthorizationProfileCatalog.Rule::permissionName)
                                .collect(Collectors.toSet())));
        Set<String> expected = Set.of(
                PermissionCatalog.API_TOKEN_VIEW,
                PermissionCatalog.API_TOKEN_CREATE,
                PermissionCatalog.API_TOKEN_REVOKE
        );

        assertThat(rulesByProfile.get(BuiltInAuthorizationProfileCatalog.API_TOKEN_MANAGER))
                .containsExactlyInAnyOrderElementsOf(expected);
        assertThat(rulesByProfile.get(BuiltInAuthorizationProfileCatalog.TENANT_ADMINISTRATOR))
                .containsAll(expected);
        assertThat(rulesByProfile.get(BuiltInAuthorizationProfileCatalog.COUNTY_ADMINISTRATOR))
                .containsAll(expected);
    }

    @Test
    void everyBuiltInProfileRuleReferencesTheFixedAtomicCatalogWithoutDuplicates() {
        for (BuiltInAuthorizationProfileCatalog.Template profile
                : BuiltInAuthorizationProfileCatalog.templates(true)) {
            assertThat(profile.rules())
                    .extracting(BuiltInAuthorizationProfileCatalog.Rule::permissionName)
                    .doesNotHaveDuplicates()
                    .allSatisfy(permissionName -> assertThat(PermissionCatalog.contains(permissionName)).isTrue());
        }
    }

    @Test
    void compositeProfileContainsEachWorkOrderReadPermissionOnce() {
        BuiltInAuthorizationProfileCatalog.Template profile = BuiltInAuthorizationProfileCatalog.templates(false)
                .stream()
                .filter(candidate -> BuiltInAuthorizationProfileCatalog.COUNTY_ADMINISTRATOR.equals(candidate.systemKey()))
                .findFirst()
                .orElseThrow();
        Set<String> permissions = profile.rules().stream()
                .map(BuiltInAuthorizationProfileCatalog.Rule::permissionName)
                .collect(Collectors.toSet());

        PermissionCatalog.workOrderEntries().forEach(workOrder -> {
            assertThat(permissions).contains(workOrder.viewPermission(), workOrder.attachmentReadPermission());
        });
    }

    @Test
    void workOrderWorkersCanReadCompleteDetailsWithinTheirOrganizationScope() {
        for (PermissionCatalog.WorkOrderEntry workOrder : PermissionCatalog.workOrderEntries()) {
            BuiltInAuthorizationProfileCatalog.Template profile = BuiltInAuthorizationProfileCatalog.templates(false)
                    .stream()
                    .filter(candidate -> BuiltInAuthorizationProfileCatalog.workOrderProfileKey(
                            workOrder.type(), WorkOrderDuty.WORKER)
                            .equals(candidate.systemKey()))
                    .findFirst()
                    .orElseThrow();
            assertThat(profile.rules())
                    .extracting(BuiltInAuthorizationProfileCatalog.Rule::permissionName)
                    .contains(workOrder.viewPermission(), workOrder.historyPermission(),
                            workOrder.attachmentReadPermission());
        }
    }

    @Test
    void boxDoorHandlingIsAnExplicitInstallationMaintenanceProfile() {
        Map<String, BuiltInAuthorizationProfileCatalog.Template> profiles =
                BuiltInAuthorizationProfileCatalog.templates(false).stream()
                        .collect(Collectors.toMap(
                                BuiltInAuthorizationProfileCatalog.Template::systemKey,
                                profile -> profile));
        PermissionCatalog.WorkOrderEntry boxDoor = PermissionCatalog.requireWorkOrderEntry(
                WorkOrderType.BOX_DOOR_OPEN);
        BuiltInAuthorizationProfileCatalog.Template installationMaintenance = profiles.get(
                BuiltInAuthorizationProfileCatalog.workOrderProfileKey(
                        WorkOrderType.BOX_DOOR_OPEN, WorkOrderDuty.WORKER));
        BuiltInAuthorizationProfileCatalog.Template generalWorkers = profiles.get(
                BuiltInAuthorizationProfileCatalog.ALL_WORKERS);

        assertThat(installationMaintenance.displayName()).isEqualTo("装维关箱处理员");
        assertThat(installationMaintenance.rules())
                .extracting(BuiltInAuthorizationProfileCatalog.Rule::permissionName)
                .contains(
                        boxDoor.claimPermission(),
                        boxDoor.assignmentAcceptPermission(),
                        boxDoor.assignmentRejectPermission(),
                        boxDoor.responseSubmitPermission());
        assertThat(generalWorkers.rules())
                .extracting(BuiltInAuthorizationProfileCatalog.Rule::permissionName)
                .doesNotContain(
                        boxDoor.claimPermission(),
                        boxDoor.assignmentAcceptPermission(),
                        boxDoor.assignmentRejectPermission(),
                        boxDoor.responseSubmitPermission());
    }

    @Test
    void appReleaseSystemCapabilitiesAreNotIncludedInTenantProfiles() {
        assertThat(BuiltInAuthorizationProfileCatalog.templates(true))
                .extracting(BuiltInAuthorizationProfileCatalog.Template::systemKey)
                .noneMatch(systemKey -> systemKey.startsWith("PROFILE_RELEASE_"));
        assertThat(BuiltInAuthorizationProfileCatalog.templates(true))
                .allSatisfy(profile -> assertThat(profile.rules())
                        .extracting(BuiltInAuthorizationProfileCatalog.Rule::permissionName)
                        .noneMatch(permissionName -> permissionName.startsWith("app-update:")));
    }

    @Test
    void slaEditorAndViewerRemainSeparatedByAdministratorLevel() {
        Map<String, Set<String>> rulesByProfile = BuiltInAuthorizationProfileCatalog.templates(true).stream()
                .collect(Collectors.toMap(
                        BuiltInAuthorizationProfileCatalog.Template::systemKey,
                        profile -> profile.rules().stream()
                                .map(BuiltInAuthorizationProfileCatalog.Rule::permissionName)
                                .collect(Collectors.toSet())));

        assertThat(rulesByProfile.get(BuiltInAuthorizationProfileCatalog.SLA_EDITOR))
                .contains(PermissionCatalog.SLA_POLICY_VIEW, PermissionCatalog.SLA_POLICY_UPDATE);
        assertThat(rulesByProfile.get(BuiltInAuthorizationProfileCatalog.SLA_VIEWER))
                .contains(PermissionCatalog.SLA_POLICY_VIEW)
                .doesNotContain(PermissionCatalog.SLA_POLICY_UPDATE);
        assertThat(rulesByProfile.get(BuiltInAuthorizationProfileCatalog.TENANT_ADMINISTRATOR))
                .contains(PermissionCatalog.SLA_POLICY_VIEW, PermissionCatalog.SLA_POLICY_UPDATE);
        assertThat(rulesByProfile.get(BuiltInAuthorizationProfileCatalog.COUNTY_ADMINISTRATOR))
                .contains(PermissionCatalog.SLA_POLICY_VIEW)
                .doesNotContain(PermissionCatalog.SLA_POLICY_UPDATE);
    }
}
