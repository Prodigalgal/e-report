package com.pro.ereportbacken.controller;

import java.util.UUID;

import com.pro.ereportbacken.model.dto.AppReleaseDTO;
import com.pro.ereportbacken.model.dto.AppReleasePolicyUpdateRequestDTO;
import com.pro.ereportbacken.model.dto.AppReleasePublishRequestDTO;
import com.pro.ereportbacken.model.dto.PageResponseDTO;
import com.pro.ereportbacken.model.pojo.AppPlatform;
import com.pro.ereportbacken.model.pojo.AppEdition;
import com.pro.ereportbacken.model.pojo.AppRelease;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.SystemAuthority;
import com.pro.ereportbacken.service.AppReleaseR2ManagementService;
import com.pro.ereportbacken.service.AppReleaseUrlBuilder;
import com.pro.ereportbacken.service.AppUpdateService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin/app-updates")
public class AdminAppUpdateController {

    private final AppUpdateService appUpdateService;
    private final AppReleaseUrlBuilder appReleaseUrlBuilder;
    private final AppReleaseR2ManagementService r2ManagementService;

    public AdminAppUpdateController(AppUpdateService appUpdateService,
                                    AppReleaseUrlBuilder appReleaseUrlBuilder,
                                    AppReleaseR2ManagementService r2ManagementService) {
        this.appUpdateService = appUpdateService;
        this.appReleaseUrlBuilder = appReleaseUrlBuilder;
        this.r2ManagementService = r2ManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + SystemAuthority.SUPER_ADMINISTRATOR + "','"
            + PermissionCatalog.APP_UPDATE_VIEW + "')")
    public ResponseEntity<PageResponseDTO<AppReleaseDTO>> list(
            @RequestParam(defaultValue = "NATIVE") AppEdition edition,
            @RequestParam(required = false) AppPlatform platform,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String searchTerm,
            @PageableDefault(size = 20, sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponseDTO.from(
                appUpdateService.list(edition, platform, channel, searchTerm, pageable).map(this::toDTO)));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('" + SystemAuthority.SUPER_ADMINISTRATOR + "','"
            + PermissionCatalog.APP_UPDATE_PUBLISH + "')")
    public ResponseEntity<AppReleaseDTO> publishFromCompletedUpload(@RequestBody AppReleasePublishRequestDTO request) {
        r2ManagementService.requireEnabledForPublication();
        AppRelease release = appUpdateService.publishFromCompletedUpload(request);
        r2ManagementService.synchronizeRelease(release.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(release));
    }

    @PatchMapping("/{id}/policy")
    @PreAuthorize("hasAnyAuthority('" + SystemAuthority.SUPER_ADMINISTRATOR + "','"
            + PermissionCatalog.APP_UPDATE_POLICY_UPDATE + "')")
    public ResponseEntity<AppReleaseDTO> updatePolicy(
            @PathVariable UUID id,
            @Valid @RequestBody AppReleasePolicyUpdateRequestDTO request) {
        return ResponseEntity.ok(toDTO(appUpdateService.updatePolicy(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + SystemAuthority.SUPER_ADMINISTRATOR + "','"
            + PermissionCatalog.APP_UPDATE_DELETE + "')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        appUpdateService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private AppReleaseDTO toDTO(AppRelease release) {
        return new AppReleaseDTO(
                release.getId(),
                release.getPlatform(),
                release.getChannel(),
                release.getVersionCode(),
                release.getVersionName(),
                release.getRuntimeVersion(),
                release.getMinSupportedVersionCode(),
                release.getForceUpdate(),
                release.getReleaseNotes(),
                release.getOriginalFileName(),
                release.getStoredFileName(),
                release.getFileSize(),
                release.getSha256(),
                release.getContentType(),
                release.getPublished(),
                release.getPublishedAt(),
                release.getCreatedAt(),
                release.getUpdatedAt(),
                apkUrl(release),
                release.getEdition()
        );
    }

    private String apkUrl(AppRelease release) {
        return appReleaseUrlBuilder.releaseApkUrl(release);
    }
}

