package com.pro.ereportbacken.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.dto.AuthChallengeDTO;
import com.pro.ereportbacken.model.dto.AuthChallengeRequestDTO;
import com.pro.ereportbacken.model.dto.LoginRequestDTO;
import com.pro.ereportbacken.model.pojo.AuthChallengeMode;
import com.pro.ereportbacken.model.pojo.AuthClientType;
import com.pro.ereportbacken.security.ClientRequestMetadataService.RequestMetadata;
import com.pro.ereportbacken.security.SecurityHashingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthChallengeServiceTests {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final AuthRiskService authRiskService = mock(AuthRiskService.class);
    private final AuthChallengeService service = new AuthChallengeService(
            redisTemplate,
            new ObjectMapper().findAndRegisterModules(),
            authRiskService,
            new SecurityHashingService("test-challenge-context-pepper-at-least-32-bytes")
    );
    private final RequestMetadata metadata = new RequestMetadata(
            "a".repeat(64), "b".repeat(64), "c".repeat(64));

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(authRiskService.assess(anyString(), any(), anyString()))
                .thenReturn(new AuthRiskService.RiskAssessment(AuthChallengeMode.SILENT, 0, 0));
    }

    @Test
    void silentChallengeIsBoundToContextAndConsumedOnce() {
        AuthChallengeRequestDTO request = challengeRequest();
        ArgumentCaptor<String> serialized = ArgumentCaptor.forClass(String.class);
        AuthChallengeDTO challenge = service.createChallenge(request, metadata);
        org.mockito.Mockito.verify(values).set(
                anyString(), serialized.capture(), any(java.time.Duration.class));
        when(values.getAndDelete(anyString())).thenReturn(serialized.getValue(), (String) null);

        LoginRequestDTO login = loginRequest(challenge, solve(challenge));
        AuthChallengeService.VerifiedChallenge verified = service.verifyAndConsume(login);

        assertThat(verified.mode()).isEqualTo(AuthChallengeMode.SILENT);
        assertThat(verified.serverNonce()).isNotBlank();
        assertThatThrownBy(() -> service.verifyAndConsume(login))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("失效");
    }

    @Test
    void keepsChallengeValidWhenMobileNetworkChanges() {
        AuthChallengeDTO challenge = service.createChallenge(challengeRequest(), metadata);
        ArgumentCaptor<String> serialized = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(values).set(
                anyString(), serialized.capture(), any(java.time.Duration.class));
        when(values.getAndDelete(anyString())).thenReturn(serialized.getValue());
        LoginRequestDTO login = loginRequest(challenge, solve(challenge));
        RequestMetadata changed = new RequestMetadata(
                metadata.ipHash(), "d".repeat(64), metadata.userAgentHash());

        AuthChallengeService.VerifiedChallenge verified = service.verifyAndConsume(login);

        assertThat(changed.networkHash()).isNotEqualTo(metadata.networkHash());
        assertThat(verified.mode()).isEqualTo(AuthChallengeMode.SILENT);
    }

    @Test
    void automationSignalEscalatesWebChallengeToInteractiveCaptcha() {
        AuthChallengeRequestDTO request = challengeRequest();
        request.setWebSecuritySignals(java.util.Set.of("WEBDRIVER"));

        AuthChallengeDTO challenge = service.createChallenge(request, metadata);

        assertThat(challenge.getMode()).isEqualTo(AuthChallengeMode.INTERACTIVE);
        assertThat(challenge.isCaptchaRequired()).isTrue();
        assertThat(challenge.getCaptchaQuestion()).isNotBlank();
    }

    @Test
    void rejectsLoginWhenWebEnvironmentChangesAfterChallenge() {
        AuthChallengeDTO challenge = service.createChallenge(challengeRequest(), metadata);
        ArgumentCaptor<String> serialized = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(values).set(anyString(), serialized.capture(), any(java.time.Duration.class));
        when(values.getAndDelete(anyString())).thenReturn(serialized.getValue());
        LoginRequestDTO login = loginRequest(challenge, solve(challenge));
        login.getWebEnvironment().setFingerprintHash("f".repeat(64));

        assertThatThrownBy(() -> service.verifyAndConsume(login))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("环境已变化");
    }

    private AuthChallengeRequestDTO challengeRequest() {
        AuthChallengeRequestDTO request = new AuthChallengeRequestDTO();
        request.setUsername("dispatcher");
        request.setClientType(AuthClientType.WEB);
        request.setClientInstanceId("browser-instance-1");
        request.setWebFingerprintHash("e".repeat(64));
        return request;
    }

    private LoginRequestDTO loginRequest(AuthChallengeDTO challenge, String powNonce) {
        LoginRequestDTO login = new LoginRequestDTO();
        login.setUsername("dispatcher");
        login.setPassword("not-used-in-challenge-test");
        login.setChallengeId(challenge.getChallengeId());
        login.setPowNonce(powNonce);
        login.setClientType(AuthClientType.WEB);
        login.setClientInstanceId("browser-instance-1");
        com.pro.ereportbacken.model.dto.WebEnvironmentDTO environment =
                new com.pro.ereportbacken.model.dto.WebEnvironmentDTO();
        environment.setSchemaVersion(1);
        environment.setFingerprintHash("e".repeat(64));
        login.setWebEnvironment(environment);
        return login;
    }

    private String solve(AuthChallengeDTO challenge) {
        String target = "0".repeat(challenge.getPowDifficulty());
        for (long nonce = 0; nonce < 10_000_000L; nonce++) {
            String candidate = String.valueOf(nonce);
            if (sha256(challenge.getChallengeId() + ":" + challenge.getPowPrefix() + ":" + candidate)
                    .startsWith(target)) {
                return candidate;
            }
        }
        throw new IllegalStateException("测试未在上限内找到 PoW nonce。");
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
