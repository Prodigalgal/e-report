package com.pro.ereportbacken.security;

import com.pro.ereportbacken.exception.ApiErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class McpOriginValidationFilter extends OncePerRequestFilter {

    private final Set<String> allowedOrigins;
    private final ApiErrorResponseWriter errorResponseWriter;

    public McpOriginValidationFilter(String[] allowedOrigins, ApiErrorResponseWriter errorResponseWriter) {
        this.allowedOrigins = Arrays.stream(allowedOrigins)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!"/mcp".equals(request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (StringUtils.hasText(origin) && !allowedOrigins.contains(origin.trim())) {
            errorResponseWriter.write(request, response, HttpStatus.FORBIDDEN, "MCP 请求来源校验失败。");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
