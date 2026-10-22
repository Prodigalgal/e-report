package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.dto.DeviceContextDTO;
import com.pro.ereportbacken.model.pojo.TrustedDevice;
import com.pro.ereportbacken.model.pojo.TrustedDeviceStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.repository.TrustedDeviceRepository;
import com.pro.ereportbacken.security.DeviceProofService;
import com.pro.ereportbacken.security.SecurityHashingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import lombok.extern.log4j.Log4j2;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Log4j2
public class TrustedDeviceService {

    private static final Set<String> ALLOWED_SECURITY_SIGNALS = Set.of(
            "ROOTED", "EMULATOR", "DEBUGGER", "HOOK_FRAMEWORK", "TEST_KEYS",
            "BOOTLOADER_UNLOCKED", "APP_SIGNATURE_MISMATCH", "PLAY_INTEGRITY_FAILED",
            "PLAY_INTEGRITY_UNAVAILABLE", "DEVELOPER_OPTIONS", "ADB_ENABLED",
            "APP_DEBUGGABLE"
    );

    private final TrustedDeviceRepository repository;
    private final DeviceProofService deviceProofService;
    private final SecurityHashingService hashingService;
    private final ObjectMapper objectMapper;
    private final String expectedAppSignatureSha256;

    public TrustedDeviceService(TrustedDeviceRepository repository,
                                DeviceProofService deviceProofService,
                                SecurityHashingService hashingService,
                                ObjectMapper objectMapper,
                                @Value("${app.security.android.expected-signature-sha256:}") String expectedAppSignatureSha256) {
        this.repository = repository;
        this.deviceProofService = deviceProofService;
        this.hashingService = hashingService;
        this.objectMapper = objectMapper;
        this.expectedAppSignatureSha256 = normalizeDigest(expectedAppSignatureSha256);
    }

    @Transactional
    public TrustedDevice registerOrRefresh(User user,
                                           DeviceContextDTO context,
                                           DeviceProofService.VerifiedDeviceKey verifiedKey) {
        if (user == null || context == null || verifiedKey == null) {
            throw new IllegalArgumentException("设备注册信息不完整。");
        }
        String installationHash = hashingService.protectedHash("device-installation", context.getInstallationId());
        TrustedDevice device = repository
                .findByTenantIdAndMembershipIdAndKeyId(user.getTenantId(), user.getId(), verifiedKey.keyId())
                .orElseGet(TrustedDevice::new);

        if (device.getId() != null) {
            if (device.getStatus() == TrustedDeviceStatus.REVOKED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该设备已被撤销，请联系管理员或使用新安装重新登录。");
            }
            if (!hashingService.constantTimeEquals(device.getInstallationIdHash(), installationHash)
                    || !device.getPublicKeySpki().equals(verifiedKey.publicKeySpki())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "设备身份发生变化，请清除应用数据后重新注册设备。");
            }
            if (device.getStatus() == TrustedDeviceStatus.REAUTHENTICATION_REQUIRED) {
                device.setStatus(TrustedDeviceStatus.TRUSTED);
                device.setRevokedAt(null);
                device.setRevokeReason(null);
            }
        } else {
            device.setTenantId(user.getTenantId());
            device.setMembershipId(user.getId());
            device.setKeyId(verifiedKey.keyId());
            device.setPublicKeySpki(verifiedKey.publicKeySpki());
            device.setInstallationIdHash(installationHash);
            device.setStatus(TrustedDeviceStatus.TRUSTED);
            device.setFirstSeenAt(UtcTime.now());
        }

