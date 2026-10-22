package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.PermissionCatalogDriftDTO;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.service.PermissionCatalogDriftService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/observability/permission-catalog")
public class AdminPermissionCatalogDiagnosticsController {

    private final PermissionCatalogDriftService driftService;

    public AdminPermissionCatalogDiagnosticsController(PermissionCatalogDriftService driftService) {
        this.driftService = driftService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.OBSERVABILITY_VIEW + "')")
    public ResponseEntity<PermissionCatalogDriftDTO> status() {
        return ResponseEntity.ok(driftService.scan());
    }
}
