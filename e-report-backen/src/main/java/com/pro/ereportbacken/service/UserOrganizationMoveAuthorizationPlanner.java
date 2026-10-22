package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.AuthorizationOperationRequestDTO;
import com.pro.ereportbacken.model.dto.UserAuthorizationViewDTO;
import com.pro.ereportbacken.model.pojo.AuthorizationOperationType;
import com.pro.ereportbacken.model.pojo.GrantIssuerType;
import com.pro.ereportbacken.model.pojo.PermissionGrantStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserOrganizationMoveStrategy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserOrganizationMoveAuthorizationPlanner {

    private final AuthorizationAdministrationService authorizationAdministrationService;

    public UserOrganizationMoveAuthorizationPlanner(
            AuthorizationAdministrationService authorizationAdministrationService) {
        this.authorizationAdministrationService = authorizationAdministrationService;
    }

    public Plan prepare(User user, UUID targetOrgUnitId, UserOrganizationMoveStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "调整所属组织必须选择授权处理策略：保留全部或撤销全部可撤销授权。");
        }
        UserAuthorizationViewDTO authorization = authorizationAdministrationService.view(user.getId());
        if (strategy == UserOrganizationMoveStrategy.KEEP) {
            return new Plan(authorization, List.of());
        }
        List<AuthorizationOperationRequestDTO> operations = authorization.grants().stream()
                .filter(grant -> grant.status() == PermissionGrantStatus.ACTIVE)
                .filter(grant -> strategy == UserOrganizationMoveStrategy.REVOKE_ALL)
                .filter(grant -> !isSystemManaged(grant.issuerType()))
                .map(grant -> new AuthorizationOperationRequestDTO(
                        AuthorizationOperationType.REVOKE_PERMISSION,
                        grant.id(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "用户调整所属组织时按策略撤销授权"))
                .toList();
        return new Plan(authorization, operations);
    }

    private boolean isSystemManaged(GrantIssuerType issuerType) {
        return issuerType == GrantIssuerType.SYSTEM_ROOT
                || issuerType == GrantIssuerType.SYSTEM_BASELINE
                || issuerType == GrantIssuerType.SYSTEM_SELF_BASELINE;
    }

    public record Plan(
            UserAuthorizationViewDTO currentAuthorization,
            List<AuthorizationOperationRequestDTO> operations
    ) {
        public Plan {
            operations = List.copyOf(operations);
        }

        public int beforeActiveGrantCount() {
            return currentAuthorization.effectivePermissionCount();
        }
    }
}
