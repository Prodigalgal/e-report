package com.pro.ereportbacken.security;

import com.pro.ereportbacken.exception.ApiErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_AUTH_PATHS = Set.of(
            "/auth/csrf",
            "/auth/me",
            "/auth/password-policy",
            "/auth/password",
            "/auth/logout"
    );

    private final ApiErrorResponseWriter errorResponseWriter;

    public PasswordChangeRequiredFilter(ApiErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean authenticated = SecurityContextHolder.getContext().getAuthentication() != null;
        boolean passwordChangeRequired = Boolean.TRUE.equals(
                request.getAttribute(AuthSessionRequestContext.PASSWORD_CHANGE_REQUIRED_ATTRIBUTE));
        if (!authenticated || !passwordChangeRequired || allowed(request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "首次登录或密码已由管理员重置，请先修改密码。"
        );
    }

    private boolean allowed(String servletPath) {
        if (ALLOWED_AUTH_PATHS.contains(servletPath) || servletPath.startsWith("/auth/sessions/")) {
            return true;
        }
        return servletPath.startsWith("/app-updates/") || servletPath.startsWith("/observability/");
    }
}
