package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "organization_move_events",
        indexes = @Index(
                name = "idx_organization_move_events_org",
                columnList = "tenant_id,org_unit_id,created_at"
        )
)
@Getter
@Setter
public class OrganizationMoveEvent extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "org_unit_id", nullable = false)
    private UUID orgUnitId;

    @Column(name = "old_parent_id", nullable = false)
    private UUID oldParentId;

    @Column(name = "new_parent_id", nullable = false)
    private UUID newParentId;

    @Column(name = "old_path", nullable = false, length = 500)
    private String oldPath;

    @Column(name = "new_path", nullable = false, length = 500)
    private String newPath;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "operator_membership_id")
    private UUID operatorMembershipId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
