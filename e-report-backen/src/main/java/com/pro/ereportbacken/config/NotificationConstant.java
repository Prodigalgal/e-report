package com.pro.ereportbacken.config;

public final class NotificationConstant {
    public static final String CENTER_ENABLED_KEY = "notification.center.enabled";
    public static final String EMAIL_ENABLED_KEY = "notification.email.enabled";
    public static final String WECOM_ENABLED_KEY = "notification.wecom.enabled";
    public static final String WECOM_WEBHOOK_URL_KEY = "notification.wecom.webhook-url";
    public static final String DINGTALK_ENABLED_KEY = "notification.dingtalk.enabled";
    public static final String DINGTALK_WEBHOOK_URL_KEY = "notification.dingtalk.webhook-url";
    public static final String DINGTALK_SECRET_KEY = "notification.dingtalk.secret";
    public static final String DAILY_OUTSTANDING_REPORT_ENABLED_KEY = "notification.daily-outstanding-report.enabled";
    public static final String DAILY_OUTSTANDING_REPORT_SEND_TIME_KEY = "notification.daily-outstanding-report.send-time";
    public static final String DAILY_OUTSTANDING_REPORT_SWITCH_NAME = "daily-outstanding-report";

    private NotificationConstant() {
    }

    public static boolean isManagedConfigKey(String configKey) {
        return configKey != null
                && (configKey.startsWith("notification.") || EmailConstant.isMailSenderConfigKey(configKey));
    }
}
