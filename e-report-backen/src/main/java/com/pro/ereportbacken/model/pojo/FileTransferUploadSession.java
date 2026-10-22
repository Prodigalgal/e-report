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
@Table(
        name = "file_transfer_upload_sessions",
        indexes = {
                @Index(name = "idx_file_transfer_user_status", columnList = "created_by_username,status"),
                @Index(name = "idx_file_transfer_expires", columnList = "expires_at")
        }
)
@Getter
@Setter
public class FileTransferUploadSession extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private java.util.UUID tenantId;

    @Column(name = "created_by_membership_id")
    private java.util.UUID createdByMembershipId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FileTransferPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileTransferStatus status = FileTransferStatus.INITIATED;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "total_size", nullable = false)
    private Long totalSize;

    @Column(name = "expected_sha256", length = 64)
    private String expectedSha256;

    @Column(name = "actual_sha256", length = 64)
    private String actualSha256;

    @Column(name = "chunk_size", nullable = false)
    private Long chunkSize;

    @Column(name = "received_bytes", nullable = false)
    private Long receivedBytes = 0L;

    @Column(name = "temp_file_name", nullable = false, length = 255)
    private String tempFileName;

    @Column(name = "stored_file_name", length = 255)
    private String storedFileName;

    @Column(name = "created_by_username", nullable = false, length = 100)
    private String createdByUsername;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

