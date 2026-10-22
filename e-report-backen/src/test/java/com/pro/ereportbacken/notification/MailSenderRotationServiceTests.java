package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.security.ConfigurationScopeContext;
import com.pro.ereportbacken.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MailSenderRotationServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void wrapsRedisCursorAcrossTheSenderPool() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        TenantContext tenantContext = mock(TenantContext.class);
        ConfigurationScopeContext scopeContext = mock(ConfigurationScopeContext.class);
        when(tenantContext.currentTenantIdOrDefault()).thenReturn(UUID.randomUUID());
        when(scopeContext.currentOrgUnitIdOrNull()).thenReturn(UUID.randomUUID());
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(12L);
        MailSenderRotationService service = new MailSenderRotationService(
                redisTemplate, tenantContext, scopeContext);

        assertThat(service.nextIndex(5)).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fallsBackToLocalRoundRobinWhenRedisIsUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        TenantContext tenantContext = mock(TenantContext.class);
        ConfigurationScopeContext scopeContext = mock(ConfigurationScopeContext.class);
        when(tenantContext.currentTenantIdOrDefault()).thenReturn(UUID.randomUUID());
        when(scopeContext.currentOrgUnitIdOrNull()).thenReturn(null);
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("redis unavailable"));
        MailSenderRotationService service = new MailSenderRotationService(
                redisTemplate, tenantContext, scopeContext);

        assertThat(List.of(service.nextIndex(3), service.nextIndex(3))).containsExactly(0, 1);
    }
}
