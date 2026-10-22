package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.UserOrganizationMoveStrategy;

import java.util.List;
import java.util.UUID;

public record UserOrganizationMovePreviewDTO(
        UUID membershipId,
        Long expectedAuthorizationVersion,
        UserOrganizationMoveStrategy selectedStrategy,
        UserOrganizationMoveStrategy recommendedStrategy,
        OrganizationChange organization,
        AuthorizationImpact authorization,
        ManagementImpact management,
        WorkOrderImpact workOrders,
        List<Notice> notices
) {
    public UserOrganizationMovePreviewDTO {
        notices = List.copyOf(notices);
    }

    public record OrganizationChange(
            UUID beforeOrgUnitId,
            String beforeOrgUnitName,
            UUID afterOrgUnitId,
            String afterOrgUnitName
    ) {
    }

    public record AuthorizationImpact(
            int beforeActiveGrantCount,
            int afterActiveGrantCount,
            List<AuthorizationPlanResponseDTO.PermissionChangePreview> changes
    ) {
        public AuthorizationImpact {
            changes = List.copyOf(changes);
        }
    }

    public record ManagementImpact(
            boolean systemIdentity,
            int activeGrantCount,
            boolean reauthorizationRecommended
    ) {
    }

    public record WorkOrderImpact(
            long openReportedWorkOrders,
            long pendingAcceptanceAssignments,
            long acceptedAssignments,
            long activeResponsibilities,
            boolean automaticallyReassigned
    ) {
    }

    public record Notice(
            NoticeSeverity severity,
            String code,
            String title,
            String detail
    ) {
    }

    public enum NoticeSeverity {
        INFO,
        WARNING
    }
}
