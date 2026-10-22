package com.pro.ereportbacken.config;

import com.pro.ereportbacken.model.dto.UserCreateRequestDTO;
import com.pro.ereportbacken.model.pojo.Permission;
import com.pro.ereportbacken.model.pojo.Tenant;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.model.pojo.UserSystemKey;
import com.pro.ereportbacken.repository.PermissionRepository;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.service.AuthorizationProfileCatalogSynchronizer;
import com.pro.ereportbacken.service.DefaultTenantService;
import com.pro.ereportbacken.service.OrganizationDirectoryCache;
import com.pro.ereportbacken.service.PermissionCatalogDriftService;
import com.pro.ereportbacken.service.SystemGrantProvisioningService;
import com.pro.ereportbacken.service.UserAuthorizationCache;
import com.pro.ereportbacken.service.UserService;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@Order(10)
@ConditionalOnProperty(name = "app.data-initializer.enabled", havingValue = "true", matchIfMissing = true)
@Log4j2
public class DataInitializer implements CommandLineRunner {

    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final DefaultTenantService defaultTenantService;
    private final TenantRepository tenantRepository;
    private final OrganizationDirectoryCache organizationDirectoryCache;
    private final UserAuthorizationCache userAuthorizationCache;
    private final AuthorizationProfileCatalogSynchronizer profileCatalogSynchronizer;
    private final SystemGrantProvisioningService systemGrantProvisioningService;
    private final PermissionCatalogDriftService permissionCatalogDriftService;
    private final String bootstrapSuperAdminUsername;
    private final String bootstrapSuperAdminPassword;
    private final String bootstrapSuperAdminPhone;

    public DataInitializer(
            PermissionRepository permissionRepository,
            UserRepository userRepository,
            UserService userService,
            DefaultTenantService defaultTenantService,
            TenantRepository tenantRepository,
            OrganizationDirectoryCache organizationDirectoryCache,
            UserAuthorizationCache userAuthorizationCache,
            AuthorizationProfileCatalogSynchronizer profileCatalogSynchronizer,
            SystemGrantProvisioningService systemGrantProvisioningService,
            PermissionCatalogDriftService permissionCatalogDriftService,
            @Value("${app.bootstrap-super-admin.username:}") String bootstrapSuperAdminUsername,
            @Value("${app.bootstrap-super-admin.password:}") String bootstrapSuperAdminPassword,
            @Value("${app.bootstrap-super-admin.phone:}") String bootstrapSuperAdminPhone) {
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.defaultTenantService = defaultTenantService;
        this.tenantRepository = tenantRepository;
        this.organizationDirectoryCache = organizationDirectoryCache;
        this.userAuthorizationCache = userAuthorizationCache;
        this.profileCatalogSynchronizer = profileCatalogSynchronizer;
        this.systemGrantProvisioningService = systemGrantProvisioningService;
        this.permissionCatalogDriftService = permissionCatalogDriftService;
        this.bootstrapSuperAdminUsername = bootstrapSuperAdminUsername;
        this.bootstrapSuperAdminPassword = bootstrapSuperAdminPassword;
        this.bootstrapSuperAdminPhone = bootstrapSuperAdminPhone;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("同步固定原子权限目录、内置岗位权限模板和系统管理员身份基线。");
        permissionCatalogDriftService.scanAndRequireCompleteCatalog();
        Map<String, Permission> permissionsByName = syncPermissionCatalog();
        syncAuthorizationProfiles(permissionsByName);
        userRepository.findAll().stream()
                .filter(user -> user.getSystemKey() != UserSystemKey.SUPER_ADMINISTRATOR)
                .forEach(systemGrantProvisioningService::reconcileSelfBaseline);
        Optional<User> superAdministrator = ensureBootstrapSuperAdministrator();
        if (superAdministrator.isPresent()) {
            User administrator = superAdministrator.orElseThrow();
            ensureSuperAdministratorSystemAuthorization(administrator);
        } else {
            log.warn("尚未配置超级管理员系统身份；已拒绝将平台级权限回退给根租户管理员。");
        }

        tenantRepository.findAll().forEach(tenant ->
                organizationDirectoryCache.invalidateAfterCommit(tenant.getId()));
        userAuthorizationCache.clearAfterCommit();
    }

    private Map<String, Permission> syncPermissionCatalog() {
        Map<String, Permission> permissions = permissionRepository.findByNameIn(PermissionCatalog.names()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        Permission::getName,
                        java.util.function.Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        if (!permissions.keySet().containsAll(PermissionCatalog.names())) {
            throw new IllegalStateException("权限目录数据库不完整，已拒绝继续初始化内置岗位。");
        }
        return Map.copyOf(permissions);
    }

    private void syncAuthorizationProfiles(Map<String, Permission> permissionsByName) {
        UUID defaultTenantId = defaultTenantService.requireDefaultTenant().getId();
        for (Tenant tenant : tenantRepository.findAll()) {
            profileCatalogSynchronizer.synchronize(
                    tenant.getId(), tenant.getId().equals(defaultTenantId), permissionsByName);
        }
    }

    private Optional<User> ensureBootstrapSuperAdministrator() {
        var existingSuperAdministrator = userRepository.findBySystemKey(UserSystemKey.SUPER_ADMINISTRATOR);
        if (existingSuperAdministrator.isPresent()) {
            log.info("数据库已配置超级管理员；保留数据库账号资料并校准最高系统权限。");
            return existingSuperAdministrator;
        }
        String username = StringUtils.hasText(bootstrapSuperAdminUsername)
                ? bootstrapSuperAdminUsername.trim()
                : "";
        if (!StringUtils.hasText(username)
                || !StringUtils.hasText(bootstrapSuperAdminPassword)
                || !StringUtils.hasText(bootstrapSuperAdminPhone)) {
            return Optional.empty();
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalStateException("已存在同名账号但未配置超级管理员系统身份，请先完成账号归属迁移。");
        }
        User superAdministrator = createBootstrapAccount(
                username, bootstrapSuperAdminPassword, bootstrapSuperAdminPhone, "超级管理员");
        superAdministrator.setSystemKey(UserSystemKey.SUPER_ADMINISTRATOR);
        superAdministrator = userRepository.saveAndFlush(superAdministrator);
        log.info("超级管理员 '{}' 已由 ENV 首次引导创建，后续账号资料以数据库为准。", username);
        return Optional.of(superAdministrator);
    }

    private void ensureSuperAdministratorSystemAuthorization(User superAdministrator) {
        UUID defaultTenantId = defaultTenantService.requireDefaultTenant().getId();
        if (!defaultTenantId.equals(superAdministrator.getTenantId())) {
            throw new IllegalStateException("超级管理员系统身份必须归属于默认市公司租户。");
        }
        systemGrantProvisioningService.reconcileSuperAdministrator(superAdministrator);
    }

    private User createBootstrapAccount(String username, String password, String phone, String name) {
        UserCreateRequestDTO request = new UserCreateRequestDTO();
        request.setUsername(username);
        request.setPassword(password);
        request.setName(name);
        request.setPhone(phone);
        request.setStatus(UserStatus.ACTIVE);
        request.setOrgUnitId(defaultTenantService.requireRootOrgUnit(
                defaultTenantService.requireDefaultTenant().getId()).getId());
        return userService.createUser(request);
    }
}
