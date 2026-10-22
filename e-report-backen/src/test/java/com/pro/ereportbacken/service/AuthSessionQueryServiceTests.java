package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.AuthClientType;
import com.pro.ereportbacken.model.pojo.AuthSession;
import com.pro.ereportbacken.model.pojo.AuthSessionStatus;
import com.pro.ereportbacken.repository.TrustedDeviceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthSessionQueryServiceTests {

    private final AuthSessionService authSessionService = mock(AuthSessionService.class);
    private final TrustedDeviceRepository trustedDeviceRepository = mock(TrustedDeviceRepository.class);
    private final AuthSessionQueryService queryService = new AuthSessionQueryService(
            authSessionService, trustedDeviceRepository);

    @Test
    void pageBoundsLargePageSizeAndReturnsOtherSessionCount() {
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID currentSessionId = UUID.randomUUID();
        AuthSession session = new AuthSession();
        session.setId(currentSessionId);
        session.setClientType(AuthClientType.WEB);
        session.setStatus(AuthSessionStatus.ACTIVE);
        session.setClientName("浏览器");
        session.setClientDeviceName("测试浏览器");
        session.setClientOsVersion("Windows");
        session.setClientAppVersion("5.5.0");
        LocalDateTime now = LocalDateTime.now();
        session.setCreatedAt(now);
        session.setLastSeenAt(now);
        session.setIdleExpiresAt(now.plusHours(1));
        session.setAbsoluteExpiresAt(now.plusDays(1));

        when(authSessionService.page(any(UUID.class), any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(session), PageRequest.of(0, 100), 101));
        when(authSessionService.countActiveOther(tenantId, membershipId, currentSessionId)).thenReturn(100L);
        when(trustedDeviceRepository.findAllById(any())).thenReturn(List.of());

        var result = queryService.page(tenantId, membershipId, currentSessionId, PageRequest.of(0, 100));

        assertThat(result.page().content()).hasSize(1);
        assertThat(result.page().content().getFirst().current()).isTrue();
        assertThat(result.activeOtherSessionCount()).isEqualTo(100L);
    }
}
