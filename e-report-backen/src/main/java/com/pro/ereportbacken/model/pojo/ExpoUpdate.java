package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "expo_updates", indexes = @Index(name = "idx_expo_updates_latest",
        columnList = "environment, runtime_version, created_at"))
@Getter
@Setter
public class ExpoUpdate {
    @Id
    private UUID id;
    @Column(nullable = false, length = 20)
    private String environment;
    @Column(name = "runtime_version", nullable = false, length = 100)
    private String runtimeVersion;
    @Column(name = "version_name", nullable = false, length = 50)
    private String versionName;
    @Column(name = "source_sha", nullable = false, length = 40)
    private String sourceSha;
    @Column(nullable = false, length = 20)
    private String kind;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
    @Column(nullable = false, length = 1024)
    private String signature;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "failure_message", length = 255)
    private String failureMessage;
    @Column(name = "attempt_id")
    private UUID attemptId;
    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}
