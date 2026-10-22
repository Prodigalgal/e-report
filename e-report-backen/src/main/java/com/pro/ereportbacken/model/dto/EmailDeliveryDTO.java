package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.notification.delivery.EmailDeliveryCategory;
import com.pro.ereportbacken.notification.delivery.EmailDeliveryStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record EmailDeliveryDTO(UUID id, UUID orgUnitId, String orgUnitName,
                               EmailDeliveryCategory category, EmailDeliveryStatus status,
                               String subject, String recipientAddress, String senderAddress,
                               UUID relatedId, UUID recipientUserId, LocalDateTime startedAt,
                               LocalDateTime completedAt, Long durationMs,
                               String failureCode, String failureMessage) {
}
