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
import java.util.UUID;

@Entity
@Table(
        name = "image_redaction_jobs",
        indexes = {
                @Index(name = "idx_image_redaction_ready", columnList = "status,available_at,created_at"),
                @Index(name = "idx_image_redaction_lease", columnList = "status,lease_until")
        }
)
@Getter
@Setter
public class ImageRedactionJob extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "upload_session_id", nullable = false, unique = true)
    private UUID uploadSessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImageRedactionStatus status = ImageRedactionStatus.QUEUED;

    @Column(name = "source_stored_file_name", nullable = false, length = 255)
    private String sourceStoredFileName;

    @Column(name = "source_content_type", nullable = false, length = 100)
    private String sourceContentType;

    @Column(name = "source_file_size", nullable = false)
    private long sourceFileSize;

    @Column(name = "output_stored_file_name", length = 255)
    private String outputStoredFileName;

    @Column(name = "output_original_file_name", length = 255)
    private String outputOriginalFileName;

    @Column(name = "output_content_type", length = 100)
    private String outputContentType;

    @Column(name = "output_file_size")
    private Long outputFileSize;

    @Column(name = "output_sha256", length = 64)
    private String outputSha256;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(name = "lease_owner", length = 100)
    private String leaseOwner;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "policy_version", nullable = false, length = 50)
    private String policyVersion;

    @Column(name = "detected_regions")
    private Integer detectedRegions;

    @Column(name = "processing_duration_ms")
    private Long processingDurationMs;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
