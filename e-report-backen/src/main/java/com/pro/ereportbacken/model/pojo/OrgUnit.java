package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "org_units",
        indexes = @Index(name = "idx_org_units_tenant_path", columnList = "tenant_id,path"),
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_org_units_tenant_code", columnNames = {"tenant_id", "code"}),
                @UniqueConstraint(name = "uk_org_units_tenant_id", columnNames = {"tenant_id", "id"})
        }
)
@Getter
@Setter
public class OrgUnit extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrgUnitType unitType;

    @Column(nullable = false, length = 500)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantStatus status = TenantStatus.ACTIVE;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

