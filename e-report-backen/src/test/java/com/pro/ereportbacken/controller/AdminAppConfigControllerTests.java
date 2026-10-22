package com.pro.ereportbacken.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.pojo.AppConfig;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.service.AppConfigService;
import com.pro.ereportbacken.service.OrgUnitService;
import com.pro.ereportbacken.service.ResolvedAppConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAppConfigControllerTests {

    private final AppConfigService appConfigService = mock(AppConfigService.class);
    private final AdminAppConfigController controller = new AdminAppConfigController(
            appConfigService,
            mock(OrganizationScopeAuthorizationService.class),
            mock(OrgUnitService.class)
    );
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void neverReturnsSensitiveConfigurationValue() throws Exception {
        String secret = "super-secret-value-that-must-not-leak";
        String key = "integration.private-key";
        when(appConfigService.findScopedConfigByKey(key, null))
                .thenReturn(Optional.of(resolved(key, secret)));

        var body = controller.getConfigByKey(key, null).getBody();
        String json = objectMapper.writeValueAsString(body);

        assertThat(body).isNotNull();
        assertThat(body.configValue()).isNull();
        assertThat(body.configured()).isTrue();
        assertThat(body.displayValue()).isEqualTo("********");
        assertThat(json).doesNotContain(secret);
    }

    @Test
    void keepsNonSensitiveConfigurationValueAvailable() {
        String key = "feature.enabled";
        when(appConfigService.findScopedConfigByKey(key, null))
                .thenReturn(Optional.of(resolved(key, "true")));

        var body = controller.getConfigByKey(key, null).getBody();

        assertThat(body).isNotNull();
        assertThat(body.configValue()).isEqualTo("true");
        assertThat(body.configured()).isTrue();
    }

    private ResolvedAppConfig resolved(String key, String value) {
        return new ResolvedAppConfig(
                new AppConfig(key, value, "测试配置"),
                null,
                null,
                ResolvedAppConfig.SourceScope.TENANT_OVERRIDE,
                null,
                null,
                false
        );
    }
}
