package com.pro.ereportbacken.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists the bounded reconciliation cursor between scheduler runs. A cursor
 * is only an optimization; if Redis is unavailable the next run safely starts
 * from the beginning and no attachment is lost.
 */
@Service
@Log4j2
public class ImageRedactionReconciliationCursorStore {

    private static final String KEY_PREFIX = "e-report:image-redaction:reconcile:v1:";
    private static final Duration CURSOR_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public ImageRedactionReconciliationCursorStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<UUID> load(UUID tenantId) {
        try {
            String value = redisTemplate.opsForValue().get(key(tenantId));
            return parse(value);
        } catch (DataAccessException exception) {
            log.warn("读取图片脱敏巡检游标失败，本轮从头扫描。tenantId={}", tenantId, exception);
            return Optional.empty();
        }
    }

    public void save(UUID tenantId, UUID attachmentId) {
        if (tenantId == null || attachmentId == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(tenantId), attachmentId.toString(), CURSOR_TTL);
        } catch (DataAccessException exception) {
            log.warn("保存图片脱敏巡检游标失败，下轮可能重复扫描前置数据。tenantId={}, attachmentId={}",
                    tenantId, attachmentId, exception);
        }
    }

    public void clear(UUID tenantId) {
        if (tenantId == null) {
            return;
        }
        try {
            redisTemplate.delete(key(tenantId));
        } catch (DataAccessException exception) {
            log.warn("清理图片脱敏巡检游标失败，将由 TTL 自动过期。tenantId={}", tenantId, exception);
        }
    }

    private String key(UUID tenantId) {
        return KEY_PREFIX + tenantId;
    }

    private Optional<UUID> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            log.warn("图片脱敏巡检游标格式无效，将忽略并从头扫描。value={}", value);
            return Optional.empty();
        }
    }
}
