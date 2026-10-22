package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.model.dto.EmailNotificationPreferencesDTO;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.AdministrativeControlAction;
import com.pro.ereportbacken.security.AdministrativeControlService;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantUserDetails;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdminEmailNotificationPreferencesService {
    private static final Logger log = LoggerFactory.getLogger(AdminEmailNotificationPreferencesService.class);

    private final UserRepository users;
    private final EmailNotificationPreferencesService preferences;
    private final OrganizationScopeAuthorizationService scopeAuthorization;
    private final AdministrativeControlService administrativeControl;

    public AdminEmailNotificationPreferencesService(UserRepository users,
                                                    EmailNotificationPreferencesService preferences,
                                                    OrganizationScopeAuthorizationService scopeAuthorization,
                                                    AdministrativeControlService administrativeControl) {
        this.users = users;
        this.preferences = preferences;
        this.scopeAuthorization = scopeAuthorization;
        this.administrativeControl = administrativeControl;
    }

    public EmailNotificationPreferencesDTO get(UUID userId) {
        TenantUserDetails operator = requireOperator();
        User target = users.findByTenantIdAndId(operator.tenantId(), userId)
                .orElseThrow(() -> new EntityNotFoundException("目标账号不存在。"));
        scopeAuthorization.requireOrganizationAccess(PermissionCatalog.USER_MANAGE_VIEW, target.getOrgUnitId());
        return preferences.get(target.getTenantId(), target.getId());
    }

    @Transactional
    public EmailNotificationPreferencesDTO update(UUID userId, EmailNotificationPreferencesDTO requested) {
        TenantUserDetails operator = requireOperator();
        // 与组织调整锁定同一账号行，避免在权限校验后账号被移出管理范围。
        User target = users.findByTenantIdAndIdForAuthorizationUpdate(operator.tenantId(), userId)
                .orElseThrow(() -> new EntityNotFoundException("目标账号不存在。"));
        scopeAuthorization.requireOrganizationAccess(PermissionCatalog.USER_MANAGE_VIEW, target.getOrgUnitId());
        scopeAuthorization.requireOrganizationAccess(PermissionCatalog.USER_MANAGE_CONTACT_UPDATE, target.getOrgUnitId());
        administrativeControl.require(target, AdministrativeControlAction.UPDATE_IDENTITY);
        if (target.getStatus() == UserStatus.DELETED) {
            throw new IllegalArgumentException("已删除账号不能修改邮件通知偏好。请先恢复账号。");
        }
        EmailNotificationPreferencesDTO saved = preferences.update(target.getTenantId(), target.getId(), requested);
        log.info("Email notification preferences updated: tenant={} operator={} target={} preferences={}",
                target.getTenantId(), operator.membershipId(), target.getId(), saved);
        return saved;
    }

    private TenantUserDetails requireOperator() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof TenantUserDetails operator)) {
            throw new AccessDeniedException("需要已认证的租户用户。");
        }
        return operator;
    }
}
