package com.pro.ereportbacken.notification.delivery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static com.pro.ereportbacken.notification.delivery.MailAccountGateTests.sender;
import static com.pro.ereportbacken.notification.delivery.MailFailurePolicy.AccountEffect.*;

@EnabledIfEnvironmentVariable(named = "E_REPORT_TEST_REDIS_PORT", matches = "[0-9]+")
class MailAccountGateRedisIntegrationTests {
    private LettuceConnectionFactory connection;
    private StringRedisTemplate redis;
    private MailAccountGate gate;
    private final String account = "mail-gate-test-" + UUID.randomUUID();
    private final List<String> keys = new ArrayList<>();

    @BeforeEach
    void connect() {
        var configuration = new RedisStandaloneConfiguration("127.0.0.1", Integer.parseInt(System.getenv("E_REPORT_TEST_REDIS_PORT")));
        String password = System.getenv("E_REPORT_TEST_REDIS_PASSWORD");
        if (password != null && !password.isBlank()) configuration.setPassword(password);
        connection = new LettuceConnectionFactory(configuration);
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);
        redis.afterPropertiesSet();
        gate = new MailAccountGate(redis, mock(org.springframework.scheduling.TaskScheduler.class));
        keys.add(key(account));
        keys.add(key(account + "-other"));
    }

    @AfterEach
    void cleanup() {
        if (redis != null) redis.delete(keys);
        if (connection != null) connection.destroy();
    }

    @Test
    void concurrentInstancesShareOneLeaseAndTheCompletedAccountCooldown() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        var attempts = java.util.stream.IntStream.range(0, 8).mapToObj(index -> CompletableFuture.supplyAsync(() -> {
            try { assertThat(start.await(5, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
            return new MailAccountGate(redis, mock(org.springframework.scheduling.TaskScheduler.class)).acquire(sender(account, index % 2 == 0 ? 465 : 587));
        })).toList();
        start.countDown();
        List<MailAccountGate.Reservation> reservations = new ArrayList<>();
        for (var attempt : attempts) reservations.add(attempt.get(10, TimeUnit.SECONDS));
        assertThat(reservations.stream().filter(result -> result.lease() != null)).hasSize(1);
        var lease = reservations.stream().map(MailAccountGate.Reservation::lease).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        Instant before = serverNow();
        Instant ready = gate.release(lease, SUCCESS);
        assertThat(ready).isBetween(before.plusSeconds(9), serverNow().plusSeconds(11));
        assertThat(gate.acquire(sender(account, 587)).retryAt()).isEqualTo(ready);
        assertThat(gate.acquire(sender(account + "-other", 465)).lease()).isNotNull();
    }

    @Test
    void transientBackoffEscalatesAuthenticationBlocksAndSuccessResetsFailures() {
        long[] delays = {60, 300, 900, 1800, 1800};
        for (long seconds : delays) {
            allowNextAttempt();
            var lease = gate.acquire(sender(account, 465)).lease();
            assertThat(lease).isNotNull();
            Instant before = serverNow();
            assertThat(gate.release(lease, TRANSIENT_FAILURE))
                    .isBetween(before.plusSeconds(seconds - 1), serverNow().plusSeconds(seconds + 1));
        }
        allowNextAttempt();
        assertThat(gate.release(gate.acquire(sender(account, 465)).lease(), AUTHENTICATION_FAILURE))
                .isAfter(serverNow().plusSeconds(1798));
        allowNextAttempt();
        gate.release(gate.acquire(sender(account, 465, 0)).lease(), SUCCESS);
        assertThat(redis.opsForHash().hasKey(key(account), "failures")).isFalse();
        assertThat(redis.opsForHash().hasKey(key(account), "blockedUntil")).isFalse();
        assertThat(gate.acquire(sender(account, 465, 0)).lease()).isNotNull();
    }

    @Test
    void expiredLeaseCanBeReclaimedAndOldOwnerCannotReleaseNewOwner() {
        var old = gate.acquire(sender(account, 465, 0)).lease();
        redis.opsForHash().put(key(account), "leaseUntil", "1");
        assertThat(gate.renew(old)).isTrue();
        assertThat(Long.parseLong((String) redis.opsForHash().get(key(account), "leaseUntil")))
                .isGreaterThan(serverNow().plusSeconds(60).toEpochMilli());
        redis.opsForHash().put(key(account), "leaseUntil", "0");
        var current = gate.acquire(sender(account, 587, 0)).lease();
        assertThat(current).isNotNull();
        assertThat(gate.renew(old)).isFalse();
        gate.release(old, SUCCESS);
        assertThat(redis.opsForHash().get(key(account), "token")).isEqualTo(current.token().toString());
        assertThat(gate.acquire(sender(account, 465)).lease()).isNull();
        gate.release(current, SUCCESS);
        assertThat(gate.acquire(sender(account, 465, 0)).lease()).isNotNull();
    }

    private void allowNextAttempt() {
        // 只推进本测试的独立账号期限，避免真实等待最长30分钟。
        redis.opsForHash().putAll(key(account), java.util.Map.of("nextAllowed", "0", "blockedUntil", "0"));
    }

    private String key(String username) {
        return "e-report:mail:account:v1:" + MailDeliveryKeys.account(sender(username, 465));
    }

    private Instant serverNow() {
        Long milliseconds = redis.execute((RedisCallback<Long>) connection -> connection.serverCommands().time());
        return Instant.ofEpochMilli(java.util.Objects.requireNonNull(milliseconds));
    }
}
