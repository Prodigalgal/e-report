package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.DepartmentCreateRequestDTO;
import com.pro.ereportbacken.model.dto.DepartmentDTO;
import com.pro.ereportbacken.model.dto.DepartmentMergeRequestDTO;
import com.pro.ereportbacken.model.dto.DepartmentMoveRequestDTO;
import com.pro.ereportbacken.model.dto.DepartmentMutationPreviewDTO;
import com.pro.ereportbacken.model.dto.DepartmentUpdateRequestDTO;
import com.pro.ereportbacken.model.pojo.Department;
import com.pro.ereportbacken.model.pojo.DepartmentChangeEvent;
import com.pro.ereportbacken.model.pojo.DepartmentChangeOperation;
import com.pro.ereportbacken.model.pojo.DepartmentStatus;
import com.pro.ereportbacken.repository.DepartmentChangeEventRepository;
import com.pro.ereportbacken.repository.DepartmentRepository;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.security.TenantUserDetails;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class DepartmentService {

    private static final int MAX_PATH_LENGTH = 700;

    private final DepartmentRepository departmentRepository;
    private final DepartmentChangeEventRepository eventRepository;
    private final DepartmentReferenceMigrationService referenceMigrationService;
    private final OrganizationHierarchyService hierarchyService;
    private final OrganizationScopeAuthorizationService scopeAuthorizationService;
    private final TenantContext tenantContext;

    public DepartmentService(
            DepartmentRepository departmentRepository,
            DepartmentChangeEventRepository eventRepository,
            DepartmentReferenceMigrationService referenceMigrationService,
            OrganizationHierarchyService hierarchyService,
            OrganizationScopeAuthorizationService scopeAuthorizationService,
            TenantContext tenantContext) {
        this.departmentRepository = departmentRepository;
        this.eventRepository = eventRepository;
        this.referenceMigrationService = referenceMigrationService;
        this.hierarchyService = hierarchyService;
        this.scopeAuthorizationService = scopeAuthorizationService;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public List<DepartmentDTO> findAccessible() {
        UUID tenantId = tenantContext.requireTenantId();
        Set<UUID> organizationIds = scopeAuthorizationService.hasTenantPrincipal()
                ? scopeAuthorizationService.accessFor(
                        PermissionCatalog.DEPARTMENT_VIEW,
                        PermissionCatalog.USER_MANAGE_VIEW,
                        PermissionCatalog.USER_MANAGE_CREATE,
                        PermissionCatalog.USER_MANAGE_DEPARTMENT_MOVE,
                        PermissionCatalog.USER_IMPORT_TEMPLATE_DOWNLOAD).orgUnitIds()
                : hierarchyService.allIds(tenantId);
        if (organizationIds.isEmpty()) {
            return List.of();
        }
        List<Department> departments = departmentRepository
                .findByTenantIdAndOrgUnitIdInOrderByPathAsc(tenantId, organizationIds);
        Map<UUID, String> organizationNames = hierarchyService.namesById(tenantId, organizationIds);
        return departments.stream().map(department -> toDTO(department, organizationNames)).toList();
    }

    @Transactional
    public DepartmentDTO create(DepartmentCreateRequestDTO request) {
        UUID tenantId = tenantContext.requireTenantId();
        scopeAuthorizationService.requireOrganizationAccessIfAuthenticated(
                PermissionCatalog.DEPARTMENT_CREATE, request.orgUnitId());
        String code = normalizeCode(request.code());
        if (departmentRepository.existsByTenantIdAndOrgUnitIdAndCodeIgnoreCase(
                tenantId, request.orgUnitId(), code)) {
            throw new IllegalArgumentException("同一组织内的部门编码不能重复。");
        }
        Department parent = request.parentId() == null
                ? null : requireDepartment(tenantId, request.parentId());
        requireSameOrganization(request.orgUnitId(), parent);
        requireActiveParent(parent);

        Department department = new Department();
        department.setTenantId(tenantId);
        department.setOrgUnitId(request.orgUnitId());
        department.setParentId(parent == null ? null : parent.getId());
        department.setCode(code);
        department.setName(request.name().trim());
        department.setStatus(DepartmentStatus.ACTIVE);
        department.setPath(parent == null ? "/pending/" : parent.getPath() + "pending/");
        department = departmentRepository.saveAndFlush(department);
        department.setPath((parent == null ? "/" : parent.getPath()) + department.getId() + "/");
        if (department.getPath().length() > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("部门层级过深，无法创建。");
        }
        return toDTO(departmentRepository.save(department));
    }

    @Transactional
    public DepartmentDTO update(UUID departmentId, DepartmentUpdateRequestDTO request) {
        Department department = requireAccessibleDepartment(departmentId, PermissionCatalog.DEPARTMENT_UPDATE);
        requireMutable(department);
        department.setName(request.name().trim());
        return toDTO(departmentRepository.save(department));
    }

    @Transactional
    public DepartmentDTO move(UUID departmentId, DepartmentMoveRequestDTO request) {
        UUID tenantId = tenantContext.requireTenantId();
        Department source = requireAccessibleDepartment(departmentId, PermissionCatalog.DEPARTMENT_MOVE);
        requireMutable(source);
        Department targetParent = request.targetParentId() == null
                ? null : requireAccessibleDepartment(request.targetParentId(), PermissionCatalog.DEPARTMENT_MOVE);
        requireSameOrganization(source.getOrgUnitId(), targetParent);
        requireActiveParent(targetParent);
        if (Objects.equals(source.getParentId(), request.targetParentId())) {
            throw new IllegalArgumentException("目标上级部门与当前上级部门相同。");
        }
        if (targetParent != null && targetParent.getPath().startsWith(source.getPath())) {
            throw new IllegalArgumentException("不能把部门移动到自身或其下级部门。");
        }
        UUID oldParentId = source.getParentId();
        rewriteSubtreePath(source, targetParent);
        source.setParentId(targetParent == null ? null : targetParent.getId());
        departmentRepository.flush();
        recordEvent(source, DepartmentChangeOperation.MOVE, oldParentId,
                source.getParentId(), null, request.reason(), new DepartmentReferenceMigrationService.Impact(0, 0));
        return toDTO(source);
    }

    @Transactional
    public DepartmentDTO enable(UUID departmentId, String reason) {
        Department department = requireAccessibleDepartment(departmentId, PermissionCatalog.DEPARTMENT_ENABLE);
        if (department.getStatus() == DepartmentStatus.MERGED) {
            throw new IllegalArgumentException("已合并部门不能重新启用。");
        }
        if (department.getStatus() == DepartmentStatus.ACTIVE) {
            return toDTO(department);
        }
        Department parent = department.getParentId() == null
                ? null : requireDepartment(department.getTenantId(), department.getParentId());
        requireActiveParent(parent);
        department.setStatus(DepartmentStatus.ACTIVE);
        recordEvent(department, DepartmentChangeOperation.ENABLE, department.getParentId(),
                department.getParentId(), null, reason, new DepartmentReferenceMigrationService.Impact(0, 0));
        return toDTO(department);
    }

    @Transactional
    public DepartmentDTO disable(UUID departmentId, String reason) {
        Department department = requireAccessibleDepartment(departmentId, PermissionCatalog.DEPARTMENT_DISABLE);
        requireMutable(department);
        if (department.getStatus() == DepartmentStatus.SUSPENDED) {
            return toDTO(department);
        }
        List<Department> subtree = departmentRepository
                .findByTenantIdAndOrgUnitIdAndPathStartingWithOrderByPathAsc(
                        department.getTenantId(), department.getOrgUnitId(), department.getPath());
        subtree.forEach(node -> node.setStatus(DepartmentStatus.SUSPENDED));
        departmentRepository.saveAll(subtree);
        recordEvent(department, DepartmentChangeOperation.DISABLE, department.getParentId(),
                department.getParentId(), null, reason, new DepartmentReferenceMigrationService.Impact(0, 0));
        return toDTO(department);
    }

    @Transactional(readOnly = true)
    public DepartmentMutationPreviewDTO previewMerge(UUID departmentId, DepartmentMergeRequestDTO request) {
        Department source = requireAccessibleDepartment(departmentId, PermissionCatalog.DEPARTMENT_MERGE);
        Department target = requireAccessibleDepartment(request.targetDepartmentId(), PermissionCatalog.DEPARTMENT_MERGE);
        validateMerge(source, target);
        return mutationPreview("MERGE", source, target, target);
    }

    @Transactional
    public DepartmentDTO merge(UUID departmentId, DepartmentMergeRequestDTO request) {
        Department source = requireAccessibleDepartment(departmentId, PermissionCatalog.DEPARTMENT_MERGE);
        Department target = requireAccessibleDepartment(request.targetDepartmentId(), PermissionCatalog.DEPARTMENT_MERGE);
        validateMerge(source, target);
        DepartmentReferenceMigrationService.Impact impact = referenceMigrationService.migrate(source, target);
        moveDirectChildren(source, target);
        source.setStatus(DepartmentStatus.MERGED);
        source.setMergedIntoDepartmentId(target.getId());
        source = departmentRepository.save(source);
        recordEvent(source, DepartmentChangeOperation.MERGE, source.getParentId(),
                source.getParentId(), target.getId(), request.reason(), impact);
        return toDTO(source);
    }

    @Transactional(readOnly = true)
    public DepartmentMutationPreviewDTO previewDelete(UUID departmentId) {
        Department source = requireAccessibleDepartment(departmentId, PermissionCatalog.DEPARTMENT_DELETE);
        Department fallback = fallbackDepartment(source);
        return mutationPreview("DELETE", source, null, fallback);
    }

    @Transactional
    public void delete(UUID departmentId, String reason) {
        Department source = requireAccessibleDepartment(departmentId, PermissionCatalog.DEPARTMENT_DELETE);
        if (source.getStatus() == DepartmentStatus.ACTIVE) {
            throw new IllegalArgumentException("请先停用部门，再执行删除。");
        }
        Department fallback = fallbackDepartment(source);
        DepartmentReferenceMigrationService.Impact impact = referenceMigrationService.migrate(source, fallback);
        moveDirectChildren(source, fallback);
        recordEvent(source, DepartmentChangeOperation.DELETE, source.getParentId(),
                fallback == null ? null : fallback.getId(), fallback == null ? null : fallback.getId(), reason, impact);
        departmentRepository.delete(source);
        departmentRepository.flush();
    }

    @Transactional(readOnly = true)
    public Department requireAssignable(UUID tenantId, UUID orgUnitId, UUID departmentId) {
        Department department = departmentRepository.findByTenantIdAndIdForUpdate(tenantId, departmentId)
                .orElseThrow(() -> new EntityNotFoundException("部门不存在：" + departmentId));
        requireSameOrganization(orgUnitId, department);
        if (department.getStatus() != DepartmentStatus.ACTIVE) {
            throw new IllegalArgumentException("只能选择已启用的部门。");
        }
        return department;
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> namesByIds(UUID tenantId, Collection<UUID> departmentIds) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return Map.of();
        }
        return departmentRepository.findByTenantIdAndIdIn(tenantId, departmentIds).stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Department::getId, Department::getName));
    }

    @Transactional(readOnly = true)
    public UUID requireOrganizationId(UUID tenantId, UUID departmentId) {
        return requireDepartment(tenantId, departmentId).getOrgUnitId();
    }

    private DepartmentMutationPreviewDTO mutationPreview(
            String operation, Department source, Department target, Department fallback) {
        DepartmentReferenceMigrationService.Impact impact = referenceMigrationService.preview(
                source.getTenantId(), source.getId());
        long children = departmentRepository.countByTenantIdAndParentId(source.getTenantId(), source.getId());
        List<String> changes = new ArrayList<>();
        if (impact.affectedUsers() > 0) {
            changes.add(impact.affectedUsers() + " 名用户将迁移部门归属并强制刷新授权会话");
        }
        if (impact.affectedWorkOrders() > 0) {
            changes.add(impact.affectedWorkOrders() + " 张工单的提单、责任及历史参与部门将同步迁移");
        }
        if (children > 0) {
            changes.add(children + " 个直属下级部门将重挂到目标层级");
        }
        if (changes.isEmpty()) {
            changes.add("当前没有用户、工单或直属下级部门受影响");
        }
        List<String> warnings = new ArrayList<>();
        if (fallback == null && "DELETE".equals(operation)) {
            warnings.add("该部门没有上级部门；用户和工单部门归属将回落到组织域");
        }
        if (source.getStatus() == DepartmentStatus.ACTIVE && "DELETE".equals(operation)) {
            warnings.add("部门仍处于启用状态，执行删除前必须先停用");
        }
        return new DepartmentMutationPreviewDTO(
                operation, source.getId(), source.getName(),
                target == null ? null : target.getId(), target == null ? null : target.getName(),
                fallback == null ? null : fallback.getId(), fallback == null ? null : fallback.getName(),
                children, impact.affectedUsers(), impact.affectedWorkOrders(), changes, warnings);
    }

    private void validateMerge(Department source, Department target) {
        requireMutable(source);
        if (source.getId().equals(target.getId())) {
            throw new IllegalArgumentException("源部门和目标部门不能相同。");
        }
        requireSameOrganization(source.getOrgUnitId(), target);
        if (target.getStatus() != DepartmentStatus.ACTIVE) {
            throw new IllegalArgumentException("只能合并到已启用部门。");
        }
        if (target.getPath().startsWith(source.getPath())) {
            throw new IllegalArgumentException("不能把部门合并到自身下级部门。");
        }
    }

    private void moveDirectChildren(Department source, Department targetParent) {
        List<Department> directChildren = departmentRepository
                .findByTenantIdAndOrgUnitIdAndPathStartingWithOrderByPathAsc(
                        source.getTenantId(), source.getOrgUnitId(), source.getPath()).stream()
                .filter(candidate -> Objects.equals(candidate.getParentId(), source.getId()))
                .toList();
        for (Department child : directChildren) {
            rewriteSubtreePath(child, targetParent);
            child.setParentId(targetParent == null ? null : targetParent.getId());
        }
        departmentRepository.flush();
    }

    private void rewriteSubtreePath(Department source, Department targetParent) {
        String oldPath = source.getPath();
        String newPath = (targetParent == null ? "/" : targetParent.getPath()) + source.getId() + "/";
        List<Department> subtree = departmentRepository
                .findByTenantIdAndOrgUnitIdAndPathStartingWithOrderByPathAsc(
                        source.getTenantId(), source.getOrgUnitId(), oldPath);
        int longestPath = subtree.stream()
                .mapToInt(node -> newPath.length() + node.getPath().length() - oldPath.length())
                .max().orElse(newPath.length());
        if (longestPath > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("调整后部门层级路径超过系统限制。");
        }
        subtree.forEach(node -> node.setPath(newPath + node.getPath().substring(oldPath.length())));
        departmentRepository.saveAll(subtree);
    }

    private Department fallbackDepartment(Department source) {
        if (source.getParentId() == null) {
            return null;
        }
        Department parent = requireDepartment(source.getTenantId(), source.getParentId());
        return parent.getStatus() == DepartmentStatus.MERGED ? null : parent;
    }

    private Department requireAccessibleDepartment(UUID departmentId, String permission) {
        Department department = requireDepartment(tenantContext.requireTenantId(), departmentId);
        scopeAuthorizationService.requireOrganizationAccessIfAuthenticated(permission, department.getOrgUnitId());
        return department;
    }

    private Department requireDepartment(UUID tenantId, UUID departmentId) {
        return departmentRepository.findByTenantIdAndId(tenantId, departmentId)
                .orElseThrow(() -> new EntityNotFoundException("部门不存在：" + departmentId));
    }

    private void requireMutable(Department department) {
        if (department.getStatus() == DepartmentStatus.MERGED) {
            throw new IllegalArgumentException("已合并部门只保留审计记录，不能继续修改。");
        }
    }

    private void requireSameOrganization(UUID orgUnitId, Department department) {
        if (department != null && !Objects.equals(orgUnitId, department.getOrgUnitId())) {
            throw new IllegalArgumentException("部门树不能跨组织挂接。");
        }
    }

    private void requireActiveParent(Department parent) {
        if (parent != null && parent.getStatus() != DepartmentStatus.ACTIVE) {
            throw new IllegalArgumentException("上级部门必须处于启用状态。");
        }
    }

    private void recordEvent(Department department,
                             DepartmentChangeOperation operation,
                             UUID oldParentId,
                             UUID newParentId,
                             UUID targetDepartmentId,
                             String reason,
                             DepartmentReferenceMigrationService.Impact impact) {
        DepartmentChangeEvent event = new DepartmentChangeEvent();
        event.setTenantId(department.getTenantId());
        event.setOrgUnitId(department.getOrgUnitId());
        event.setDepartmentId(department.getId());
        event.setOperation(operation);
        event.setOldParentId(oldParentId);
        event.setNewParentId(newParentId);
        event.setTargetDepartmentId(targetDepartmentId);
        event.setOperatorMembershipId(currentMembershipId());
        event.setReason(reason.trim());
        event.setAffectedUsers(impact.affectedUsers());
        event.setAffectedWorkOrders(impact.affectedWorkOrders());
        eventRepository.save(event);
    }

    private UUID currentMembershipId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof TenantUserDetails principal
                ? principal.membershipId() : null;
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private DepartmentDTO toDTO(Department department) {
        Map<UUID, String> names = hierarchyService.namesById(
                department.getTenantId(), Set.of(department.getOrgUnitId()));
        return toDTO(department, names);
    }

    private DepartmentDTO toDTO(Department department, Map<UUID, String> organizationNames) {
        return new DepartmentDTO(
                department.getId(), department.getTenantId(), department.getOrgUnitId(),
                organizationNames.get(department.getOrgUnitId()), department.getParentId(),
                department.getCode(), department.getName(), department.getPath(), department.getStatus(),
                department.getMergedIntoDepartmentId(), department.getCreatedAt(), department.getUpdatedAt());
    }
}
