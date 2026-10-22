package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.ApiToken;
import com.pro.ereportbacken.model.pojo.AuthClientType;
import com.pro.ereportbacken.model.pojo.AuthSession;
import com.pro.ereportbacken.model.pojo.AuthSessionStatus;
import com.pro.ereportbacken.model.pojo.TrustedDevice;
import com.pro.ereportbacken.model.pojo.TrustedDeviceStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.repository.ApiTokenRepository;
import com.pro.ereportbacken.repository.AuthSessionRepository;
import com.pro.ereportbacken.repository.TrustedDeviceRepository;
import com.pro.ereportbacken.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CredentialRevocationIntegrationTests {

    @Autowired
    private CredentialRevocationService credentialRevocationService;
    @Autowired
    private DefaultTenantService defaultTenantService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthSessionRepository authSessionRepository;
    @Autowired
    private ApiTokenRepository apiTokenRepository;
    @Autowired
    private TrustedDeviceRepository trustedDeviceRepository;

    @Test
    void adminResetAtomicallyRevokesSessionTokenAndDeviceTrust() {
        var tenant = defaultTenantService.requireDefaultTenant();
        User user = userRepository.saveAndFlush(user(tenant.getId()));
        TrustedDevice device = trustedDeviceRepository.saveAndFlush(device(user));
        AuthSession session = authSessionRepository.saveAndFlush(session(user, device));
        ApiToken token = apiTokenRepository.saveAndFlush(token(user));

        var result = credentialRevocationService.afterAdminPasswordReset(tenant.getId(), user.getId());

        AuthSession revokedSession = authSessionRepository.findById(session.getId()).orElseThrow();
        ApiToken revokedToken = apiTokenRepository.findById(token.getId()).orElseThrow();
        TrustedDevice revokedDevice = trustedDeviceRepository.findById(device.getId()).orElseThrow();
        assertThat(result).isEqualTo(new CredentialRevocationService.RevocationSummary(1, 1, 1));
        assertThat(revokedSession.getStatus()).isEqualTo(AuthSessionStatus.REVOKED);
        assertThat(revokedSession.getRefreshTokenHash()).isNull();
        assertThat(revokedToken.getRevokedAt()).isNotNull();
        assertThat(revokedToken.getRevokeReason()).isEqualTo("管理员重置密码");
        assertThat(revokedDevice.getStatus()).isEqualTo(TrustedDeviceStatus.REAUTHENTICATION_REQUIRED);
        assertThat(revokedDevice.getRevokedAt()).isNotNull();
        assertThat(revokedDevice.getRevokeReason()).isEqualTo("管理员重置密码");
    }

    private User user(UUID tenantId) {
        String suffix = String.format("%08d", Math.floorMod(System.nanoTime(), 100_000_000L));
        User user = new User();
        user.setTenantId(tenantId);
        user.setOrgUnitId(defaultTenantService.requireRootOrgUnit(tenantId).getId());
        user.setUsername("credential-int-" + suffix);
        user.setPassword("{argon2id}integration-test-password");
        user.setName("凭据集成测试用户");
        user.setPhone("139" + suffix);
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthorizationVersion(1L);
        user.setPasswordChangeRequired(false);
        return user;
    }

    private TrustedDevice device(User user) {
        LocalDateTime now = LocalDateTime.now();
        TrustedDevice device = new TrustedDevice();
        device.setTenantId(user.getTenantId());
        device.setMembershipId(user.getId());
        device.setKeyId(UUID.randomUUID().toString().replace("-", ""));
        device.setPublicKeySpki("integration-public-key");
        device.setInstallationIdHash("a".repeat(64));
        device.setStatus(TrustedDeviceStatus.TRUSTED);
        device.setHardwareBacked(false);
        device.setFirstSeenAt(now.minusDays(1));
        device.setLastSeenAt(now.minusMinutes(1));
        return device;
    }

    private AuthSession session(User user, TrustedDevice device) {
        LocalDateTime now = LocalDateTime.now();
        AuthSession session = new AuthSession();
        session.setTenantId(user.getTenantId());
        session.setMembershipId(user.getId());
        session.setTrustedDeviceId(device.getId());
        session.setClientType(AuthClientType.ANDROID);
        session.setStatus(AuthSessionStatus.ACTIVE);
        session.setRefreshTokenHash("b".repeat(64));
        session.setAuthorizationVersion(user.getAuthorizationVersion());
        session.setPasswordChangeRequired(false);
        session.setPersistentLogin(false);
        session.setIpHash("c".repeat(64));
        session.setUserAgentHash("d".repeat(64));
        session.setCreatedAt(now.minusHours(1));
        session.setLastSeenAt(now.minusMinutes(1));
        session.setIdleExpiresAt(now.plusDays(1));
        session.setAbsoluteExpiresAt(now.plusDays(30));
        return session;
    }

    private ApiToken token(User user) {
        ApiToken token = new ApiToken();
        token.setTenantId(user.getTenantId());
        token.setMembershipId(user.getId());
        token.setName("凭据集成测试 Token");
        token.setLookupKey("x".repeat(22));
        token.setSecretHash("e".repeat(64));
        token.setCreatedAt(LocalDateTime.now().minusDays(1));
        return token;
    }
}
