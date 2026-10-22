package com.pro.ereportbacken.controller;

import java.util.UUID;

import com.pro.ereportbacken.model.dto.TenantCreateRequestDTO;
import com.pro.ereportbacken.model.dto.TenantDTO;
import com.pro.ereportbacken.model.dto.TenantGroupDTO;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.service.IdempotencyService;
import com.pro.ereportbacken.service.TenantProvisioningService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/tenants")
public class AdminTenantController {

    private final TenantProvisioningService tenantProvisioningService;
    private final IdempotencyService idempotencyService;
    private final OrganizationScopeAuthorizationService authorizationService;

    public AdminTenantController(TenantProvisioningService tenantProvisioningService,
                                 IdempotencyService idempotencyService,
                                 OrganizationScopeAuthorizationService authorizationService) {
        this.tenantProvisioningService = tenantProvisioningService;
        this.idempotencyService = idempotencyService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.TENANT_MANAGE_VIEW + "')")
    public ResponseEntity<List<TenantDTO>> findAll() {
        return ResponseEntity.ok(tenantProvisioningService.findAll());
    }

    @GetMapping("/groups")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.TENANT_MANAGE_VIEW + "')")
    public ResponseEntity<List<TenantGroupDTO>> findGroups() {
        return ResponseEntity.ok(tenantProvisioningService.findGroups());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.TENANT_MANAGE_CREATE + "')")
    public ResponseEntity<TenantDTO> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TenantCreateRequestDTO request) {
        TenantDTO tenant = idempotencyService.execute(
                idempotencyKey,
                "POST:/admin/tenants",
                request,
                TenantDTO.class,
                () -> tenantProvisioningService.create(request)
        );
        return new ResponseEntity<>(tenant, HttpStatus.CREATED);
    }

    @PatchMapping("/{tenantId}/status")
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.TENANT_MANAGE_ACTIVATE + "','"
            + PermissionCatalog.TENANT_MANAGE_DEACTIVATE + "')")
    public ResponseEntity<TenantDTO> updateStatus(
            @PathVariable UUID tenantId,
            @RequestParam TenantStatus status) {
        authorizationService.requireAuthorityIfAuthenticated(status == TenantStatus.ACTIVE
                ? PermissionCatalog.TENANT_MANAGE_ACTIVATE
                : PermissionCatalog.TENANT_MANAGE_DEACTIVATE);
        return ResponseEntity.ok(tenantProvisioningService.updateStatus(tenantId, status));
    }
}

