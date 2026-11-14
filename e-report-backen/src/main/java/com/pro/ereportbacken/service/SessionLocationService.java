package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.SessionLocationReportRequestDTO;
import com.pro.ereportbacken.model.pojo.AuthSession;
import com.pro.ereportbacken.model.pojo.AuthSessionStatus;
import com.pro.ereportbacken.repository.AuthSessionRepository;
import com.pro.ereportbacken.time.UtcTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * 登录态最新位置上报。每个会话只保留一条最新坐标，重复上报为覆盖更新，不产生历史轨迹。
 */
@Service
public class SessionLocationService {

    private static final String COORDINATE_SYSTEM_WGS84 = "wgs84";

    private final AuthSessionRepository authSessionRepository;

    public SessionLocationService(AuthSessionRepository authSessionRepository) {
        this.authSessionRepository = authSessionRepository;
    }

    @Transactional
    public void report(UUID sessionId, String clientIp, SessionLocationReportRequestDTO request) {
        AuthSession session = authSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("登录会话不存在或已被撤销。"));
        if (session.getStatus() != AuthSessionStatus.ACTIVE) {
            throw new IllegalStateException("登录会话不是活跃状态，已拒绝位置上报。");
        }

        LocalDateTime now = UtcTime.now();
        session.setLocationLatitude(request.latitude());
        session.setLocationLongitude(request.longitude());
        session.setLocationCoordinateSystem(COORDINATE_SYSTEM_WGS84);
        session.setLocationAccuracyMeters(request.accuracyMeters());
        session.setLocationProvider(limit(request.provider(), 40));
        // 客户端上报的定位时刻不可信，早于当前记录或来自未来时收敛为服务端接收时刻。
        session.setLocationFixedAt(resolveFixedAt(request.fixedAt(), now));
        session.setLocationReportedAt(now);
        if (StringUtils.hasText(clientIp)) {
            session.setClientIp(limit(clientIp, 64));
        }
        authSessionRepository.save(session);
    }

    private LocalDateTime resolveFixedAt(LocalDateTime reportedFixedAt, LocalDateTime now) {
        if (reportedFixedAt == null || reportedFixedAt.isAfter(now)) {
            return now;
        }
        return reportedFixedAt;
    }

    private String limit(String value, int maximumLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maximumLength ? trimmed : trimmed.substring(0, maximumLength);
    }
}
