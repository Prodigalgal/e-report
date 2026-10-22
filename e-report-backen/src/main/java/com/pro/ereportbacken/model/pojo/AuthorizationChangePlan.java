package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "authorization_change_plans",
        indexes = {
                @Index(name = "idx_authorization_plans_target", columnList = "tenant_id,target_membership_id,status,expires_at"),
                @Index(name = "idx_authorization_plans_operator", columnList = "tenant_id,operator_membership_id,created_at")
        }
)
@Getter
@Setter
public class AuthorizationChangePlan extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "target_membership_id", nullable = false)
    private UUID targetMembershipId;

    @Column(name = "operator_membership_id", nullable = false)
    private UUID operatorMembershipId;

    @Column(name = "expected_authorization_version", nullable = false)
    private Long expectedAuthorizationVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthorizationChangePlanStatus status = AuthorizationChangePlanStatus.READY;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "request_json", nullable = false)
    private String requestJson;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "preview_json", nullable = false)
    private String previewJson;

    @Column(name = "request_digest", nullable = false, length = 64)
    private String requestDigest;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;
}
