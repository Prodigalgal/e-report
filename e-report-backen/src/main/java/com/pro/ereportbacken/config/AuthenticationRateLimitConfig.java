package com.pro.ereportbacken.config;

import com.pro.ereportbacken.service.AuthenticationRateLimitPolicy;
import com.pro.ereportbacken.service.AuthenticationHashWorkloadGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AuthenticationRateLimitConfig {

    @Bean
    public AuthenticationRateLimitPolicy authenticationRateLimitPolicy(
            @Value("${app.security.auth-rate-limit.challenge.global-per-minute:1200}") int challengeGlobal,
            @Value("${app.security.auth-rate-limit.challenge.ip-per-minute:300}") int challengeIp,
            @Value("${app.security.auth-rate-limit.challenge.account-per-minute:12}") int challengeAccount,
            @Value("${app.security.auth-rate-limit.challenge.device-per-minute:30}") int challengeDevice,
            @Value("${app.security.auth-rate-limit.login.global-per-minute:600}") int loginGlobal,
            @Value("${app.security.auth-rate-limit.login.ip-per-minute:300}") int loginIp,
            @Value("${app.security.auth-rate-limit.login.account-per-minute:10}") int loginAccount,
            @Value("${app.security.auth-rate-limit.login.device-per-minute:20}") int loginDevice) {
        return new AuthenticationRateLimitPolicy(
                new AuthenticationRateLimitPolicy.EndpointLimits(
                        challengeGlobal, challengeIp, challengeAccount, challengeDevice),
                new AuthenticationRateLimitPolicy.EndpointLimits(
                        loginGlobal, loginIp, loginAccount, loginDevice)
        );
    }

    @Bean
    public AuthenticationHashWorkloadGuard authenticationHashWorkloadGuard(
            @Value("${app.security.authentication-hash.maximum-concurrent:4}") int maximumConcurrent,
            @Value("${app.security.authentication-hash.maximum-queued:32}") int maximumQueued,
            @Value("${app.security.authentication-hash.wait-timeout-seconds:5}") long waitTimeoutSeconds,
            @Value("${app.security.authentication-hash.retry-after-seconds:2}") long retryAfterSeconds) {
        return new AuthenticationHashWorkloadGuard(
                maximumConcurrent,
                maximumQueued,
                Duration.ofSeconds(waitTimeoutSeconds),
                Duration.ofSeconds(retryAfterSeconds)
        );
    }
}
