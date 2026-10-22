package com.pro.ereportbacken.notification.delivery;

import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import jakarta.mail.MessagingException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Set;

public enum EmailDeliveryFailure {
    AUTHENTICATION("SMTP 认证失败，请检查发件账号、授权码或 OAuth 配置。"),
    CONNECTION("无法连接 SMTP 服务器，请检查服务器地址、端口和网络。"),
    TIMEOUT("SMTP 请求超时，服务器是否接受邮件暂不能确认。"),
    CONFIGURATION("无法取得可用发件配置，请检查发件池及认证配置。"),
    DELIVERY("邮件构建或发送失败，请结合记录编号检查服务端日志。");

    private final String message;

    EmailDeliveryFailure(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }

    public static EmailDeliveryFailure classify(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        var pending = new ArrayDeque<Throwable>();
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable cause = pending.removeFirst();
            if (!visited.add(cause)) continue;
            if (cause instanceof MailAuthenticationException) return AUTHENTICATION;
            if (cause instanceof SocketTimeoutException) return TIMEOUT;
            if (cause instanceof ConnectException || cause instanceof UnknownHostException) return CONNECTION;
            if (cause.getCause() != null) pending.add(cause.getCause());
            if (cause instanceof MessagingException messaging && messaging.getNextException() != null) pending.add(messaging.getNextException());
            if (cause instanceof MailSendException sendFailure) pending.addAll(sendFailure.getFailedMessages().values());
        }
        return DELIVERY;
    }
}
