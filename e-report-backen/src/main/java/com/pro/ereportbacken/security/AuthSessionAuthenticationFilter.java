package com.pro.ereportbacken.security;

import com.pro.ereportbacken.exception.ApiErrorResponseWriter;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
public class AuthSessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthSessionService authSessionService;
    private final UserDetailsServiceImpl userDetailsService;
    private final ApiErrorResponseWriter apiErrorResponseWriter;
    private final WebSessionCookieFactory webSessionCookieFactory;

    public AuthSessionAuthenticationFilter(AuthSessionService authSessionService,
                                           UserDetailsServiceImpl userDetailsService,
                                           ApiErrorResponseWriter apiErrorResponseWriter,
                                           WebSessionCookieFactory webSessionCookieFactory) {
        this.authSessionService = authSessionService;
        this.userDetailsService = userDetailsService;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
        this.webSessionCookieFactory = webSessionCookieFactory;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String bearerToken = bearerToken(request);
        String webCookie = webCookie(request);
        if (!StringUtils.hasText(bearerToken) && !StringUtils.hasText(webCookie)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (StringUtils.hasText(bearerToken) && StringUtils.hasText(webCookie)) {
            apiErrorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED, "请求同时携带了两种登录凭据，请重新登录。");
            return;
        }
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AuthSessionService.AuthenticatedSession authenticated = StringUtils.hasText(bearerToken)
                    ? authSessionService.authenticateAndroid(bearerToken)
                    : authSessionService.authenticateWeb(webCookie);
            TenantUserDetails userDetails = userDetailsService.loadForSession(
                    authenticated.session().getTenantId(),
                    authenticated.session().getMembershipId()
            );
            if (!authenticated.session().getAuthorizationVersion().equals(userDetails.authorizationVersion())) {
                authSessionService.revoke(
                        authenticated.session().getId(),
                        authenticated.session().getMembershipId(),
                        "用户权限版本已变化"
                );
                throw new IllegalArgumentException("Session authorization version is stale.");
            }

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setAttribute(AuthSessionRequestContext.SESSION_ID_ATTRIBUTE, authenticated.session().getId());
            request.setAttribute(AuthSessionRequestContext.CLIENT_TYPE_ATTRIBUTE, authenticated.session().getClientType());
            request.setAttribute(
                    AuthSessionRequestContext.PASSWORD_CHANGE_REQUIRED_ATTRIBUTE,
                    Boolean.TRUE.equals(authenticated.session().getPasswordChangeRequired())
            );
            if (authenticated.accessTokenIdentity() != null) {
                request.setAttribute(
                        AuthSessionRequestContext.DEVICE_KEY_ID_ATTRIBUTE,
                        authenticated.accessTokenIdentity().deviceKeyId()
                );
            }
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            if (StringUtils.hasText(webCookie)) {
                response.addHeader(HttpHeaders.SET_COOKIE, webSessionCookieFactory.clear(request).toString());
                filterChain.doFilter(request, response);
                return;
            }
            apiErrorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录。");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }

    private String webCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> AuthSessionService.WEB_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
