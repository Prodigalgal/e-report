package com.pro.ereportbacken.notification;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeliveryProfileTests {

    @Test
    void testProfileDisablesDeliveryByDefault() throws IOException {
        assertThat(testEnvironment().getProperty("notification.delivery-enabled", Boolean.class)).isFalse();
    }

    @Test
    void testProfileHonorsExplicitDeliveryEnablement() throws IOException {
        MockEnvironment environment = testEnvironment().withProperty("NOTIFICATION_DELIVERY_ENABLED", "true");
        assertThat(environment.getProperty("notification.delivery-enabled", Boolean.class)).isTrue();
    }

    @Test
    void testProfileHonorsExplicitDeliveryDisablement() throws IOException {
        MockEnvironment environment = testEnvironment().withProperty("NOTIFICATION_DELIVERY_ENABLED", "false");
        assertThat(environment.getProperty("notification.delivery-enabled", Boolean.class)).isFalse();
    }

    private MockEnvironment testEnvironment() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addLast(new ResourcePropertySource("classpath:application-e2e.properties"));
        environment.getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
        return environment;
    }
}
