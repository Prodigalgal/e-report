package com.pro.ereportbacken.model.dto;

import java.util.UUID;

public record MailSenderAccountDTO(
        UUID id,
        String host,
        Integer port,
        String username,
        String fromAddress,
        Boolean smtpAuth,
        Boolean sslEnabled,
        Boolean startTlsEnabled,
        boolean passwordConfigured,
        String providerId,
        String authenticationMode,
        String authMechanism,
        Boolean startTlsRequired,
        String ehloDomain,
        String authDomain,
        String saslRealm,
        String sslProtocols,
        Integer connectionTimeoutMs,
        Integer readTimeoutMs,
        Integer writeTimeoutMs,
        String oauthClientId,
        String oauthTenantId,
        String oauthScope,
        boolean oauthClientSecretConfigured,
        boolean oauthRefreshTokenConfigured,
        String sourceScope,
        UUID sourceOrgUnitId,
        String sourceOrgUnitName,
        Integer sendIntervalSeconds
) {
}
