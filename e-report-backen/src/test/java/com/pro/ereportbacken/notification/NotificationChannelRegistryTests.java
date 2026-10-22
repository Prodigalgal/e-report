package com.pro.ereportbacken.notification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationChannelRegistryTests {

    @Test
    void exposesStableSortedChannelNames() {
        NotificationChannelRegistry registry = new NotificationChannelRegistry(List.of(
                channel("wecom"), channel("email"), channel("dingtalk")
        ));

        assertThat(registry.channelNames()).containsExactly("dingtalk", "email", "wecom");
        assertThat(registry.require(" WECOM ").descriptor().name()).isEqualTo("wecom");
    }

    @Test
    void rejectsDuplicateNamesDuringStartup() {
        assertThatThrownBy(() -> new NotificationChannelRegistry(List.of(channel("email"), channel("EMAIL"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate notification channel name");
    }

    private NotificationChannel channel(String name) {
        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.descriptor()).thenReturn(new NotificationChannelDescriptor(
                name, name, name + " notifications", "notification." + name.toLowerCase() + ".enabled", false
        ));
        return channel;
    }
}
