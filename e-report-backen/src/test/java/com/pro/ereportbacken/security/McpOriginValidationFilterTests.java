package com.pro.ereportbacken.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.exception.ApiErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class McpOriginValidationFilterTests {

    private final McpOriginValidationFilter filter = new McpOriginValidationFilter(
            new String[]{"https://e-report.example"}, new ApiErrorResponseWriter());

    @Test
    void rejectsUntrustedBrowserOrigin() throws Exception {
        MockHttpServletRequest request = mcpRequest();
        request.addHeader("Origin", "https://attacker.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(new ObjectMapper().readTree(response.getContentAsString()).path("message").asText())
                .contains("来源校验失败");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void acceptsConfiguredOriginAndNativeClientWithoutOrigin() throws Exception {
        MockHttpServletRequest browserRequest = mcpRequest();
        browserRequest.addHeader("Origin", "https://e-report.example");
        MockFilterChain browserChain = new MockFilterChain();
        filter.doFilter(browserRequest, new MockHttpServletResponse(), browserChain);

        MockFilterChain nativeChain = new MockFilterChain();
        filter.doFilter(mcpRequest(), new MockHttpServletResponse(), nativeChain);

        assertThat(browserChain.getRequest()).isSameAs(browserRequest);
        assertThat(nativeChain.getRequest()).isNotNull();
    }

    @Test
    void leavesNonMcpRoutesToTheirOwnOriginPolicy() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/work-orders");
        request.setContextPath("/api");
        request.setServletPath("/work-orders");
        request.addHeader("Origin", "https://attacker.example");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    private MockHttpServletRequest mcpRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mcp");
        request.setContextPath("/api");
        request.setServletPath("/mcp");
        return request;
    }
}
