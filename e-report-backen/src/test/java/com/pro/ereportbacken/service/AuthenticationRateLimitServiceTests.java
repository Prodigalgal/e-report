package com.pro.ereportbacken.service;

import com.pro.ereportbacken.security.ClientRequestMetadataService.RequestMetadata;
import com.pro.ereportbacken.security.SecurityHashingService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuthenticationRateLimitServiceTests {

    private final RedisFixedWindowRateLimiter rateLimiter = mock(RedisFixedWindowRateLimiter.class);
    private final AuthenticationRateLimitPolicy policy = new AuthenticationRateLimitPolicy(
            new AuthenticationRateLimitPolicy.EndpointLimits(1200, 300, 12, 30),
            new AuthenticationRateLimitPolicy.EndpointLimits(600, 300, 10, 20)
    );
    private final AuthenticationRateLimitService service = new AuthenticationRateLimitService(
            rateLimiter,
            new SecurityHashingService("test-rate-limit-context-pepper-at-least-32-bytes"),
            policy
    );
    private final RequestMetadata metadata = new RequestMetadata(
            "a".repeat(64), "b".repeat(64), "c".repeat(64));

    @Test
    void appliesIndependentGlobalIpAccountAndDeviceLoginBuckets() {
        service.requireLogin(" SharedUser ", "device-key-a", metadata);

        verify(rateLimiter).require(eq("auth:login:global"), eq("authentication-service"), eq(1), eq(600),
                eq(Duration.ofMinutes(1)), anyString());
        verify(rateLimiter).require(eq("auth:login:ip"), eq(metadata.ipHash()), eq(1), eq(300),
                eq(Duration.ofMinutes(1)), anyString());
        verify(rateLimiter).require(eq("auth:login:account"), anyString(), eq(1), eq(10),
                eq(Duration.ofMinutes(1)), anyString());
        verify(rateLimiter).require(eq("auth:login:device"), eq("device-key-a"), eq(1), eq(20),
                eq(Duration.ofMinutes(1)), anyString());
    }

    @Test
    void doesNotCreateOneSharedDeviceBucketForUnknownDevices() {
        service.requireChallenge("first-user", "unknown-device", metadata);

        verify(rateLimiter, never()).require(eq("auth:challenge:device"), anyString(), eq(1), eq(30),
                eq(Duration.ofMinutes(1)), anyString());
    }
}
