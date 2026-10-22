package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.AuthChallengeMode;
import com.pro.ereportbacken.security.ClientRequestMetadataService.RequestMetadata;
import com.pro.ereportbacken.security.SecurityHashingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthRiskServiceTests {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final AuthRiskService service = new AuthRiskService(
            redisTemplate,
            new SecurityHashingService("test-auth-risk-context-pepper-at-least-32-bytes")
    );
    private final RequestMetadata metadata = new RequestMetadata(
            "a".repeat(64), "b".repeat(64), "c".repeat(64));

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(-2L);
    }

    @Test
    void escalatesAfterThreeFailuresAndKeepsThreeAttemptsBeforeCooldown() {
        when(values.get(anyString())).thenReturn(null);
        assertThat(service.assess("dispatcher", metadata, "device-a").mode())
                .isEqualTo(AuthChallengeMode.SILENT);

        when(values.get(anyString())).thenReturn("2");
        assertThat(service.assess("dispatcher", metadata, "device-a").mode())
                .isEqualTo(AuthChallengeMode.SILENT);

        when(values.get(anyString())).thenReturn("3");
        assertThat(service.assess("dispatcher", metadata, "device-a").mode())
                .isEqualTo(AuthChallengeMode.INTERACTIVE);
    }

    @Test
    void assessesOnlyAccountAndDeviceInsteadOfSharedCarrierNetwork() {
        when(values.get(anyString())).thenReturn(null);

        assertThat(service.assess("dispatcher", metadata, "device-a").mode())
                .isEqualTo(AuthChallengeMode.SILENT);

        verify(values, times(2)).get(anyString());
        verify(redisTemplate, times(2)).getExpire(anyString(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void reportsCooldownUsingLongestRemainingSubjectBlock() {
        when(values.get(anyString())).thenReturn("5");
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(1_000L, 8_200L);

        AuthRiskService.RiskAssessment risk = service.assess("dispatcher", metadata, "device-a");

        assertThat(risk.mode()).isEqualTo(AuthChallengeMode.COOLDOWN);
        assertThat(risk.retryAfterSeconds()).isEqualTo(9);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void keepsInteractiveChallengeAvailableThroughTheFifthFailure() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(),
                any(), any(), any(), any())).thenReturn((List) List.of(5L, 0L));

        AuthRiskService.RiskAssessment risk = service.registerFailure(
                "dispatcher", metadata, "device-a");

        assertThat(risk.mode()).isEqualTo(AuthChallengeMode.INTERACTIVE);
        assertThat(risk.failureCount()).isEqualTo(5);
        assertThat(risk.retryAfterSeconds()).isZero();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void registerFailureUsesSixFailureCooldownThresholdAndSuccessClearsAccountDeviceOnly() {
        when(redisTemplate.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                anyList(), any(), any(), any(), any())).thenReturn((List) List.of(6L, 2_000L));

        AuthRiskService.RiskAssessment risk = service.registerFailure(
                "dispatcher", metadata, "device-a");
        service.registerSuccess("dispatcher", metadata, "device-a");

        assertThat(risk.mode()).isEqualTo(AuthChallengeMode.COOLDOWN);
        assertThat(risk.failureCount()).isEqualTo(6);
        assertThat(risk.retryAfterSeconds()).isEqualTo(2);

        ArgumentCaptor<Object> arguments = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<RedisScript> script = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(script.capture(), anyList(),
                arguments.capture(), arguments.capture(), arguments.capture(), arguments.capture());
        assertThat(arguments.getAllValues()).containsExactly("1800000", "2000", "300000", "6");
        assertThat(script.getValue().getScriptAsString()).contains("tonumber(ARGV[4])");
        verify(redisTemplate).delete(anyList());
    }
}
