package com.pro.ereportbacken.model.dto;

import java.util.List;

/**
 * 坐标系识别与转换结果。
 *
 * <p>WGS84 与 GCJ-02 在中国大陆相差数百米，仅凭数值无法可靠区分，因此结果可能包含多个候选，
 * 由用户在界面上确认。系统最终存储一律使用 WGS84 与「经度,纬度」契约。</p>
 */
public record CoordinateRecognitionResponseDTO(
        boolean recognized,
        /** 被采纳的坐标系标准 code；存在多个候选且未指定时可能为空。 */
        String standard,
        String standardDisplayName,
        boolean confident,
        /** 采纳结果：WGS84 经度。 */
        Double wgs84Longitude,
        Double wgs84Latitude,
        /** 系统存储契约值「经度,纬度」，WGS84。 */
        String gpsCoordinates,
        /** 采纳结果对应的 GCJ-02，供高德地图展示与核对。 */
        Double gcj02Longitude,
        Double gcj02Latitude,
        /** 采纳坐标系下的原样输入值，用于界面展示转换前后对比。 */
        Double sourceLongitude,
        Double sourceLatitude,
        String address,
        String township,
        List<CoordinateStandardCandidateDTO> candidates,
        List<String> notes,
        String message
) {

    public record CoordinateStandardCandidateDTO(
            String standard,
            String standardDisplayName,
            Double sourceLongitude,
            Double sourceLatitude,
            Double wgs84Longitude,
            Double wgs84Latitude,
            Double gcj02Longitude,
            Double gcj02Latitude,
            String gpsCoordinates,
            /** 该候选相对首选结果的偏移距离（米），用于展示差异量级。 */
            Double offsetFromPreferredMeters,
            String address,
            String township,
            boolean preferred,
            boolean addressResolved,
            String note
    ) {
    }
}
