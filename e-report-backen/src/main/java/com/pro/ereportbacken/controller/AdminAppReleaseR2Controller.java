package com.pro.ereportbacken.controller;

import java.util.UUID;

import com.pro.ereportbacken.model.dto.AppReleaseR2OverviewDTO;
import com.pro.ereportbacken.model.pojo.AppEdition;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.SystemAuthority;
import com.pro.ereportbacken.service.AppReleaseR2ManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/app-updates/r2")
public class AdminAppReleaseR2Controller {

    private final AppReleaseR2ManagementService r2ManagementService;

    public AdminAppReleaseR2Controller(AppReleaseR2ManagementService r2ManagementService) {
        this.r2ManagementService = r2ManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + SystemAuthority.SUPER_ADMINISTRATOR + "','"
            + PermissionCatalog.APP_UPDATE_STORAGE_VIEW + "')")
    public ResponseEntity<AppReleaseR2OverviewDTO> overview(@RequestParam(defaultValue = "NATIVE") AppEdition edition) {
        return ResponseEntity.ok(r2ManagementService.overview(edition));
    }

    @PostMapping("/releases/{releaseId}")
    @PreAuthorize("hasAnyAuthority('" + SystemAuthority.SUPER_ADMINISTRATOR + "','"
            + PermissionCatalog.APP_UPDATE_STORAGE_SYNC + "')")
    public ResponseEntity<Void> synchronizeRelease(@PathVariable UUID releaseId) {
        r2ManagementService.synchronizeRelease(releaseId);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/objects")
    @PreAuthorize("hasAnyAuthority('" + SystemAuthority.SUPER_ADMINISTRATOR + "','"
            + PermissionCatalog.APP_UPDATE_STORAGE_DELETE + "')")
    public ResponseEntity<Void> deleteObject(@RequestParam String key) {
        r2ManagementService.deleteObject(key);
        return ResponseEntity.noContent().build();
    }
}

