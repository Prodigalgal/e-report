package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.AppPlatform;
import com.pro.ereportbacken.model.pojo.AppEdition;

import java.time.LocalDateTime;

public record AppReleaseDTO(
        UUID id,
        AppPlatform platform,
        String channel,
        Integer versionCode,
        String versionName,
        String runtimeVersion,
        Integer minSupportedVersionCode,
        Boolean forceUpdate,
        String releaseNotes,
        String originalFileName,
        String storedFileName,
        Long fileSize,
        String sha256,
        String contentType,
        Boolean published,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String apkUrl,
        AppEdition edition
) {
}

