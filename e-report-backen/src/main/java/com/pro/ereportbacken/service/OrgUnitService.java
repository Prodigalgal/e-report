package com.pro.ereportbacken.service;

import java.util.UUID;

import com.pro.ereportbacken.model.dto.OrgUnitDTO;
import com.pro.ereportbacken.model.dto.OrgUnitRequestDTO;
import com.pro.ereportbacken.model.dto.OrgUnitMoveRequestDTO;
import com.pro.ereportbacken.model.pojo.OrgUnit;
import com.pro.ereportbacken.model.pojo.OrgUnitType;
import com.pro.ereportbacken.model.pojo.OrganizationMoveEvent;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.OrganizationMoveEventRepository;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantUserDetails;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

@Service
public class OrgUnitService {

    private final OrgUnitRepository orgUnitRepository;
    private final TenantRepository tenantRepository;
    private final OrganizationMoveEventRepository organizationMoveEventRepository;
    private final OrganizationDirectoryCache organizationDirectoryCache;
    private final TenantContext tenantContext;
    private final OrganizationScopeAuthorizationService scopeAuthorizationService;
    private final UserAuthorizationCache userAuthorizationCache;

    public OrgUnitService(OrgUnitRepository orgUnitRepository,
                          TenantRepository tenantRepository,
                          OrganizationMoveEventRepository organizationMoveEventRepository,
                          OrganizationDirectoryCache organizationDirectoryCache,
                          TenantContext tenantContext,
                          OrganizationScopeAuthorizationService scopeAuthorizationService,
                          UserAuthorizationCache userAuthorizationCache) {
        this.orgUnitRepository = orgUnitRepository;
        this.tenantRepository = tenantRepository;
        this.organizationMoveEventRepository = organizationMoveEventRepository;
        this.organizationDirectoryCache = organizationDirectoryCache;
        this.tenantContext = tenantContext;
        this.scopeAuthorizationService = scopeAuthorizationService;
        this.userAuthorizationCache = userAuthorizationCache;
    }

    @Transactional(readOnly = true)
    public List<OrgUnitDTO> findAll() {
        return findAccessible(
                PermissionCatalog.ORG_MANAGE_VIEW,
                PermissionCatalog.USER_MANAGE_VIEW,
                PermissionCatalog.SYSTEM_CONFIG_VIEW,
                PermissionCatalog.DEPARTMENT_VIEW
        );
    }

    @Transactional(readOnly = true)
    public List<OrgUnitDTO> findConfigAccessible() {
        return findAccessible(PermissionCatalog.SYSTEM_CONFIG_VIEW);
    }

    @Transactional(readOnly = true)
    public List<OrgUnitDTO> findNotificationConfigAccessible() {
        return findAccessible(PermissionCatalog.NOTIFICATION_CONFIG_VIEW);
    }

    private List<OrgUnitDTO> findAccessible(String... permissions) {
        UUID tenantId = tenantContext.requireTenantId();
        List<OrgUnitDTO> orgUnits = organizationDirectoryCache.get(tenantId).orgUnits();
        if (scopeAuthorizationService.hasTenantPrincipal()) {
            var access = scopeAuthorizationService.accessFor(permissions);
            if (!access.tenantWide()) {
                orgUnits = orgUnits.stream()
                        .filter(org -> access.orgUnitIds().contains(org.id()))
                        .toList();
            }
        }
        return List.copyOf(orgUnits);
    }

