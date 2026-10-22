package com.pro.ereportbacken.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisDistributedLockServiceTests {

    @Test
    void propagatesBusinessFailureAndStillReleasesLease() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        RedisDistributedLockService lockService = new RedisDistributedLockService(redisTemplate);

        assertThatThrownBy(() -> lockService.runWithLock(
                "test", Duration.ofSeconds(10), () -> { throw new IllegalStateException("business failure"); }
        )).isInstanceOf(IllegalStateException.class).hasMessage("business failure");

        verify(redisTemplate).execute(any(), any(), anyString());
    }

    @Test
    void doesNotRunActionWhenLeaseIsOwnedElsewhere() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        RedisDistributedLockService lockService = new RedisDistributedLockService(redisTemplate);

        boolean executed = lockService.runWithLock("test", Duration.ofSeconds(10), () -> {
            throw new AssertionError("must not run");
        });

        assertThat(executed).isFalse();
    }
}
