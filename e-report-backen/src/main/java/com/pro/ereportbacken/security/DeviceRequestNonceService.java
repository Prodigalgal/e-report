package com.pro.ereportbacken.security;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

@Service
public class DeviceRequestNonceService {

    private static final Duration NONCE_TTL = Duration.ofMinutes(3);
    private final StringRedisTemplate redisTemplate;
    private final SecurityHashingService hashingService;

    public DeviceRequestNonceService(StringRedisTemplate redisTemplate, SecurityHashingService hashingService) {
        this.redisTemplate = redisTemplate;
        this.hashingService = hashingService;
    }

    public void requireUnused(UUID sessionId, String nonce) {
        String protectedNonce = hashingService.protectedHash("request-nonce", sessionId + ":" + nonce);
        String key = "e-report:auth:nonce:v1:" + protectedNonce;
        try {
            Boolean accepted = redisTemplate.opsForValue().setIfAbsent(key, "1", NONCE_TTL);
            if (!Boolean.TRUE.equals(accepted)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "检测到重复请求，请刷新后重试。");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "请求防重放服务暂时不可用，请稍后重试。",
                    exception
            );
        }
    }
}
