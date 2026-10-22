package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.RedisRuntimeStatusDTO;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Properties;

@Service
@Log4j2
public class RedisRuntimeStatusService {

    private static final String OBSERVATION_PREFIX = "e-report:observability:v1:";

    private final StringRedisTemplate redisTemplate;

    public RedisRuntimeStatusService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public RedisRuntimeStatusDTO status() {
        Instant collectedAt = Instant.now();
        try {
            ServerSnapshot server = redisTemplate.execute((RedisCallback<ServerSnapshot>) connection -> {
                Properties info = connection.serverCommands().info();
                return new ServerSnapshot(info, connection.serverCommands().dbSize(), connection.serverCommands().lastSave());
            });
            if (server == null) {
                throw new IllegalStateException("Redis 未返回运行状态。");
            }
            long hits = longValue(server.info(), "keyspace_hits");
            long misses = longValue(server.info(), "keyspace_misses");
            long lookups = hits + misses;
            return new RedisRuntimeStatusDTO(
                    true,
                    collectedAt,
                    server.info().getProperty("redis_version"),
                    nullableLong(server.info(), "uptime_in_seconds"),
                    nullableLong(server.info(), "connected_clients"),
                    nullableLong(server.info(), "used_memory"),
                    nullableLong(server.info(), "maxmemory"),
                    server.info().getProperty("maxmemory_policy"),
                    server.dbSize(),
                    hits,
                    misses,
                    lookups == 0 ? 0D : Math.round(hits * 10000D / lookups) / 100D,
                    "1".equals(server.info().getProperty("aof_enabled")),
                    server.info().getProperty("aof_last_write_status"),
                    server.lastSave(),
                    listSize("ready"),
                    listSize("processing"),
                    listSize("dead-letter"),
                    null
            );
        } catch (DataAccessException | IllegalStateException exception) {
            log.warn("Redis 运行状态读取失败。", exception);
            return unavailable(collectedAt);
        }
    }

    private Long listSize(String queueName) {
        Long size = redisTemplate.opsForList().size(OBSERVATION_PREFIX + queueName);
        return size == null ? 0L : size;
    }

    private long longValue(Properties properties, String key) {
        Long value = nullableLong(properties, key);
        return value == null ? 0L : value;
    }

    private Long nullableLong(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private RedisRuntimeStatusDTO unavailable(Instant collectedAt) {
        return new RedisRuntimeStatusDTO(
                false, collectedAt, null, null, null, null, null, null,
                null, null, null, null, false, null, null,
                null, null, null, "Redis 暂时不可用。"
        );
    }

    private record ServerSnapshot(Properties info, Long dbSize, Long lastSave) {
    }
}
