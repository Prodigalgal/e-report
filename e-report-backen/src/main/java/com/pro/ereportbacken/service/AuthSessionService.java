package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.dto.WebEnvironmentDTO;
import com.pro.ereportbacken.model.pojo.AuthClientType;
import com.pro.ereportbacken.model.pojo.AuthSession;
import com.pro.ereportbacken.model.pojo.AuthSessionStatus;
import com.pro.ereportbacken.model.pojo.TrustedDevice;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.repository.AuthSessionRepository;
import com.pro.ereportbacken.security.AuthTokenService;
import com.pro.ereportbacken.security.ClientRequestMetadataService.RequestMetadata;
import com.pro.ereportbacken.security.SecurityHashingService;
import com.pro.ereportbacken.security.WebEnvironmentSignals;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuthSessionService {

    public static final String WEB_COOKIE_NAME = "EREPORT_SESSION";
    private static final Duration WEB_IDLE_TIMEOUT = Duration.ofHours(12);
    private static final Duration WEB_REMEMBERED_IDLE_TIMEOUT = Duration.ofDays(7);
    private static final Duration WEB_ABSOLUTE_TIMEOUT = Duration.ofDays(7);
    private static final Duration ANDROID_IDLE_TIMEOUT = Duration.ofDays(7);
    private static final Duration ANDROID_ABSOLUTE_TIMEOUT = Duration.ofDays(30);
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);
    private static final Duration PREVIOUS_REFRESH_RECOVERY_WINDOW = Duration.ofSeconds(30);

    private final SecureRandom secureRandom = new SecureRandom();
    private final AuthSessionRepository repository;
    private final AuthTokenService authTokenService;
    private final SecurityHashingService hashingService;
    private final TrustedDeviceService trustedDeviceService;
    private final ObjectMapper objectMapper;

    public AuthSessionService(AuthSessionRepository repository,
                              AuthTokenService authTokenService,
                              SecurityHashingService hashingService,
                              TrustedDeviceService trustedDeviceService,
                              ObjectMapper objectMapper) {
        this.repository = repository;
        this.authTokenService = authTokenService;
        this.hashingService = hashingService;
        this.trustedDeviceService = trustedDeviceService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IssuedSession createWebSession(User user,
                                          RequestMetadata metadata,
                                          WebEnvironmentDTO environment) {
        return createWebSession(user, metadata, environment, true);
    }

    @Transactional
    public IssuedSession createWebSession(User user,
                                          RequestMetadata metadata,
                                          WebEnvironmentDTO environment,
                                          boolean persistentLogin) {
        if (environment == null) {
            throw new IllegalArgumentException("WEB 环境信息不能为空。");
        }
        String secret = randomSecret();
        AuthSession session = newSession(user, AuthClientType.WEB, null, metadata, persistentLogin);
        session.setWebSecretHash(hashingService.sha256(secret));
        applyWebEnvironment(session, environment);
        session = repository.saveAndFlush(session);
        return new IssuedSession(session, session.getId() + "." + secret, null, null);
    }

    @Transactional
    public IssuedSession createAndroidSession(User user, TrustedDevice device, RequestMetadata metadata) {
        if (device == null) {
            throw new IllegalArgumentException("Android session requires a trusted device.");
        }
        String refreshSecret = randomSecret();
        AuthSession session = newSession(user, AuthClientType.ANDROID, device.getId(), metadata, false);
        session.setRefreshTokenHash(hashingService.sha256(refreshSecret));
        session = repository.saveAndFlush(session);
        AuthTokenService.IssuedAccessToken accessToken = issueAccessToken(session, user, device);
        return new IssuedSession(
                session,
                null,
                accessToken,
                session.getId() + "." + refreshSecret
        );
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthenticatedSession authenticateWeb(String cookieValue) {
        ParsedSecret parsed = parseSecret(cookieValue, "WEB 会话凭据格式不正确。");
        AuthSession session = repository.findById(parsed.sessionId()).orElseThrow(this::invalidSession);
        String submittedHash = hashingService.sha256(parsed.secret());
        if (session.getClientType() != AuthClientType.WEB
                || !hashingService.constantTimeEquals(session.getWebSecretHash(), submittedHash)) {
            throw invalidSession();
        }
        requireActive(session);
        touch(session);
        return new AuthenticatedSession(session, null, null);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthenticatedSession authenticateAndroid(String bearerToken) {
        AuthTokenService.AccessTokenIdentity identity = authTokenService.requireAccessIdentity(bearerToken);
        AuthSession session = repository.findById(identity.sessionId()).orElseThrow(this::invalidSession);
        if (session.getClientType() != AuthClientType.ANDROID
                || !session.getTenantId().equals(identity.tenantId())
                || !session.getMembershipId().equals(identity.membershipId())
                || !session.getAuthorizationVersion().equals(identity.authorizationVersion())) {
            throw invalidSession();
        }
        requireActive(session);
        TrustedDevice device = trustedDeviceService.requireActive(
                session.getTenantId(), session.getMembershipId(), identity.deviceKeyId());
        if (!device.getId().equals(session.getTrustedDeviceId())) {
            throw invalidSession();
        }
        touch(session);
        return new AuthenticatedSession(session, identity, device);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public RefreshContext requireRefreshContext(UUID sessionId, String deviceKeyId) {
        AuthSession session = repository.findById(sessionId).orElseThrow(this::invalidSession);
        if (session.getClientType() != AuthClientType.ANDROID) {
            throw invalidSession();
        }
        requireActive(session);
        TrustedDevice device = trustedDeviceService.requireActive(
                session.getTenantId(), session.getMembershipId(), deviceKeyId);
        if (!device.getId().equals(session.getTrustedDeviceId())) {
            throw invalidSession();
        }
        return new RefreshContext(session, device);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public RefreshedSession rotateRefreshToken(UUID sessionId, String submittedRefreshToken) {
        ParsedSecret parsed = parseSecret(submittedRefreshToken, "刷新凭据格式不正确。");
        if (!parsed.sessionId().equals(sessionId)) {
            throw invalidSession();
        }
        AuthSession session = repository.findByIdForUpdate(sessionId).orElseThrow(this::invalidSession);
        requireActive(session);
        if (session.getClientType() != AuthClientType.ANDROID) {
            throw invalidSession();
        }

        LocalDateTime now = UtcTime.now();
        String submittedHash = hashingService.sha256(parsed.secret());
        boolean submittedCurrent = hashingService.constantTimeEquals(session.getRefreshTokenHash(), submittedHash);
        boolean submittedPrevious = hashingService.constantTimeEquals(
                session.getPreviousRefreshTokenHash(), submittedHash);
        boolean recoverablePrevious = submittedPrevious
                && session.getRefreshRotatedAt() != null
                && session.getRefreshRotatedAt().plus(PREVIOUS_REFRESH_RECOVERY_WINDOW).isAfter(now);
        if (submittedPrevious && !recoverablePrevious) {
            revoke(session, AuthSessionStatus.COMPROMISED, "检测到已轮换 Refresh Token 重用");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "检测到登录凭据重放，当前设备会话已撤销。");
        }
        if (!submittedCurrent && !recoverablePrevious) {
            throw invalidSession();
        }

        String nextSecret = randomSecret();
        session.setPreviousRefreshTokenHash(session.getRefreshTokenHash());
        session.setRefreshTokenHash(hashingService.sha256(nextSecret));
        session.setRefreshRotatedAt(now);
        session.setLastSeenAt(now);
        session.setIdleExpiresAt(min(
                now.plus(ANDROID_IDLE_TIMEOUT),
                session.getAbsoluteExpiresAt()
        ));
        repository.saveAndFlush(session);
        return new RefreshedSession(session, session.getId() + "." + nextSecret);
    }

    public AuthTokenService.IssuedAccessToken issueAccessToken(AuthSession session,
                                                                User user,
                                                                TrustedDevice device) {
        if (!session.getMembershipId().equals(user.getId())
                || !session.getTenantId().equals(user.getTenantId())
                || !device.getId().equals(session.getTrustedDeviceId())) {
            throw invalidSession();
        }
        return authTokenService.issueAccessToken(
                session.getId(),
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                user.getAuthorizationVersion(),
                device.getKeyId()
        );
    }

    @Transactional
    public void revoke(UUID sessionId, UUID membershipId, String reason) {
        AuthSession session = repository.findByIdForUpdate(sessionId).orElseThrow(this::invalidSession);
        if (!session.getMembershipId().equals(membershipId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能撤销其他用户的会话。");
        }
        revoke(session, AuthSessionStatus.REVOKED, reason);
    }

    @Transactional
    public int revokeAllOther(UUID tenantId, UUID membershipId, UUID currentSessionId, String reason) {
        return repository.revokeActiveSessions(
                tenantId,
                membershipId,
                currentSessionId,
                AuthSessionStatus.REVOKED,
                UtcTime.now(),
                limitReason(reason)
        );
    }

    @Transactional
    public int revokeAllAfterPasswordChange(UUID sessionId,
                                            UUID tenantId,
                                            UUID membershipId) {
        AuthSession current = repository.findByIdForUpdate(sessionId).orElseThrow(this::invalidSession);
        if (!current.getTenantId().equals(tenantId) || !current.getMembershipId().equals(membershipId)) {
            throw invalidSession();
        }
        return repository.revokeActiveSessions(
                tenantId,
                membershipId,
                null,
                AuthSessionStatus.REVOKED,
                UtcTime.now(),
                "用户修改密码"
        );
    }

    @Transactional(readOnly = true)
    public Page<AuthSession> page(UUID tenantId, UUID membershipId, Pageable pageable) {
        return repository.findByTenantIdAndMembershipId(tenantId, membershipId, pageable);
    }

    @Transactional(readOnly = true)
    public long countActiveOther(UUID tenantId, UUID membershipId, UUID currentSessionId) {
        return repository.countByTenantIdAndMembershipIdAndStatusAndIdNot(
                tenantId, membershipId, AuthSessionStatus.ACTIVE, currentSessionId);
    }

    private AuthSession newSession(User user,
                                   AuthClientType clientType,
                                   UUID trustedDeviceId,
                                   RequestMetadata metadata,
                                   boolean persistentLogin) {
        LocalDateTime now = UtcTime.now();
        Duration idleTimeout = clientType == AuthClientType.WEB
                ? webIdleTimeout(persistentLogin)
                : ANDROID_IDLE_TIMEOUT;
        Duration absoluteTimeout = clientType == AuthClientType.WEB ? WEB_ABSOLUTE_TIMEOUT : ANDROID_ABSOLUTE_TIMEOUT;
        AuthSession session = new AuthSession();
        session.setTenantId(user.getTenantId());
        session.setMembershipId(user.getId());
        session.setTrustedDeviceId(trustedDeviceId);
        session.setClientType(clientType);
        session.setStatus(AuthSessionStatus.ACTIVE);
        session.setAuthorizationVersion(user.getAuthorizationVersion() == null ? 1L : user.getAuthorizationVersion());
        session.setPasswordChangeRequired(Boolean.TRUE.equals(user.getPasswordChangeRequired()));
        session.setPersistentLogin(clientType == AuthClientType.WEB && persistentLogin);
        session.setIpHash(metadata.ipHash());
        session.setUserAgentHash(metadata.userAgentHash());
        session.setCreatedAt(now);
        session.setLastSeenAt(now);
        session.setIdleExpiresAt(now.plus(idleTimeout));
        session.setAbsoluteExpiresAt(now.plus(absoluteTimeout));
        return session;
    }

    private void applyWebEnvironment(AuthSession session, WebEnvironmentDTO environment) {
        session.setWebFingerprintHash(protectedEnvironmentHash("web-environment", environment.getFingerprintHash()));
        session.setClientName(join(environment.getBrowserName(), environment.getBrowserVersion(), 120));
        session.setClientDeviceName(clean(environment.getDeviceName(), 120));
        session.setClientOsVersion(join(environment.getOsName(), environment.getOsVersion(), 120));
        session.setClientAppVersion(clean(environment.getAppVersion(), 50));
        java.util.Set<String> signals = WebEnvironmentSignals.normalize(environment.getSecuritySignals());
        session.setClientSecuritySignals(signals.stream().collect(Collectors.joining(",")));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schemaVersion", environment.getSchemaVersion());
        summary.put("fingerprintHash", protectedEnvironmentHash("web-environment", environment.getFingerprintHash()));
        summary.put("canvasHash", protectedEnvironmentHash("web-canvas", environment.getCanvasHash()));
        summary.put("webglHash", protectedEnvironmentHash("web-webgl", environment.getWebglHash()));
        summary.put("audioHash", protectedEnvironmentHash("web-audio", environment.getAudioHash()));
        summary.put("fontHash", protectedEnvironmentHash("web-fonts", environment.getFontHash()));
        summary.put("localeTag", environment.getLocaleTag());
        summary.put("timeZone", environment.getTimeZone());
        summary.put("manufacturer", environment.getManufacturer());
        summary.put("model", environment.getModel());
        summary.put("securitySignals", signals);
        try {
            session.setWebEnvironmentJson(objectMapper.writeValueAsString(summary));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("WEB 环境信息无法序列化。", exception);
        }
    }

    private String join(String left, String right, int maximumLength) {
        return clean(java.util.stream.Stream.of(left, right)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" ")), maximumLength);
    }

    private String clean(String value, int maximumLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}]", "").trim();
        return cleaned.length() <= maximumLength ? cleaned : cleaned.substring(0, maximumLength);
    }

    private String protectedEnvironmentHash(String domain, String value) {
        return StringUtils.hasText(value) ? hashingService.protectedHash(domain, value) : null;
    }

    private void requireActive(AuthSession session) {
        if (session.getStatus() != AuthSessionStatus.ACTIVE) {
            throw invalidSession();
        }
        LocalDateTime now = UtcTime.now();
        if (!session.getIdleExpiresAt().isAfter(now) || !session.getAbsoluteExpiresAt().isAfter(now)) {
            revoke(session, AuthSessionStatus.EXPIRED, "会话已超时");
            throw invalidSession();
        }
    }

    private void touch(AuthSession session) {
        LocalDateTime now = UtcTime.now();
        if (session.getLastSeenAt().plus(TOUCH_INTERVAL).isAfter(now)) {
            return;
        }
        Duration idleTimeout = session.getClientType() == AuthClientType.WEB
                ? webIdleTimeout(Boolean.TRUE.equals(session.getPersistentLogin()))
                : ANDROID_IDLE_TIMEOUT;
        session.setLastSeenAt(now);
        session.setIdleExpiresAt(min(now.plus(idleTimeout), session.getAbsoluteExpiresAt()));
        repository.save(session);
    }

    private Duration webIdleTimeout(boolean persistentLogin) {
        return persistentLogin ? WEB_REMEMBERED_IDLE_TIMEOUT : WEB_IDLE_TIMEOUT;
    }

    private void revoke(AuthSession session, AuthSessionStatus status, String reason) {
        session.setStatus(status);
        session.setRevokedAt(UtcTime.now());
        session.setRevokeReason(limitReason(reason));
        session.setWebSecretHash(null);
        session.setRefreshTokenHash(null);
        repository.saveAndFlush(session);
    }

    private ParsedSecret parseSecret(String encoded, String message) {
        if (!StringUtils.hasText(encoded)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
        }
        int separator = encoded.indexOf('.');
        if (separator <= 0 || separator == encoded.length() - 1) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
        }
        try {
            UUID sessionId = UUID.fromString(encoded.substring(0, separator));
            String secret = encoded.substring(separator + 1);
            if (secret.length() < 40 || secret.length() > 60) {
                throw new IllegalArgumentException("Invalid secret length.");
            }
            Base64.getUrlDecoder().decode(secret);
            return new ParsedSecret(sessionId, secret);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
        }
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private LocalDateTime min(LocalDateTime left, LocalDateTime right) {
        return left.isBefore(right) ? left : right;
    }

    private String limitReason(String reason) {
        String normalized = StringUtils.hasText(reason) ? reason.trim() : "用户撤销";
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    private ResponseStatusException invalidSession() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录。");
    }

    private record ParsedSecret(UUID sessionId, String secret) {
    }

    public record IssuedSession(AuthSession session,
                                String webCookieValue,
                                AuthTokenService.IssuedAccessToken accessToken,
                                String refreshToken) {
    }

    public record AuthenticatedSession(AuthSession session,
                                       AuthTokenService.AccessTokenIdentity accessTokenIdentity,
                                       TrustedDevice trustedDevice) {
    }

    public record RefreshedSession(AuthSession session, String refreshToken) {
    }

    public record RefreshContext(AuthSession session, TrustedDevice trustedDevice) {
    }
}
