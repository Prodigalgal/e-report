package com.pro.ereportbacken.controller;

import java.util.UUID;

import com.pro.ereportbacken.model.dto.PermissionDTO;
import com.pro.ereportbacken.model.dto.PageResponseDTO;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.service.PermissionService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/permissions")
@PreAuthorize("hasAuthority('" + PermissionCatalog.AUTHORIZATION_TEMPLATE_VIEW + "')")
public class AdminPermissionController {

    private final PermissionService permissionService;

    public AdminPermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    public ResponseEntity<PageResponseDTO<PermissionDTO>> getAllPermissions(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(PageResponseDTO.from(permissionService.findAllPermissions(pageable)));
    }

    @GetMapping("/{permissionId}")
    public ResponseEntity<PermissionDTO> getPermissionById(@PathVariable UUID permissionId) {
        return ResponseEntity.ok(permissionService.findPermissionById(permissionId));
    }
}

