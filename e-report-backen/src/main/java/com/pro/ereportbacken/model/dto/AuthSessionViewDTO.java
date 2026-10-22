package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.AuthClientType;
import com.pro.ereportbacken.model.pojo.AuthSessionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuthSessionViewDTO(
        UUID id,
        AuthClientType clientType,
        AuthSessionStatus status,
        boolean current,
        String deviceName,
        String deviceModel,
        String osVersion,
        String appVersion,
        boolean hardwareBacked,
        String securitySignals,
        LocalDateTime createdAt,
        LocalDateTime lastSeenAt,
        LocalDateTime idleExpiresAt,
        LocalDateTime absoluteExpiresAt,
        LocalDateTime revokedAt,
        String revokeReason
) {
}
