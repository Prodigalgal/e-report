package com.pro.ereportbacken.notification.delivery;

import java.time.Instant;

public record EmailDispatchResult(EmailDeliveryStatus status, Instant retryAt, String failureCode) {
    public static EmailDispatchResult accepted() {
        return new EmailDispatchResult(EmailDeliveryStatus.SMTP_ACCEPTED, null, null);
    }

    public static EmailDispatchResult deferred(Instant retryAt) {
        return new EmailDispatchResult(EmailDeliveryStatus.SENDING, retryAt, null);
    }

    public static EmailDispatchResult failed(String code) {
        return new EmailDispatchResult(EmailDeliveryStatus.FAILED, null, code);
    }
}
