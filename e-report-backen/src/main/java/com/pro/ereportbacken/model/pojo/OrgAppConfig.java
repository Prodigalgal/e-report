package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "org_app_config",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_org_app_config_scope_key",
                columnNames = {"tenant_id", "org_unit_id", "config_key"}
        ),
        indexes = @Index(name = "idx_org_app_config_scope", columnList = "tenant_id,org_unit_id")
)
@Getter
@Setter
public class OrgAppConfig extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private java.util.UUID tenantId;

    @Column(name = "org_unit_id", nullable = false)
    private java.util.UUID orgUnitId;

    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    @Column(length = 255)
    private String description;

    @UpdateTimestamp
    @Column(name = "last_updated_time", nullable = false)
    private LocalDateTime lastUpdatedTime;
}

