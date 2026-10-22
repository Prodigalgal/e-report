package com.pro.ereportbacken.notification.delivery;

import java.util.UUID;

public record EmailDeliveryContext(
        UUID tenantId,
        UUID orgUnitId,
        EmailDeliveryCategory category,
        UUID relatedId,
        UUID recipientUserId,
        String recipientAddress,
        String subject
) {
}
