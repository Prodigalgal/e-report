package com.pro.ereportbacken.security;

import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserSystemKey;
import com.pro.ereportbacken.service.OrganizationHierarchyService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AdministrativeControlService {

    private static final Set<AdministrativeControlAction> SENSITIVE_SELF_ACTIONS = Set.of(
            AdministrativeControlAction.MOVE_ORGANIZATION,
            AdministrativeControlAction.MOVE_DEPARTMENT,
            AdministrativeControlAction.UPDATE_STATUS,
            AdministrativeControlAction.RESET_PASSWORD,
            AdministrativeControlAction.DELETE_OR_RESTORE,
            AdministrativeControlAction.REVOKE_SESSION,
            AdministrativeControlAction.MANAGE_ACCESS
    );

    private final OrganizationHierarchyService hierarchyService;

    public AdministrativeControlService(OrganizationHierarchyService hierarchyService) {
        this.hierarchyService = hierarchyService;
    }

    public AdministrativeControlDecision decide(User target, AdministrativeControlAction action) {
        TenantUserDetails principal = currentPrincipal();
        if (principal == null) {
            return AdministrativeControlDecision.allow();
        }
        if (!principal.tenantId().equals(target.getTenantId())) {
            return deny("TARGET_TENANT_MISMATCH", "目标账号不属于当前租户。");
        }
        if (principal.membershipId().equals(target.getId()) && SENSITIVE_SELF_ACTIONS.contains(action)) {
            return deny("SELF_PRIVILEGED_ACTION_DENIED", "不能通过管理入口对自己执行提权、停用、凭据或授权控制操作。");
        }
        if (principal.authorizationSnapshot().systemKey() == UserSystemKey.SUPER_ADMINISTRATOR) {
            return AdministrativeControlDecision.allow();
        }
        if (target.getSystemKey() == UserSystemKey.SUPER_ADMINISTRATOR) {
            return deny("SYSTEM_ROOT_PROTECTED", "系统根或紧急恢复账号不能通过租户管理入口修改。");
        }

        if (!principal.orgUnitId().equals(target.getOrgUnitId())
                && !hierarchyService.treeContains(
                        target.getTenantId(), principal.orgUnitId(), target.getOrgUnitId())) {
            return deny("ORGANIZATION_RELATION_DENIED", "只能管理所属组织或下级组织内的用户。");
        }
        return AdministrativeControlDecision.allow();
    }

    public void require(User target, AdministrativeControlAction action) {
        AdministrativeControlDecision decision = decide(target, action);
        if (!decision.allowed()) {
            throw new AccessDeniedException(decision.message() + "（" + decision.reasonCode() + "）");
        }
    }

    private TenantUserDetails currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof TenantUserDetails principal)) {
            return null;
        }
        return principal;
    }

    private AdministrativeControlDecision deny(String code, String message) {
        return AdministrativeControlDecision.deny(code, message);
    }
}
