package com.pro.ereportbacken.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WebSessionCookieFactoryTests {

    private final WebSessionCookieFactory factory = new WebSessionCookieFactory(true);
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    void rememberedSessionUsesPersistentHttpOnlyCookie() {
        var cookie = factory.issue(request, "session-secret", true);

        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(7));
        assertThat(cookie.toString()).contains("Max-Age=604800");
    }

    @Test
    void browserSessionDoesNotWritePersistentMaxAge() {
        var cookie = factory.issue(request, "session-secret", false);

        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofSeconds(-1));
        assertThat(cookie.toString()).doesNotContain("Max-Age=");
    }
}
