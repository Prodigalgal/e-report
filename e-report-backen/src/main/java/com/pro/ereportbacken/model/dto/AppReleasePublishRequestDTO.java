package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.AppPlatform;
import com.pro.ereportbacken.model.pojo.AppEdition;

import java.util.UUID;

public record AppReleasePublishRequestDTO(
        AppPlatform platform,
        String channel,
        Integer versionCode,
        String versionName,
        String runtimeVersion,
        Integer minSupportedVersionCode,
        Boolean forceUpdate,
        String releaseNotes,
        UUID completedUploadId,
        AppEdition edition
) {
}
