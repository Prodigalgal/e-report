package com.pro.ereportbacken.service;

import java.util.UUID;

import com.pro.ereportbacken.security.BuiltInAuthorizationProfileCatalog;
import com.pro.ereportbacken.model.dto.TenantCreateRequestDTO;
import com.pro.ereportbacken.model.dto.TenantDTO;
import com.pro.ereportbacken.model.dto.TenantGroupDTO;
import com.pro.ereportbacken.model.pojo.OrgUnit;
import com.pro.ereportbacken.model.pojo.OrgUnitType;
import com.pro.ereportbacken.model.pojo.Tenant;
import com.pro.ereportbacken.model.pojo.TenantGroup;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.PermissionRepository;
import com.pro.ereportbacken.repository.TenantGroupRepository;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final TenantGroupRepository tenantGroupRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final WorkOrderSlaPolicyService slaPolicyService;
    private final OrganizationDirectoryCache organizationDirectoryCache;
    private final UserAuthorizationCache userAuthorizationCache;
    private final AuthorizationProfileCatalogSynchronizer profileCatalogSynchronizer;
    private final SystemGrantProvisioningService systemGrantProvisioningService;

    public TenantProvisioningService(TenantRepository tenantRepository,
                                     TenantGroupRepository tenantGroupRepository,
                                     OrgUnitRepository orgUnitRepository,
                                     PermissionRepository permissionRepository,
                                     UserRepository userRepository,
                                     PasswordEncoder passwordEncoder,
                                     PasswordPolicy passwordPolicy,
                                     WorkOrderSlaPolicyService slaPolicyService,
                                     OrganizationDirectoryCache organizationDirectoryCache,
                                     UserAuthorizationCache userAuthorizationCache,
                                     AuthorizationProfileCatalogSynchronizer profileCatalogSynchronizer,
                                     SystemGrantProvisioningService systemGrantProvisioningService) {
        this.tenantRepository = tenantRepository;
        this.tenantGroupRepository = tenantGroupRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.slaPolicyService = slaPolicyService;
        this.organizationDirectoryCache = organizationDirectoryCache;
        this.userAuthorizationCache = userAuthorizationCache;
        this.profileCatalogSynchronizer = profileCatalogSynchronizer;
        this.systemGrantProvisioningService = systemGrantProvisioningService;
    }

    @Transactional(readOnly = true)
    public List<TenantDTO> findAll() {
        return tenantRepository.findAllByOrderByNameAsc().stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<TenantGroupDTO> findGroups() {
        return tenantGroupRepository.findAll().stream()
                .map(group -> new TenantGroupDTO(group.getId(), group.getCode(), group.getName(), group.getStatus()))
                .toList();
    }

    @Transactional
    public TenantDTO create(TenantCreateRequestDTO request) {
        String tenantCode = request.getCode().trim().toUpperCase(Locale.ROOT);
        if (tenantRepository.findByCode(tenantCode).isPresent()) {
            throw new IllegalArgumentException("市公司租户编码已存在，请更换后重试。");
        }
        String administratorUsername = request.getAdminUsername().trim();
        if (userRepository.findByUsername(administratorUsername).isPresent()) {
            throw new IllegalArgumentException("初始管理员用户名已在系统中使用，请更换后重试。");
        }
        TenantGroup tenantGroup = tenantGroupRepository.findById(request.getTenantGroupId())
                .filter(group -> group.getStatus() == TenantStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("选择的省级租户组不存在或已停用。"));

        Tenant tenant = new Tenant();
        tenant.setTenantGroupId(tenantGroup.getId());
        tenant.setCode(tenantCode);
        tenant.setName(request.getName().trim());
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant = tenantRepository.saveAndFlush(tenant);

        OrgUnit rootOrg = createRootOrg(tenant, request.getRootOrgCode(), request.getRootOrgName());
        tenant.setRootOrgUnitId(rootOrg.getId());
        tenant = tenantRepository.saveAndFlush(tenant);
        Map<String, com.pro.ereportbacken.model.pojo.Permission> permissionsByName =
                permissionRepository.findAll().stream().collect(java.util.stream.Collectors.toMap(
                        com.pro.ereportbacken.model.pojo.Permission::getName,
                        permission -> permission));
        profileCatalogSynchronizer.synchronize(tenant.getId(), false, permissionsByName);
        User administrator = createInitialAdministrator(tenant, rootOrg, request);
        systemGrantProvisioningService.reconcileSelfBaseline(administrator);
        systemGrantProvisioningService.reconcileTemplateBaseline(
                administrator, BuiltInAuthorizationProfileCatalog.TENANT_ADMINISTRATOR,
                "创建市公司租户时授予初始管理员系统权限基线");
        slaPolicyService.initializeDefaultsForTenant(tenant.getId());
        organizationDirectoryCache.invalidateAfterCommit(tenant.getId());
        return toDTO(tenant);
    }

    @Transactional
    public TenantDTO updateStatus(UUID tenantId, TenantStatus status) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + tenantId));
        if (tenant.getSystemKey() == com.pro.ereportbacken.model.pojo.TenantSystemKey.DEFAULT_TENANT
                && status != TenantStatus.ACTIVE) {
            throw new IllegalArgumentException("默认市公司租户不能停用。");
        }
        tenant.setStatus(status);
        TenantDTO result = toDTO(tenantRepository.save(tenant));
        userAuthorizationCache.clearAfterCommit();
        return result;
    }

    private OrgUnit createRootOrg(Tenant tenant, String rootOrgCode, String rootOrgName) {
        OrgUnit root = new OrgUnit();
        root.setTenantId(tenant.getId());
        root.setCode(rootOrgCode.trim().toUpperCase(Locale.ROOT));
        root.setName(rootOrgName.trim());
        root.setUnitType(OrgUnitType.CITY);
        root.setStatus(TenantStatus.ACTIVE);
        root.setPath("/pending/");
        root = orgUnitRepository.saveAndFlush(root);
        root.setPath("/" + root.getId() + "/");
        return orgUnitRepository.save(root);
    }

    private User createInitialAdministrator(Tenant tenant,
                                            OrgUnit rootOrg,
                                            TenantCreateRequestDTO request) {
        User admin = new User();
        admin.setTenantId(tenant.getId());
        admin.setOrgUnitId(rootOrg.getId());
        admin.setAuthorizationVersion(1L);
        admin.setUsername(request.getAdminUsername().trim());
        passwordPolicy.requireValid(request.getAdminPassword(), new PasswordPolicy.PasswordContext(
                request.getAdminUsername(), request.getAdminName(), request.getAdminPhone(), request.getAdminEmail()),
                passwordPolicy.current());
        admin.setPassword(passwordEncoder.encode(request.getAdminPassword()));
        admin.setPasswordChangeRequired(true);
        admin.setName(request.getAdminName().trim());
        admin.setPhone(UserContactPolicy.normalizeRequiredPhone(request.getAdminPhone()));
        admin.setEmail(UserContactPolicy.normalizeOptionalEmail(request.getAdminEmail()));
        admin.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(admin);
    }

    private TenantDTO toDTO(Tenant tenant) {
        return new TenantDTO(
                tenant.getId(),
                tenant.getTenantGroupId(),
                tenant.getCode(),
                tenant.getName(),
                tenant.getStatus(),
                tenant.getSystemKey() == com.pro.ereportbacken.model.pojo.TenantSystemKey.DEFAULT_TENANT,
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}

