package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "trusted_devices",
        indexes = {
                @Index(name = "idx_trusted_device_key", columnList = "key_id"),
                @Index(name = "idx_trusted_device_member_status", columnList = "tenant_id,membership_id,status")
        }
)
@Getter
@Setter
public class TrustedDevice extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(name = "key_id", nullable = false, length = 64)
    private String keyId;

    @Column(name = "public_key_spki", nullable = false, length = 1024)
    private String publicKeySpki;

    @Column(name = "installation_id_hash", nullable = false, length = 64)
    private String installationIdHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TrustedDeviceStatus status = TrustedDeviceStatus.TRUSTED;

    @Column(length = 80)
    private String manufacturer;

    @Column(length = 80)
    private String brand;

    @Column(length = 120)
    private String model;

    @Column(name = "device_name", length = 120)
    private String deviceName;

    @Column(name = "product_name", length = 120)
    private String productName;

    @Column(name = "hardware_name", length = 120)
    private String hardwareName;

    @Column(name = "board_name", length = 120)
    private String boardName;

    @Column(name = "supported_abis", length = 255)
    private String supportedAbis;

    @Column(name = "supported_32_bit_abis", length = 255)
    private String supported32BitAbis;

    @Column(name = "supported_64_bit_abis", length = 255)
    private String supported64BitAbis;

    @Column(name = "soc_manufacturer", length = 80)
    private String socManufacturer;

    @Column(name = "soc_model", length = 120)
    private String socModel;

    @Column(name = "cpu_core_count")
    private Integer cpuCoreCount;

    @Column(name = "low_ram_device")
    private Boolean lowRamDevice;

    @Column(name = "os_release", length = 50)
    private String osRelease;

    @Column(name = "sdk_int")
    private Integer sdkInt;

    @Column(name = "security_patch", length = 20)
    private String securityPatch;

    @Column(name = "build_fingerprint_hash", length = 64)
    private String buildFingerprintHash;

    @Column(name = "environment_fingerprint_hash", length = 64)
    private String environmentFingerprintHash;

    @Column(name = "environment_schema_version")
    private Integer environmentSchemaVersion;

    @Column(name = "screen_width")
    private Integer screenWidth;

    @Column(name = "screen_height")
    private Integer screenHeight;

    @Column(name = "screen_density")
    private Integer screenDensity;

    @Column(name = "memory_mb_bucket")
    private Integer memoryMbBucket;

    @Column(name = "storage_mb_bucket")
    private Integer storageMbBucket;

    @Column(name = "locale_tag", length = 50)
    private String localeTag;

    @Column(name = "time_zone", length = 80)
    private String timeZone;

    @Column(name = "app_version", length = 30)
    private String appVersion;

    @Column(name = "app_build", length = 20)
    private String appBuild;

    @Column(name = "installer_package", length = 120)
    private String installerPackage;

    @Column(name = "app_signature_sha256", length = 64)
    private String appSignatureSha256;

    @Column(name = "hardware_backed", nullable = false)
    private Boolean hardwareBacked = false;

    @Column(name = "security_signals", columnDefinition = "TEXT")
    private String securitySignals;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoke_reason", length = 255)
    private String revokeReason;
}
