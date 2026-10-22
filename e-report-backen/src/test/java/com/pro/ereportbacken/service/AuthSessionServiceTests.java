package com.pro.ereportbacken.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.TestIds;
import com.pro.ereportbacken.model.dto.WebEnvironmentDTO;
import com.pro.ereportbacken.model.pojo.AuthClientType;
import com.pro.ereportbacken.model.pojo.AuthSession;
import com.pro.ereportbacken.model.pojo.AuthSessionStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.repository.AuthSessionRepository;
import com.pro.ereportbacken.security.AuthTokenService;
import com.pro.ereportbacken.security.ClientRequestMetadataService.RequestMetadata;
import com.pro.ereportbacken.security.SecurityHashingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionServiceTests {

    private final AuthSessionRepository repository = mock(AuthSessionRepository.class);
    private final SecurityHashingService hashingService =
            new SecurityHashingService("test-session-context-pepper-at-least-32-bytes");
    private final AuthSessionService service = new AuthSessionService(
            repository,
            mock(AuthTokenService.class),
            hashingService,
            mock(TrustedDeviceService.class),
            new ObjectMapper()
    );
    private AuthSession session;
    private String currentSecret;

    @BeforeEach
    void setUp() {
        currentSecret = secret(1);
        session = activeAndroidSession();
        session.setRefreshTokenHash(hashingService.sha256(currentSecret));
        when(repository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
        when(repository.saveAndFlush(any(AuthSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rotatesCurrentRefreshToken() {
        AuthSessionService.RefreshedSession refreshed = service.rotateRefreshToken(
                session.getId(), token(currentSecret));

        assertThat(refreshed.refreshToken()).startsWith(session.getId() + ".");
        assertThat(session.getPreviousRefreshTokenHash()).isEqualTo(hashingService.sha256(currentSecret));
        assertThat(session.getRefreshTokenHash()).isNotEqualTo(hashingService.sha256(currentSecret));
        verify(repository).saveAndFlush(session);
    }

    @Test
    void recoversPreviousRefreshTokenOnlyInsideShortWindow() {
        String previousSecret = secret(2);
        session.setPreviousRefreshTokenHash(hashingService.sha256(previousSecret));
        session.setRefreshRotatedAt(LocalDateTime.now().minusSeconds(5));

        AuthSessionService.RefreshedSession recovered = service.rotateRefreshToken(
                session.getId(), token(previousSecret));

        assertThat(recovered.refreshToken()).startsWith(session.getId() + ".");
        assertThat(session.getStatus()).isEqualTo(AuthSessionStatus.ACTIVE);
    }

    @Test
    void revokesSessionWhenPreviousRefreshTokenIsReplayedOutsideWindow() {
        String previousSecret = secret(3);
        session.setPreviousRefreshTokenHash(hashingService.sha256(previousSecret));
        session.setRefreshRotatedAt(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> service.rotateRefreshToken(session.getId(), token(previousSecret)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("重放");
        assertThat(session.getStatus()).isEqualTo(AuthSessionStatus.COMPROMISED);
        assertThat(session.getRefreshTokenHash()).isNull();
    }

    @Test
    void revokesEverySessionAfterPasswordChange() {
        service.revokeAllAfterPasswordChange(
                session.getId(),
                session.getTenantId(),
                session.getMembershipId()
        );

        verify(repository).revokeActiveSessions(
                eq(session.getTenantId()),
                eq(session.getMembershipId()),
                isNull(),
                eq(AuthSessionStatus.REVOKED),
                any(LocalDateTime.class),
                eq("用户修改密码")
        );
    }

    @Test
    void rememberedWebSessionUsesSevenDayIdleWindow() {
        AuthSession created = service.createWebSession(
                webUser(), requestMetadata(), webEnvironment(), true).session();

        assertThat(created.getPersistentLogin()).isTrue();
        assertThat(Duration.between(created.getCreatedAt(), created.getIdleExpiresAt()))
                .isEqualTo(Duration.ofDays(7));
    }

    @Test
    void browserOnlyWebSessionUsesTwelveHourIdleWindow() {
        AuthSession created = service.createWebSession(
                webUser(), requestMetadata(), webEnvironment(), false).session();

        assertThat(created.getPersistentLogin()).isFalse();
        assertThat(Duration.between(created.getCreatedAt(), created.getIdleExpiresAt()))
                .isEqualTo(Duration.ofHours(12));
    }

    private AuthSession activeAndroidSession() {
        AuthSession value = new AuthSession();
        value.setId(TestIds.uuid(901));
        value.setTenantId(TestIds.uuid(902));
        value.setMembershipId(TestIds.uuid(903));
        value.setTrustedDeviceId(TestIds.uuid(904));
        value.setClientType(AuthClientType.ANDROID);
        value.setStatus(AuthSessionStatus.ACTIVE);
        value.setAuthorizationVersion(1L);
        value.setPasswordChangeRequired(false);
        value.setIpHash("a".repeat(64));
        value.setUserAgentHash("b".repeat(64));
        value.setCreatedAt(LocalDateTime.now().minusHours(1));
        value.setLastSeenAt(LocalDateTime.now().minusMinutes(1));
        value.setIdleExpiresAt(LocalDateTime.now().plusDays(1));
        value.setAbsoluteExpiresAt(LocalDateTime.now().plusDays(10));
        return value;
    }

    private User webUser() {
        User user = new User();
        user.setId(TestIds.uuid(905));
        user.setTenantId(TestIds.uuid(906));
        user.setAuthorizationVersion(1L);
        user.setPasswordChangeRequired(false);
        return user;
    }

    private RequestMetadata requestMetadata() {
        return new RequestMetadata("c".repeat(64), "d".repeat(64), "e".repeat(64));
    }

    private WebEnvironmentDTO webEnvironment() {
        WebEnvironmentDTO environment = new WebEnvironmentDTO();
        environment.setSchemaVersion(1);
        environment.setFingerprintHash("f".repeat(64));
        return environment;
    }

    private String token(String secret) {
        return session.getId() + "." + secret;
    }

    private String secret(int marker) {
        byte[] value = new byte[32];
        java.util.Arrays.fill(value, (byte) marker);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
