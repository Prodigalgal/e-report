package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.RedisRuntimeStatusDTO;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.service.RedisRuntimeStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/observability/redis")
public class AdminRedisDiagnosticsController {

    private final RedisRuntimeStatusService statusService;

    public AdminRedisDiagnosticsController(RedisRuntimeStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.REDIS_DIAGNOSTICS_VIEW + "')")
    public ResponseEntity<RedisRuntimeStatusDTO> status() {
        return ResponseEntity.ok(statusService.status());
    }
}
