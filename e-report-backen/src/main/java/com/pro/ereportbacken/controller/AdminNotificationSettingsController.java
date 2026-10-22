package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.NotificationCenterSettingsDTO;
import com.pro.ereportbacken.model.dto.DailyOutstandingReportScheduleUpdateRequestDTO;
import com.pro.ereportbacken.model.dto.DailyOutstandingReportSettingsDTO;
import com.pro.ereportbacken.model.dto.ConfigScopeDTO;
import com.pro.ereportbacken.model.dto.MailSenderSettingsDTO;
import com.pro.ereportbacken.model.dto.MailSenderSettingsUpdateRequestDTO;
import com.pro.ereportbacken.model.dto.MailSenderTestRequestDTO;
import com.pro.ereportbacken.model.dto.MailSenderTestResultDTO;
import com.pro.ereportbacken.model.dto.NotificationSwitchDTO;
import com.pro.ereportbacken.model.dto.NotificationSwitchUpdateRequestDTO;
import com.pro.ereportbacken.notification.MailSenderConfigurationService;
import com.pro.ereportbacken.notification.MailSenderTestService;
import com.pro.ereportbacken.notification.NotificationSettingsService;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.service.OrgUnitService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.Locale;

import static com.pro.ereportbacken.config.NotificationConstant.DAILY_OUTSTANDING_REPORT_SWITCH_NAME;

@RestController
@RequestMapping("/admin/notifications/settings")
public class AdminNotificationSettingsController {

    private final NotificationSettingsService notificationSettingsService;
    private final MailSenderConfigurationService mailSenderConfigurationService;
    private final MailSenderTestService mailSenderTestService;
    private final OrganizationScopeAuthorizationService scopeAuthorizationService;
    private final OrgUnitService orgUnitService;

    public AdminNotificationSettingsController(NotificationSettingsService notificationSettingsService,
                                               MailSenderConfigurationService mailSenderConfigurationService,
                                               MailSenderTestService mailSenderTestService,
                                               OrganizationScopeAuthorizationService scopeAuthorizationService,
                                               OrgUnitService orgUnitService) {
        this.notificationSettingsService = notificationSettingsService;
        this.mailSenderConfigurationService = mailSenderConfigurationService;
        this.mailSenderTestService = mailSenderTestService;
        this.scopeAuthorizationService = scopeAuthorizationService;
        this.orgUnitService = orgUnitService;
    }

    @GetMapping("/scopes")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.NOTIFICATION_CONFIG_VIEW + "')")
    public ResponseEntity<ConfigScopeDTO> getNotificationConfigScopes() {
        var access = scopeAuthorizationService.accessFor(PermissionCatalog.NOTIFICATION_CONFIG_VIEW);
        return ResponseEntity.ok(new ConfigScopeDTO(
                access.tenantWide(), orgUnitService.findNotificationConfigAccessible()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.NOTIFICATION_CONFIG_VIEW + "')")
    public ResponseEntity<NotificationCenterSettingsDTO> getSettings(
            @RequestParam(required = false) UUID orgUnitId) {
        requireScope(PermissionCatalog.NOTIFICATION_CONFIG_VIEW, orgUnitId);
        return ResponseEntity.ok(notificationSettingsService.getSettings(orgUnitId));
    }

    @PutMapping("/{switchName}")
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.NOTIFICATION_CHANNEL_UPDATE + "', '"
            + PermissionCatalog.NOTIFICATION_SCHEDULE_UPDATE + "')")
    public ResponseEntity<NotificationSwitchDTO> updateSwitch(
            @PathVariable String switchName,
            @Valid @RequestBody NotificationSwitchUpdateRequestDTO request,
            @RequestParam(required = false) UUID orgUnitId) {
        requireScope(switchUpdatePermission(switchName), orgUnitId);
        return ResponseEntity.ok(notificationSettingsService.update(switchName, request.enabled(), orgUnitId));
    }

    @PutMapping("/daily-outstanding-report/schedule")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.NOTIFICATION_SCHEDULE_UPDATE + "')")
    public ResponseEntity<DailyOutstandingReportSettingsDTO> updateDailyOutstandingReportSchedule(
            @Valid @RequestBody DailyOutstandingReportScheduleUpdateRequestDTO request,
            @RequestParam(required = false) UUID orgUnitId) {
        requireScope(PermissionCatalog.NOTIFICATION_SCHEDULE_UPDATE, orgUnitId);
        return ResponseEntity.ok(notificationSettingsService.updateDailyOutstandingReportSchedule(
                request.sendTime(), orgUnitId));
    }

    @PutMapping("/sender")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.NOTIFICATION_SENDER_UPDATE + "')")
    public ResponseEntity<MailSenderSettingsDTO> updateSender(
            @Valid @RequestBody MailSenderSettingsUpdateRequestDTO request,
            @RequestParam(required = false) UUID orgUnitId) {
        requireScope(PermissionCatalog.NOTIFICATION_SENDER_UPDATE, orgUnitId);
        return ResponseEntity.ok(mailSenderConfigurationService.update(request, orgUnitId));
    }

    @PostMapping("/sender/test")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.NOTIFICATION_SENDER_UPDATE + "')")
    public ResponseEntity<MailSenderTestResultDTO> testSender(
            @Valid @RequestBody MailSenderTestRequestDTO request,
            @RequestParam(required = false) UUID orgUnitId) {
        requireScope(PermissionCatalog.NOTIFICATION_SENDER_UPDATE, orgUnitId);
        return ResponseEntity.ok(mailSenderTestService.send(request, orgUnitId));
    }

    @DeleteMapping("/sender")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.NOTIFICATION_SENDER_UPDATE + "')")
    public ResponseEntity<MailSenderSettingsDTO> deleteSender(
            @RequestParam(required = false) UUID orgUnitId) {
        requireScope(PermissionCatalog.NOTIFICATION_SENDER_UPDATE, orgUnitId);
        return ResponseEntity.ok(mailSenderConfigurationService.deleteLocal(orgUnitId));
    }

    private void requireScope(String permission, UUID orgUnitId) {
        if (orgUnitId == null) {
            scopeAuthorizationService.requireTenantWideAccess(permission);
        } else {
            scopeAuthorizationService.requireOrganizationAccess(permission, orgUnitId);
        }
    }

    private String switchUpdatePermission(String switchName) {
        String normalized = switchName == null ? "" : switchName.trim().toLowerCase(Locale.ROOT);
        return DAILY_OUTSTANDING_REPORT_SWITCH_NAME.equals(normalized)
                ? PermissionCatalog.NOTIFICATION_SCHEDULE_UPDATE
                : PermissionCatalog.NOTIFICATION_CHANNEL_UPDATE;
    }
}
