package com.pro.ereportbacken.controller;

import java.util.UUID;

import com.pro.ereportbacken.model.dto.AppConfigDTO;
import com.pro.ereportbacken.model.dto.AppConfigUpdateRequestDTO;
import com.pro.ereportbacken.model.dto.ConfigScopeDTO;
import com.pro.ereportbacken.model.dto.ScopedAppConfigDTO;
import com.pro.ereportbacken.model.dto.PageResponseDTO;
import com.pro.ereportbacken.config.NotificationConstant;
import com.pro.ereportbacken.config.AppConfigConstant;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.service.AppConfigService;
import com.pro.ereportbacken.service.OrgUnitService;
import com.pro.ereportbacken.service.ResolvedAppConfig;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Sort;


@RestController
@RequestMapping("/admin/configs")
public class AdminAppConfigController {

    private final AppConfigService appConfigService;
    private final OrganizationScopeAuthorizationService scopeAuthorizationService;
    private final OrgUnitService orgUnitService;

    public AdminAppConfigController(AppConfigService appConfigService,
                                    OrganizationScopeAuthorizationService scopeAuthorizationService,
                                    OrgUnitService orgUnitService) {
        this.appConfigService = appConfigService;
        this.scopeAuthorizationService = scopeAuthorizationService;
        this.orgUnitService = orgUnitService;
    }

    @GetMapping("/scopes")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SYSTEM_CONFIG_VIEW + "')")
    public ResponseEntity<ConfigScopeDTO> getConfigScopes() {
        var access = scopeAuthorizationService.accessFor(PermissionCatalog.SYSTEM_CONFIG_VIEW);
        return ResponseEntity.ok(new ConfigScopeDTO(access.tenantWide(), orgUnitService.findConfigAccessible()));
    }

    private ScopedAppConfigDTO convertToDTO(ResolvedAppConfig resolved) {
        if (resolved == null) return null;
        var appConfig = resolved.config();
        boolean isSensitive = AppConfigService.isKeySensitive(appConfig.getConfigKey());
        boolean configured = StringUtils.hasText(appConfig.getConfigValue());
        String responseValue = isSensitive ? null : appConfig.getConfigValue();
        String displayValue = isSensitive
                ? (configured ? "********" : "未配置")
                : appConfig.getConfigValue();

        return new ScopedAppConfigDTO(
                appConfig.getConfigKey(),
                responseValue,
                appConfig.getDescription(),
                appConfig.getLastUpdatedTime(),
                isSensitive,
                configured,
                displayValue,
                resolved.requestedOrgUnitId(),
                resolved.requestedOrgUnitName(),
                resolved.sourceScope().name(),
                resolved.sourceOrgUnitId(),
                resolved.sourceOrgUnitName(),
                resolved.inherited()
        );
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SYSTEM_CONFIG_VIEW + "')")
    public ResponseEntity<PageResponseDTO<ScopedAppConfigDTO>> getAllConfigs(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) UUID orgUnitId,
            @PageableDefault(size = 20, sort = "configKey", direction = Sort.Direction.ASC) Pageable pageable) {
        requireScope(PermissionCatalog.SYSTEM_CONFIG_VIEW, orgUnitId);
        return ResponseEntity.ok(PageResponseDTO.from(appConfigService.searchScopedConfigs(
                searchTerm, pageable, orgUnitId, false, false).map(this::convertToDTO)));
    }

    @GetMapping("/{configKey}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SYSTEM_CONFIG_VIEW + "')")
    public ResponseEntity<ScopedAppConfigDTO> getConfigByKey(@PathVariable String configKey,
                                                              @RequestParam(required = false) UUID orgUnitId) {
        requireSystemManagedConfig(configKey, orgUnitId);
        requireScope(PermissionCatalog.SYSTEM_CONFIG_VIEW, orgUnitId);
        return appConfigService.findScopedConfigByKey(configKey, orgUnitId)
                .map(this::convertToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 用于创建新配置
    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SYSTEM_CONFIG_CREATE + "')")
    public ResponseEntity<ScopedAppConfigDTO> createConfig(@Valid @RequestBody AppConfigDTO appConfigDTO,
                                                            @RequestParam(required = false) UUID orgUnitId) {
        requireSystemManagedConfig(appConfigDTO.getConfigKey(), orgUnitId);
        requireScope(PermissionCatalog.SYSTEM_CONFIG_CREATE, orgUnitId);
        ResolvedAppConfig createdConfig = appConfigService.saveOrUpdateScopedConfig(
                appConfigDTO.getConfigKey(),
                appConfigDTO.getConfigValue(),
                appConfigDTO.getDescription(),
                orgUnitId
        );
        return new ResponseEntity<>(convertToDTO(createdConfig), HttpStatus.CREATED);
    }

    // 用于更新现有配置 (只能更新 value 和 description)
    @PutMapping("/{configKey}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SYSTEM_CONFIG_UPDATE + "')")
    public ResponseEntity<ScopedAppConfigDTO> updateConfig(@PathVariable String configKey,
                                                            @Valid @RequestBody AppConfigUpdateRequestDTO updateRequestDTO,
                                                            @RequestParam(required = false) UUID orgUnitId) {
        requireSystemManagedConfig(configKey, orgUnitId);
        requireScope(PermissionCatalog.SYSTEM_CONFIG_UPDATE, orgUnitId);
        ResolvedAppConfig updatedConfig = appConfigService.saveOrUpdateScopedConfig(
                configKey,
                updateRequestDTO.getConfigValue(),
                updateRequestDTO.getDescription(),
                orgUnitId,
                updateRequestDTO.isRetainSensitiveValue()
        );
        return ResponseEntity.ok(convertToDTO(updatedConfig));
    }

    @DeleteMapping("/{configKey}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SYSTEM_CONFIG_DELETE + "')")
    public ResponseEntity<Void> deleteConfig(@PathVariable String configKey,
                                             @RequestParam(required = false) UUID orgUnitId) {
        requireSystemManagedConfig(configKey, orgUnitId);
        requireScope(PermissionCatalog.SYSTEM_CONFIG_DELETE, orgUnitId);
        appConfigService.deleteScopedConfig(configKey, orgUnitId);
        return ResponseEntity.noContent().build();
    }

    private void requireScope(String permission, UUID orgUnitId) {
        if (orgUnitId == null) {
            scopeAuthorizationService.requireTenantWideAccess(permission);
        } else {
            scopeAuthorizationService.requireOrganizationAccess(permission, orgUnitId);
        }
    }

    private void requireSystemManagedConfig(String configKey, UUID orgUnitId) {
        if (NotificationConstant.isManagedConfigKey(configKey)) {
            throw new IllegalArgumentException("通知中心配置必须在通知设置中修改，不能通过通用系统配置接口操作。");
        }
        if (AppConfigConstant.isPasswordPolicyKey(configKey)) {
            throw new IllegalArgumentException("密码策略必须在密码策略面板中修改，不能通过通用系统配置接口操作。");
        }
        if (AppConfigConstant.isTenantWideOnlyKey(configKey) && orgUnitId != null) {
            throw new IllegalArgumentException("图片脱敏提交开关只能在租户默认配置中修改，不能按组织覆盖。");
        }
    }
}

