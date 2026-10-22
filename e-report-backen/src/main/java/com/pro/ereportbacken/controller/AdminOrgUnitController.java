package com.pro.ereportbacken.controller;

import java.util.UUID;

import com.pro.ereportbacken.model.dto.OrgUnitDTO;
import com.pro.ereportbacken.model.dto.OrgUnitRequestDTO;
import com.pro.ereportbacken.model.dto.OrgUnitMoveRequestDTO;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.service.OrgUnitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/org-units")
public class AdminOrgUnitController {

    private final OrgUnitService orgUnitService;

    public AdminOrgUnitController(OrgUnitService orgUnitService) {
        this.orgUnitService = orgUnitService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.ORG_MANAGE_VIEW + "', '"
            + PermissionCatalog.USER_MANAGE_VIEW + "', '" + PermissionCatalog.SYSTEM_CONFIG_VIEW + "', '"
            + PermissionCatalog.NOTIFICATION_CONFIG_VIEW + "', '" + PermissionCatalog.DEPARTMENT_VIEW + "')")
    public ResponseEntity<List<OrgUnitDTO>> findAll() {
        return ResponseEntity.ok(orgUnitService.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.ORG_MANAGE_CREATE + "')")
    public ResponseEntity<OrgUnitDTO> create(@Valid @RequestBody OrgUnitRequestDTO request) {
        return new ResponseEntity<>(orgUnitService.create(request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.ORG_MANAGE_UPDATE + "','"
            + PermissionCatalog.ORG_MANAGE_ENABLE + "','"
            + PermissionCatalog.ORG_MANAGE_DISABLE + "')")
    public ResponseEntity<OrgUnitDTO> update(@PathVariable UUID id, @Valid @RequestBody OrgUnitRequestDTO request) {
        return ResponseEntity.ok(orgUnitService.update(id, request));
    }

    @PostMapping("/{id}/move")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.ORG_MANAGE_MOVE + "')")
    public ResponseEntity<OrgUnitDTO> move(
            @PathVariable UUID id,
            @Valid @RequestBody OrgUnitMoveRequestDTO request) {
        return ResponseEntity.ok(orgUnitService.move(id, request));
    }

}

