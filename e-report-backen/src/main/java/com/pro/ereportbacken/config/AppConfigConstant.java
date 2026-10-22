package com.pro.ereportbacken.config;

import java.util.Set;

public class AppConfigConstant {
    public static final String AMAP_API_KEY = "amap.webservice.apikey";
    public static final String AMAP_API_KEY_POOL = "map.amap.webservice.api-key-pool";
    public static final String OBSERVABILITY_CLIENT_ENABLED = "observability.client.enabled";
    public static final String OBSERVABILITY_WARNING_ENABLED = "observability.client.warning-enabled";
    public static final String OBSERVABILITY_OPERATION_ENABLED = "observability.client.operation-enabled";
    public static final String OBSERVABILITY_OPERATION_SUCCESS_SAMPLE_RATE_PERCENT =
            "observability.client.operation-success-sample-rate-percent";
    public static final String OBSERVABILITY_BATCH_SIZE = "observability.client.batch-size";
    public static final String OBSERVABILITY_FLUSH_INTERVAL_SECONDS = "observability.client.flush-interval-seconds";
    public static final String OBSERVABILITY_RETENTION_DAYS = "observability.retention-days";
    public static final String OBSERVABILITY_OPERATION_RETENTION_DAYS = "observability.operation-retention-days";
    public static final String PASSWORD_POLICY_MIN_LENGTH = "security.password-policy.min-length";
    public static final String PASSWORD_POLICY_REQUIRE_UPPERCASE = "security.password-policy.require-uppercase";
    public static final String PASSWORD_POLICY_REQUIRE_LOWERCASE = "security.password-policy.require-lowercase";
    public static final String PASSWORD_POLICY_REQUIRE_DIGIT = "security.password-policy.require-digit";
    public static final String PASSWORD_POLICY_REQUIRE_SPECIAL = "security.password-policy.require-special";
    public static final String IMAGE_REDACTION_SUBMISSION_ENABLED = "media.image-redaction.submission-enabled";

    private static final Set<String> PASSWORD_POLICY_KEYS = Set.of(
            PASSWORD_POLICY_MIN_LENGTH,
            PASSWORD_POLICY_REQUIRE_UPPERCASE,
            PASSWORD_POLICY_REQUIRE_LOWERCASE,
            PASSWORD_POLICY_REQUIRE_DIGIT,
            PASSWORD_POLICY_REQUIRE_SPECIAL
    );

    public static boolean isPasswordPolicyKey(String key) {
        return key != null && PASSWORD_POLICY_KEYS.contains(key);
    }

    public static boolean isTenantWideOnlyKey(String key) {
        return IMAGE_REDACTION_SUBMISSION_ENABLED.equals(key);
    }

    private AppConfigConstant() {
    }
}
