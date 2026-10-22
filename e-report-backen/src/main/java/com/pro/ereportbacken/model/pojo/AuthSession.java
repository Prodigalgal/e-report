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
        name = "auth_sessions",
        indexes = {
                @Index(name = "idx_auth_session_member_status", columnList = "tenant_id,membership_id,status"),
                @Index(name = "idx_auth_session_expiry", columnList = "status,idle_expires_at,absolute_expires_at")
        }
)
@Getter
@Setter
public class AuthSession extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(name = "trusted_device_id")
    private UUID trustedDeviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", nullable = false, length = 20)
    private AuthClientType clientType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthSessionStatus status = AuthSessionStatus.ACTIVE;

    @Column(name = "web_secret_hash", unique = true, length = 64)
    private String webSecretHash;

    @Column(name = "refresh_token_hash", unique = true, length = 64)
    private String refreshTokenHash;

    @Column(name = "previous_refresh_token_hash", length = 64)
    private String previousRefreshTokenHash;

    @Column(name = "refresh_rotated_at")
    private LocalDateTime refreshRotatedAt;

    @Column(name = "authorization_version", nullable = false)
    private Long authorizationVersion;

    @Column(name = "password_change_required", nullable = false)
    private Boolean passwordChangeRequired = false;

    @Column(name = "persistent_login", nullable = false)
    private Boolean persistentLogin = false;

    @Column(name = "ip_hash", nullable = false, length = 64)
    private String ipHash;

    @Column(name = "user_agent_hash", nullable = false, length = 64)
    private String userAgentHash;

    @Column(name = "web_fingerprint_hash", length = 64)
    private String webFingerprintHash;

    @Column(name = "client_name", length = 120)
    private String clientName;

    @Column(name = "client_device_name", length = 120)
    private String clientDeviceName;

    @Column(name = "client_os_version", length = 120)
    private String clientOsVersion;

    @Column(name = "client_app_version", length = 50)
    private String clientAppVersion;

    @Column(name = "client_security_signals", length = 1000)
    private String clientSecuritySignals;

    @Column(name = "web_environment_json", columnDefinition = "TEXT")
    private String webEnvironmentJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "idle_expires_at", nullable = false)
    private LocalDateTime idleExpiresAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private LocalDateTime absoluteExpiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoke_reason", length = 255)
    private String revokeReason;
}
