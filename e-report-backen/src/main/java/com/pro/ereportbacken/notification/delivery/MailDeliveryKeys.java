package com.pro.ereportbacken.notification.delivery;

import com.pro.ereportbacken.notification.MailSenderConfigurationService.MailSenderAccountConfiguration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class MailDeliveryKeys {
    private MailDeliveryKeys() {
    }

    public static String account(MailSenderAccountConfiguration sender) {
        String identity = sender.smtpAuth() ? sender.username() : sender.fromAddress();
        return digest(sender.host().trim().toLowerCase(Locale.ROOT), identity.trim().toLowerCase(Locale.ROOT));
    }

    public static String dispatch(String notificationKey, EmailDeliveryContext context) {
        return digest(context.tenantId().toString(), context.category().name(), notificationKey,
                context.recipientAddress().trim().toLowerCase(Locale.ROOT));
    }

    private static String digest(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':');
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("无法计算邮件投递标识。", failure);
        }
    }
}
