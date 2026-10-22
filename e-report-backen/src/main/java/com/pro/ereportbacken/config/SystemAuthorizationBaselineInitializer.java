package com.pro.ereportbacken.config;

import com.pro.ereportbacken.model.pojo.GrantIssuerType;
import com.pro.ereportbacken.model.pojo.Permission;
import com.pro.ereportbacken.model.pojo.Tenant;
import com.pro.ereportbacken.model.pojo.TenantSystemKey;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserSystemKey;
import com.pro.ereportbacken.repository.PermissionGrantRepository;
import com.pro.ereportbacken.repository.PermissionRepository;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.BuiltInAuthorizationProfileCatalog;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.service.AuthorizationProfileCatalogSynchronizer;
import com.pro.ereportbacken.service.PermissionCatalogDriftService;
import com.pro.ereportbacken.service.SystemGrantProvisioningService;
import com.pro.ereportbacken.service.UserAuthorizationCache;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Keeps code-owned authorization invariants current even when bootstrap data creation is disabled.
 * User grants remain the runtime source of truth; this component only maintains SYSTEM_ROOT and
 * SYSTEM_BASELINE facts that the application itself owns.
 */
@Component
@Order(20)
@ConditionalOnProperty(
        name = "app.system-authorization-baseline.enabled",
        havingValue = "true",
        matchIfMissing = true)
@Log4j2
public class SystemAuthorizationBaselineInitializer implements CommandLineRunner {

    private final PermissionCatalogDriftService permissionCatalogDriftService;
    private final PermissionRepository permissionRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PermissionGrantRepository grantRepository;
    private final AuthorizationProfileCatalogSynchronizer profileCatalogSynchronizer;
    private final SystemGrantProvisioningService grantProvisioningService;
    private final UserAuthorizationCache userAuthorizationCache;

    public SystemAuthorizationBaselineInitializer(
            PermissionCatalogDriftService permissionCatalogDriftService,
            PermissionRepository permissionRepository,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            PermissionGrantRepository grantRepository,
            AuthorizationProfileCatalogSynchronizer profileCatalogSynchronizer,
            SystemGrantProvisioningService grantProvisioningService,
            UserAuthorizationCache userAuthorizationCache) {
        this.permissionCatalogDriftService = permissionCatalogDriftService;
        this.permissionRepository = permissionRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.grantRepository = grantRepository;
        this.profileCatalogSynchronizer = profileCatalogSynchronizer;
        this.grantProvisioningService = grantProvisioningService;
        this.userAuthorizationCache = userAuthorizationCache;
    }

    @Override
    @Transactional
    public void run(String... args) {
        permissionCatalogDriftService.scanAndRequireCompleteCatalog();
        Map<String, Permission> permissionsByName = permissionsByName();
        List<Tenant> tenants = tenantRepository.findAllByOrderByNameAsc();
        for (Tenant tenant : tenants) {
            profileCatalogSynchronizer.synchronize(
                    tenant.getId(), tenant.getSystemKey() == TenantSystemKey.DEFAULT_TENANT, permissionsByName);
        }

        userRepository.findBySystemKey(UserSystemKey.SUPER_ADMINISTRATOR)
                .ifPresent(grantProvisioningService::reconcileSuperAdministrator);

        List<UUID> baselineAdministratorIds = grantRepository
                .findActiveMembershipIdsByPermissionAndIssuerType(
                        PermissionCatalog.USER_AUTHORIZATION_GRANT,
                        GrantIssuerType.SYSTEM_BASELINE);
        Map<UUID, Tenant> tenantsById = tenants.stream()
                .collect(Collectors.toMap(Tenant::getId, Function.identity()));
        int reconciledAdministrators = 0;
        for (User administrator : userRepository.findAllById(baselineAdministratorIds)) {
            if (administrator.getSystemKey() == UserSystemKey.SUPER_ADMINISTRATOR) {
                continue;
            }
            Tenant tenant = tenantsById.get(administrator.getTenantId());
            if (tenant == null || administrator.getOrgUnitId() == null || tenant.getRootOrgUnitId() == null) {
                throw new IllegalStateException("系统基线管理员缺少有效租户或所属组织：" + administrator.getId());
            }
            boolean tenantAdministrator = Objects.equals(
                    tenant.getRootOrgUnitId(), administrator.getOrgUnitId());
            grantProvisioningService.reconcileTemplateBaseline(
                    administrator,
                    tenantAdministrator
                            ? BuiltInAuthorizationProfileCatalog.TENANT_ADMINISTRATOR
                            : BuiltInAuthorizationProfileCatalog.COUNTY_ADMINISTRATOR,
                    tenantAdministrator
                            ? "系统校准：市公司管理员标准权限基线"
                            : "系统校准：区县管理员标准权限基线");
            reconciledAdministrators++;
        }
        userAuthorizationCache.clearAfterCommit();
        log.info("System authorization baseline reconciled; tenants={}, baselineAdministrators={}, catalogPermissions={}",
                tenants.size(), reconciledAdministrators, permissionsByName.size());
    }

    private Map<String, Permission> permissionsByName() {
        Map<String, Permission> permissions = permissionRepository.findByNameIn(PermissionCatalog.names()).stream()
                .collect(Collectors.toMap(
                        Permission::getName,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        if (!permissions.keySet().containsAll(PermissionCatalog.names())) {
            throw new IllegalStateException("权限目录数据库不完整，已拒绝校准系统授权基线。");
        }
        return Map.copyOf(permissions);
    }
}
