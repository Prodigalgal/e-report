package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.DepartmentCreateRequestDTO;
import com.pro.ereportbacken.model.dto.DepartmentDTO;
import com.pro.ereportbacken.model.dto.DepartmentMergeRequestDTO;
import com.pro.ereportbacken.model.dto.DepartmentMoveRequestDTO;
import com.pro.ereportbacken.model.dto.DepartmentMutationPreviewDTO;
import com.pro.ereportbacken.model.dto.DepartmentReasonRequestDTO;
import com.pro.ereportbacken.model.dto.DepartmentUpdateRequestDTO;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.service.DepartmentService;
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
@RequestMapping("/admin/departments")
public class AdminDepartmentController {

    private final DepartmentService departmentService;

    public AdminDepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.DEPARTMENT_VIEW + "','"
            + PermissionCatalog.USER_MANAGE_VIEW + "')")
    public ResponseEntity<List<DepartmentDTO>> findAll() {
        return ResponseEntity.ok(departmentService.findAccessible());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.DEPARTMENT_CREATE + "')")
    public ResponseEntity<DepartmentDTO> create(@Valid @RequestBody DepartmentCreateRequestDTO request) {
        return new ResponseEntity<>(departmentService.create(request), HttpStatus.CREATED);
    }

    @PutMapping("/{departmentId}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.DEPARTMENT_UPDATE + "')")
    public ResponseEntity<DepartmentDTO> update(
            @PathVariable UUID departmentId,
            @Valid @RequestBody DepartmentUpdateRequestDTO request) {
        return ResponseEntity.ok(departmentService.update(departmentId, request));
    }

    @PostMapping("/{departmentId}/move")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.DEPARTMENT_MOVE + "')")
    public ResponseEntity<DepartmentDTO> move(
            @PathVariable UUID departmentId,
            @Valid @RequestBody DepartmentMoveRequestDTO request) {
        return ResponseEntity.ok(departmentService.move(departmentId, request));
    }

    @PostMapping("/{departmentId}/enable")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.DEPARTMENT_ENABLE + "')")
    public ResponseEntity<DepartmentDTO> enable(
            @PathVariable UUID departmentId,
            @Valid @RequestBody DepartmentReasonRequestDTO request) {
        return ResponseEntity.ok(departmentService.enable(departmentId, request.reason()));
    }

    @PostMapping("/{departmentId}/disable")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.DEPARTMENT_DISABLE + "')")
    public ResponseEntity<DepartmentDTO> disable(
            @PathVariable UUID departmentId,
            @Valid @RequestBody DepartmentReasonRequestDTO request) {
        return ResponseEntity.ok(departmentService.disable(departmentId, request.reason()));
    }

    @PostMapping("/{departmentId}/merge/preview")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.DEPARTMENT_MERGE + "')")
    public ResponseEntity<DepartmentMutationPreviewDTO> previewMerge(
            @PathVariable UUID departmentId,
            @Valid @RequestBody DepartmentMergeRequestDTO request) {
        return ResponseEntity.ok(departmentService.previewMerge(departmentId, request));
    }

    @PostMapping("/{departmentId}/merge")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.DEPARTMENT_MERGE + "')")
    public ResponseEntity<DepartmentDTO> merge(
            @PathVariable UUID departmentId,
            @Valid @RequestBody DepartmentMergeRequestDTO request) {
        return ResponseEntity.ok(departmentService.merge(departmentId, request));
    }

    @GetMapping("/{departmentId}/delete-preview")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.DEPARTMENT_DELETE + "')")
    public ResponseEntity<DepartmentMutationPreviewDTO> previewDelete(@PathVariable UUID departmentId) {
        return ResponseEntity.ok(departmentService.previewDelete(departmentId));
    }

    @PostMapping("/{departmentId}/delete")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.DEPARTMENT_DELETE + "')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID departmentId,
            @Valid @RequestBody DepartmentReasonRequestDTO request) {
        departmentService.delete(departmentId, request.reason());
        return ResponseEntity.noContent().build();
    }
}
