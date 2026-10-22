package com.pro.ereportbacken.security;

import com.pro.ereportbacken.exception.ApiErrorResponseWriter;
import com.pro.ereportbacken.service.ApiTokenService;
import com.pro.ereportbacken.service.AuthSessionService;
import com.pro.ereportbacken.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

@Component
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_TOKEN_AUTHORITY = "authentication:api-token";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiTokenService apiTokenService;
    private final UserDetailsServiceImpl userDetailsService;
    private final ApiErrorResponseWriter errorResponseWriter;

    public ApiTokenAuthenticationFilter(ApiTokenService apiTokenService,
                                        UserDetailsServiceImpl userDetailsService,
                                        ApiErrorResponseWriter errorResponseWriter) {
        this.apiTokenService = apiTokenService;
        this.userDetailsService = userDetailsService;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String rawToken = bearerToken(request);
        if (!StringUtils.hasText(rawToken) || !rawToken.startsWith(ApiTokenService.TOKEN_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (hasWebSessionCookie(request)) {
            errorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED, "请求同时携带了两种登录凭据。" );
            return;
        }
        if (blockedControlPlanePath(request.getServletPath())) {
            errorResponseWriter.write(request, response, HttpStatus.FORBIDDEN, "API Token 只能访问已授权的工单与统计接口，不能进入管理控制面。" );
            return;
        }

        try {
            ApiTokenService.AuthenticatedApiToken authenticated = apiTokenService.authenticate(rawToken);
            TenantUserDetails principal = userDetailsService.loadForApiToken(
                    authenticated.tenantId(), authenticated.membershipId(), authenticated.permissionNames());
            Collection<GrantedAuthority> authorities = new ArrayList<>(principal.getAuthorities());
            authorities.add(new SimpleGrantedAuthority(API_TOKEN_AUTHORITY));
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities
            );
            authentication.setDetails(new ApiTokenAuthenticationDetails(
                    authenticated.tokenId(), authenticated.name(), authenticated.expiresAt()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            apiTokenService.markUsed(authenticated.tokenId());
        } catch (ResponseStatusException exception) {
            SecurityContextHolder.clearContext();
            HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
            errorResponseWriter.write(
                    request,
                    response,
                    status == null ? HttpStatus.UNAUTHORIZED : status,
                    status == HttpStatus.TOO_MANY_REQUESTS
                            ? "API 调用过于频繁，请稍后重试。"
                            : "API Token 无效、已过期或已撤销。"
            );
            return;
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            errorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED, "API Token 无效、已过期或已撤销。" );
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)
                || authorization.length() <= BEARER_PREFIX.length()
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }

    private boolean hasWebSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return cookies != null && Arrays.stream(cookies)
                .anyMatch(cookie -> AuthSessionService.WEB_COOKIE_NAME.equals(cookie.getName()));
    }

    private boolean blockedControlPlanePath(String servletPath) {
        return servletPath.equals("/auth")
                || servletPath.startsWith("/auth/")
                || servletPath.equals("/api-tokens")
                || servletPath.startsWith("/api-tokens/")
                || servletPath.equals("/admin")
                || servletPath.startsWith("/admin/");
    }
}
