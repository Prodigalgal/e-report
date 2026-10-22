package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.AuthorizationProfile;
import com.pro.ereportbacken.model.pojo.GrantIssuerType;
import com.pro.ereportbacken.model.pojo.Permission;
import com.pro.ereportbacken.model.pojo.PermissionGrant;
import com.pro.ereportbacken.model.pojo.PermissionGrantStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.repository.AuthorizationProfilePermissionRuleRepository;
import com.pro.ereportbacken.repository.AuthorizationProfileRepository;
import com.pro.ereportbacken.repository.PermissionGrantRepository;
import com.pro.ereportbacken.repository.PermissionRepository;
import com.pro.ereportbacken.security.PermissionCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SystemGrantProvisioningService {

    private static final Set<String> SELF_BASELINE_PERMISSIONS = Set.of(
            PermissionCatalog.SELF_PROFILE_VIEW,
            PermissionCatalog.SELF_PROFILE_UPDATE,
            PermissionCatalog.SELF_PASSWORD_CHANGE,
            PermissionCatalog.SELF_SESSION_VIEW,
            PermissionCatalog.SELF_SESSION_REVOKE,
            PermissionCatalog.API_TOKEN_VIEW,
            PermissionCatalog.API_TOKEN_CREATE,
            PermissionCatalog.API_TOKEN_REVOKE
    );

    private final PermissionGrantRepository grantRepository;
    private final PermissionRepository permissionRepository;
    private final AuthorizationProfileRepository templateRepository;
    private final AuthorizationProfilePermissionRuleRepository templateRuleRepository;
    private final PermissionGrantChainService grantChainService;

    public SystemGrantProvisioningService(
            PermissionGrantRepository grantRepository,
            PermissionRepository permissionRepository,
            AuthorizationProfileRepository templateRepository,
            AuthorizationProfilePermissionRuleRepository templateRuleRepository,
            PermissionGrantChainService grantChainService) {
        this.grantRepository = grantRepository;
        this.permissionRepository = permissionRepository;
        this.templateRepository = templateRepository;
        this.templateRuleRepository = templateRuleRepository;
        this.grantChainService = grantChainService;
    }

    @Transactional
    public void reconcileSelfBaseline(User user) {
        reconcile(user, SELF_BASELINE_PERMISSIONS, GrantIssuerType.SYSTEM_SELF_BASELINE,
                permissionName -> false, false, "系统用户本人操作权限基线");
    }

    @Transactional
    public void reconcileTemplateBaseline(User user, String templateSystemKey, String reason) {
        Set<String> permissions = permissionsOfTemplate(user.getTenantId(), templateSystemKey);
        reconcile(user, permissions, GrantIssuerType.SYSTEM_BASELINE,
                permissionName -> PermissionCatalog.require(permissionName).delegable(), true, reason);
    }

    @Transactional
    public void applyTemplateBaseline(User user, String templateSystemKey, String reason) {
        Set<String> permissions = permissionsOfTemplate(user.getTenantId(), templateSystemKey);
        reconcile(user, permissions, GrantIssuerType.SYSTEM_BASELINE,
                permissionName -> PermissionCatalog.require(permissionName).delegable(), false, reason);
    }

    @Transactional
    public void reconcileSuperAdministrator(User user) {
        reconcile(user, PermissionCatalog.names(), GrantIssuerType.SYSTEM_ROOT,
                permissionName -> PermissionCatalog.require(permissionName).delegable(), true,
                "全局唯一超级管理员系统信任根");
    }

    @Transactional
    public void revokeTemplateBaseline(User user, String templateSystemKey, String reason) {
        Set<String> permissionNames = permissionsOfTemplate(user.getTenantId(), templateSystemKey);
        Set<UUID> permissionIds = permissionRepository.findByNameIn(permissionNames).stream()
                .map(Permission::getId)
                .collect(Collectors.toSet());
        List<PermissionGrant> removable = grantRepository.findByTenantIdAndMembershipIdAndStatus(
                        user.getTenantId(), user.getId(), PermissionGrantStatus.ACTIVE).stream()
                .filter(grant -> grant.getIssuerType() == GrantIssuerType.SYSTEM_BASELINE)
                .filter(grant -> permissionIds.contains(grant.getPermissionId()))
                .toList();
        if (removable.isEmpty()) {
            return;
        }
        removable.forEach(grant -> grantChainService.revoke(
                user.getTenantId(), grant.getId(), null, reason));
    }

    private void reconcile(User user,
                           Collection<String> desiredPermissionNames,
                           GrantIssuerType issuerType,
                           Function<String, Boolean> grantOptionPolicy,
                           boolean revokeObsoleteIssuerGrants,
                           String reason) {
        Set<String> desired = new LinkedHashSet<>(desiredPermissionNames);
        Map<UUID, Permission> permissionsById = permissionRepository.findByNameIn(desired).stream()
                .collect(Collectors.toMap(Permission::getId, Function.identity()));
        if (permissionsById.size() != desired.size()) {
            Set<String> found = permissionsById.values().stream()
                    .map(Permission::getName)
                    .collect(Collectors.toSet());
            desired.removeAll(found);
            throw new IllegalStateException("数据库缺少系统授权所需权限：" + String.join(", ", desired));
        }

        List<PermissionGrant> active = grantRepository.findByTenantIdAndMembershipIdAndStatus(
                user.getTenantId(), user.getId(), PermissionGrantStatus.ACTIVE);
        Map<UUID, List<PermissionGrant>> activeByPermission = active.stream()
                .collect(Collectors.groupingBy(PermissionGrant::getPermissionId));
        for (Permission permission : permissionsById.values()) {
            List<PermissionGrant> existing = activeByPermission.getOrDefault(permission.getId(), List.of());
            if (existing.size() > 1) {
                grantChainService.consolidateWithSystemRoot(
                        user.getTenantId(), existing.stream().map(PermissionGrant::getId).toList(),
                        Boolean.TRUE.equals(grantOptionPolicy.apply(permission.getName())),
                        issuerType, reason + "；合并历史重复授权");
                continue;
            }
            boolean grantOption = Boolean.TRUE.equals(grantOptionPolicy.apply(permission.getName()));
            if (existing.isEmpty()) {
                grantChainService.issueRootGrant(
                        user.getTenantId(), user.getId(), permission.getName(), grantOption,
                        issuerType, null, reason);
                continue;
            }
            PermissionGrant current = existing.get(0);
            if (!isCanonicalRoot(current, issuerType, grantOption)) {
                grantChainService.replaceWithSystemRoot(
                        user.getTenantId(), current.getId(), grantOption, issuerType, reason);
            }
        }

        if (revokeObsoleteIssuerGrants) {
            Set<UUID> desiredIds = permissionsById.keySet();
            for (PermissionGrant current : active) {
                boolean obsoleteCurrentBaseline = current.getIssuerType() == issuerType
                        && !desiredIds.contains(current.getPermissionId());
                boolean obsoleteRoleDerivedGrant = !desiredIds.contains(current.getPermissionId())
                        && current.getIssuerType() == GrantIssuerType.MIGRATION
                        && current.getParentGrantId() == null
                        && !Boolean.TRUE.equals(current.getCustomized())
                        && current.getGrantedByMembershipId() == null
                        && current.getReason() != null
                        && current.getReason().startsWith("由 1.0.47 角色权限规则展开");
                if (obsoleteCurrentBaseline || obsoleteRoleDerivedGrant) {
                    grantChainService.revoke(
                            user.getTenantId(), current.getId(), null,
                            "系统权限基线已移除该原子权限");
                }
            }
        }
    }

    private boolean isCanonicalRoot(PermissionGrant grant,
                                    GrantIssuerType issuerType,
                                    boolean grantOption) {
        return grant.getParentGrantId() == null
                && grant.getIssuerType() == issuerType
                && Boolean.valueOf(grantOption).equals(grant.getGrantOption())
                && grant.getExpiresAt() == null;
    }

    private Set<String> permissionsOfTemplate(UUID tenantId, String templateSystemKey) {
        AuthorizationProfile template = templateRepository.findByTenantIdAndSystemKey(tenantId, templateSystemKey)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getActive()))
                .filter(candidate -> candidate.getCurrentVersionId() != null)
                .orElseThrow(() -> new IllegalStateException("系统岗位模板不存在或未发布：" + templateSystemKey));
        Set<UUID> permissionIds = templateRuleRepository
                .findByTenantIdAndProfileVersionIdOrderBySortOrderAsc(tenantId, template.getCurrentVersionId())
                .stream()
                .map(rule -> rule.getPermissionId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> permissionNames = permissionRepository.findAllById(permissionIds).stream()
                .map(Permission::getName)
                .filter(PermissionCatalog::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (permissionNames.size() != permissionIds.size()) {
            throw new IllegalStateException("系统岗位模板引用了缺失或已退役的权限：" + templateSystemKey);
        }
        return Set.copyOf(permissionNames);
    }
}
