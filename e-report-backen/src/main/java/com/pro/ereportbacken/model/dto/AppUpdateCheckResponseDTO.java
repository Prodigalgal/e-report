package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.AppPlatform;
import com.pro.ereportbacken.model.pojo.AppEdition;

import java.time.LocalDateTime;

public record AppUpdateCheckResponseDTO(
        boolean updateAvailable,
        boolean forceUpdate,
        boolean nativeUpgradeRequired,
        AppPlatform platform,
        String channel,
        Integer versionCode,
        String versionName,
        String runtimeVersion,
        Integer minSupportedVersionCode,
        String releaseNotes,
        String apkUrl,
        Long apkSize,
        String sha256,
        boolean rangeDownloadSupported,
        Long downloadChunkSize,
        LocalDateTime publishedAt,
        String message,
        AppEdition edition
) {
}