        applyContext(device, context);
        device.setLastSeenAt(UtcTime.now());
        return repository.save(device);
    }

    @Transactional
    public int requireReauthenticationAfterCredentialChange(UUID tenantId, UUID membershipId, String reason) {
        int affected = repository.requireReauthenticationByOwner(
                tenantId,
                membershipId,
                TrustedDeviceStatus.REAUTHENTICATION_REQUIRED,
                UtcTime.now(),
                limitReason(reason)
        );
        if (affected > 0) {
            log.info("Trusted devices require reauthentication after credential change; membershipId={}, count={}",
                    membershipId, affected);
        }
        return affected;
    }

    @Transactional(readOnly = true)
    public TrustedDevice requireActive(java.util.UUID tenantId, java.util.UUID membershipId, String keyId) {
        TrustedDevice device = repository.findByTenantIdAndMembershipIdAndKeyId(tenantId, membershipId, keyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前设备尚未登记。"));
        if (device.getStatus() != TrustedDeviceStatus.TRUSTED || device.getRevokedAt() != null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前设备已被撤销。");
        }
        return device;
    }

    private void applyContext(TrustedDevice device, DeviceContextDTO context) {
        device.setManufacturer(clean(context.getManufacturer(), 80));
        device.setBrand(clean(context.getBrand(), 80));
        device.setModel(clean(context.getModel(), 120));
        device.setDeviceName(clean(context.getDeviceName(), 120));
        device.setProductName(clean(context.getProductName(), 120));
        device.setHardwareName(clean(context.getHardwareName(), 120));
        device.setBoardName(clean(context.getBoardName(), 120));
        device.setSupportedAbis(join(context.getSupportedAbis(), 255));
        device.setSupported32BitAbis(join(context.getSupported32BitAbis(), 255));
        device.setSupported64BitAbis(join(context.getSupported64BitAbis(), 255));
        device.setSocManufacturer(clean(context.getSocManufacturer(), 80));
        device.setSocModel(clean(context.getSocModel(), 120));
        device.setCpuCoreCount(context.getCpuCoreCount());
        device.setLowRamDevice(context.getLowRamDevice());
        device.setOsRelease(clean(context.getOsRelease(), 50));
        device.setSdkInt(context.getSdkInt());
        device.setSecurityPatch(clean(context.getSecurityPatch(), 20));
        device.setBuildFingerprintHash(StringUtils.hasText(context.getBuildFingerprint())
                ? hashingService.protectedHash("device-build-fingerprint", context.getBuildFingerprint())
                : null);
        String environmentFingerprint = normalizeDigest(context.getEnvironmentFingerprintHash());
        device.setEnvironmentFingerprintHash(StringUtils.hasText(environmentFingerprint)
                ? hashingService.protectedHash("android-environment", environmentFingerprint)
                : null);
        device.setEnvironmentSchemaVersion(context.getEnvironmentSchemaVersion());
        device.setScreenWidth(context.getScreenWidth());
        device.setScreenHeight(context.getScreenHeight());
        device.setScreenDensity(context.getScreenDensity());
        device.setMemoryMbBucket(context.getMemoryMbBucket());
        device.setStorageMbBucket(context.getStorageMbBucket());
        device.setLocaleTag(clean(context.getLocaleTag(), 50));
        device.setTimeZone(clean(context.getTimeZone(), 80));
        device.setAppVersion(clean(context.getAppVersion(), 30));
        device.setAppBuild(clean(context.getAppBuild(), 20));
        device.setInstallerPackage(clean(context.getInstallerPackage(), 120));
        String submittedSignature = normalizeDigest(context.getAppSignatureSha256());
        device.setAppSignatureSha256(submittedSignature);
        device.setHardwareBacked(context.isHardwareBacked());
        device.setSecuritySignals(serializeSignals(context.getSecuritySignals(), submittedSignature));
    }

    private String serializeSignals(Set<String> submitted, String submittedSignature) {
        Set<String> normalized = new LinkedHashSet<>();
        if (submitted != null) {
            submitted.stream()
                    .filter(StringUtils::hasText)
                    .map(value -> value.trim().toUpperCase(Locale.ROOT))
                    .filter(ALLOWED_SECURITY_SIGNALS::contains)
                    .sorted()
                    .forEach(normalized::add);
        }
        if (StringUtils.hasText(expectedAppSignatureSha256)
                && !hashingService.constantTimeEquals(expectedAppSignatureSha256, submittedSignature)) {
            normalized.add("APP_SIGNATURE_MISMATCH");
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize device security signals.", exception);
        }
    }

    private String join(Set<String> values, int maximumLength) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream()
                .filter(StringUtils::hasText)
                .map(value -> clean(value, 40))
                .distinct()
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
        return clean(joined, maximumLength);
    }

    private String clean(String value, int maximumLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() <= maximumLength ? cleaned : cleaned.substring(0, maximumLength);
    }

    private String normalizeDigest(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-f0-9]{64}") ? normalized : null;
    }

    private String limitReason(String reason) {
        String normalized = StringUtils.hasText(reason) ? reason.trim() : "凭据变更";
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }
}
