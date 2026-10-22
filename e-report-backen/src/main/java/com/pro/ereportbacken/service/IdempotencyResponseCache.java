package com.pro.ereportbacken.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@Log4j2
public class IdempotencyResponseCache {

    private static final String KEY_PREFIX = "e-report:idempotency-response:v1:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyResponseCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<CachedResponse> find(UUID tenantId, UUID membershipId, String endpoint, String idempotencyKey) {
        try {
            String serialized = redisTemplate.opsForValue().get(key(tenantId, membershipId, endpoint, idempotencyKey));
            if (serialized == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(serialized, CachedResponse.class));
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("幂等响应缓存读取失败，将回源数据库。tenantId={}, membershipId={}, endpoint={}",
                    tenantId, membershipId, endpoint, exception);
            return Optional.empty();
        }
    }

    public void put(UUID tenantId,
                    UUID membershipId,
                    String endpoint,
                    String idempotencyKey,
                    String requestHash,
                    String responseBody) {
        try {
            redisTemplate.opsForValue().set(
                    key(tenantId, membershipId, endpoint, idempotencyKey),
                    objectMapper.writeValueAsString(new CachedResponse(requestHash, responseBody)),
                    TTL
            );
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("幂等响应缓存写入失败，数据库幂等记录仍然有效。tenantId={}, membershipId={}, endpoint={}",
                    tenantId, membershipId, endpoint, exception);
        }
    }

    private String key(UUID tenantId, UUID membershipId, String endpoint, String idempotencyKey) {
        String scope = tenantId + "\n" + membershipId + "\n" + endpoint + "\n" + idempotencyKey;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    public record CachedResponse(String requestHash, String responseBody) {
    }
}
