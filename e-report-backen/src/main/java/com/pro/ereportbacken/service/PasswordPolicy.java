package com.pro.ereportbacken.service;

import com.pro.ereportbacken.config.AppConfigConstant;
import com.pro.ereportbacken.model.dto.PasswordPolicyDTO;
import com.pro.ereportbacken.model.dto.PasswordPolicyUpdateRequestDTO;
import com.pro.ereportbacken.service.ResolvedAppConfig.SourceScope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class PasswordPolicy {

    public static final int DEFAULT_MIN_LENGTH = 8;
    public static final int MIN_ALLOWED_LENGTH = 8;
    public static final int MAX_LENGTH = 100;

    private static final Set<String> BLOCKLIST = Set.of(
            "12345678", "123456789012345", "111111111111111", "000000000000000",
            "password", "password1", "password1!", "passwordpassword", "password123456",
            "qwertyui", "qwertyuiop12345", "qwerty123456789", "qwerty123!",
            "abcdefgh", "abc123456789012", "abcd1234", "admin1234", "admin1234!",
            "admin1234567890", "administrator123", "welcome1", "welcome123456789",
            "changeme12345678", "letmein123456789", "ereport123456789",
            "zaq12wsxzaq12ws", "1q2w3e4r5t6y7u8", "iloveyou12345678"
    );

    private final AppConfigService appConfigService;

    public PasswordPolicy(AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
    }

    public PasswordPolicySettings current() {
        return settings(
                parseInt(appConfigService.getConfigValue(AppConfigConstant.PASSWORD_POLICY_MIN_LENGTH), DEFAULT_MIN_LENGTH),
                parseBoolean(appConfigService.getConfigValue(AppConfigConstant.PASSWORD_POLICY_REQUIRE_UPPERCASE), true),
                parseBoolean(appConfigService.getConfigValue(AppConfigConstant.PASSWORD_POLICY_REQUIRE_LOWERCASE), true),
                parseBoolean(appConfigService.getConfigValue(AppConfigConstant.PASSWORD_POLICY_REQUIRE_DIGIT), true),
                parseBoolean(appConfigService.getConfigValue(AppConfigConstant.PASSWORD_POLICY_REQUIRE_SPECIAL), true)
        );
    }

    public PasswordPolicySettings settingsForScope(UUID orgUnitId) {
        return settings(
                valueAsInt(AppConfigConstant.PASSWORD_POLICY_MIN_LENGTH, orgUnitId, DEFAULT_MIN_LENGTH),
                valueAsBoolean(AppConfigConstant.PASSWORD_POLICY_REQUIRE_UPPERCASE, orgUnitId, true),
                valueAsBoolean(AppConfigConstant.PASSWORD_POLICY_REQUIRE_LOWERCASE, orgUnitId, true),
                valueAsBoolean(AppConfigConstant.PASSWORD_POLICY_REQUIRE_DIGIT, orgUnitId, true),
                valueAsBoolean(AppConfigConstant.PASSWORD_POLICY_REQUIRE_SPECIAL, orgUnitId, true)
        );
    }

    public PasswordPolicyDTO dtoForScope(UUID orgUnitId) {
        PasswordPolicySettings settings = settingsForScope(orgUnitId);
        var resolvedConfig = appConfigService.findScopedConfigByKey(
                AppConfigConstant.PASSWORD_POLICY_MIN_LENGTH, orgUnitId).orElse(null);
        if (resolvedConfig == null) {
            return new PasswordPolicyDTO(
                    settings.minLength(), settings.maxLength(), settings.requireUppercase(),
                    settings.requireLowercase(), settings.requireDigit(), settings.requireSpecial(),
                    SourceScope.PLATFORM_DEFAULT.name(), null, true, false);
        }
        boolean localOverride = resolvedConfig.sourceScope() == SourceScope.TENANT_OVERRIDE
                || resolvedConfig.sourceScope() == SourceScope.ORGANIZATION_OVERRIDE;
        return new PasswordPolicyDTO(
                settings.minLength(), settings.maxLength(), settings.requireUppercase(),
                settings.requireLowercase(), settings.requireDigit(), settings.requireSpecial(),
                resolvedConfig.sourceScope().name(), resolvedConfig.sourceOrgUnitName(),
                resolvedConfig.inherited(), localOverride && !resolvedConfig.inherited());
    }

    @Transactional
    public PasswordPolicyDTO update(UUID orgUnitId, PasswordPolicyUpdateRequestDTO request) {
        PasswordPolicySettings settings = settings(
                request.minLength(),
                Boolean.TRUE.equals(request.requireUppercase()),
                Boolean.TRUE.equals(request.requireLowercase()),
                Boolean.TRUE.equals(request.requireDigit()),
                Boolean.TRUE.equals(request.requireSpecial())
        );
        save(orgUnitId, AppConfigConstant.PASSWORD_POLICY_MIN_LENGTH,
                Integer.toString(settings.minLength()), "密码最小长度（系统上限100位）");
        save(orgUnitId, AppConfigConstant.PASSWORD_POLICY_REQUIRE_UPPERCASE,
                Boolean.toString(settings.requireUppercase()), "密码是否必须包含大写字母");
        save(orgUnitId, AppConfigConstant.PASSWORD_POLICY_REQUIRE_LOWERCASE,
                Boolean.toString(settings.requireLowercase()), "密码是否必须包含小写字母");
        save(orgUnitId, AppConfigConstant.PASSWORD_POLICY_REQUIRE_DIGIT,
                Boolean.toString(settings.requireDigit()), "密码是否必须包含数字");
        save(orgUnitId, AppConfigConstant.PASSWORD_POLICY_REQUIRE_SPECIAL,
                Boolean.toString(settings.requireSpecial()), "密码是否必须包含特殊字符");
        return dtoForScope(orgUnitId);
    }

    @Transactional
    public void deleteOverride(UUID orgUnitId) {
        appConfigService.deleteScopedConfig(AppConfigConstant.PASSWORD_POLICY_MIN_LENGTH, orgUnitId);
        appConfigService.deleteScopedConfig(AppConfigConstant.PASSWORD_POLICY_REQUIRE_UPPERCASE, orgUnitId);
        appConfigService.deleteScopedConfig(AppConfigConstant.PASSWORD_POLICY_REQUIRE_LOWERCASE, orgUnitId);
        appConfigService.deleteScopedConfig(AppConfigConstant.PASSWORD_POLICY_REQUIRE_DIGIT, orgUnitId);
        appConfigService.deleteScopedConfig(AppConfigConstant.PASSWORD_POLICY_REQUIRE_SPECIAL, orgUnitId);
    }

    public void requireValid(String password, PasswordContext context) {
        requireValid(password, context, current());
    }

    public void requireValid(String password, PasswordContext context, PasswordPolicySettings settings) {
        if (!StringUtils.hasLength(password)) {
            throw new IllegalArgumentException("密码不能为空。");
        }
        int length = password.codePointCount(0, password.length());
        if (length < settings.minLength() || length > settings.maxLength()) {
            throw new IllegalArgumentException("密码长度必须在" + settings.minLength()
                    + "到" + settings.maxLength() + "字符之间。");
        }

        String normalized = normalize(password);
        if (BLOCKLIST.contains(normalized)
                || normalized.codePoints().distinct().count() <= 2
                || hasLongSequentialRun(normalized)) {
            throw new IllegalArgumentException("该密码过于常见或容易猜测，请使用更长的随机密码或多个无关词组成的口令。");
        }

        List<String> missingRequirements = missingCompositionRequirements(password, settings);
        if (!missingRequirements.isEmpty()) {
            throw new IllegalArgumentException("密码必须包含" + String.join("、", missingRequirements) + "。");
        }

        if (context != null && containsContextValue(normalized, context)) {
            throw new IllegalArgumentException("密码不能包含登录账号、姓名、手机号或邮箱账号部分。");
        }
    }

    private void save(UUID orgUnitId, String key, String value, String description) {
        appConfigService.saveOrUpdateScopedConfig(key, value, description, orgUnitId);
    }

    private PasswordPolicySettings settings(int minLength,
                                            boolean requireUppercase,
                                            boolean requireLowercase,
                                            boolean requireDigit,
                                            boolean requireSpecial) {
        return new PasswordPolicySettings(
                Math.max(MIN_ALLOWED_LENGTH, Math.min(minLength, MAX_LENGTH)),
                MAX_LENGTH,
                requireUppercase,
                requireLowercase,
                requireDigit,
                requireSpecial
        );
    }

    private int valueAsInt(String key, UUID orgUnitId, int defaultValue) {
        return appConfigService.findScopedConfigByKey(key, orgUnitId)
                .map(config -> parseInt(config.config().getConfigValue(), defaultValue))
                .orElse(defaultValue);
    }

    private boolean valueAsBoolean(String key, UUID orgUnitId, boolean defaultValue) {
        return appConfigService.findScopedConfigByKey(key, orgUnitId)
                .map(config -> parseBoolean(config.config().getConfigValue(), defaultValue))
                .orElse(defaultValue);
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException exception) {
            return defaultValue;
        }
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value) || "0".equals(value) || "no".equalsIgnoreCase(value)) return false;
        return defaultValue;
    }

    private List<String> missingCompositionRequirements(String password, PasswordPolicySettings settings) {
        List<String> missing = new ArrayList<>();
        if (settings.requireUppercase() && password.codePoints().noneMatch(Character::isUpperCase)) {
            missing.add("大写字母");
        }
        if (settings.requireLowercase() && password.codePoints().noneMatch(Character::isLowerCase)) {
            missing.add("小写字母");
        }
        if (settings.requireDigit() && password.codePoints().noneMatch(Character::isDigit)) {
            missing.add("数字");
        }
        if (settings.requireSpecial() && password.codePoints()
                .noneMatch(codePoint -> !Character.isLetterOrDigit(codePoint) && !Character.isWhitespace(codePoint))) {
            missing.add("特殊字符");
        }
        return missing;
    }

    private boolean containsContextValue(String normalizedPassword, PasswordContext context) {
        return containsMeaningful(normalizedPassword, context.username())
                || containsMeaningful(normalizedPassword, context.name())
                || containsMeaningful(normalizedPassword, context.phone())
                || containsMeaningful(normalizedPassword, emailLocalPart(context.email()));
    }

    private boolean containsMeaningful(String normalizedPassword, String candidate) {
        if (!StringUtils.hasText(candidate)) return false;
        String normalizedCandidate = normalize(candidate);
        return normalizedCandidate.codePointCount(0, normalizedCandidate.length()) >= 4
                && normalizedPassword.contains(normalizedCandidate);
    }

    private String emailLocalPart(String email) {
        if (!StringUtils.hasText(email)) return null;
        int separator = email.indexOf('@');
        return separator > 0 ? email.substring(0, separator) : email;
    }

    private boolean hasLongSequentialRun(String value) {
        int ascending = 1;
        int descending = 1;
        for (int index = 1; index < value.length(); index++) {
            int difference = value.charAt(index) - value.charAt(index - 1);
            ascending = difference == 1 ? ascending + 1 : 1;
            descending = difference == -1 ? descending + 1 : 1;
            if (ascending >= 8 || descending >= 8) return true;
        }
        return false;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    public record PasswordContext(String username, String name, String phone, String email) {
    }

    public record PasswordPolicySettings(
            int minLength,
            int maxLength,
            boolean requireUppercase,
            boolean requireLowercase,
            boolean requireDigit,
            boolean requireSpecial) {
    }
}
