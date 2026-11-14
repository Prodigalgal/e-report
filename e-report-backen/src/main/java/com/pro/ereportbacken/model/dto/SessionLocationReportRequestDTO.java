package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 客户端登录态位置上报。坐标一律以 WGS84 提交，避免各端坐标系差异写入不一致的数据。
 */
public record SessionLocationReportRequestDTO(

        @NotNull
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        Double latitude,

        @NotNull
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        Double longitude,

        @Positive
        Double accuracyMeters,

        @Size(max = 20)
        String provider,

        LocalDateTime fixedAt
) {
}
