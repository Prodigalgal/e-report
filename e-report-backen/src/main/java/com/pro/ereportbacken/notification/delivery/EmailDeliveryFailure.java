package com.pro.ereportbacken.notification.delivery;

import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import jakarta.mail.MessagingException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.SocketException;
import java.io.EOFException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Set;

public enum EmailDeliveryFailure {
    AUTHENTICATION("SMTP 认证失败，请检查发件账号、授权码或 OAuth 配置。"),
    CONNECTION("无法连接 SMTP 服务器，请检查服务器地址、端口和网络。"),
    TIMEOUT("SMTP 请求超时，本次发送未正常完成；收件人仍可能收到邮件。"),
    INCOMPLETE("发送中断或未取得明确结果，可能因连接中断、服务重启或结果记录失败；收件人仍可能收到邮件。"),
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
        boolean connectionInterrupted = false;
        boolean confirmedRejection = false;
        EmailDeliveryFailure transportFailure = null;
        while (!pending.isEmpty()) {
            Throwable cause = pending.removeFirst();
            if (!visited.add(cause)) continue;
            if (transportFailure == null) {
                if (cause instanceof MailAuthenticationException) transportFailure = AUTHENTICATION;
                else if (cause instanceof SocketTimeoutException) transportFailure = TIMEOUT;
                else if (cause instanceof ConnectException || cause instanceof UnknownHostException) transportFailure = CONNECTION;
            }
            confirmedRejection |= MailFailurePolicy.hasRejectionResponse(cause);
            if (cause instanceof SocketException || cause instanceof EOFException) connectionInterrupted = true;
            if (cause.getCause() != null) pending.add(cause.getCause());
            if (cause instanceof MessagingException messaging && messaging.getNextException() != null) pending.add(messaging.getNextException());
            if (cause instanceof MailSendException sendFailure) pending.addAll(sendFailure.getFailedMessages().values());
        }
        if (confirmedRejection && transportFailure != AUTHENTICATION) return DELIVERY;
        if (transportFailure != null) return transportFailure;
        return connectionInterrupted ? INCOMPLETE : DELIVERY;
    }
}
