package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.EmailNotificationPreferencesDTO;
import com.pro.ereportbacken.notification.EmailNotificationPreferencesService;
import com.pro.ereportbacken.security.TenantUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth/me/email-notifications")
public class CurrentEmailNotificationPreferencesController {
    private final EmailNotificationPreferencesService preferencesService;

    public CurrentEmailNotificationPreferencesController(EmailNotificationPreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    @GetMapping
    public EmailNotificationPreferencesDTO get(Authentication authentication) {
        TenantUserDetails principal = requirePrincipal(authentication);
        return preferencesService.get(principal.tenantId(), principal.membershipId());
    }

    @PutMapping
    public EmailNotificationPreferencesDTO update(Authentication authentication,
                                                   @Valid @RequestBody EmailNotificationPreferencesDTO preferences) {
        TenantUserDetails principal = requirePrincipal(authentication);
        return preferencesService.update(principal.tenantId(), principal.membershipId(), preferences);
    }

    private TenantUserDetails requirePrincipal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof TenantUserDetails principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录。");
        }
        return principal;
    }
}
