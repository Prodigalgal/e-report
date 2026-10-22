package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.AuthSessionViewDTO;
import com.pro.ereportbacken.model.dto.AuthSessionPageDTO;
import com.pro.ereportbacken.model.dto.PageResponseDTO;
import com.pro.ereportbacken.model.pojo.AuthSession;
import com.pro.ereportbacken.model.pojo.TrustedDevice;
import com.pro.ereportbacken.repository.TrustedDeviceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AuthSessionQueryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final AuthSessionService authSessionService;
    private final TrustedDeviceRepository trustedDeviceRepository;

    public AuthSessionQueryService(AuthSessionService authSessionService,
                                   TrustedDeviceRepository trustedDeviceRepository) {
        this.authSessionService = authSessionService;
        this.trustedDeviceRepository = trustedDeviceRepository;
    }

    @Transactional(readOnly = true)
    public AuthSessionPageDTO page(UUID tenantId,
                                   UUID membershipId,
                                   UUID currentSessionId,
                                   Pageable pageable) {
        Pageable bounded = bound(pageable);
        Page<AuthSession> sessions = authSessionService.page(tenantId, membershipId, bounded);
        Map<UUID, TrustedDevice> devices = trustedDevices(sessions.getContent());
        Page<AuthSessionViewDTO> views = sessions.map(session -> toView(
                session,
                devices.get(session.getTrustedDeviceId()),
                session.getId().equals(currentSessionId)
        ));
        return new AuthSessionPageDTO(
                PageResponseDTO.from(views),
                authSessionService.countActiveOther(tenantId, membershipId, currentSessionId)
        );
    }

    private Pageable bound(Pageable pageable) {
        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.min(Math.max(1, pageable.getPageSize()), MAX_PAGE_SIZE);
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Direction.DESC, "createdAt");
        return PageRequest.of(page, size, sort);
    }

    private Map<UUID, TrustedDevice> trustedDevices(java.util.List<AuthSession> sessions) {
        java.util.Set<UUID> deviceIds = sessions.stream()
                .map(AuthSession::getTrustedDeviceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return trustedDeviceRepository.findAllById(deviceIds).stream()
                .collect(Collectors.toMap(TrustedDevice::getId, Function.identity()));
    }

    private AuthSessionViewDTO toView(AuthSession session, TrustedDevice device, boolean current) {
        return new AuthSessionViewDTO(
                session.getId(),
                session.getClientType(),
                session.getStatus(),
                current,
                device == null ? fallback(session.getClientName(), "网页浏览器") : join(device.getManufacturer(), device.getModel()),
                device == null ? session.getClientDeviceName() : device.getModel(),
                device == null ? session.getClientOsVersion() : join("Android", device.getOsRelease()),
                device == null ? session.getClientAppVersion() : device.getAppVersion(),
                device != null && Boolean.TRUE.equals(device.getHardwareBacked()),
                device == null ? session.getClientSecuritySignals() : device.getSecuritySignals(),
                session.getCreatedAt(),
                session.getLastSeenAt(),
                session.getIdleExpiresAt(),
                session.getAbsoluteExpiresAt(),
                session.getRevokedAt(),
                session.getRevokeReason()
        );
    }

    private String join(String left, String right) {
        return java.util.stream.Stream.of(left, right)
                .filter(org.springframework.util.StringUtils::hasText)
                .collect(Collectors.joining(" "));
    }

    private String fallback(String value, String fallback) {
        return org.springframework.util.StringUtils.hasText(value) ? value : fallback;
    }
}
