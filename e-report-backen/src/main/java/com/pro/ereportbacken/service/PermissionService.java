package com.pro.ereportbacken.service;

import java.util.UUID;

import com.pro.ereportbacken.model.dto.PermissionDTO;
import com.pro.ereportbacken.model.pojo.Permission;
import com.pro.ereportbacken.repository.PermissionRepository;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.PermissionCatalog;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@Log4j2
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final OrganizationScopeAuthorizationService scopeAuthorizationService;

    public PermissionService(PermissionRepository permissionRepository,
                             OrganizationScopeAuthorizationService scopeAuthorizationService) {
        this.permissionRepository = permissionRepository;
        this.scopeAuthorizationService = scopeAuthorizationService;
    }

    @Transactional(readOnly = true)
    public Page<PermissionDTO> findAllPermissions(Pageable pageable) {
        log.debug("Fetching fixed permission catalog page: {}", pageable);
        List<PermissionCatalog.Entry> visible = PermissionCatalog.entries().stream()
                .filter(definition -> definition.delegable() && !definition.systemScoped())
                .filter(definition -> scopeAuthorizationService.hasAuthority(definition.name()))
                .sorted(Comparator.comparing(PermissionCatalog.Entry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int from = Math.min((int) pageable.getOffset(), visible.size());
        int to = Math.min(from + pageable.getPageSize(), visible.size());
        List<PermissionCatalog.Entry> pageDefinitions = visible.subList(from, to);
        Map<String, Permission> permissionsByName = permissionRepository.findByNameIn(
                        pageDefinitions.stream().map(PermissionCatalog.Entry::name).toList())
                .stream().collect(Collectors.toMap(Permission::getName, Function.identity()));
        List<PermissionDTO> content = pageDefinitions.stream()
                .map(definition -> toDto(requirePersistedPermission(permissionsByName, definition.name()), definition))
                .toList();
        return new PageImpl<>(content, pageable, visible.size());
    }

    @Transactional(readOnly = true)
    public PermissionDTO findPermissionById(UUID permissionId) {
        log.debug("Fetching permission by id: {}", permissionId);
        return permissionRepository.findById(permissionId)
                .flatMap(permission -> PermissionCatalog.find(permission.getName())
                        .filter(definition -> definition.delegable() && !definition.systemScoped())
                        .map(definition -> new DirectoryPermission(permission, definition)))
                .filter(permission -> scopeAuthorizationService.hasAuthority(permission.getName()))
                .map(permission -> toDto(permission.permission(), permission.definition()))
                .orElseThrow(() -> new EntityNotFoundException("Permission not found with id: " + permissionId));
    }

    @Transactional(readOnly = true)
    public PermissionDTO findPermissionByName(String name) {
        log.debug("Fetching permission by name: {}", name);
        PermissionCatalog.Entry definition = PermissionCatalog.find(name)
                .filter(entry -> entry.delegable() && !entry.systemScoped())
                .orElseThrow(() -> new EntityNotFoundException("Permission not found with name: " + name));
        return permissionRepository.findByName(name)
                .filter(permission -> scopeAuthorizationService.hasAuthority(permission.getName()))
                .map(permission -> toDto(permission, definition))
                .orElseThrow(() -> new EntityNotFoundException("Permission not found with name: " + name));
    }

    private Permission requirePersistedPermission(Map<String, Permission> permissionsByName, String name) {
        Permission permission = permissionsByName.get(name);
        if (permission == null) {
            throw new IllegalStateException("权限目录数据库缺少当前代码声明的权限：" + name);
        }
        return permission;
    }

    private PermissionDTO toDto(Permission permission, PermissionCatalog.Entry definition) {
        return new PermissionDTO(
                permission.getId(), definition.name(), definition.displayName(), definition.description(),
                definition.accessMode(), definition.riskLevel(), definition.apiTokenAssignable());
    }

    private record DirectoryPermission(Permission permission, PermissionCatalog.Entry definition) {
        private String getName() {
            return permission.getName();
        }
    }

}
