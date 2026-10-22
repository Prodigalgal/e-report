package com.pro.ereportbacken.model.dto;

import java.util.List;

public record AppReleaseR2OverviewDTO(
        boolean enabled,
        String bucketName,
        String publicBaseUrl,
        int objectCount,
        int releaseCount,
        int synchronizedCount,
        int missingCount,
        int mismatchCount,
        int orphanCount,
        List<AppReleaseR2ItemDTO> items
) {
}
