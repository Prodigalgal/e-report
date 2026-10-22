package com.pro.ereportbacken.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.dto.DeviceContextDTO;
import com.pro.ereportbacken.model.pojo.TrustedDevice;
import com.pro.ereportbacken.model.pojo.TrustedDeviceStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.repository.TrustedDeviceRepository;
import com.pro.ereportbacken.security.DeviceProofService;
import com.pro.ereportbacken.security.SecurityHashingService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrustedDeviceServiceTests {

    private final TrustedDeviceRepository repository = mock(TrustedDeviceRepository.class);
    private final SecurityHashingService hashingService =
            new SecurityHashingService("test-trusted-device-pepper-at-least-32-bytes");
    private final TrustedDeviceService service = new TrustedDeviceService(
            repository,
            mock(DeviceProofService.class),
            hashingService,
            new ObjectMapper(),
            ""
    );

    @Test
    void successfulPasswordLoginRestoresOnlyReauthenticationRequiredDevice() {
        User user = user();
        DeviceContextDTO context = context();
        DeviceProofService.VerifiedDeviceKey verifiedKey =
                new DeviceProofService.VerifiedDeviceKey("device-key", "public-key", null);
        TrustedDevice device = existingDevice(user, context, verifiedKey, TrustedDeviceStatus.REAUTHENTICATION_REQUIRED);
        when(repository.findByTenantIdAndMembershipIdAndKeyId(
                user.getTenantId(), user.getId(), verifiedKey.keyId())).thenReturn(Optional.of(device));
        when(repository.save(any(TrustedDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrustedDevice restored = service.registerOrRefresh(user, context, verifiedKey);

        assertThat(restored.getStatus()).isEqualTo(TrustedDeviceStatus.TRUSTED);
        assertThat(restored.getRevokedAt()).isNull();
        assertThat(restored.getRevokeReason()).isNull();
    }

    @Test
    void manuallyRevokedDeviceRemainsBlocked() {
        User user = user();
        DeviceContextDTO context = context();
        DeviceProofService.VerifiedDeviceKey verifiedKey =
                new DeviceProofService.VerifiedDeviceKey("device-key", "public-key", null);
        TrustedDevice device = existingDevice(user, context, verifiedKey, TrustedDeviceStatus.REVOKED);
        when(repository.findByTenantIdAndMembershipIdAndKeyId(
                user.getTenantId(), user.getId(), verifiedKey.keyId())).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> service.registerOrRefresh(user, context, verifiedKey))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(UUID.randomUUID());
        return user;
    }

    private DeviceContextDTO context() {
        DeviceContextDTO context = new DeviceContextDTO();
        context.setInstallationId("installation-id");
        return context;
    }

    private TrustedDevice existingDevice(User user,
                                         DeviceContextDTO context,
                                         DeviceProofService.VerifiedDeviceKey key,
                                         TrustedDeviceStatus status) {
        TrustedDevice device = new TrustedDevice();
        device.setId(UUID.randomUUID());
        device.setTenantId(user.getTenantId());
        device.setMembershipId(user.getId());
        device.setKeyId(key.keyId());
        device.setPublicKeySpki(key.publicKeySpki());
        device.setInstallationIdHash(hashingService.protectedHash(
                "device-installation", context.getInstallationId()));
        device.setStatus(status);
        device.setFirstSeenAt(LocalDateTime.now().minusDays(1));
        device.setLastSeenAt(LocalDateTime.now().minusHours(1));
        device.setRevokedAt(LocalDateTime.now().minusMinutes(1));
        device.setRevokeReason("密码变更");
        return device;
    }
}
