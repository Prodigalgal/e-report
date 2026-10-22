package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "authorization_profile_permission_rules",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_authorization_profile_rules_permission",
                columnNames = {"profile_version_id", "permission_id"}
        ),
        indexes = @Index(
                name = "idx_authorization_profile_rules_version",
                columnList = "tenant_id,profile_version_id,sort_order"
        )
)
@Getter
@Setter
public class AuthorizationProfilePermissionRule extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "profile_version_id", nullable = false)
    private UUID profileVersionId;

    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 100;
}
