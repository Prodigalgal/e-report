package com.pro.ereportbacken.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticationHashWorkloadGuardTests {

    @Test
    void rejectsBeyondBoundedCapacityAndPublishesRetryAfter() {
        AuthenticationHashWorkloadGuard guard = new AuthenticationHashWorkloadGuard(
                1, 0, Duration.ofMillis(10), Duration.ofSeconds(2));

        try (AuthenticationHashWorkloadGuard.Permit ignored = guard.acquire()) {
            assertThatThrownBy(guard::acquire)
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(exception.getHeaders().getFirst("Retry-After")).isEqualTo("2");
                    });
        }

        try (AuthenticationHashWorkloadGuard.Permit ignored = guard.acquire()) {
            assertThat(ignored).isNotNull();
        }
    }
}
