package com.pro.ereportbacken.security;

import com.pro.ereportbacken.model.pojo.AccessMode;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionCatalogTests {

    @Test
    void exposesEveryWorkOrderBusinessActionAsAnAtomicPermission() {
        assertThat(PermissionCatalog.workOrderEntries())
                .hasSize(WorkOrderType.values().length)
                .extracting(PermissionCatalog.WorkOrderEntry::type)
                .containsExactlyInAnyOrder(WorkOrderType.values());

        for (PermissionCatalog.WorkOrderEntry entry : PermissionCatalog.workOrderEntries()) {
            assertThat(entry.allPermissions()).hasSize(24);
            assertThat(entry.verificationApprovePermission())
                    .isNotEqualTo(entry.verificationRejectPermission());
            assertThat(entry.assignmentAcceptPermission())
                    .isNotEqualTo(entry.assignmentRejectPermission());
            assertThat(entry.holdPermission()).isNotEqualTo(entry.resumePermission());
            assertThat(entry.verificationSelfApprovePermission())
                    .isNotEqualTo(entry.verificationApprovePermission());
            assertThat(Set.of(
                    entry.transferCreatePermission(), entry.transferAcceptPermission(),
                    entry.transferRejectPermission(), entry.transferCancelPermission())).hasSize(4);
            assertThat(PermissionCatalog.names()).containsAll(entry.allPermissions());
        }
    }

    @Test
    void groupedEndpointGatesExposeEachAtomicAction() {
        assertThat(PermissionCatalog.reportPermissions()).hasSize(3);
        assertThat(PermissionCatalog.responseSubmitPermissions()).hasSize(3);
        assertThat(PermissionCatalog.exportPermissions()).hasSize(3);
        assertThat(PermissionCatalog.attachmentReadPermissions()).hasSize(3);
        assertThat(PermissionCatalog.assignmentAcceptPermissions()).hasSize(3);
        assertThat(PermissionCatalog.assignmentRejectPermissions()).hasSize(3);
        assertThat(PermissionCatalog.assignmentCreatePermissions()).hasSize(3);
        assertThat(PermissionCatalog.assignmentReassignPermissions()).hasSize(3);
        assertThat(PermissionCatalog.verificationApprovePermissions()).hasSize(3);
        assertThat(PermissionCatalog.verificationRejectPermissions()).hasSize(3);
        assertThat(PermissionCatalog.holdPermissions()).hasSize(3);
        assertThat(PermissionCatalog.resumePermissions()).hasSize(3);
    }

    @Test
    void permissionMetadataDefinesRiskDelegationAndSystemBoundary() {
        PermissionCatalog.Entry approve = PermissionCatalog.require(
                PermissionCatalog.requireWorkOrderEntry(WorkOrderType.NETWORK_FAULT)
                        .verificationApprovePermission());
        assertThat(approve.accessMode()).isEqualTo(AccessMode.W);
        assertThat(approve.riskLevel()).isEqualTo(PermissionRiskLevel.CRITICAL);
        assertThat(approve.delegable()).isTrue();

        PermissionCatalog.Entry attachment = PermissionCatalog.require(
                PermissionCatalog.NETWORK_FAULT_ATTACHMENT_READ);
        assertThat(attachment.accessMode()).isEqualTo(AccessMode.R);
        assertThat(attachment.apiTokenAssignable()).isTrue();

        assertThat(PermissionCatalog.entries())
                .filteredOn(entry -> entry.name().startsWith("app-update:"))
                .isNotEmpty()
                .allSatisfy(entry -> {
                    assertThat(entry.delegable()).isTrue();
                    assertThat(entry.systemScoped()).isTrue();
                    assertThat(entry.riskLevel()).isEqualTo(PermissionRiskLevel.CRITICAL);
                });
        assertThat(PermissionCatalog.entries())
                .filteredOn(entry -> !entry.name().startsWith("app-update:"))
                .allSatisfy(entry -> assertThat(entry.systemScoped()).isFalse());
    }

    @Test
    void apiTokensOnlyEmbedWorkOrderAndDashboardPermissions() {
        assertThat(Set.of(
                PermissionCatalog.API_TOKEN_VIEW,
                PermissionCatalog.API_TOKEN_CREATE,
                PermissionCatalog.API_TOKEN_REVOKE
        )).allSatisfy(permissionName -> {
            PermissionCatalog.Entry permission = PermissionCatalog.require(permissionName);
            assertThat(permission.delegable()).isTrue();
            assertThat(permission.systemScoped()).isFalse();
            assertThat(PermissionCatalog.assignableToApiToken(permissionName)).isFalse();
        });
        assertThat(PermissionCatalog.assignableToApiToken(PermissionCatalog.NETWORK_FAULT_VIEW)).isTrue();
        assertThat(PermissionCatalog.assignableToApiToken(PermissionCatalog.DASHBOARD_STATISTICS_VIEW)).isTrue();
        assertThat(PermissionCatalog.assignableToApiToken(PermissionCatalog.USER_MANAGE_VIEW)).isFalse();
        assertThat(PermissionCatalog.assignableToApiToken(PermissionCatalog.USER_AUTHORIZATION_GRANT)).isFalse();
    }

    @Test
    void atomicPermissionCodesArePresent() {
        assertThat(PermissionCatalog.names()).contains(
                PermissionCatalog.USER_MANAGE_STATUS_ACTIVATE,
                PermissionCatalog.USER_MANAGE_STATUS_DEACTIVATE,
                PermissionCatalog.USER_MANAGE_ORGANIZATION_MOVE,
                PermissionCatalog.USER_AUTHORIZATION_GRANT,
                PermissionCatalog.USER_AUTHORIZATION_ADJUST,
                PermissionCatalog.USER_AUTHORIZATION_REVOKE,
                PermissionCatalog.NOTIFICATION_CHANNEL_UPDATE,
                PermissionCatalog.NOTIFICATION_SCHEDULE_UPDATE,
                PermissionCatalog.NOTIFICATION_SENDER_UPDATE,
                PermissionCatalog.API_TOKEN_VIEW,
                PermissionCatalog.API_TOKEN_CREATE,
                PermissionCatalog.API_TOKEN_REVOKE,
                PermissionCatalog.APP_UPDATE_STORAGE_SYNC,
                PermissionCatalog.APP_UPDATE_STORAGE_DELETE
        );
    }
}
