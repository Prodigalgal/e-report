package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.EmailDeliveryDTO;
import com.pro.ereportbacken.model.dto.PageResponseDTO;
import com.pro.ereportbacken.notification.delivery.EmailDeliveryCategory;
import com.pro.ereportbacken.notification.delivery.EmailDeliveryQueryService;
import com.pro.ereportbacken.notification.delivery.EmailDeliveryStatus;
import com.pro.ereportbacken.security.PermissionCatalog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/notifications/emails")
@PreAuthorize("hasAuthority('" + PermissionCatalog.EMAIL_DELIVERY_VIEW + "')")
public class AdminEmailDeliveryController {
    private final EmailDeliveryQueryService service;

    public AdminEmailDeliveryController(EmailDeliveryQueryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponseDTO<EmailDeliveryDTO> search(
            @RequestParam(required = false) EmailDeliveryCategory category,
            @RequestParam(required = false) EmailDeliveryStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID orgUnitId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponseDTO.from(service.search(category, status, keyword, orgUnitId, from, to, pageable));
    }

    @GetMapping("/{id}")
    public EmailDeliveryDTO detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @GetMapping("/scopes")
    public List<EmailDeliveryQueryService.OrganizationOption> scopes() {
        return service.scopes();
    }
}
