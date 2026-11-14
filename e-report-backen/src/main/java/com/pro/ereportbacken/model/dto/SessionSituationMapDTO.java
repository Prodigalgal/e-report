package com.pro.ereportbacken.model.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 登录态态势地图数据。坐标统一为 GCJ-02，供高德渲染直接使用，避免前端重复转换。
 */
public record SessionSituationMapDTO(
        List<OnlineUserMarkerDTO> onlineUsers,
        List<FaultPointMarkerDTO> faultPoints,
        SituationMapSummaryDTO summary,
        SituationMapBoundsDTO bounds,
        MapClientConfigDTO mapConfig,
        LocalDateTime generatedAt
) {

    /**
     * 前端地图渲染所需的客户端配置。
     *
     * <p>高德 Web 端 JS API 的 key 必须出现在浏览器中，无法保密，真正的访问控制是高德控制台的域名白名单；
     * 这里只向已持有地点查看权限的账号下发，避免匿名访问获取。安全密钥同样必须随前端下发，
     * 否则高德接口会返回 INVALID_USER_SCODE。</p>
     */
    public record MapClientConfigDTO(
            String provider,
            String jsApiKey,
            String securityJsCode,
            /** 是否已完成配置；为 false 时前端应展示「地图未配置」而不是空白画布。 */
            boolean configured
    ) {
    }

    public record OnlineUserMarkerDTO(
            UUID sessionId,
            UUID userId,
            String username,
            String displayName,
            String contactPhone,
            String organizationName,
            String departmentName,
            String clientType,
            String deviceName,
            String deviceModel,
            String osVersion,
            String appVersion,
            String clientIp,
            /** GCJ-02 经度，null 表示该会话尚未上报有效位置。 */
            Double longitude,
            /** GCJ-02 纬度，null 表示该会话尚未上报有效位置。 */
            Double latitude,
            Double accuracyMeters,
            String locationProvider,
            /** 原始上报坐标系，恒为 wgs84；渲染坐标为上面的 GCJ-02。 */
            String sourceCoordinateSystem,
            LocalDateTime locationFixedAt,
            LocalDateTime locationReportedAt,
            LocalDateTime loginAt,
            LocalDateTime lastSeenAt,
            String status
    ) {
    }

    public record FaultPointMarkerDTO(
            UUID workOrderId,
            String workOrderNo,
            String reportType,
            String reportTypeName,
            String statusLabel,
            String priorityName,
            String slaRiskName,
            String location,
            /** GCJ-02 经度。 */
            Double longitude,
            /** GCJ-02 纬度。 */
            Double latitude,
            LocalDateTime reportTime,
            LocalDateTime lastUpdatedTime
    ) {
    }

    public record SituationMapSummaryDTO(
            int onlineUserCount,
            int locatedUserCount,
            int unlocatedUserCount,
            int faultPointCount,
            int faultPointWithoutCoordinateCount
    ) {
    }

    /** 有效点位的外接矩形，供地图默认视野与聚合缩放使用。 */
    public record SituationMapBoundsDTO(
            Double minLongitude,
            Double minLatitude,
            Double maxLongitude,
            Double maxLatitude
    ) {
    }
}
