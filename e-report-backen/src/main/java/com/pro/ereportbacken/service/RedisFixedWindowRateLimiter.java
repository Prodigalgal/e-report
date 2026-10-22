package com.pro.ereportbacken.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Component
public class RedisFixedWindowRateLimiter {

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCRBY', KEYS[1], ARGV[1])
            if current == tonumber(ARGV[1]) then
              redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisFixedWindowRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void require(String namespace,
                        String subject,
                        int permits,
                        int maximumPermits,
                        Duration window,
                        String rejectionMessage) {
        String key = "e-report:rate-limit:v1:" + normalizeNamespace(namespace) + ":" + sha256(subject);
        try {
            Long consumed = redisTemplate.execute(
                    ACQUIRE_SCRIPT,
                    List.of(key),
                    String.valueOf(Math.max(1, permits)),
                    String.valueOf(window.toMillis())
            );
            if (consumed == null || consumed > maximumPermits) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, rejectionMessage);
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "并发控制服务暂时不可用，请稍后重试。",
                    exception
            );
        }
    }

    private String normalizeNamespace(String namespace) {
        return namespace == null ? "default" : namespace.replaceAll("[^a-zA-Z0-9:_-]", "_");
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