    @Transactional
    public OrgUnitDTO create(OrgUnitRequestDTO request) {
        UUID tenantId = tenantContext.requireTenantId();
        if (request.getParentId() == null) {
            throw new IllegalArgumentException("租户根组织随市公司创建，不能在组织目录中重复创建。");
        }
        String code = request.getCode().trim().toUpperCase(java.util.Locale.ROOT);
        if (orgUnitRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new IllegalArgumentException("组织编码已存在，请更换后重试。");
        }
        OrgUnit parent = requireOrgUnit(tenantId, request.getParentId());
        requireOrganizationAccessIfAuthenticated(parent.getId(), PermissionCatalog.ORG_MANAGE_CREATE);
        OrgUnit orgUnit = new OrgUnit();
        orgUnit.setTenantId(tenantId);
        orgUnit.setParentId(parent.getId());
        orgUnit.setCode(code);
        orgUnit.setName(request.getName().trim());
        orgUnit.setUnitType(request.getUnitType());
        orgUnit.setStatus(request.getStatus());
        orgUnit.setPath(parent.getPath() + "pending/");
        OrgUnit saved = orgUnitRepository.saveAndFlush(orgUnit);
        saved.setPath(parent.getPath() + saved.getId() + "/");
        OrgUnitDTO result = toDTO(orgUnitRepository.save(saved));
        organizationDirectoryCache.invalidateAfterCommit(tenantId);
        userAuthorizationCache.clearAfterCommit();
        return result;
    }

    @Transactional
    public OrgUnitDTO update(UUID id, OrgUnitRequestDTO request) {
        UUID tenantId = tenantContext.requireTenantId();
        OrgUnit orgUnit = requireOrgUnit(tenantId, id);
        if (!orgUnit.getCode().equalsIgnoreCase(request.getCode().trim())) {
            throw new IllegalArgumentException("组织编码创建后不可修改。");
        }
        if (!java.util.Objects.equals(request.getParentId(), orgUnit.getParentId())) {
            throw new IllegalArgumentException("组织父节点调整需要使用独立迁移操作，不能直接修改。");
        }
        UUID rootOrgUnitId = requireRootOrgUnitId(orgUnit.getTenantId());
        if (orgUnit.getId().equals(rootOrgUnitId)
                && (request.getUnitType() != OrgUnitType.CITY || request.getStatus() != TenantStatus.ACTIVE)) {
            throw new IllegalArgumentException("租户根组织必须保持市公司类型和启用状态。");
        }
        if (!java.util.Objects.equals(request.getName().trim(), orgUnit.getName())
                || request.getUnitType() != orgUnit.getUnitType()) {
            requireOrganizationAccessIfAuthenticated(id, PermissionCatalog.ORG_MANAGE_UPDATE);
        }
        if (request.getStatus() != orgUnit.getStatus()) {
            requireOrganizationAccessIfAuthenticated(id,
                    request.getStatus() == TenantStatus.ACTIVE
                            ? PermissionCatalog.ORG_MANAGE_ENABLE
                            : PermissionCatalog.ORG_MANAGE_DISABLE);
        }
        orgUnit.setName(request.getName().trim());
        orgUnit.setUnitType(request.getUnitType());
        orgUnit.setStatus(request.getStatus());
        OrgUnitDTO result = toDTO(orgUnitRepository.save(orgUnit));
        organizationDirectoryCache.invalidateAfterCommit(tenantId);
        userAuthorizationCache.clearAfterCommit();
        return result;
    }

