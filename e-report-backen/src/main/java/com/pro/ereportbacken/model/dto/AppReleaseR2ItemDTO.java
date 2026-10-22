package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.AppPlatform;
import com.pro.ereportbacken.model.pojo.AppEdition;

import java.time.Instant;
import java.time.LocalDateTime;

public record AppReleaseR2ItemDTO(
        String objectKey,
        String status,
        UUID releaseId,
        AppPlatform platform,
        String channel,
        Integer versionCode,
        String versionName,
        String runtimeVersion,
        Integer minSupportedVersionCode,
        Boolean forceUpdate,
        String releaseNotes,
        LocalDateTime updatedAt,
        Long expectedSize,
        Long objectSize,
        String sha256,
        String etag,
        Instant lastModified,
        String publicUrl,
        boolean published,
        boolean current,
        String operationMessage,
        AppEdition edition
) {
}

