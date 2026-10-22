package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.AppConfig;
import com.pro.ereportbacken.model.pojo.TenantAppConfig;
import com.pro.ereportbacken.repository.AppConfigRepository;
import com.pro.ereportbacken.repository.OrgAppConfigRepository;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.TenantAppConfigRepository;
import com.pro.ereportbacken.security.ConfigurationScopeContext;
import com.pro.ereportbacken.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppConfigServiceTests {

    @Test
    void recognizesWindows1252MojibakeOfExpectedDescription() {
        String expected = "企业微信";
        String mojibake = "\u00E4\u00BC\u0081\u00E4\u00B8\u0161\u00E5\u00BE\u00AE\u00E4\u00BF\u00A1";

        assertThat(AppConfigService.isMisencodedDescriptionOf(mojibake, expected)).isTrue();
    }

    @Test
    void preservesValidAndCustomizedDescriptions() {
        String expected = "企业微信机器人 Webhook URL";

        assertThat(AppConfigService.isMisencodedDescriptionOf(expected, expected)).isFalse();
        assertThat(AppConfigService.isMisencodedDescriptionOf("管理员自定义说明", expected)).isFalse();
        assertThat(AppConfigService.isMisencodedDescriptionOf(null, expected)).isFalse();
    }

    @Test
    void typedConfigReadsUseTheSameProgrammaticCacheAsDirectReads() {
        UUID tenantId = UUID.randomUUID();
        AppConfigRepository appConfigRepository = mock(AppConfigRepository.class);
        TenantAppConfigRepository tenantAppConfigRepository = mock(TenantAppConfigRepository.class);
        OrgAppConfigRepository orgAppConfigRepository = mock(OrgAppConfigRepository.class);
        OrgUnitRepository orgUnitRepository = mock(OrgUnitRepository.class);
        TenantContext tenantContext = mock(TenantContext.class);
        ConfigurationScopeContext configurationScopeContext = mock(ConfigurationScopeContext.class);
        CacheInvalidationService cacheInvalidationService = mock(CacheInvalidationService.class);
        CacheManager cacheManager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        String configKey = "observability.client.enabled";
        String cacheKey = tenantId + ":TENANT:" + configKey;

        when(tenantContext.currentTenantIdOrDefault()).thenReturn(tenantId);
        when(configurationScopeContext.currentOrgUnitIdOrNull()).thenReturn(null);
        when(cacheManager.getCache("appConfigCache")).thenReturn(cache);
        when(cache.get(cacheKey, String.class)).thenReturn(null, "true");
        when(appConfigRepository.findByConfigKey(configKey))
                .thenReturn(Optional.of(new AppConfig(configKey, "true", "测试配置")));
        when(tenantAppConfigRepository.findByTenantIdAndConfigKey(tenantId, configKey))
                .thenReturn(Optional.empty());

        AppConfigService service = new AppConfigService(
                appConfigRepository,
                tenantAppConfigRepository,
                orgAppConfigRepository,
                orgUnitRepository,
                tenantContext,
                configurationScopeContext,
                cacheInvalidationService,
                cacheManager
        );

        assertThat(service.getConfigValueAsBoolean(configKey, false)).isTrue();
        assertThat(service.getConfigValueAsBoolean(configKey, false)).isTrue();
        verify(appConfigRepository, times(1)).findByConfigKey(configKey);
        verify(cache, times(1)).put(cacheKey, "true");
    }

    @Test
    void tenantScopedBooleanPrefersTenantOverrideAndUsesTenantCacheKey() {
        UUID tenantId = UUID.randomUUID();
        String configKey = "media.image-redaction.submission-enabled";
        AppConfigRepository appConfigRepository = mock(AppConfigRepository.class);
        TenantAppConfigRepository tenantAppConfigRepository = mock(TenantAppConfigRepository.class);
        OrgAppConfigRepository orgAppConfigRepository = mock(OrgAppConfigRepository.class);
        OrgUnitRepository orgUnitRepository = mock(OrgUnitRepository.class);
        TenantContext tenantContext = mock(TenantContext.class);
        ConfigurationScopeContext configurationScopeContext = mock(ConfigurationScopeContext.class);
        CacheInvalidationService cacheInvalidationService = mock(CacheInvalidationService.class);
        CacheManager cacheManager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        TenantAppConfig tenantConfig = new TenantAppConfig();
        tenantConfig.setTenantId(tenantId);
        tenantConfig.setConfigKey(configKey);
        tenantConfig.setConfigValue("false");

        when(cacheManager.getCache("appConfigCache")).thenReturn(cache);
        when(cache.get(tenantId + ":TENANT:" + configKey, String.class)).thenReturn(null);
        when(tenantAppConfigRepository.findByTenantIdAndConfigKey(tenantId, configKey))
                .thenReturn(Optional.of(tenantConfig));

        AppConfigService service = new AppConfigService(
                appConfigRepository,
                tenantAppConfigRepository,
                orgAppConfigRepository,
                orgUnitRepository,
                tenantContext,
                configurationScopeContext,
                cacheInvalidationService,
                cacheManager
        );

        assertThat(service.getTenantConfigValueAsBoolean(tenantId, configKey, true)).isFalse();
        verify(appConfigRepository, org.mockito.Mockito.never()).findByConfigKey(configKey);
        verify(cache).put(tenantId + ":TENANT:" + configKey, "false");
    }

    @Test
    void rejectsInvalidImageRedactionSubmissionSwitchValue() {
        AppConfigService service = new AppConfigService(
                mock(AppConfigRepository.class),
                mock(TenantAppConfigRepository.class),
                mock(OrgAppConfigRepository.class),
                mock(OrgUnitRepository.class),
                mock(TenantContext.class),
                mock(ConfigurationScopeContext.class),
                mock(CacheInvalidationService.class),
                mock(CacheManager.class)
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.saveOrUpdateScopedConfig(
                        "media.image-redaction.submission-enabled",
                        "maybe",
                        "测试",
                        null
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("图片脱敏提交开关只能填写 true/false（也接受 1/0、yes/no）。");
    }
}
