package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.EmailNotificationPreferencesDTO;
import com.pro.ereportbacken.notification.AdminEmailNotificationPreferencesService;
import com.pro.ereportbacken.security.PermissionCatalog;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/users/{userId}/email-notifications")
public class AdminEmailNotificationPreferencesController {
    private final AdminEmailNotificationPreferencesService preferences;

    public AdminEmailNotificationPreferencesController(AdminEmailNotificationPreferencesService preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_MANAGE_VIEW + "')")
    public EmailNotificationPreferencesDTO get(@PathVariable UUID userId) {
        return preferences.get(userId);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_MANAGE_VIEW + "') and hasAuthority('"
            + PermissionCatalog.USER_MANAGE_CONTACT_UPDATE + "')")
    public EmailNotificationPreferencesDTO update(@PathVariable UUID userId,
                                                   @Valid @RequestBody EmailNotificationPreferencesDTO requested) {
        return preferences.update(userId, requested);
    }
}
