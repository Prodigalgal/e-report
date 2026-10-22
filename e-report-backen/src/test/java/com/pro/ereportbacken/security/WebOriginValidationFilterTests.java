package com.pro.ereportbacken.security;

import com.pro.ereportbacken.exception.ApiErrorResponseWriter;
import com.pro.ereportbacken.service.AuthSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class WebOriginValidationFilterTests {

    private final WebOriginValidationFilter filter = new WebOriginValidationFilter(
            new String[]{"https://e-report.example"}, new ApiErrorResponseWriter());

    @Test
    void rejectsCrossOriginWebWriteRequest() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.addHeader("X-Client-Platform", "WEB");
        request.addHeader(HttpHeaders.ORIGIN, "https://attacker.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("请求来源校验失败");
        verifyNoInteractions(chain);
    }

    @Test
    void acceptsAllowedOriginAndReferer() throws Exception {
        MockHttpServletRequest request = request("PATCH");
        request.setCookies(new Cookie(AuthSessionService.WEB_COOKIE_NAME, "opaque"));
        request.addHeader(HttpHeaders.REFERER, "https://e-report.example/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotApplyBrowserOriginRulesToAndroidRequest() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.addHeader("X-Client-Platform", "ANDROID");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest request(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/auth/password");
        request.setRequestURI("/auth/password");
        return request;
    }
}
