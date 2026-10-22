package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "command_idempotency_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_command_idempotency_scope",
                columnNames = {"tenant_id", "membership_id", "endpoint", "idempotency_key"}
        ),
        indexes = @Index(name = "idx_command_idempotency_expiry", columnList = "expires_at")
)
@Getter
@Setter
public class CommandIdempotencyRecord extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private java.util.UUID tenantId;

    @Column(name = "membership_id", nullable = false)
    private java.util.UUID membershipId;

    @Column(nullable = false, length = 255)
    private String endpoint;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false, length = 20)
    private String state;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}

