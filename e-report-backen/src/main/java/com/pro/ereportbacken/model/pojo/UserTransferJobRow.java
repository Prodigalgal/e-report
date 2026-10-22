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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "user_transfer_job_rows",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_transfer_job_rows_position",
                columnNames = {"job_id", "sheet_name", "source_row_number"}
        ),
        indexes = @Index(
                name = "idx_user_transfer_job_rows_page",
                columnList = "tenant_id,job_id,validation_status,source_row_number"
        )
)
@Getter
@Setter
public class UserTransferJobRow extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "sheet_name", nullable = false, length = 40)
    private String sheetName;

    @Column(name = "source_row_number", nullable = false)
    private Integer rowNumber;

    @Column(name = "subject_key_hash", length = 64)
    private String subjectKeyHash;

    @Column(name = "subject_display", length = 32)
    private String subjectDisplay;

    @Column(name = "command_type", length = 30)
    private String commandType;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 20)
    private UserTransferValidationStatus validationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 20)
    private UserTransferExecutionStatus executionStatus = UserTransferExecutionStatus.PENDING;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "field_diff_json", length = 65_535)
    private String fieldDiffJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "grant_diff_json", length = 65_535)
    private String grantDiffJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "validation_errors_json", length = 65_535)
    private String validationErrorsJson;

    @Column(name = "execution_message", length = 1000)
    private String executionMessage;

    @Column(name = "before_version")
    private Long beforeVersion;

    @Column(name = "after_version")
    private Long afterVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
