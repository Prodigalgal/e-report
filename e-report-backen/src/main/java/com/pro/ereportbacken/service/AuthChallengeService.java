package com.pro.ereportbacken.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.dto.AuthChallengeDTO;
import com.pro.ereportbacken.model.dto.AuthChallengeRequestDTO;
import com.pro.ereportbacken.model.dto.LoginRequestDTO;
import com.pro.ereportbacken.model.pojo.AuthChallengeMode;
import com.pro.ereportbacken.model.pojo.AuthClientType;
import com.pro.ereportbacken.security.ClientRequestMetadataService.RequestMetadata;
import com.pro.ereportbacken.security.SecurityHashingService;
import com.pro.ereportbacken.security.WebEnvironmentSignals;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthChallengeService {

    private static final int EXPIRES_IN_SECONDS = 180;
    private static final int SILENT_POW_DIFFICULTY = 3;
    private static final int INTERACTIVE_POW_DIFFICULTY = 4;
    private static final int CAPTCHA_MIN = 1;
    private static final int CAPTCHA_MAX = 20;
    private static final String POW_ALGORITHM = "SHA-256";
    private static final String KEY_PREFIX = "e-report:auth:challenge:v3:";

    private final SecureRandom secureRandom = new SecureRandom();
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthRiskService authRiskService;
    private final SecurityHashingService hashingService;

    public AuthChallengeService(StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper,
                                AuthRiskService authRiskService,
                                SecurityHashingService hashingService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.authRiskService = authRiskService;
        this.hashingService = hashingService;
    }

    public AuthChallengeDTO createChallenge(AuthChallengeRequestDTO request, RequestMetadata metadata) {
        requireClientEnvironment(request);
        String deviceSubject = deviceSubject(
                request.getDeviceKeyId(), request.getClientInstanceId(), request.getWebFingerprintHash());
        AuthRiskService.RiskAssessment risk = authRiskService.assess(request.getUsername(), metadata, deviceSubject);
        if (risk.mode() == AuthChallengeMode.COOLDOWN) {
            return new AuthChallengeDTO(
                    null, null, POW_ALGORITHM, null, 0, 0,
                    AuthChallengeMode.COOLDOWN, false, null, risk.retryAfterSeconds()
            );
        }

        String challengeId = UUID.randomUUID().toString();
        String powPrefix = randomHex(16);
        String serverNonce = randomHex(32);
        boolean interactive = risk.mode() == AuthChallengeMode.INTERACTIVE
                || WebEnvironmentSignals.requiresInteractiveChallenge(request.getWebSecuritySignals());
        AuthChallengeMode effectiveMode = interactive ? AuthChallengeMode.INTERACTIVE : AuthChallengeMode.SILENT;
        Captcha captcha = interactive ? createCaptcha() : new Captcha(null, null);
        int powDifficulty = interactive ? INTERACTIVE_POW_DIFFICULTY : SILENT_POW_DIFFICULTY;
        Instant expiresAt = Instant.now().plusSeconds(EXPIRES_IN_SECONDS);

        StoredChallenge challenge = new StoredChallenge(
                hashingService.protectedHash("challenge-account", normalizeUsername(request.getUsername())),
                request.getClientType(),
                normalizeOptional(request.getDeviceKeyId()),
                hashingService.protectedHash("challenge-client-instance", normalizeInstance(request.getClientInstanceId())),
                normalizeOptional(request.getWebFingerprintHash()),
                captcha.answer(),
                powPrefix,
                powDifficulty,
                serverNonce,
                effectiveMode,
                expiresAt
        );
        try {
            redisTemplate.opsForValue().set(
                    key(challengeId),
                    objectMapper.writeValueAsString(challenge),
                    Duration.ofSeconds(EXPIRES_IN_SECONDS)
            );
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize authentication challenge.", exception);
        }

        return new AuthChallengeDTO(
                challengeId,
                captcha.question(),
                POW_ALGORITHM,
                powPrefix,
                powDifficulty,
                EXPIRES_IN_SECONDS,
                effectiveMode,
                interactive,
                serverNonce,
                0
        );
    }

    public VerifiedChallenge verifyAndConsume(LoginRequestDTO loginRequest) {
        StoredChallenge challenge;
        try {
            String serialized = redisTemplate.opsForValue().getAndDelete(key(loginRequest.getChallengeId()));
            challenge = deserialize(serialized);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
        if (challenge == null || Instant.now().isAfter(challenge.expiresAt())) {
            throw new IllegalArgumentException("验证码或安全挑战已失效，请刷新后重试。");
        }
        requireBoundContext(challenge, loginRequest);

        if (challenge.mode() == AuthChallengeMode.INTERACTIVE
                && !isCaptchaAnswerValid(loginRequest.getCaptchaAnswer(), challenge.captchaAnswer())) {
            throw new IllegalArgumentException("数学验证码错误，请重新输入。");
        }
        if (!StringUtils.hasText(loginRequest.getPowNonce())) {
            throw new IllegalArgumentException("安全挑战校验失败，请刷新后重试。");
        }
        String hash = sha256Hex(powPayload(
                loginRequest.getChallengeId(), challenge.powPrefix(), loginRequest.getPowNonce()));
        if (!hash.startsWith("0".repeat(challenge.powDifficulty()))) {
            throw new IllegalArgumentException("安全挑战校验失败，请刷新后重试。");
        }
        return new VerifiedChallenge(challenge.serverNonce(), challenge.mode());
    }

    public String loginDeviceProofPayload(LoginRequestDTO request, String serverNonce) {
        return String.join("\n",
                "e-report-login-v1",
                request.getChallengeId(),
                serverNonce,
                sha256Hex(normalizeUsername(request.getUsername())),
                request.getDeviceContext() == null ? "" : request.getDeviceContext().getInstallationId(),
                normalizeOptional(request.getDeviceKeyId())
        );
    }

    private void requireBoundContext(StoredChallenge challenge, LoginRequestDTO request) {
        String accountHash = hashingService.protectedHash("challenge-account", normalizeUsername(request.getUsername()));
        String instanceHash = hashingService.protectedHash(
                "challenge-client-instance", normalizeInstance(request.getClientInstanceId()));
        boolean matches = hashingService.constantTimeEquals(challenge.accountHash(), accountHash)
                && challenge.clientType() == request.getClientType()
                && java.util.Objects.equals(challenge.deviceKeyId(), normalizeOptional(request.getDeviceKeyId()))
                && java.util.Objects.equals(challenge.webFingerprintHash(), webFingerprintHash(request))
                && hashingService.constantTimeEquals(challenge.clientInstanceHash(), instanceHash);
        if (!matches) {
            throw new IllegalArgumentException("登录环境已变化，请刷新安全挑战后重试。");
        }
    }

    private StoredChallenge deserialize(String serialized) {
        if (!StringUtils.hasText(serialized)) {
            return null;
        }
        try {
            return objectMapper.readValue(serialized, StoredChallenge.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private Captcha createCaptcha() {
        int left = randomCaptchaValue();
        int right = randomCaptchaValue();
        boolean subtract = secureRandom.nextBoolean();
        if (subtract && left < right) {
            int temporary = left;
            left = right;
            right = temporary;
        }
        return subtract
                ? new Captcha(left + " - " + right + " = ?", left - right)
                : new Captcha(left + " + " + right + " = ?", left + right);
    }

    private int randomCaptchaValue() {
        return secureRandom.nextInt(CAPTCHA_MAX - CAPTCHA_MIN + 1) + CAPTCHA_MIN;
    }

    private boolean isCaptchaAnswerValid(String submittedAnswer, Integer expectedAnswer) {
        if (expectedAnswer == null || !StringUtils.hasText(submittedAnswer)) {
            return false;
        }
        try {
            return Integer.parseInt(submittedAnswer.trim()) == expectedAnswer;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String powPayload(String challengeId, String powPrefix, String nonce) {
        return challengeId + ":" + powPrefix + ":" + nonce;
    }

    private String sha256Hex(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance(POW_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private String key(String challengeId) {
        return KEY_PREFIX + challengeId;
    }

    private String normalizeUsername(String username) {
        return StringUtils.hasText(username) ? username.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String normalizeInstance(String instanceId) {
        return StringUtils.hasText(instanceId) ? instanceId.trim() : "unknown-instance";
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String deviceSubject(String deviceKeyId, String clientInstanceId, String webFingerprintHash) {
        if (StringUtils.hasText(deviceKeyId)) {
            return deviceKeyId.trim();
        }
        if (StringUtils.hasText(webFingerprintHash)) {
            return webFingerprintHash.trim();
        }
        return normalizeInstance(clientInstanceId);
    }

    private String webFingerprintHash(LoginRequestDTO request) {
        return request.getWebEnvironment() == null
                ? null
                : normalizeOptional(request.getWebEnvironment().getFingerprintHash());
    }

    private void requireClientEnvironment(AuthChallengeRequestDTO request) {
        if (request.getClientType() == AuthClientType.WEB && !StringUtils.hasText(request.getWebFingerprintHash())) {
            throw new IllegalArgumentException("WEB 环境摘要不能为空。");
        }
        if (request.getClientType() == AuthClientType.ANDROID
                && (StringUtils.hasText(request.getWebFingerprintHash())
                || (request.getWebSecuritySignals() != null && !request.getWebSecuritySignals().isEmpty()))) {
            throw new IllegalArgumentException("Android 客户端不能提交 WEB 环境信息。");
        }
    }

    private ResponseStatusException unavailable(DataAccessException exception) {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "登录安全挑战服务暂时不可用，请稍后重试。",
                exception
        );
    }

    private record StoredChallenge(String accountHash,
                                   AuthClientType clientType,
                                   String deviceKeyId,
                                   String clientInstanceHash,
                                   String webFingerprintHash,
                                   Integer captchaAnswer,
                                   String powPrefix,
                                   int powDifficulty,
                                   String serverNonce,
                                   AuthChallengeMode mode,
                                   Instant expiresAt) {
    }

    private record Captcha(String question, Integer answer) {
    }

    public record VerifiedChallenge(String serverNonce, AuthChallengeMode mode) {
    }
}
