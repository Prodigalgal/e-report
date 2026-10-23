package com.pro.ereportbacken.notification.delivery;

import com.pro.ereportbacken.notification.MailSenderConfigurationService.MailSenderAccountConfiguration;
import com.pro.ereportbacken.notification.MailSenderOrigin;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MailAccountGateTests {
    static MailSenderAccountConfiguration sender(String username, int port) {
        return sender(username, port, 10);
    }

    static MailSenderAccountConfiguration sender(String username, int port, int intervalSeconds) {
        return new MailSenderAccountConfiguration(UUID.randomUUID(), "CUSTOM", "smtp.example.test", port,
                username, "example-password", username + "@example.test", true, true, false, false,
                "PASSWORD", "AUTO", null, null, null, "TLSv1.2 TLSv1.3", 1000, 1000, 1000,
                null, null, null, null, null, intervalSeconds, new MailSenderOrigin(UUID.randomUUID(), "测试组织", false));
    }

    @Test
    void inheritedCopiesAndAlternateTlsPortsShareTheSameAccountIdentity() {
        assertThat(MailDeliveryKeys.account(sender("shared", 465)))
                .isEqualTo(MailDeliveryKeys.account(sender("shared", 587)))
                .doesNotContain("shared", "example.test", "password");
        assertThat(MailDeliveryKeys.account(sender("other", 465)))
                .isNotEqualTo(MailDeliveryKeys.account(sender("shared", 465)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void unavailableRedisDefersWithoutFallingBackToUncoordinatedSending() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("redis unavailable"));
        var result = new MailAccountGate(redis, mock(org.springframework.scheduling.TaskScheduler.class)).acquire(sender("shared", 465));
        assertThat(result.lease()).isNull();
        assertThat(result.retryAt()).isAfter(Instant.now());
    }

    @Test
    void missingRedisReplyDefersAndDoesNotDiscardTheSmtpResultDuringRelease() {
        var gate = new MailAccountGate(mock(StringRedisTemplate.class), mock(org.springframework.scheduling.TaskScheduler.class));
        assertThat(gate.acquire(sender("shared", 465)).lease()).isNull();
        var lease = new MailAccountGate.Lease("test-key", UUID.randomUUID(), java.time.Duration.ofMinutes(2), 10);
        assertThat(gate.release(lease, MailFailurePolicy.AccountEffect.SUCCESS)).isAfter(Instant.now());
    }

    @Test
    @SuppressWarnings("unchecked")
    void busyAccountReturnsTheServerDeadlineWithoutCreatingALease() {
        var redis = mock(StringRedisTemplate.class);
        long ready = Instant.now().plusSeconds(10).toEpochMilli();
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(ready);
        var result = new MailAccountGate(redis, mock(org.springframework.scheduling.TaskScheduler.class)).acquire(sender("shared", 465));
        assertThat(result.lease()).isNull();
        assertThat(result.retryAt()).isEqualTo(Instant.ofEpochMilli(ready));
    }

    @Test
    @SuppressWarnings("unchecked")
    void grantedAccountKeepsConfiguredIntervalAndReleaseUsesTheOwnedToken() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(0L, Instant.now().plusSeconds(10).toEpochMilli());
        var gate = new MailAccountGate(redis, mock(org.springframework.scheduling.TaskScheduler.class));
        var lease = gate.acquire(sender("shared", 465)).lease();
        assertThat(lease.intervalSeconds()).isEqualTo(10);
        assertThat(lease.duration().toSeconds()).isGreaterThan(60);
        assertThat(gate.release(lease, MailFailurePolicy.AccountEffect.SUCCESS)).isAfter(Instant.now());
        verify(redis).execute(any(RedisScript.class), eq(List.of(lease.key())),
                eq(lease.token().toString()), eq("10000"), eq("SUCCESS"));
    }
}
