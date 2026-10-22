package com.pro.ereportbacken.security;

import com.pro.ereportbacken.TestIds;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthTokenServiceTest {

    private static final String TEST_SECRET = "test-auth-token-secret-at-least-32-bytes";

    @Test
    void deviceBoundAccessTokenPreservesSessionIdentity() {
        AuthTokenService tokenService = new AuthTokenService(TEST_SECRET, 60);
        java.util.UUID sessionId = TestIds.uuid(11);
        java.util.UUID membershipId = TestIds.uuid(17);
        java.util.UUID tenantId = TestIds.uuid(23);

        AuthTokenService.IssuedAccessToken issued = tokenService.issueAccessToken(
                sessionId,
                membershipId,
                tenantId,
                "dispatcher",
                9L,
                "abcdefghijklmnopqrstuvwxyzABCDEFGH123456789"
        );

        assertThat(tokenService.requireAccessIdentity(issued.value()))
                .extracting(
                        AuthTokenService.AccessTokenIdentity::sessionId,
                        AuthTokenService.AccessTokenIdentity::membershipId,
                        AuthTokenService.AccessTokenIdentity::tenantId,
                        AuthTokenService.AccessTokenIdentity::username,
                        AuthTokenService.AccessTokenIdentity::authorizationVersion,
                        AuthTokenService.AccessTokenIdentity::deviceKeyId
                )
                .containsExactly(
                        sessionId,
                        membershipId,
                        tenantId,
                        "dispatcher",
                        9L,
                        "abcdefghijklmnopqrstuvwxyzABCDEFGH123456789"
                );
        assertThat(issued.expiresInSeconds()).isEqualTo(60);
    }

    @Test
    void tamperedAccessTokenIsRejected() {
        AuthTokenService tokenService = new AuthTokenService(TEST_SECRET, 60);
        String token = tokenService.issueAccessToken(
                TestIds.uuid(31), TestIds.uuid(32), TestIds.uuid(33), "admin", 1L,
                "abcdefghijklmnopqrstuvwxyzABCDEFGH123456789"
        ).value();
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

        assertThatThrownBy(() -> tokenService.requireAccessIdentity(tampered))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unsupportedTokenVersionIsRejected() {
        AuthTokenService tokenService = new AuthTokenService(TEST_SECRET, 60);

        assertThatThrownBy(() -> tokenService.requireAccessIdentity("v2.payload.signature"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void weakOrBlankSecretIsRejected() {
        assertThatThrownBy(() -> new AuthTokenService("", 60))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AuthTokenService("short-secret", 60))
                .isInstanceOf(IllegalStateException.class);
    }
}
