package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.security.ConfigurationScopeContext;
import com.pro.ereportbacken.security.TenantContext;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Log4j2
public class MailSenderRotationService {

    private static final DefaultRedisScript<Long> NEXT_CURSOR_SCRIPT = new DefaultRedisScript<>("""
            local cursor = redis.call('INCR', KEYS[1])
            if cursor == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return cursor
            """, Long.class);
    private static final Duration CURSOR_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final TenantContext tenantContext;
    private final ConfigurationScopeContext configurationScopeContext;
    private final AtomicInteger fallbackCursor = new AtomicInteger();

    public MailSenderRotationService(StringRedisTemplate redisTemplate,
                                     TenantContext tenantContext,
                                     ConfigurationScopeContext configurationScopeContext) {
        this.redisTemplate = redisTemplate;
        this.tenantContext = tenantContext;
        this.configurationScopeContext = configurationScopeContext;
    }

    public int nextIndex(int poolSize) {
        if (poolSize < 1) {
            throw new IllegalArgumentException("邮件发件池不能为空。");
        }
        String key = "e-report:cursor:v1:mail:tenant:" + tenantContext.currentTenantIdOrDefault()
                + ":org:" + Objects.toString(configurationScopeContext.currentOrgUnitIdOrNull(), "TENANT");
        try {
            Long cursor = redisTemplate.execute(
                    NEXT_CURSOR_SCRIPT,
                    List.of(key),
                    String.valueOf(CURSOR_TTL.toMillis())
            );
            long current = cursor == null ? 1L : cursor;
            return (int) Math.floorMod(current - 1L, poolSize);
        } catch (DataAccessException exception) {
            log.warn("Redis 邮件发件池轮询游标不可用，当前实例使用本地游标降级。", exception);
            return Math.floorMod(fallbackCursor.getAndIncrement(), poolSize);
        }
    }
}
