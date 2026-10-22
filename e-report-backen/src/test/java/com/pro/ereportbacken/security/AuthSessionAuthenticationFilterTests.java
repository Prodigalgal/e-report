package com.pro.ereportbacken.security;

import com.pro.ereportbacken.exception.ApiErrorResponseWriter;
import com.pro.ereportbacken.model.pojo.AuthSession;
import com.pro.ereportbacken.service.AuthSessionService;
import com.pro.ereportbacken.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthSessionAuthenticationFilterTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void downstreamBusinessExceptionIsNotConvertedToAuthenticationFailure() throws Exception {
        AuthSessionService authSessionService = mock(AuthSessionService.class);
        UserDetailsServiceImpl userDetailsService = mock(UserDetailsServiceImpl.class);
        AuthSession session = mock(AuthSession.class);
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(session.getTenantId()).thenReturn(tenantId);
        when(session.getMembershipId()).thenReturn(membershipId);
        when(session.getId()).thenReturn(sessionId);
        when(session.getAuthorizationVersion()).thenReturn(3L);
        when(authSessionService.authenticateAndroid("valid-token"))
                .thenReturn(new AuthSessionService.AuthenticatedSession(session, null, null));
        TenantUserDetails userDetails = mock(TenantUserDetails.class);
        when(userDetails.authorizationVersion()).thenReturn(3L);
        when(userDetails.getAuthorities()).thenReturn(java.util.List.of());
        when(userDetailsService.loadForSession(tenantId, membershipId)).thenReturn(userDetails);

        AuthSessionAuthenticationFilter filter = new AuthSessionAuthenticationFilter(
                authSessionService,
                userDetailsService,
                mock(ApiErrorResponseWriter.class),
                new WebSessionCookieFactory(false)
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/work-orders/search");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            throw new IllegalStateException("business failure");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("business failure");
    }
}
