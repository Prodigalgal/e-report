package com.pro.ereportbacken.service;

import com.pro.ereportbacken.config.AppConfigConstant;
import com.pro.ereportbacken.model.dto.SessionSituationMapDTO;
import com.pro.ereportbacken.model.dto.WorkOrderFilterDTO;
import com.pro.ereportbacken.model.dto.WorkOrderListItemDTO;
import com.pro.ereportbacken.model.pojo.AuthSession;
import com.pro.ereportbacken.model.pojo.AuthSessionStatus;
import com.pro.ereportbacken.model.pojo.Department;
import com.pro.ereportbacken.model.pojo.OrgUnit;
import com.pro.ereportbacken.model.pojo.TrustedDevice;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.repository.AuthSessionRepository;
import com.pro.ereportbacken.repository.DepartmentRepository;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.TrustedDeviceRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.time.UtcTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 登录态态势地图聚合：在线用户点位与工单故障点。
 *
 * <p>在线用户按“活跃会话 + 最近上报过位置”判定，仅保留最新位置；
 * 故障点复用既有工单可见范围，不新开更宽的读取通路。</p>
 */
@Service
public class SessionSituationMapService {

    /** 活跃会话的判定窗口，与登录会话的最后活跃时间口径一致。 */
    private static final Duration ACTIVE_SESSION_WINDOW = Duration.ofHours(24);
    private static final int MAX_FAULT_POINTS = 500;

    private final AuthSessionRepository authSessionRepository;
    private final UserRepository userRepository;
    private final TrustedDeviceRepository trustedDeviceRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final DepartmentRepository departmentRepository;
    private final TenantContext tenantContext;
    private final OrganizationScopeAuthorizationService scopeAuthorizationService;
    private final WorkOrderService workOrderService;
    private final MapService mapService;
    private final AppConfigService appConfigService;

    public SessionSituationMapService(AuthSessionRepository authSessionRepository,
                                      UserRepository userRepository,
                                      TrustedDeviceRepository trustedDeviceRepository,
                                      OrgUnitRepository orgUnitRepository,
                                      DepartmentRepository departmentRepository,
                                      TenantContext tenantContext,
                                      OrganizationScopeAuthorizationService scopeAuthorizationService,
                                      WorkOrderService workOrderService,
                                      MapService mapService,
                                      AppConfigService appConfigService) {
        this.authSessionRepository = authSessionRepository;
        this.userRepository = userRepository;
        this.trustedDeviceRepository = trustedDeviceRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.departmentRepository = departmentRepository;
        this.tenantContext = tenantContext;
        this.scopeAuthorizationService = scopeAuthorizationService;
        this.workOrderService = workOrderService;
        this.mapService = mapService;
        this.appConfigService = appConfigService;
    }

    @Transactional(readOnly = true)
    public SessionSituationMapDTO loadSituationMap() {
        UUID tenantId = tenantContext.requireTenantId();
        // 在线用户与会话位置共享同一数据域，避免出现“能看位置但看不到人”的口径分裂。
        OrganizationScopeAuthorizationService.OrganizationAccess access =
                scopeAuthorizationService.accessFor(PermissionCatalog.USER_SESSION_LOCATION_VIEW);

        List<AuthSession> sessions = loadScopedSessions(tenantId, access);
        List<SessionSituationMapDTO.OnlineUserMarkerDTO> onlineUsers = buildOnlineUsers(sessions);
        List<SessionSituationMapDTO.FaultPointMarkerDTO> faultPoints = loadVisibleFaultPoints();

        int locatedUserCount = (int) onlineUsers.stream()
                .filter(marker -> marker.longitude() != null && marker.latitude() != null)
                .count();
        List<SessionSituationMapDTO.FaultPointMarkerDTO> locatedFaultPoints = faultPoints.stream()
                .filter(point -> point.longitude() != null && point.latitude() != null)
                .toList();
        SessionSituationMapDTO.SituationMapSummaryDTO summary = new SessionSituationMapDTO.SituationMapSummaryDTO(
                onlineUsers.size(),
                locatedUserCount,
                onlineUsers.size() - locatedUserCount,
                faultPoints.size(),
                faultPoints.size() - locatedFaultPoints.size()
        );

        return new SessionSituationMapDTO(
                onlineUsers,
                faultPoints,
                summary,
                bounds(onlineUsers, locatedFaultPoints),
                mapClientConfig(),
                UtcTime.now()
        );
    }

