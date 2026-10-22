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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenants", indexes = @Index(name = "idx_tenants_group", columnList = "tenant_group_id"))
@Getter
@Setter
public class Tenant extends UuidV7Entity {

    @Column(name = "tenant_group_id")
    private java.util.UUID tenantGroupId;

    @Column(name = "root_org_unit_id")
    private java.util.UUID rootOrgUnitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_key", unique = true, length = 40)
    private TenantSystemKey systemKey;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

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