    @Transactional
    public OrgUnitDTO move(UUID id, OrgUnitMoveRequestDTO request) {
        UUID tenantId = tenantContext.requireTenantId();
        var lockedOrganizations = orgUnitRepository.findAllByTenantIdAndIdInForUpdate(
                        tenantId, List.of(id, request.targetParentId())).stream()
                .collect(java.util.stream.Collectors.toMap(OrgUnit::getId, organization -> organization));
        OrgUnit orgUnit = java.util.Optional.ofNullable(lockedOrganizations.get(id))
                .orElseThrow(() -> new EntityNotFoundException("组织不存在：" + id));
        UUID rootOrgUnitId = requireRootOrgUnitId(tenantId);
        if (orgUnit.getId().equals(rootOrgUnitId)) {
            throw new IllegalArgumentException("租户根组织不能移动。");
        }
        if (orgUnit.getParentId().equals(request.targetParentId())) {
            throw new IllegalArgumentException("目标上级组织与当前上级组织相同。");
        }
        OrgUnit targetParent = java.util.Optional.ofNullable(lockedOrganizations.get(request.targetParentId()))
                .orElseThrow(() -> new EntityNotFoundException("目标上级组织不存在：" + request.targetParentId()));
        String oldPath = orgUnit.getPath();
        if (targetParent.getPath().startsWith(oldPath)) {
            throw new IllegalArgumentException("不能把组织移动到自身或其下级组织中。");
        }

        List<OrgUnit> subtree = orgUnitRepository
                .findByTenantIdAndPathStartingWithOrderByPathAsc(tenantId, oldPath);
        requireMoveAccessIfAuthenticated(subtree, targetParent.getId());
        String newPath = targetParent.getPath() + orgUnit.getId() + "/";
        int longestPath = subtree.stream()
                .mapToInt(node -> newPath.length() + node.getPath().length() - oldPath.length())
                .max().orElse(newPath.length());
        if (longestPath > 500) {
            throw new IllegalArgumentException("移动后组织路径超过系统限制，请调整目标层级。");
        }

        UUID oldParentId = orgUnit.getParentId();
        for (OrgUnit node : subtree) {
            node.setPath(newPath + node.getPath().substring(oldPath.length()));
        }
        orgUnit.setParentId(targetParent.getId());
        orgUnitRepository.saveAll(subtree);
        orgUnitRepository.flush();

        OrganizationMoveEvent event = new OrganizationMoveEvent();
        event.setTenantId(tenantId);
        event.setOrgUnitId(orgUnit.getId());
        event.setOldParentId(oldParentId);
        event.setNewParentId(targetParent.getId());
        event.setOldPath(oldPath);
        event.setNewPath(newPath);
        event.setReason(request.reason().trim());
        event.setOperatorMembershipId(currentMembershipId());
        organizationMoveEventRepository.save(event);

        organizationDirectoryCache.invalidateAfterCommit(tenantId);
        userAuthorizationCache.clearAfterCommit();
        return toDTO(orgUnit);
    }

    private OrgUnit requireOrgUnit(UUID tenantId, UUID id) {
        return orgUnitRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found with id: " + id));
    }

    private void requireMoveAccessIfAuthenticated(List<OrgUnit> subtree, UUID targetParentId) {
        if (!scopeAuthorizationService.hasTenantPrincipal()) {
            return;
        }
        var access = scopeAuthorizationService.accessFor(PermissionCatalog.ORG_MANAGE_MOVE);
        boolean coversSubtree = subtree.stream()
                .map(OrgUnit::getId)
                .allMatch(access.orgUnitIds()::contains);
        if (!coversSubtree || !access.orgUnitIds().contains(targetParentId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "组织移动权限必须同时覆盖整个待移动子树和目标上级组织。");
        }
    }

    private UUID currentMembershipId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof TenantUserDetails principal
                ? principal.membershipId() : null;
    }

    private void requireOrganizationAccessIfAuthenticated(UUID orgUnitId, String... permissions) {
        if (scopeAuthorizationService.hasTenantPrincipal()
                && !scopeAuthorizationService.accessFor(permissions).orgUnitIds().contains(orgUnitId)) {
            throw new org.springframework.security.access.AccessDeniedException("当前账号不能管理所选组织。");
        }
    }

    private OrgUnitDTO toDTO(OrgUnit orgUnit) {
        UUID rootOrgUnitId = requireRootOrgUnitId(orgUnit.getTenantId());
        return new OrgUnitDTO(
                orgUnit.getId(),
                orgUnit.getTenantId(),
                orgUnit.getParentId(),
                orgUnit.getCode(),
                orgUnit.getName(),
                orgUnit.getUnitType(),
                orgUnit.getPath(),
                orgUnit.getStatus(),
                orgUnit.getId().equals(rootOrgUnitId)
        );
    }

    private UUID requireRootOrgUnitId(UUID tenantId) {
        UUID rootOrgUnitId = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + tenantId))
                .getRootOrgUnitId();
        if (rootOrgUnitId == null) {
            throw new IllegalStateException("租户未配置根组织：" + tenantId);
        }
        return rootOrgUnitId;
    }
}


