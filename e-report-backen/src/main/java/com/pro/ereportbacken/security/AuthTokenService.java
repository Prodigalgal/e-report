package com.pro.ereportbacken.security;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Service
public class AuthTokenService {

    private static final String TOKEN_VERSION_V3 = "v3";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final byte[] secret;
    private final long ttlSeconds;

    public AuthTokenService(@Value("${app.auth-token.secret}") String configuredSecret,
                            @Value("${app.auth-token.ttl-seconds:900}") long ttlSeconds) {
        if (!StringUtils.hasText(configuredSecret)) {
            throw new IllegalStateException("app.auth-token.secret must be configured.");
        }
        this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) {
            throw new IllegalStateException("app.auth-token.secret must contain at least 32 bytes.");
        }
        this.ttlSeconds = ttlSeconds;
    }

    public IssuedAccessToken issueAccessToken(UUID sessionId,
                                               UUID membershipId,
                                               UUID tenantId,
                                               String username,
                                               Long authorizationVersion,
                                               String deviceKeyId) {
        if (sessionId == null || membershipId == null || tenantId == null
                || !StringUtils.hasText(username) || !StringUtils.hasText(deviceKeyId)) {
            throw new IllegalArgumentException("Session, membership, tenant, username and device key must be provided.");
        }
        try {
            long issuedAt = Instant.now().getEpochSecond();
            long expiresAt = issuedAt + ttlSeconds;
            TokenPayloadV3 payload = new TokenPayloadV3(
                    sessionId,
                    membershipId,
                    tenantId,
                    username,
                    authorizationVersion == null ? 1L : authorizationVersion,
                    deviceKeyId,
                    issuedAt,
                    expiresAt
            );
            String encodedPayload = base64UrlEncode(objectMapper.writeValueAsBytes(payload));
            String unsignedToken = TOKEN_VERSION_V3 + "." + encodedPayload;
            return new IssuedAccessToken(unsignedToken + "." + sign(unsignedToken), ttlSeconds, expiresAt);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to issue access token.", exception);
        }
    }

    public AccessTokenIdentity requireAccessIdentity(String token) {
        String[] parts = requireValidParts(token, TOKEN_VERSION_V3);
        try {
            TokenPayloadV3 payload = objectMapper.readValue(base64UrlDecode(parts[1]), TokenPayloadV3.class);
            if (payload.sessionId() == null || payload.membershipId() == null || payload.tenantId() == null
                    || !StringUtils.hasText(payload.username()) || !StringUtils.hasText(payload.deviceKeyId())) {
                throw new IllegalArgumentException("Access token identity is incomplete.");
            }
            if (Instant.now().getEpochSecond() > payload.expiresAtEpochSecond()) {
                throw new IllegalArgumentException("Access token has expired.");
            }
            return new AccessTokenIdentity(
                    payload.sessionId(),
                    payload.membershipId(),
                    payload.tenantId(),
                    payload.username(),
                    payload.authorizationVersion(),
                    payload.deviceKeyId(),
                    payload.issuedAtEpochSecond(),
                    payload.expiresAtEpochSecond()
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid access token payload.", exception);
        }
    }

    private String[] requireValidParts(String token, String requiredVersion) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Bearer token is blank.");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !requiredVersion.equals(parts[0])) {
            throw new IllegalArgumentException("Unsupported bearer token.");
        }
        String unsignedToken = parts[0] + "." + parts[1];
        String expectedSignature = sign(unsignedToken);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid bearer token signature.");
        }
        return parts;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return base64UrlEncode(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign auth token.", ex);
        }
    }

    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private record TokenPayloadV3(UUID sessionId,
                                  UUID membershipId,
                                  UUID tenantId,
                                  String username,
                                  Long authorizationVersion,
                                  String deviceKeyId,
                                  long issuedAtEpochSecond,
                                  long expiresAtEpochSecond) {
    }

    public record IssuedAccessToken(String value, long expiresInSeconds, long expiresAtEpochSecond) {
    }

    public record AccessTokenIdentity(UUID sessionId,
                                      UUID membershipId,
                                      UUID tenantId,
                                      String username,
                                      Long authorizationVersion,
                                      String deviceKeyId,
                                      long issuedAtEpochSecond,
                                      long expiresAtEpochSecond) {
    }
}

