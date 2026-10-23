package com.pro.ereportbacken.notification.delivery;

import com.pro.ereportbacken.notification.MailSenderConfigurationService.MailSenderAccountConfiguration;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Log4j2
public class MailAccountGate {
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            local clock = redis.call('TIME')
            local now = clock[1] * 1000 + math.floor(clock[2] / 1000)
            local busy = tonumber(redis.call('HGET', KEYS[1], 'leaseUntil') or '0')
            if busy > now then return math.min(busy, now + 1000) end
            local ready = math.max(tonumber(redis.call('HGET', KEYS[1], 'nextAllowed') or '0'),
                tonumber(redis.call('HGET', KEYS[1], 'blockedUntil') or '0'))
            if ready > now then return ready end
            redis.call('HSET', KEYS[1], 'token', ARGV[1], 'leaseUntil', now + tonumber(ARGV[2]))
            redis.call('PEXPIRE', KEYS[1], 86400000)
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            local clock = redis.call('TIME')
            local now = clock[1] * 1000 + math.floor(clock[2] / 1000)
            if redis.call('HGET', KEYS[1], 'token') ~= ARGV[1] then return now + 1000 end
            local ready = now + tonumber(ARGV[2])
            if ARGV[3] == 'SUCCESS' then
                redis.call('HDEL', KEYS[1], 'failures', 'blockedUntil')
            elseif ARGV[3] == 'TRANSIENT_FAILURE' or ARGV[3] == 'AUTHENTICATION_FAILURE' then
                local failures = redis.call('HINCRBY', KEYS[1], 'failures', 1)
                local delays = {60000, 300000, 900000, 1800000}
                local delay = delays[math.min(failures, 4)]
                if ARGV[3] == 'AUTHENTICATION_FAILURE' then delay = 1800000 end
                ready = math.max(ready, now + delay)
                redis.call('HSET', KEYS[1], 'blockedUntil', ready)
            end
            redis.call('HSET', KEYS[1], 'nextAllowed', ready)
            redis.call('HDEL', KEYS[1], 'token', 'leaseUntil')
            redis.call('PEXPIRE', KEYS[1], 86400000)
            return ready
            """, Long.class);
    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'token') ~= ARGV[1] then return 0 end
            local clock = redis.call('TIME')
            local now = clock[1] * 1000 + math.floor(clock[2] / 1000)
            redis.call('HSET', KEYS[1], 'leaseUntil', now + tonumber(ARGV[2]))
            redis.call('PEXPIRE', KEYS[1], 86400000)
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;
    private final TaskScheduler scheduler;

    public MailAccountGate(StringRedisTemplate redis, TaskScheduler scheduler) {
        this.redis = redis;
        this.scheduler = scheduler;
    }

    public LeaseRenewal maintain(Lease lease, Runnable renewCheckpoint) {
        Duration interval = lease.duration().dividedBy(3);
        var scheduled = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (renew(lease)) renewCheckpoint.run();
                else log.error("邮件账号续租未取得执行权，请检查对应发送记录。senderKey={}", lease.key());
            } catch (RuntimeException failure) {
                log.error("邮件发送续租失败，请检查对应发送记录。senderKey={}", lease.key(), failure);
            }
        }, Instant.now().plus(interval), interval);
        return () -> scheduled.cancel(false);
    }

    boolean renew(Lease lease) {
        return Long.valueOf(1).equals(redis.execute(RENEW, List.of(lease.key()),
                lease.token().toString(), String.valueOf(lease.duration().toMillis())));
    }

    public Reservation acquire(MailSenderAccountConfiguration sender) {
        String key = "e-report:mail:account:v1:" + MailDeliveryKeys.account(sender);
        UUID token = UUID.randomUUID();
        Duration duration = Duration.ofMillis(sender.connectionTimeoutMs() + 4L * sender.readTimeoutMs()
                + sender.writeTimeoutMs() + 60_000L);
        try {
            Long ready = redis.execute(ACQUIRE, List.of(key), token.toString(), String.valueOf(duration.toMillis()));
            if (ready == null) {
                log.warn("邮件协调服务未返回领取结果，已暂缓发送。senderKey={}", MailDeliveryKeys.account(sender));
                return new Reservation(null, Instant.now().plusSeconds(30));
            }
            return ready == 0 ? new Reservation(new Lease(key, token, duration, sender.sendIntervalSeconds()), null)
                    : new Reservation(null, Instant.ofEpochMilli(ready));
        } catch (DataAccessException failure) {
            log.warn("邮件账号协调服务不可用，已暂缓发送。senderKey={}", MailDeliveryKeys.account(sender), failure);
            return new Reservation(null, Instant.now().plusSeconds(30));
        }
    }

    public Instant release(Lease lease, MailFailurePolicy.AccountEffect effect) {
        try {
            Long ready = redis.execute(RELEASE, List.of(lease.key()), lease.token().toString(),
                    String.valueOf(lease.intervalSeconds() * 1000L), effect.name());
            if (ready == null) {
                log.error("邮件协调服务未返回释放结果，保留租约等待自动到期。senderKey={}", lease.key());
                return Instant.now().plus(lease.duration());
            }
            return Instant.ofEpochMilli(ready);
        } catch (DataAccessException failure) {
            log.error("邮件账号租约释放失败，保留租约等待自动到期。senderKey={}", lease.key(), failure);
            return Instant.now().plus(lease.duration());
        }
    }

    public record Lease(String key, UUID token, Duration duration, int intervalSeconds) {
    }

    public record Reservation(Lease lease, Instant retryAt) {
    }

    @FunctionalInterface
    public interface LeaseRenewal extends AutoCloseable {
        @Override void close();
    }
}
