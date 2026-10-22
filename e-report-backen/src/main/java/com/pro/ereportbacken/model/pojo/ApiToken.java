package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "api_tokens",
        indexes = {
                @Index(name = "idx_api_token_owner_created", columnList = "tenant_id,membership_id,created_at"),
                @Index(name = "idx_api_token_expiry", columnList = "expires_at")
        }
)
@Getter
@Setter
public class ApiToken extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "lookup_key", nullable = false, unique = true, length = 22)
    private String lookupKey;

    @Column(name = "secret_hash", nullable = false, unique = true, length = 64)
    private String secretHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_token_permissions", joinColumns = @JoinColumn(name = "token_id"))
    @Column(name = "permission_name", nullable = false, length = 100)
    private Set<String> permissionNames = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoke_reason", length = 255)
    private String revokeReason;
}
