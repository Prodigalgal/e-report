package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.ApiTokenCreateRequestDTO;
import com.pro.ereportbacken.model.dto.ApiTokenCreatedDTO;
import com.pro.ereportbacken.model.dto.ApiTokenDTO;
import com.pro.ereportbacken.model.dto.ApiTokenPermissionOptionDTO;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.service.ApiTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api-tokens")
public class ApiTokenController {

    private final ApiTokenService apiTokenService;

    public ApiTokenController(ApiTokenService apiTokenService) {
        this.apiTokenService = apiTokenService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.API_TOKEN_VIEW + "')")
    public ResponseEntity<List<ApiTokenDTO>> list() {
        return ResponseEntity.ok(apiTokenService.listOwnTokens());
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.API_TOKEN_CREATE + "')")
    public ResponseEntity<List<ApiTokenPermissionOptionDTO>> options() {
        return ResponseEntity.ok(apiTokenService.ownPermissionOptions());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.API_TOKEN_CREATE + "')")
    public ResponseEntity<ApiTokenCreatedDTO> create(@Valid @RequestBody ApiTokenCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(apiTokenService.create(request));
    }

    @DeleteMapping("/{tokenId}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.API_TOKEN_REVOKE + "')")
    public ResponseEntity<Void> revoke(@PathVariable UUID tokenId) {
        apiTokenService.revokeOwnToken(tokenId);
        return ResponseEntity.noContent().build();
    }
}
