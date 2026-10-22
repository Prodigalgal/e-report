package com.pro.ereportbacken.service;

import com.pro.ereportbacken.security.ConfigurationScopeContext;
import com.pro.ereportbacken.security.TenantContext;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.time.Duration;

import static com.pro.ereportbacken.config.AppConfigConstant.AMAP_API_KEY;
import static com.pro.ereportbacken.config.AppConfigConstant.AMAP_API_KEY_POOL;

@Service
@Log4j2
public class MapKeyPoolService {
    private static final Pattern KEY_SEPARATOR = Pattern.compile("[,;\\s]+");
    private static final DefaultRedisScript<Long> NEXT_CURSOR_SCRIPT = new DefaultRedisScript<>("""
            local cursor = redis.call('INCR', KEYS[1])
            if cursor == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return cursor
            """, Long.class);

    private final AppConfigService appConfigService;
    private final StringRedisTemplate redisTemplate;
    private final TenantContext tenantContext;
    private final ConfigurationScopeContext configurationScopeContext;
    private final AtomicInteger amapKeyCursor = new AtomicInteger();

    public MapKeyPoolService(AppConfigService appConfigService,
                             StringRedisTemplate redisTemplate,
                             TenantContext tenantContext,
                             ConfigurationScopeContext configurationScopeContext) {
        this.appConfigService = appConfigService;
        this.redisTemplate = redisTemplate;
        this.tenantContext = tenantContext;
        this.configurationScopeContext = configurationScopeContext;
    }

    public String nextAmapKey() {
        List<String> keyPool = configuredAmapKeyPool();
        if (keyPool.isEmpty()) {
            return "";
        }
        int index = nextCursor(keyPool.size());
        return keyPool.get(index);
    }

    private int nextCursor(int poolSize) {
        String key = "e-report:cursor:v1:amap:tenant:" + tenantContext.currentTenantIdOrDefault()
                + ":org:" + java.util.Objects.toString(configurationScopeContext.currentOrgUnitIdOrNull(), "TENANT");
        try {
            Long cursor = redisTemplate.execute(
                    NEXT_CURSOR_SCRIPT,
                    List.of(key),
                    String.valueOf(Duration.ofDays(7).toMillis())
            );
            long current = cursor == null ? 1L : cursor;
            return (int) Math.floorMod(current - 1L, poolSize);
        } catch (DataAccessException exception) {
            log.warn("Redis 高德密钥轮询游标不可用，当前实例使用本地游标降级。", exception);
            return Math.floorMod(amapKeyCursor.getAndIncrement(), poolSize);
        }
    }

    private List<String> configuredAmapKeyPool() {
        List<String> pooledKeys = splitKeys(appConfigService.getConfigValue(AMAP_API_KEY_POOL));
        if (!pooledKeys.isEmpty()) {
            return pooledKeys;
        }
        return splitKeys(appConfigService.getConfigValue(AMAP_API_KEY));
    }

    private List<String> splitKeys(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return List.of();
        }
        return KEY_SEPARATOR.splitAsStream(rawValue)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }
}
