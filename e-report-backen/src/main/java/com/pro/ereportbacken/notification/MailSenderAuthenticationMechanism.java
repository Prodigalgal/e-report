package com.pro.ereportbacken.notification;

public enum MailSenderAuthenticationMechanism {
    AUTO,
    LOGIN,
    PLAIN,
    CRAM_MD5,
    DIGEST_MD5,
    NTLM,
    XOAUTH2;

    public String protocolName() {
        return switch (this) {
            case CRAM_MD5 -> "CRAM-MD5";
            case DIGEST_MD5 -> "DIGEST-MD5";
            default -> name();
        };
    }
}
