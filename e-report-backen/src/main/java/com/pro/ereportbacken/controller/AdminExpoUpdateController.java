package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.ExpoUpdateDTO;
import com.pro.ereportbacken.model.dto.PageResponseDTO;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.SystemAuthority;
import com.pro.ereportbacken.service.ExpoUpdateService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RestController
@RequestMapping("/admin/app-updates/ota")
public class AdminExpoUpdateController {
    private final ExpoUpdateService service;
    public AdminExpoUpdateController(ExpoUpdateService service) { this.service = service; }
    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + SystemAuthority.SUPER_ADMINISTRATOR + "','" + PermissionCatalog.APP_UPDATE_VIEW + "')")
    public PageResponseDTO<ExpoUpdateDTO> list(@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponseDTO.from(service.list(pageable));
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + SystemAuthority.SUPER_ADMINISTRATOR + "','" + PermissionCatalog.APP_UPDATE_VIEW + "')")
    public ExpoUpdateDTO status(@PathVariable UUID id) { return service.find(id); }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('" + SystemAuthority.SUPER_ADMINISTRATOR + "','" + PermissionCatalog.APP_UPDATE_PUBLISH + "')")
    public ResponseEntity<ExpoUpdateDTO> publish(@RequestPart("archive") MultipartFile archive) {
        return ResponseEntity.accepted().body(service.publish(archive));
    }
}
