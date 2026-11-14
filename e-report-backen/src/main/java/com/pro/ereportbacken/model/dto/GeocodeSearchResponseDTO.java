package com.pro.ereportbacken.model.dto;

import java.util.List;

/**
 * 地址正向地理编码结果（地址 → 坐标）。
 *
 * <p>与逆地理编码互补：用户在无法到达现场时，可输入已知地址或 POI 名称定位到坐标，
 * 而不必自行获取经纬度。返回坐标统一为 WGS84（系统存储契约），并附带 GCJ-02 供地图渲染核对。</p>
 */
public record GeocodeSearchResponseDTO(
        boolean found,
        /** 系统存储契约值「经度,纬度」，WGS84。 */
        String gpsCoordinates,
        Double wgs84Longitude,
        Double wgs84Latitude,
        /** 高德渲染坐标 GCJ-02。 */
        Double gcj02Longitude,
        Double gcj02Latitude,
        String formattedAddress,
        List<GeocodeCandidateDTO> candidates,
        String message
) {

    public record GeocodeCandidateDTO(
            String name,
            String address,
            String district,
            String city,
            String adcode,
            String gpsCoordinates,
            Double wgs84Longitude,
            Double wgs84Latitude,
            Double gcj02Longitude,
            Double gcj02Latitude
    ) {
    }

    public static GeocodeSearchResponseDTO notFound(String message) {
        return new GeocodeSearchResponseDTO(false, null, null, null, null, null, null, List.of(), message);
    }
}
