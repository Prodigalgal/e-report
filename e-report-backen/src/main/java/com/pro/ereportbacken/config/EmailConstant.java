package com.pro.ereportbacken.config;

import java.util.List;
import java.util.Set;

public final class EmailConstant {
    public static final String MAIL_HOST_KEY = "spring.mail.host";
    public static final String MAIL_PORT_KEY = "spring.mail.port";
    public static final String MAIL_USERNAME_KEY = "spring.mail.username";
    public static final String MAIL_PASSWORD_KEY = "spring.mail.password"; // 邮箱授权码的键
    public static final String MAIL_SENDER_POOL_KEY = "notification.mail.password.sender-pool";
    public static final String MAIL_SENDER_STRATEGY_KEY = "notification.mail.sender-pool.strategy";
    public static final String MAIL_FROM_KEY = "spring.mail.from";
    public static final String MAIL_SMTP_AUTH_KEY = "spring.mail.properties.mail.smtp.auth";
    public static final String MAIL_SSL_ENABLE_KEY = "spring.mail.properties.mail.smtp.ssl.enable";
    public static final String MAIL_STARTTLS_ENABLE_KEY = "spring.mail.properties.mail.smtp.starttls.enable";
    public static final List<String> MAIL_SENDER_CONFIG_KEYS = List.of(
            MAIL_HOST_KEY,
            MAIL_PORT_KEY,
            MAIL_USERNAME_KEY,
            MAIL_PASSWORD_KEY,
            MAIL_FROM_KEY,
            MAIL_SMTP_AUTH_KEY,
            MAIL_SSL_ENABLE_KEY,
            MAIL_STARTTLS_ENABLE_KEY,
            MAIL_SENDER_POOL_KEY,
            MAIL_SENDER_STRATEGY_KEY
    );
    public static final Set<String> MAIL_SENDER_CONFIG_KEY_SET = Set.copyOf(MAIL_SENDER_CONFIG_KEYS);

    private EmailConstant() {
    }

    public static boolean isMailSenderConfigKey(String configKey) {
        return configKey != null && MAIL_SENDER_CONFIG_KEY_SET.contains(configKey);
    }
}
