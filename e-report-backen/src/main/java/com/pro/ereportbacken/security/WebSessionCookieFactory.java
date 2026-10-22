package com.pro.ereportbacken.security;

import com.pro.ereportbacken.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
public class WebSessionCookieFactory {

    private static final Duration SESSION_LIFETIME = Duration.ofDays(7);

    private final boolean secureCookie;

    public WebSessionCookieFactory(@Value("${app.security.cookie-secure:true}") boolean secureCookie) {
        this.secureCookie = secureCookie;
    }

    public ResponseCookie issue(HttpServletRequest request, String value) {
        return issue(request, value, true);
    }

    public ResponseCookie issue(HttpServletRequest request, String value, boolean persistentLogin) {
        return build(request, value, persistentLogin ? SESSION_LIFETIME : null);
    }

    public ResponseCookie clear(HttpServletRequest request) {
        return build(request, "", Duration.ZERO);
    }

    private ResponseCookie build(HttpServletRequest request, String value, Duration maxAge) {
        String path = StringUtils.hasText(request.getContextPath()) ? request.getContextPath() : "/";
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(AuthSessionService.WEB_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path(path);
        if (maxAge != null) {
            builder.maxAge(maxAge);
        }
        return builder.build();
    }
}