    /**
     * 地图渲染所需的前端凭据。
     *
     * 报单页的坐标提取器同样需要它，而报单人员并不持有登录态地图查看权限，
     * 因此该配置必须能单独下发，不能只挂在管理员地图接口上。
     */
    public SessionSituationMapDTO.MapClientConfigDTO mapClientConfig() {
        String jsApiKey = appConfigService.getConfigValue(AppConfigConstant.AMAP_WEB_JS_API_KEY, "");
        String securityCode = appConfigService.getConfigValue(AppConfigConstant.AMAP_WEB_JS_SECURITY_CODE, "");
        return new SessionSituationMapDTO.MapClientConfigDTO(
                "amap",
                StringUtils.hasText(jsApiKey) ? jsApiKey.trim() : null,
                StringUtils.hasText(securityCode) ? securityCode.trim() : null,
                StringUtils.hasText(jsApiKey)
        );
    }

    private List<AuthSession> loadScopedSessions(UUID tenantId,
                                                OrganizationScopeAuthorizationService.OrganizationAccess access) {
        LocalDateTime activeSince = UtcTime.now().minus(ACTIVE_SESSION_WINDOW);
        if (access.tenantWide()) {
            return authSessionRepository.findActiveLocatedSessions(tenantId, activeSince, null);
        }
        if (access.orgUnitIds().isEmpty()) {
            return List.of();
        }
        // 由数据库按组织范围裁剪成员，避免把整个租户的用户载入内存后再过滤。
        Set<UUID> scopedMemberIds = new LinkedHashSet<>(
                userRepository.findIdsByTenantIdAndOrgUnitIdIn(tenantId, access.orgUnitIds()));
        if (scopedMemberIds.isEmpty()) {
            return List.of();
        }
        return authSessionRepository.findActiveLocatedSessions(tenantId, activeSince, scopedMemberIds);
    }

    private List<SessionSituationMapDTO.OnlineUserMarkerDTO> buildOnlineUsers(List<AuthSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }

