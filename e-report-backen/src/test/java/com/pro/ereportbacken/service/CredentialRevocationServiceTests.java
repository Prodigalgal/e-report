package com.pro.ereportbacken.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CredentialRevocationServiceTests {

    private final AuthSessionService authSessionService = mock(AuthSessionService.class);
    private final ApiTokenService apiTokenService = mock(ApiTokenService.class);
    private final TrustedDeviceService trustedDeviceService = mock(TrustedDeviceService.class);
    private final CredentialRevocationService service = new CredentialRevocationService(
            authSessionService, apiTokenService, trustedDeviceService);

    @Test
    void selfPasswordChangeRevokesEveryCredentialClass() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        when(authSessionService.revokeAllAfterPasswordChange(sessionId, tenantId, membershipId)).thenReturn(3);
        when(apiTokenService.revokeAllForCredentialChange(tenantId, membershipId, "用户修改密码")).thenReturn(2);
        when(trustedDeviceService.requireReauthenticationAfterCredentialChange(
                tenantId, membershipId, "用户修改密码")).thenReturn(1);

        var result = service.afterSelfPasswordChange(sessionId, tenantId, membershipId);

        assertThat(result).isEqualTo(new CredentialRevocationService.RevocationSummary(3, 2, 1));
    }

    @Test
    void adminPasswordResetRevokesEveryCredentialClass() {
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        when(authSessionService.revokeAllOther(tenantId, membershipId, null, "管理员重置密码")).thenReturn(4);

        service.afterAdminPasswordReset(tenantId, membershipId);

        verify(apiTokenService).revokeAllForCredentialChange(tenantId, membershipId, "管理员重置密码");
        verify(trustedDeviceService).requireReauthenticationAfterCredentialChange(
                tenantId, membershipId, "管理员重置密码");
    }
}
