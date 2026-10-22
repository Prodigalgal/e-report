package com.pro.ereportbacken.model.pojo;

import com.pro.ereportbacken.time.UtcTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "permission_grants",
        indexes = {
                @Index(name = "idx_permission_grants_member", columnList = "tenant_id,membership_id,status,expires_at"),
                @Index(name = "idx_permission_grants_permission", columnList = "tenant_id,permission_id,status,expires_at"),
                @Index(name = "idx_permission_grants_parent", columnList = "parent_grant_id,status")
        }
)
@Getter
@Setter
public class PermissionGrant extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    @Column(name = "parent_grant_id")
    private UUID parentGrantId;

    @Column(name = "grant_option", nullable = false)
    private Boolean grantOption = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "issuer_type", nullable = false, length = 30)
    private GrantIssuerType issuerType = GrantIssuerType.USER;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PermissionGrantStatus status = PermissionGrantStatus.ACTIVE;

    @Column(nullable = false)
    private Boolean customized = false;

    @Column(length = 1000)
    private String reason;

    @Column(name = "granted_by_membership_id")
    private UUID grantedByMembershipId;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    @Column(name = "revoked_by_membership_id")
    private UUID revokedByMembershipId;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoke_reason", length = 1000)
    private String revokeReason;

    @Column(name = "replaced_by_grant_id")
    private UUID replacedByGrantId;

    @Column(name = "status_changed_at", nullable = false)
    private LocalDateTime statusChangedAt;

    @PrePersist
    void initializeGrantAuditFields() {
        LocalDateTime now = UtcTime.now();
        if (grantedAt == null) {
            grantedAt = now;
        }
        if (statusChangedAt == null) {
            statusChangedAt = grantedAt;
        }
    }
}
