package com.pro.ereportbacken.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@Log4j2
public class RedisDistributedLockService {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisDistributedLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean runWithLock(String lockName, Duration lease, Runnable action) {
        return callWithLock(lockName, lease, () -> {
            action.run();
            return Boolean.TRUE;
        }).orElse(false);
    }

    public <T> Optional<T> callWithLock(String lockName, Duration lease, Supplier<T> action) {
        String key = "e-report:lock:v1:" + lockName.replaceAll("[^a-zA-Z0-9:_-]", "_");
        String ownerToken = UUID.randomUUID().toString();
        boolean acquired;
        try {
            acquired = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, ownerToken, lease));
        } catch (RuntimeException exception) {
            log.warn("Redis 分布式锁不可用，本轮任务跳过。lockName={}", lockName, exception);
            return Optional.empty();
        }
        if (!acquired) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(action.get());
        } finally {
            release(key, ownerToken);
        }
    }

    private void release(String key, String ownerToken) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(key), ownerToken);
        } catch (RuntimeException exception) {
            log.warn("Redis 分布式锁释放失败，将由租约自动过期。key={}", key, exception);
        }
    }
}
