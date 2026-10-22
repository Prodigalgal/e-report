package com.pro.ereportbacken.security;

import com.pro.ereportbacken.exception.ApiErrorResponseWriter;
import com.pro.ereportbacken.service.ApiTokenService;
import com.pro.ereportbacken.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiTokenAuthenticationFilterTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesApiTokenWithFilteredAuthoritiesAndPreservesBusinessErrors() throws Exception {
        ApiTokenService apiTokenService = mock(ApiTokenService.class);
        UserDetailsServiceImpl userDetailsService = mock(UserDetailsServiceImpl.class);
        ApiErrorResponseWriter errorWriter = mock(ApiErrorResponseWriter.class);
        UUID tokenId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        String rawToken = "erpt_abcdefghijklmnopqrstuv." + "x".repeat(43);
        when(apiTokenService.authenticate(rawToken)).thenReturn(new ApiTokenService.AuthenticatedApiToken(
                tokenId,
                tenantId,
                membershipId,
                "LLM 查询",
                null,
                Set.of(PermissionCatalog.NETWORK_FAULT_VIEW)
        ));
        TenantUserDetails principal = mock(TenantUserDetails.class);
        doReturn(java.util.List.<org.springframework.security.core.GrantedAuthority>of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        PermissionCatalog.NETWORK_FAULT_VIEW)))
                .when(principal).getAuthorities();
        when(userDetailsService.loadForApiToken(
                tenantId, membershipId, Set.of(PermissionCatalog.NETWORK_FAULT_VIEW))).thenReturn(principal);
        ApiTokenAuthenticationFilter filter = new ApiTokenAuthenticationFilter(
                apiTokenService, userDetailsService, errorWriter);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/work-orders/search");
        request.addHeader("Authorization", "Bearer " + rawToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting("authority")
                    .containsExactlyInAnyOrder(
                            PermissionCatalog.NETWORK_FAULT_VIEW,
                            ApiTokenAuthenticationFilter.API_TOKEN_AUTHORITY);
            throw new IllegalStateException("business failure");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("business failure");
        verify(apiTokenService).markUsed(tokenId);
        verify(errorWriter, never()).write(any(), any(), any(), any());
    }

    @Test
    void blocksCredentialManagementBeforeTokenAuthentication() throws Exception {
        ApiTokenService apiTokenService = mock(ApiTokenService.class);
        ApiErrorResponseWriter errorWriter = mock(ApiErrorResponseWriter.class);
        ApiTokenAuthenticationFilter filter = new ApiTokenAuthenticationFilter(
                apiTokenService, mock(UserDetailsServiceImpl.class), errorWriter);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api-tokens");
        request.setServletPath("/api-tokens");
        request.addHeader("Authorization", "Bearer erpt_abcdefghijklmnopqrstuv." + "x".repeat(43));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new AssertionError("blocked request reached the controller");
        });

        verify(errorWriter).write(request, response, HttpStatus.FORBIDDEN,
                "API Token 只能访问已授权的工单与统计接口，不能进入管理控制面。" );
        verify(apiTokenService, never()).authenticate(any());
    }
}
