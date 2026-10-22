package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.PasswordPolicyDTO;
import com.pro.ereportbacken.model.dto.PasswordPolicyUpdateRequestDTO;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.service.PasswordPolicy;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/password-policy")
public class AdminPasswordPolicyController {

    private final PasswordPolicy passwordPolicy;
    private final OrganizationScopeAuthorizationService scopeAuthorizationService;

    public AdminPasswordPolicyController(PasswordPolicy passwordPolicy,
                                         OrganizationScopeAuthorizationService scopeAuthorizationService) {
        this.passwordPolicy = passwordPolicy;
        this.scopeAuthorizationService = scopeAuthorizationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SYSTEM_CONFIG_VIEW + "')")
    public ResponseEntity<PasswordPolicyDTO> getPolicy(
            @RequestParam(required = false) UUID orgUnitId) {
        requireScope(PermissionCatalog.SYSTEM_CONFIG_VIEW, orgUnitId);
        return ResponseEntity.ok(passwordPolicy.dtoForScope(orgUnitId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SYSTEM_CONFIG_UPDATE + "')")
    public ResponseEntity<PasswordPolicyDTO> updatePolicy(
            @RequestParam(required = false) UUID orgUnitId,
            @Valid @RequestBody PasswordPolicyUpdateRequestDTO request) {
        requireScope(PermissionCatalog.SYSTEM_CONFIG_UPDATE, orgUnitId);
        return ResponseEntity.ok(passwordPolicy.update(orgUnitId, request));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SYSTEM_CONFIG_DELETE + "')")
    public ResponseEntity<Void> deletePolicyOverride(
            @RequestParam(required = false) UUID orgUnitId) {
        requireScope(PermissionCatalog.SYSTEM_CONFIG_DELETE, orgUnitId);
        passwordPolicy.deleteOverride(orgUnitId);
        return ResponseEntity.noContent().build();
    }

    private void requireScope(String permission, UUID orgUnitId) {
        if (orgUnitId == null) {
            scopeAuthorizationService.requireTenantWideAccess(permission);
        } else {
            scopeAuthorizationService.requireOrganizationAccess(permission, orgUnitId);
        }
    }
}
