package com.pro.ereportbacken.notification;

public enum MailSenderProvider {
    CUSTOM,
    GMAIL,
    MICROSOFT_365,
    MOBILE_139;

    private static final String GMAIL_OAUTH_SCOPE = "https://mail.google.com/";
    private static final String MICROSOFT_365_OAUTH_SCOPE = "https://outlook.office.com/SMTP.Send offline_access";
    private static final String MICROSOFT_365_ALTERNATE_SCOPE = "https://outlook.office365.com/SMTP.Send offline_access";

    public boolean supportsOAuth2() {
        return this == GMAIL || this == MICROSOFT_365;
    }

    public String resolveOAuthScope(String configuredScope) {
        if (!supportsOAuth2()) {
            throw new IllegalArgumentException("当前邮箱服务商不支持OAuth2认证。");
        }
        if (this == MICROSOFT_365 && MICROSOFT_365_ALTERNATE_SCOPE.equals(configuredScope)) {
            return MICROSOFT_365_OAUTH_SCOPE;
        }
        if (configuredScope == null || configuredScope.isBlank()) {
            return this == GMAIL ? GMAIL_OAUTH_SCOPE : MICROSOFT_365_OAUTH_SCOPE;
        }
        return configuredScope;
    }
}
