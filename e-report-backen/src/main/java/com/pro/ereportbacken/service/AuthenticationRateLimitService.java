package com.pro.ereportbacken.service;

import com.pro.ereportbacken.security.ClientRequestMetadataService.RequestMetadata;
import com.pro.ereportbacken.security.SecurityHashingService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;

@Service
public class AuthenticationRateLimitService {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String GLOBAL_SUBJECT = "authentication-service";

    private final RedisFixedWindowRateLimiter rateLimiter;
    private final SecurityHashingService hashingService;
    private final AuthenticationRateLimitPolicy policy;

    public AuthenticationRateLimitService(RedisFixedWindowRateLimiter rateLimiter,
                                          SecurityHashingService hashingService,
                                          AuthenticationRateLimitPolicy policy) {
        this.rateLimiter = rateLimiter;
        this.hashingService = hashingService;
        this.policy = policy;
    }

    public void requireChallenge(String username, String deviceSubject, RequestMetadata metadata) {
        require("challenge", username, deviceSubject, metadata, policy.challenge(), "验证码获取过于频繁，请稍后重试。");
    }

    public void requireLogin(String username, String deviceSubject, RequestMetadata metadata) {
        require("login", username, deviceSubject, metadata, policy.login(), "登录尝试过于频繁，请稍后重试。");
    }

    private void require(String operation,
                         String username,
                         String deviceSubject,
                         RequestMetadata metadata,
                         AuthenticationRateLimitPolicy.EndpointLimits limits,
                         String rejectionMessage) {
        rateLimiter.require(namespace(operation, "global"), GLOBAL_SUBJECT, 1,
                limits.globalPerMinute(), WINDOW, rejectionMessage);
        rateLimiter.require(namespace(operation, "ip"), metadata.ipHash(), 1,
                limits.ipPerMinute(), WINDOW, rejectionMessage);
        rateLimiter.require(namespace(operation, "account"), accountSubject(username), 1,
                limits.accountPerMinute(), WINDOW, rejectionMessage);
        if (isStableDeviceSubject(deviceSubject)) {
            rateLimiter.require(namespace(operation, "device"), deviceSubject.trim(), 1,
                    limits.devicePerMinute(), WINDOW, rejectionMessage);
        }
    }

    private String accountSubject(String username) {
        String normalized = StringUtils.hasText(username)
                ? username.trim().toLowerCase(Locale.ROOT)
                : "unknown-account";
        return hashingService.protectedHash("auth-rate-account", normalized);
    }

    private boolean isStableDeviceSubject(String deviceSubject) {
        return StringUtils.hasText(deviceSubject)
                && !"unknown-device".equals(deviceSubject)
                && !"unknown-instance".equals(deviceSubject);
    }

    private String namespace(String operation, String dimension) {
        return "auth:" + operation + ":" + dimension;
    }
}
