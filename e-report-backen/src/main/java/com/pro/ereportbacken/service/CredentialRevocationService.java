package com.pro.ereportbacken.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Log4j2
public class CredentialRevocationService {

    private static final String SELF_PASSWORD_CHANGE_REASON = "用户修改密码";
    private static final String ADMIN_PASSWORD_RESET_REASON = "管理员重置密码";
    private static final String SECURITY_CONTROL_CHANGE_REASON = "账号管理权或保护级别变更";

    private final AuthSessionService authSessionService;
    private final ApiTokenService apiTokenService;
    private final TrustedDeviceService trustedDeviceService;

    public CredentialRevocationService(AuthSessionService authSessionService,
                                       ApiTokenService apiTokenService,
                                       TrustedDeviceService trustedDeviceService) {
        this.authSessionService = authSessionService;
        this.apiTokenService = apiTokenService;
        this.trustedDeviceService = trustedDeviceService;
    }

    @Transactional
    public RevocationSummary afterSelfPasswordChange(UUID currentSessionId,
                                                     UUID tenantId,
                                                     UUID membershipId) {
        int sessions = authSessionService.revokeAllAfterPasswordChange(
                currentSessionId, tenantId, membershipId);
        return revokeSecondaryCredentials(
                tenantId, membershipId, sessions, SELF_PASSWORD_CHANGE_REASON);
    }

    @Transactional
    public RevocationSummary afterAdminPasswordReset(UUID tenantId, UUID membershipId) {
        int sessions = authSessionService.revokeAllOther(
                tenantId, membershipId, null, ADMIN_PASSWORD_RESET_REASON);
        return revokeSecondaryCredentials(
                tenantId, membershipId, sessions, ADMIN_PASSWORD_RESET_REASON);
    }

    @Transactional
    public RevocationSummary afterSecurityControlChange(UUID tenantId, UUID membershipId) {
        int sessions = authSessionService.revokeAllOther(
                tenantId, membershipId, null, SECURITY_CONTROL_CHANGE_REASON);
        return revokeSecondaryCredentials(
                tenantId, membershipId, sessions, SECURITY_CONTROL_CHANGE_REASON);
    }

    private RevocationSummary revokeSecondaryCredentials(UUID tenantId,
                                                         UUID membershipId,
                                                         int revokedSessions,
                                                         String reason) {
        int revokedApiTokens = apiTokenService.revokeAllForCredentialChange(
                tenantId, membershipId, reason);
        int reauthenticationRequiredDevices = trustedDeviceService
                .requireReauthenticationAfterCredentialChange(tenantId, membershipId, reason);
        RevocationSummary summary = new RevocationSummary(
                revokedSessions, revokedApiTokens, reauthenticationRequiredDevices);
        log.info("Credential revocation completed; membershipId={}, sessions={}, apiTokens={}, trustedDevices={}",
                membershipId,
                summary.revokedSessions(),
                summary.revokedApiTokens(),
                summary.reauthenticationRequiredDevices());
        return summary;
    }

    public record RevocationSummary(int revokedSessions,
                                    int revokedApiTokens,
                                    int reauthenticationRequiredDevices) {
    }
}
