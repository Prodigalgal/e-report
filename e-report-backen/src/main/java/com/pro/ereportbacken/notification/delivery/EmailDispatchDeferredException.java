package com.pro.ereportbacken.notification.delivery;

import java.time.Instant;

public final class EmailDispatchDeferredException extends RuntimeException {
    private final Instant retryAt;

    public EmailDispatchDeferredException(Instant retryAt) {
        super("邮件发送已按账号冷却或失败退避延后。");
        this.retryAt = retryAt;
    }

    public Instant retryAt() {
        return retryAt;
    }
}