        Set<UUID> memberIds = sessions.stream()
                .map(AuthSession::getMembershipId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<UUID, User> users = userRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));

        Set<UUID> deviceIds = sessions.stream()
                .map(AuthSession::getTrustedDeviceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<UUID, TrustedDevice> devices = deviceIds.isEmpty()
                ? Map.of()
                : trustedDeviceRepository.findAllById(deviceIds).stream()
                        .collect(Collectors.toMap(TrustedDevice::getId, Function.identity(), (left, right) -> left));

        Map<UUID, String> orgNames = orgNames(users.values().stream()
                .map(User::getOrgUnitId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new)));
        Map<UUID, String> departmentNames = departmentNames(users.values().stream()
                .map(User::getDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new)));

        List<SessionSituationMapDTO.OnlineUserMarkerDTO> markers = new ArrayList<>(sessions.size());
        for (AuthSession session : sessions) {
            User user = users.get(session.getMembershipId());
            TrustedDevice device = session.getTrustedDeviceId() == null
                    ? null
                    : devices.get(session.getTrustedDeviceId());
            markers.add(new SessionSituationMapDTO.OnlineUserMarkerDTO(
                    session.getId(),
                    session.getMembershipId(),
                    user == null ? null : user.getUsername(),
                    user == null ? session.getClientName() : user.getName(),
                    user == null ? null : user.getPhone(),
                    user == null || user.getOrgUnitId() == null ? null : orgNames.get(user.getOrgUnitId()),
                    user == null || user.getDepartmentId() == null ? null : departmentNames.get(user.getDepartmentId()),
                    session.getClientType() == null ? null : session.getClientType().name(),
                    resolveDeviceName(session, device),
                    device == null ? session.getClientDeviceName() : device.getModel(),
                    device == null ? session.getClientOsVersion() : join("Android", device.getOsRelease()),
                    device == null ? session.getClientAppVersion() : device.getAppVersion(),
                    session.getClientIp(),
                    toGcjLongitude(session),
                    toGcjLatitude(session),
                    session.getLocationAccuracyMeters(),
                    session.getLocationProvider(),
                    session.getLocationCoordinateSystem(),
                    session.getLocationFixedAt(),
                    session.getLocationReportedAt(),
                    session.getCreatedAt(),
                    session.getLastSeenAt(),
                    session.getStatus() == null ? null : session.getStatus().name()
            ));
        }
        markers.sort(Comparator.comparing(
                SessionSituationMapDTO.OnlineUserMarkerDTO::locationReportedAt,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));
        return markers;
    }

    private List<SessionSituationMapDTO.FaultPointMarkerDTO> loadVisibleFaultPoints() {
        WorkOrderFilterDTO filter = new WorkOrderFilterDTO();
        Page<WorkOrderListItemDTO> page = workOrderService.searchVisibleWorkOrders(
                filter, PageRequest.of(0, MAX_FAULT_POINTS));
        return page.getContent().stream()
                .map(this::toFaultPoint)
                .toList();
    }

    private SessionSituationMapDTO.FaultPointMarkerDTO toFaultPoint(WorkOrderListItemDTO workOrder) {
        Coordinates coordinates = parseStoredWgs84(workOrder.getGpsCoordinates());
        return new SessionSituationMapDTO.FaultPointMarkerDTO(
                workOrder.getId(),
                workOrder.getWorkOrderNo(),
                workOrder.getReportType(),
                workOrder.getReportTypeName(),
                workOrder.getStatusLabel(),
                workOrder.getPriorityName(),
                workOrder.getSlaRiskName(),
                workOrder.getLocation(),
                coordinates == null ? null : coordinates.longitude(),
                coordinates == null ? null : coordinates.latitude(),
                workOrder.getReportTime(),
                workOrder.getLastUpdatedTime()
        );
    }

    /**
     * 工单坐标以 WGS84 存储，地图渲染需要 GCJ-02。坐标非法时返回 null，由前端标注为“缺少坐标”，
     * 而不是退回原始值在地图上制造偏移数百米的假点位。
     */
    private Coordinates parseStoredWgs84(String gpsCoordinates) {
        if (!StringUtils.hasText(gpsCoordinates)) {
            return null;
        }
        String[] parts = gpsCoordinates.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            double longitude = Double.parseDouble(parts[0].trim());
            double latitude = Double.parseDouble(parts[1].trim());
            if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                    || Math.abs(longitude) > 180 || Math.abs(latitude) > 90) {
                return null;
            }
            MapService.Point gcj = mapService.wgs84ToGcj02(latitude, longitude);
            return new Coordinates(gcj.lon, gcj.lat);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Double toGcjLongitude(AuthSession session) {
        Coordinates coordinates = toGcjCoordinates(session);
        return coordinates == null ? null : coordinates.longitude();
    }

    private Double toGcjLatitude(AuthSession session) {
        Coordinates coordinates = toGcjCoordinates(session);
        return coordinates == null ? null : coordinates.latitude();
    }

    private Coordinates toGcjCoordinates(AuthSession session) {
        Double latitude = session.getLocationLatitude();
        Double longitude = session.getLocationLongitude();
        if (latitude == null || longitude == null
                || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(longitude) > 180 || Math.abs(latitude) > 90) {
            return null;
        }
        MapService.Point gcj = mapService.wgs84ToGcj02(latitude, longitude);
        return new Coordinates(gcj.lon, gcj.lat);
    }

    private Map<UUID, String> orgNames(Set<UUID> orgUnitIds) {
        if (orgUnitIds.isEmpty()) {
            return Map.of();
        }
        return orgUnitRepository.findAllById(orgUnitIds).stream()
                .collect(Collectors.toMap(OrgUnit::getId, OrgUnit::getName, (left, right) -> left));
    }

    private Map<UUID, String> departmentNames(Set<UUID> departmentIds) {
        if (departmentIds.isEmpty()) {
            return Map.of();
        }
        return departmentRepository.findAllById(departmentIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName, (left, right) -> left));
    }

    private String resolveDeviceName(AuthSession session, TrustedDevice device) {
        if (device != null) {
            String deviceName = join(device.getManufacturer(), device.getModel());
            if (StringUtils.hasText(deviceName)) {
                return deviceName;
            }
        }
        return StringUtils.hasText(session.getClientName()) ? session.getClientName() : "网页浏览器";
    }

    private String join(String left, String right) {
        return java.util.stream.Stream.of(left, right)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" "));
    }

    private SessionSituationMapDTO.SituationMapBoundsDTO bounds(
            List<SessionSituationMapDTO.OnlineUserMarkerDTO> onlineUsers,
            List<SessionSituationMapDTO.FaultPointMarkerDTO> faultPoints) {
        List<Coordinates> points = new ArrayList<>();
        onlineUsers.forEach(marker -> {
            if (marker.longitude() != null && marker.latitude() != null) {
                points.add(new Coordinates(marker.longitude(), marker.latitude()));
            }
        });
        faultPoints.forEach(point -> {
            if (point.longitude() != null && point.latitude() != null) {
                points.add(new Coordinates(point.longitude(), point.latitude()));
            }
        });
        if (points.isEmpty()) {
            return null;
        }
        Double minLongitude = points.stream().map(Coordinates::longitude).min(Double::compareTo).orElse(null);
        Double maxLongitude = points.stream().map(Coordinates::longitude).max(Double::compareTo).orElse(null);
        Double minLatitude = points.stream().map(Coordinates::latitude).min(Double::compareTo).orElse(null);
        Double maxLatitude = points.stream().map(Coordinates::latitude).max(Double::compareTo).orElse(null);
        return new SessionSituationMapDTO.SituationMapBoundsDTO(minLongitude, minLatitude, maxLongitude, maxLatitude);
    }



    private record Coordinates(double longitude, double latitude) {
    }
}
