package com.pro.ereportbacken.service;

public record AuthenticationRateLimitPolicy(EndpointLimits challenge, EndpointLimits login) {

    public AuthenticationRateLimitPolicy {
        challenge = requireValid(challenge, "challenge");
        login = requireValid(login, "login");
    }

    private static EndpointLimits requireValid(EndpointLimits limits, String name) {
        if (limits == null
                || limits.globalPerMinute() <= 0
                || limits.ipPerMinute() <= 0
                || limits.accountPerMinute() <= 0
                || limits.devicePerMinute() <= 0) {
            throw new IllegalArgumentException("Authentication rate limits must be positive: " + name);
        }
        return limits;
    }

    public record EndpointLimits(int globalPerMinute,
                                 int ipPerMinute,
                                 int accountPerMinute,
                                 int devicePerMinute) {
    }
}
