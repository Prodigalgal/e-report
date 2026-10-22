package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "user_transfer_jobs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_transfer_jobs_idempotency",
                columnNames = {"tenant_id", "created_by_membership_id", "idempotency_key"}
        ),
        indexes = {
                @Index(name = "idx_user_transfer_jobs_owner", columnList = "tenant_id,created_by_membership_id,created_at"),
                @Index(name = "idx_user_transfer_jobs_status", columnList = "status,updated_at"),
                @Index(name = "idx_user_transfer_jobs_expires", columnList = "expires_at")
        }
)
@Getter
@Setter
public class UserTransferJob extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_by_membership_id", nullable = false)
    private UUID createdByMembershipId;

    @Column(name = "created_by_username", nullable = false, length = 100)
    private String createdByUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 20)
    private UserTransferJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserTransferJobStatus status;

    @Column(name = "input_file_id")
    private UUID inputFileId;

    @Column(name = "result_file_id")
    private UUID resultFileId;

    @Column(name = "error_file_id")
    private UUID errorFileId;

    @Column(name = "input_sha256", length = 64)
    private String inputSha256;

    @Column(name = "template_version", length = 30)
    private String templateVersion;

    @Column(name = "catalog_digest", length = 64)
    private String catalogDigest;

    @Column(name = "user_snapshot_digest", length = 64)
    private String userSnapshotDigest;

    @Column(name = "preview_snapshot_digest", length = 64)
    private String previewSnapshotDigest;

    @Column(name = "preview_expires_at")
    private LocalDateTime previewExpiresAt;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "total_rows", nullable = false)
    private Integer totalRows = 0;

    @Column(name = "valid_rows", nullable = false)
    private Integer validRows = 0;

    @Column(name = "invalid_rows", nullable = false)
    private Integer invalidRows = 0;

    @Column(name = "processed_rows", nullable = false)
    private Integer processedRows = 0;

    @Column(name = "succeeded_rows", nullable = false)
    private Integer succeededRows = 0;

    @Column(name = "failed_rows", nullable = false)
    private Integer failedRows = 0;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "summary_json", length = 65_535)
    private String summaryJson;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}
