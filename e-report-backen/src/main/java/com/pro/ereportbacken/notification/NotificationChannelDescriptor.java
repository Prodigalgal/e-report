package com.pro.ereportbacken.notification;

import org.springframework.util.StringUtils;

import java.util.Locale;

public record NotificationChannelDescriptor(
        String name,
        String displayName,
        String description,
        String enabledConfigKey,
        boolean defaultEnabled
) {
    public NotificationChannelDescriptor {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Notification channel name must not be blank.");
        }
        if (!StringUtils.hasText(displayName)) {
            throw new IllegalArgumentException("Notification channel display name must not be blank.");
        }
        if (!StringUtils.hasText(description)) {
            throw new IllegalArgumentException("Notification channel description must not be blank.");
        }
        if (!StringUtils.hasText(enabledConfigKey)) {
            throw new IllegalArgumentException("Notification channel enabled config key must not be blank.");
        }
        name = name.trim().toLowerCase(Locale.ROOT);
        displayName = displayName.trim();
        description = description.trim();
        enabledConfigKey = enabledConfigKey.trim();
    }
}
