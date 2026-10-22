package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.ExpoUpdate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ExpoUpdateDTO(UUID id, String environment, String runtimeVersion, String versionName,
                            String sourceSha, String kind, String sha256, LocalDateTime createdAt,
                            LocalDateTime publishedAt, String status, String failureMessage) {
    public static ExpoUpdateDTO from(ExpoUpdate update) {
        return new ExpoUpdateDTO(update.getId(), update.getEnvironment(), update.getRuntimeVersion(),
                update.getVersionName(), update.getSourceSha(), update.getKind(), update.getSha256(),
                update.getCreatedAt(), update.getPublishedAt(), update.getStatus(), update.getFailureMessage());
    }
}
