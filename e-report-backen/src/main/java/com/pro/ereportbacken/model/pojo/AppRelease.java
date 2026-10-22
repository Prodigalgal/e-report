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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "app_releases",
        indexes = {
                @Index(name = "idx_app_releases_latest", columnList = "edition, platform, channel, published, version_code"),
                @Index(name = "idx_app_releases_channel_time", columnList = "edition, platform, channel, published_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_app_releases_version", columnNames = {"edition", "platform", "channel", "version_code"})
        }
)
@Getter
@Setter
public class AppRelease extends UuidV7Entity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppEdition edition = AppEdition.NATIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppPlatform platform = AppPlatform.ANDROID;

    @Column(nullable = false, length = 50)
    private String channel = "stable";

    @Column(name = "version_code", nullable = false)
    private Integer versionCode;

    @Column(name = "version_name", nullable = false, length = 50)
    private String versionName;

    @Column(name = "runtime_version", nullable = false, length = 100)
    private String runtimeVersion;

    @Column(name = "min_supported_version_code", nullable = false)
    private Integer minSupportedVersionCode = 0;

    @Column(name = "force_update", nullable = false)
    private Boolean forceUpdate = false;

    @Column(name = "release_notes", columnDefinition = "TEXT")
    private String releaseNotes;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "stored_file_name", nullable = false, length = 255)
    private String storedFileName;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType = "application/vnd.android.package-archive";

    @Column(nullable = false)
    private Boolean published = true;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

