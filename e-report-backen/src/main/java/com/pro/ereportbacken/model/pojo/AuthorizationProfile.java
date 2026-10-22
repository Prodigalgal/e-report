package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "authorization_profiles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_authorization_profiles_tenant_code", columnNames = {"tenant_id", "code"}),
                @UniqueConstraint(name = "uk_authorization_profiles_tenant_system", columnNames = {"tenant_id", "system_key"})
        },
        indexes = @Index(name = "idx_authorization_profiles_tenant_active", columnList = "tenant_id,active,sort_order")
)
@Getter
@Setter
public class AuthorizationProfile extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(name = "system_key", length = 80)
    private String systemKey;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(length = 500)
    private String description;

    @Column(name = "built_in", nullable = false)
    private Boolean builtIn = false;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 100;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
