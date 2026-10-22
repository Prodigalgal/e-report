package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.AuthorizationOptionsDTO;
import com.pro.ereportbacken.model.dto.AuthorizationPlanRequestDTO;
import com.pro.ereportbacken.model.dto.AuthorizationPlanResponseDTO;
import com.pro.ereportbacken.model.dto.UserAuthorizationViewDTO;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.SystemAuthority;
import com.pro.ereportbacken.service.AuthorizationAdministrationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/users/{userId}/authorization")
public class AdminUserAuthorizationController {

    private final AuthorizationAdministrationService authorizationService;

    public AdminUserAuthorizationController(AuthorizationAdministrationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + SystemAuthority.SUPER_ADMINISTRATOR + "','"
            + PermissionCatalog.USER_AUTHORIZATION_VIEW + "')")
    public ResponseEntity<UserAuthorizationViewDTO> getAuthorization(@PathVariable UUID userId) {
        return ResponseEntity.ok(authorizationService.view(userId));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('" + SystemAuthority.SUPER_ADMINISTRATOR + "','"
            + PermissionCatalog.USER_AUTHORIZATION_VIEW + "')")
    public ResponseEntity<AuthorizationOptionsDTO> getOptions(@PathVariable UUID userId) {
        return ResponseEntity.ok(authorizationService.options(userId));
    }

    @PostMapping("/plans")
    @PreAuthorize("hasAnyAuthority('" + SystemAuthority.SUPER_ADMINISTRATOR + "','"
            + PermissionCatalog.USER_AUTHORIZATION_GRANT + "','"
            + PermissionCatalog.USER_AUTHORIZATION_ADJUST + "','"
            + PermissionCatalog.USER_AUTHORIZATION_REVOKE + "','"
            + PermissionCatalog.USER_AUTHORIZATION_REATTACH + "')")
    public ResponseEntity<AuthorizationPlanResponseDTO> createPlan(
            @PathVariable UUID userId,
            @Valid @RequestBody AuthorizationPlanRequestDTO request) {
        return ResponseEntity.ok(authorizationService.createPlan(userId, request));
    }

    @PostMapping("/plans/{planToken}/apply")
    @PreAuthorize("hasAnyAuthority('" + SystemAuthority.SUPER_ADMINISTRATOR + "','"
            + PermissionCatalog.USER_AUTHORIZATION_GRANT + "','"
            + PermissionCatalog.USER_AUTHORIZATION_ADJUST + "','"
            + PermissionCatalog.USER_AUTHORIZATION_REVOKE + "','"
            + PermissionCatalog.USER_AUTHORIZATION_REATTACH + "')")
    public ResponseEntity<UserAuthorizationViewDTO> applyPlan(
            @PathVariable UUID userId,
            @PathVariable String planToken,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.ok(authorizationService.applyPlan(userId, planToken, idempotencyKey));
    }
}
