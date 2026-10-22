package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.ObservationEventDetailDTO;
import com.pro.ereportbacken.model.dto.ObservationEventSummaryDTO;
import com.pro.ereportbacken.model.dto.ObservationUserOptionDTO;
import com.pro.ereportbacken.model.dto.PageResponseDTO;
import com.pro.ereportbacken.model.pojo.ObservationEventType;
import com.pro.ereportbacken.model.pojo.ObservationPlatform;
import com.pro.ereportbacken.model.pojo.ObservationSeverity;
import com.pro.ereportbacken.model.pojo.ObservationViewMode;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.service.ObservationQueryService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/admin/observability/events")
public class AdminObservabilityController {

    private final ObservationQueryService queryService;

    public AdminObservabilityController(ObservationQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.OBSERVABILITY_VIEW + "')")
    public ResponseEntity<PageResponseDTO<ObservationEventSummaryDTO>> search(
            @RequestParam(required = false) ObservationPlatform platform,
            @RequestParam(required = false) ObservationEventType eventType,
            @RequestParam(required = false) ObservationSeverity severity,
            @RequestParam(defaultValue = "DIAGNOSTIC") ObservationViewMode view,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID orgUnitId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PageResponseDTO.from(queryService.search(
                platform, eventType, severity, view, category, keyword, orgUnitId, userId, from, to, pageable
        )));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.OBSERVABILITY_VIEW + "')")
    public ResponseEntity<ObservationEventDetailDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(queryService.findById(id));
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.OBSERVABILITY_VIEW + "')")
    public ResponseEntity<List<ObservationUserOptionDTO>> searchUsers(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(queryService.searchUsers(keyword, limit));
    }
}
