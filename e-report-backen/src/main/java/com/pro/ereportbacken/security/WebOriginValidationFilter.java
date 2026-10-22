package com.pro.ereportbacken.security;

import com.pro.ereportbacken.exception.ApiErrorResponseWriter;
import com.pro.ereportbacken.service.AuthSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class WebOriginValidationFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final Set<String> allowedOrigins;
    private final ApiErrorResponseWriter errorResponseWriter;

    public WebOriginValidationFilter(
            @Value("${api.cors.allowed-origins:http://192.168.101.126:5173}") String[] allowedOrigins,
            ApiErrorResponseWriter errorResponseWriter) {
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
        if (SAFE_METHODS.contains(request.getMethod()) || !isWebRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String origin = normalizedOrigin(request);
        if (!StringUtils.hasText(origin) || !allowedOrigins.contains(origin)) {
            errorResponseWriter.write(request, response, HttpStatus.FORBIDDEN, "请求来源校验失败，请刷新页面后重试。");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isWebRequest(HttpServletRequest request) {
        if ("WEB".equalsIgnoreCase(request.getHeader("X-Client-Platform"))) {
            return true;
        }
        Cookie[] cookies = request.getCookies();
        return cookies != null && Arrays.stream(cookies)
                .anyMatch(cookie -> AuthSessionService.WEB_COOKIE_NAME.equals(cookie.getName()));
    }

    private String normalizedOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (StringUtils.hasText(origin) && !"null".equalsIgnoreCase(origin.trim())) {
            return origin.trim();
        }
        String referer = request.getHeader(HttpHeaders.REFERER);
        if (!StringUtils.hasText(referer)) {
            return null;
        }
        try {
            URI uri = URI.create(referer.trim());
            int port = uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + (port < 0 ? "" : ":" + port);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
