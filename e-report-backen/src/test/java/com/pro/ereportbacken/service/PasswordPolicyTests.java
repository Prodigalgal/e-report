package com.pro.ereportbacken.service;

import com.pro.ereportbacken.config.AppConfigConstant;
import com.pro.ereportbacken.model.dto.PasswordPolicyUpdateRequestDTO;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTests {

    private final AppConfigService appConfigService = mock(AppConfigService.class);
    private final PasswordPolicy passwordPolicy;
    private final PasswordPolicy.PasswordContext context = new PasswordPolicy.PasswordContext(
            "dispatcher", "调度管理员", "13959601234", "dispatcher@139.com");

    PasswordPolicyTests() {
        when(appConfigService.getConfigValue(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            return key.endsWith("min-length") ? "8" : "true";
        });
        passwordPolicy = new PasswordPolicy(appConfigService);
    }

    @Test
    void acceptsPasswordsThatMeetDefaultCompositionRules() {
        assertThatCode(() -> passwordPolicy.requireValid("清风-月明-Aa1-工单", context))
                .doesNotThrowAnyException();
        assertThatCode(() -> passwordPolicy.requireValid("correct-Horse-2026!", context))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsShortCommonSequentialAndContextPasswords() {
        assertThatThrownBy(() -> passwordPolicy.requireValid("Aa1!bc", context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8到100");
        assertThatThrownBy(() -> passwordPolicy.requireValid("1111111111111111", context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("容易猜测");
        assertThatThrownBy(() -> passwordPolicy.requireValid("abcdefgh-2026-safe", context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("容易猜测");
        assertThatThrownBy(() -> passwordPolicy.requireValid("dispatcher-Safe-2026", context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("登录账号");
    }

    @Test
    void enforcesConfiguredCompositionRules() {
        assertThatThrownBy(() -> passwordPolicy.requireValid("aa1!bbbb", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("大写字母");
        assertThatThrownBy(() -> passwordPolicy.requireValid("AA1!BBBB", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("小写字母");
        assertThatThrownBy(() -> passwordPolicy.requireValid("Aa!bbbbb", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("数字");
        assertThatThrownBy(() -> passwordPolicy.requireValid("Aa1bbbbb", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("特殊字符");
    }

    @Test
    void updatesAllPolicyComponentsInTheSelectedScope() {
        UUID orgUnitId = UUID.randomUUID();
        PasswordPolicyUpdateRequestDTO request = new PasswordPolicyUpdateRequestDTO(12, false, true, true, false);

        passwordPolicy.update(orgUnitId, request);

        verify(appConfigService).saveOrUpdateScopedConfig(
                AppConfigConstant.PASSWORD_POLICY_MIN_LENGTH, "12", "密码最小长度（系统上限100位）", orgUnitId);
        verify(appConfigService).saveOrUpdateScopedConfig(
                AppConfigConstant.PASSWORD_POLICY_REQUIRE_UPPERCASE, "false", "密码是否必须包含大写字母", orgUnitId);
        verify(appConfigService).saveOrUpdateScopedConfig(
                AppConfigConstant.PASSWORD_POLICY_REQUIRE_LOWERCASE, "true", "密码是否必须包含小写字母", orgUnitId);
        verify(appConfigService).saveOrUpdateScopedConfig(
                AppConfigConstant.PASSWORD_POLICY_REQUIRE_DIGIT, "true", "密码是否必须包含数字", orgUnitId);
        verify(appConfigService).saveOrUpdateScopedConfig(
                AppConfigConstant.PASSWORD_POLICY_REQUIRE_SPECIAL, "false", "密码是否必须包含特殊字符", orgUnitId);
    }

    @Test
    void countsUnicodeCodePointsAndEnforcesMaximumLength() {
        assertThatCode(() -> passwordPolicy.requireValid("甲乙丙丁戊己庚辛壬癸Aa1!", null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> passwordPolicy.requireValid("安".repeat(101), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8到100");
    }
}
