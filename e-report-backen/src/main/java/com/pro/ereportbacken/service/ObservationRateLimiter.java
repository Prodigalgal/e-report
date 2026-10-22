package com.pro.ereportbacken.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Component
public class ObservationRateLimiter {

    private static final int AUTHENTICATED_EVENTS_PER_MINUTE = 1000;
    private static final int ANONYMOUS_EVENTS_PER_MINUTE = 200;
    private static final long MAX_REQUEST_BYTES = 512 * 1024;

    private final RedisFixedWindowRateLimiter rateLimiter;

    public ObservationRateLimiter(RedisFixedWindowRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public void check(HttpServletRequest request, int eventCount, boolean authenticated) {
        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_REQUEST_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "运行诊断上报内容不能超过512KB。");
        }
        int limit = authenticated ? AUTHENTICATED_EVENTS_PER_MINUTE : ANONYMOUS_EVENTS_PER_MINUTE;
        rateLimiter.require(
                authenticated ? "observability:authenticated" : "observability:anonymous",
                request.getRemoteAddr(),
                eventCount,
                limit,
                Duration.ofMinutes(1),
                "运行诊断上报过于频繁，请稍后重试。"
        );
    }
}
