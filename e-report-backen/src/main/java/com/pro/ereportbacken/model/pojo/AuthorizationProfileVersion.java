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

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "authorization_profile_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_authorization_profile_versions_number",
                columnNames = {"profile_id", "version_no"}
        ),
        indexes = @Index(
                name = "idx_authorization_profile_versions_status",
                columnList = "tenant_id,profile_id,status"
        )
)
@Getter
@Setter
public class AuthorizationProfileVersion extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthorizationProfileVersionStatus status = AuthorizationProfileVersionStatus.DRAFT;

    @Column(name = "change_summary", length = 500)
    private String changeSummary;

    @Column(name = "created_by_membership_id")
    private UUID createdByMembershipId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_by_membership_id")
    private UUID publishedByMembershipId;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}
