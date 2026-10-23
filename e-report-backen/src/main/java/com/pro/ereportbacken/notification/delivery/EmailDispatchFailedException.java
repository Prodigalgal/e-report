package com.pro.ereportbacken.notification.delivery;

public final class EmailDispatchFailedException extends RuntimeException {
    public EmailDispatchFailedException(int failureCount) {
        super("邮件有 " + failureCount + " 个收件人投递失败，已停止自动重发；请查看邮件台账。");
    }
}
