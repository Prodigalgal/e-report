package com.pro.ereportbacken.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@Log4j2
public class AppReleaseSyncOperationStore {

    private static final String KEY_PREFIX = "e-report:app-release:r2-sync:v1:";
    private static final Duration SYNC_LEASE = Duration.ofHours(2);
    private static final Duration FAILURE_RETENTION = Duration.ofHours(6);
    private static final DefaultRedisScript<Long> START_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current and string.sub(current, 1, 7) == 'SYNCING' then
              return 0
            end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public AppReleaseSyncOperationStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryStart(UUID releaseId, String message) {
        try {
            Long started = redisTemplate.execute(
                    START_SCRIPT,
                    List.of(key(releaseId)),
                    serialize(new SyncOperation("SYNCING", message)),
                    String.valueOf(SYNC_LEASE.toMillis())
            );
            return Long.valueOf(1).equals(started);
        } catch (DataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "安装包同步并发控制暂时不可用，请稍后重试。",
                    exception
            );
        }
    }

    public Map<UUID, SyncOperation> findAll(Collection<UUID> releaseIds) {
        if (releaseIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(releaseIds);
        List<String> keys = ids.stream().map(this::key).toList();
        try {
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                return Map.of();
            }
            Map<UUID, SyncOperation> result = new HashMap<>();
            for (int index = 0; index < Math.min(ids.size(), values.size()); index++) {
                UUID releaseId = ids.get(index);
                deserialize(values.get(index)).ifPresent(operation -> result.put(releaseId, operation));
            }
            return Map.copyOf(result);
        } catch (DataAccessException exception) {
            log.warn("读取安装包同步状态失败，管理页将继续展示持久化发布状态。", exception);
            return Map.of();
        }
    }

    public void markFailed(UUID releaseId, String message) {
        try {
            redisTemplate.opsForValue().set(
                    key(releaseId),
                    serialize(new SyncOperation("SYNC_FAILED", message)),
                    FAILURE_RETENTION
            );
        } catch (DataAccessException exception) {
            log.warn("保存安装包同步失败状态时 Redis 不可用。releaseId={}", releaseId, exception);
        }
    }

    public void clear(UUID releaseId) {
        try {
            redisTemplate.delete(key(releaseId));
        } catch (DataAccessException exception) {
            log.warn("清理安装包同步状态时 Redis 不可用，状态将由租约自动过期。releaseId={}", releaseId, exception);
        }
    }

    private String key(UUID releaseId) {
        return KEY_PREFIX + releaseId;
    }

    private String serialize(SyncOperation operation) {
        return operation.status() + "\n" + operation.message().replace('\n', ' ');
    }

    private Optional<SyncOperation> deserialize(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String[] parts = value.split("\n", 2);
        return Optional.of(new SyncOperation(parts[0], parts.length > 1 ? parts[1] : ""));
    }

    public record SyncOperation(String status, String message) {
    }
}
