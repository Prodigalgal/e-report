package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.AuthorizationProfileCopyRequestDTO;
import com.pro.ereportbacken.model.dto.AuthorizationProfileDTO;
import com.pro.ereportbacken.model.dto.AuthorizationProfileRequestDTO;
import com.pro.ereportbacken.model.dto.AuthorizationProfileSummaryDTO;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.service.AuthorizationProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/authorization-profiles")
public class AdminAuthorizationProfileController {

    private final AuthorizationProfileService profileService;

    public AdminAuthorizationProfileController(AuthorizationProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.AUTHORIZATION_TEMPLATE_VIEW + "')")
    public ResponseEntity<List<AuthorizationProfileDTO>> list() {
        return ResponseEntity.ok(profileService.list());
    }

    @GetMapping("/summaries")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.AUTHORIZATION_TEMPLATE_VIEW + "')")
    public ResponseEntity<List<AuthorizationProfileSummaryDTO>> summaries() {
        return ResponseEntity.ok(profileService.listSummaries());
    }

    @GetMapping("/{profileId}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.AUTHORIZATION_TEMPLATE_VIEW + "')")
    public ResponseEntity<AuthorizationProfileDTO> get(@PathVariable UUID profileId) {
        return ResponseEntity.ok(profileService.get(profileId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.AUTHORIZATION_TEMPLATE_CREATE + "')")
    public ResponseEntity<AuthorizationProfileDTO> create(
            @Valid @RequestBody AuthorizationProfileRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(profileService.createDraft(request));
    }

    @PutMapping("/{profileId}/draft")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.AUTHORIZATION_TEMPLATE_UPDATE + "')")
    public ResponseEntity<AuthorizationProfileDTO> updateDraft(
            @PathVariable UUID profileId,
            @Valid @RequestBody AuthorizationProfileRequestDTO request) {
        return ResponseEntity.ok(profileService.updateDraft(profileId, request));
    }

    @PostMapping("/{profileId}/copy")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.AUTHORIZATION_TEMPLATE_CLONE + "')")
    public ResponseEntity<AuthorizationProfileDTO> copy(
            @PathVariable UUID profileId,
            @Valid @RequestBody AuthorizationProfileCopyRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(profileService.copy(profileId, request));
    }

    @PostMapping("/{profileId}/publish")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.AUTHORIZATION_TEMPLATE_ENABLE + "')")
    public ResponseEntity<AuthorizationProfileDTO> publish(@PathVariable UUID profileId) {
        return ResponseEntity.ok(profileService.publish(profileId));
    }

    @PostMapping("/{profileId}/retire")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.AUTHORIZATION_TEMPLATE_DISABLE + "')")
    public ResponseEntity<AuthorizationProfileDTO> retire(@PathVariable UUID profileId) {
        return ResponseEntity.ok(profileService.retire(profileId));
    }
}
