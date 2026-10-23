package com.pro.ereportbacken.notification.delivery;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.util.MailConnectException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public record MailFailurePolicy(boolean retryable, AccountEffect accountEffect, String failureCode) {
    public enum AccountEffect { NONE, SUCCESS, TRANSIENT_FAILURE, AUTHENTICATION_FAILURE }

    public static MailFailurePolicy classify(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        var pending = new ArrayDeque<Throwable>();
        pending.add(failure);
        MailFailurePolicy smtpFailure = null;
        while (!pending.isEmpty()) {
            Throwable cause = pending.removeFirst();
            if (!visited.add(cause)) continue;
            if (cause instanceof MailAuthenticationException || cause instanceof AuthenticationFailedException) {
                return new MailFailurePolicy(true, AccountEffect.AUTHENTICATION_FAILURE, "AUTHENTICATION");
            }
            if (cause instanceof MailConnectException || cause instanceof ConnectException
                    || cause instanceof UnknownHostException) {
                return new MailFailurePolicy(true, AccountEffect.TRANSIENT_FAILURE, "CONNECTION");
            }
            if (cause instanceof SMTPAddressFailedException addressFailure
                    && hasRejectionResponse(cause)) {
                boolean temporary = addressFailure.getReturnCode() / 100 == 4;
                smtpFailure = new MailFailurePolicy(temporary, AccountEffect.NONE,
                        temporary ? "SMTP_TEMPORARY" : "RECIPIENT_REJECTED");
            } else if (cause instanceof SMTPSendFailedException sendFailure && smtpFailure == null
                    && hasRejectionResponse(cause)) {
                boolean temporary = sendFailure.getReturnCode() / 100 == 4;
                smtpFailure = new MailFailurePolicy(temporary,
                        temporary ? AccountEffect.TRANSIENT_FAILURE : AccountEffect.NONE,
                        temporary ? "SMTP_TEMPORARY" : "SMTP_REJECTED");
            }
            if (cause.getCause() != null) pending.add(cause.getCause());
            if (cause instanceof MessagingException messaging && messaging.getNextException() != null) {
                pending.add(messaging.getNextException());
            }
            if (cause instanceof MailSendException sendFailure) pending.addAll(sendFailure.getFailedMessages().values());
        }
        if (smtpFailure != null) return smtpFailure;
        // 没有明确拒绝响应的提交阶段故障不能安全自动重发。
        return new MailFailurePolicy(false, AccountEffect.NONE, EmailDeliveryFailure.classify(failure).name());
    }

    static boolean hasRejectionResponse(Throwable failure) {
        int response = switch (failure) {
            case SMTPAddressFailedException rejected -> rejected.getReturnCode();
            case SMTPSendFailedException rejected -> rejected.getReturnCode();
            default -> 0;
        };
        return response >= 400 && response < 600;
    }
}
