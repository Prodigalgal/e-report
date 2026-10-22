package com.pro.ereportbacken.notification;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class NotificationChannelRegistry {

    private final List<NotificationChannel> channels;
    private final Map<String, NotificationChannel> channelsByName;

    public NotificationChannelRegistry(List<NotificationChannel> notificationChannels) {
        Map<String, NotificationChannel> indexedChannels = new LinkedHashMap<>();
        notificationChannels.stream()
                .sorted((left, right) -> left.descriptor().name().compareTo(right.descriptor().name()))
                .forEach(channel -> {
                    String channelName = channel.descriptor().name();
                    NotificationChannel existing = indexedChannels.putIfAbsent(channelName, channel);
                    if (existing != null) {
                        throw new IllegalStateException("Duplicate notification channel name: " + channelName);
                    }
                });
        if (indexedChannels.isEmpty()) {
            throw new IllegalStateException("At least one notification channel must be configured.");
        }
        this.channelsByName = Map.copyOf(indexedChannels);
        this.channels = List.copyOf(indexedChannels.values());
    }

    public List<NotificationChannel> channels() {
        return channels;
    }

    public List<String> channelNames() {
        return channels.stream().map(channel -> channel.descriptor().name()).toList();
    }

    public NotificationChannel require(String channelName) {
        if (!StringUtils.hasText(channelName)) {
            throw new IllegalArgumentException("Notification channel name must not be blank.");
        }
        String normalizedName = channelName.trim().toLowerCase(Locale.ROOT);
        NotificationChannel channel = channelsByName.get(normalizedName);
        if (channel == null) {
            throw new IllegalArgumentException("Unknown notification channel: " + normalizedName);
        }
        return channel;
    }
}
