package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.AuthChallengeMode;
import com.pro.ereportbacken.security.ClientRequestMetadataService.RequestMetadata;
import com.pro.ereportbacken.security.SecurityHashingService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class AuthRiskService {

    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(30);
    private static final long MAX_DELAY_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final int INTERACTIVE_FAILURE_THRESHOLD = 3;
    private static final int COOLDOWN_FAILURE_THRESHOLD = INTERACTIVE_FAILURE_THRESHOLD * 2;
    private static final String KEY_PREFIX = "e-report:auth:risk:v1:";

    private static final DefaultRedisScript<List> REGISTER_FAILURE_SCRIPT = new DefaultRedisScript<>("""
            local maximum = 0
            for index = 1, 3 do
              local current = redis.call('INCR', KEYS[index])
              if current == 1 then
                redis.call('PEXPIRE', KEYS[index], ARGV[1])
              end
              if index <= 2 and current > maximum then maximum = current end
            end
            local delay = 0
            local cooldownThreshold = tonumber(ARGV[4])
            if maximum >= cooldownThreshold then
              delay = tonumber(ARGV[2]) * (2 ^ (maximum - cooldownThreshold))
              if delay > tonumber(ARGV[3]) then delay = tonumber(ARGV[3]) end
              for index = 4, 5 do
                redis.call('SET', KEYS[index], '1', 'PX', delay)
              end
            end
            return {maximum, delay}
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final SecurityHashingService hashingService;

    public AuthRiskService(StringRedisTemplate redisTemplate, SecurityHashingService hashingService) {
        this.redisTemplate = redisTemplate;
        this.hashingService = hashingService;
    }

    public RiskAssessment assess(String username, RequestMetadata metadata, String deviceSubject) {
        Subjects subjects = subjects(username, metadata, deviceSubject);
        try {
            int failures = Math.max(
                    counter(subjects.account()),
                    counter(subjects.device())
            );
            long retryAfterMillis = Math.max(
                    remaining(subjects.account()),
                    remaining(subjects.device())
            );
            if (retryAfterMillis > 0) {
                return new RiskAssessment(
                        AuthChallengeMode.COOLDOWN,
                        failures,
                        Math.max(1, (retryAfterMillis + 999) / 1000)
                );
            }
            return new RiskAssessment(
                    failures >= INTERACTIVE_FAILURE_THRESHOLD
                            ? AuthChallengeMode.INTERACTIVE
                            : AuthChallengeMode.SILENT,
                    failures,
                    0
            );
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    public RiskAssessment registerFailure(String username, RequestMetadata metadata, String deviceSubject) {
        Subjects subjects = subjects(username, metadata, deviceSubject);
        List<String> keys = List.of(
                countKey(subjects.account()), countKey(subjects.device()), networkObservationKey(subjects.network()),
                blockKey(subjects.account()), blockKey(subjects.device())
        );
        try {
            List<?> result = redisTemplate.execute(
                    REGISTER_FAILURE_SCRIPT,
                    keys,
                    String.valueOf(FAILURE_WINDOW.toMillis()),
                    String.valueOf(Duration.ofSeconds(2).toMillis()),
                    String.valueOf(MAX_DELAY_MILLIS),
                    String.valueOf(COOLDOWN_FAILURE_THRESHOLD)
            );
            if (result == null || result.size() < 2) {
                throw new DataAccessException("Login risk script returned no result.") { };
            }
            int failures = ((Number) result.get(0)).intValue();
            long delayMillis = ((Number) result.get(1)).longValue();
            return new RiskAssessment(
                    delayMillis > 0
                            ? AuthChallengeMode.COOLDOWN
                            : failures >= INTERACTIVE_FAILURE_THRESHOLD
                                    ? AuthChallengeMode.INTERACTIVE
                                    : AuthChallengeMode.SILENT,
                    failures,
                    Math.max(0, (delayMillis + 999) / 1000)
            );
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    public void registerSuccess(String username, RequestMetadata metadata, String deviceSubject) {
        Subjects subjects = subjects(username, metadata, deviceSubject);
        List<String> resetKeys = new ArrayList<>();
        resetKeys.add(countKey(subjects.account()));
        resetKeys.add(blockKey(subjects.account()));
        resetKeys.add(countKey(subjects.device()));
        resetKeys.add(blockKey(subjects.device()));
        try {
            redisTemplate.delete(resetKeys);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    private int counter(String subject) {
        String value = redisTemplate.opsForValue().get(countKey(subject));
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private long remaining(String subject) {
        Long ttl = redisTemplate.getExpire(blockKey(subject), TimeUnit.MILLISECONDS);
        return ttl == null || ttl < 0 ? 0 : ttl;
    }

    private Subjects subjects(String username, RequestMetadata metadata, String deviceSubject) {
        String normalizedUsername = normalize(username);
        String account = hashingService.protectedHash("login-account", normalizedUsername);
        String network = metadata.networkHash();
        String normalizedDevice = StringUtils.hasText(deviceSubject)
                && !"unknown-device".equals(deviceSubject)
                && !"unknown-instance".equals(deviceSubject)
                ? deviceSubject.trim()
                : "account:" + normalizedUsername;
        String device = hashingService.protectedHash(
                "login-device",
                normalizedDevice
        );
        return new Subjects(account, network, device);
    }

    private String normalize(String username) {
        return StringUtils.hasText(username) ? username.trim().toLowerCase(java.util.Locale.ROOT) : "unknown-account";
    }

    private String countKey(String subject) {
        return KEY_PREFIX + "count:" + subject;
    }

    private String blockKey(String subject) {
        return KEY_PREFIX + "block:" + subject;
    }

    private String networkObservationKey(String networkSubject) {
        return KEY_PREFIX + "network-observation:" + networkSubject;
    }

    private ResponseStatusException unavailable(DataAccessException exception) {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "登录安全服务暂时不可用，请稍后重试。",
                exception
        );
    }

    private record Subjects(String account, String network, String device) {
    }

    public record RiskAssessment(AuthChallengeMode mode, int failureCount, long retryAfterSeconds) {
    }
}
