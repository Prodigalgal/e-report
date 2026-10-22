package com.pro.ereportbacken.controller;

import java.util.UUID;

import com.pro.ereportbacken.model.dto.WorkOrderSlaPolicyDTO;
import com.pro.ereportbacken.model.dto.WorkOrderSlaPolicyUpdateRequestDTO;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.service.WorkOrderSlaPolicyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/sla-policies")
public class AdminWorkOrderSlaController {
    private final WorkOrderSlaPolicyService slaPolicyService;

    public AdminWorkOrderSlaController(WorkOrderSlaPolicyService slaPolicyService) {
        this.slaPolicyService = slaPolicyService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SLA_POLICY_VIEW + "')")
    public ResponseEntity<List<WorkOrderSlaPolicyDTO>> findAll() {
        return ResponseEntity.ok(slaPolicyService.findAll());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SLA_POLICY_UPDATE + "')")
    public ResponseEntity<WorkOrderSlaPolicyDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody WorkOrderSlaPolicyUpdateRequestDTO request) {
        return ResponseEntity.ok(slaPolicyService.update(id, request));
    }
}

