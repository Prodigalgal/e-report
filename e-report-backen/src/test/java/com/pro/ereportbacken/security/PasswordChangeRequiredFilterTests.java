package com.pro.ereportbacken.security;

import com.pro.ereportbacken.exception.ApiErrorResponseWriter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PasswordChangeRequiredFilterTests {

    private final PasswordChangeRequiredFilter filter =
            new PasswordChangeRequiredFilter(new ApiErrorResponseWriter());

    @Test
    void allowsCsrfBootstrapWhilePasswordChangeIsRequired() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("GET", "/auth/csrf");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        try {
            filter.doFilter(request, response, chain);
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void allowsPasswordPolicyWhilePasswordChangeIsRequired() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("GET", "/auth/password-policy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        try {
            filter.doFilter(request, response, chain);
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void stillBlocksBusinessRequestsWhilePasswordChangeIsRequired() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("GET", "/work-orders/search");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        try {
            filter.doFilter(request, response, chain);
        } finally {
            SecurityContextHolder.clearContext();
        }

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("请先修改密码");
    }

    private MockHttpServletRequest authenticatedRequest(String method, String servletPath) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("user", null, java.util.List.of())
        );
        MockHttpServletRequest request = new MockHttpServletRequest(method, servletPath);
        request.setServletPath(servletPath);
        request.setAttribute(AuthSessionRequestContext.PASSWORD_CHANGE_REQUIRED_ATTRIBUTE, true);
        return request;
    }
}
