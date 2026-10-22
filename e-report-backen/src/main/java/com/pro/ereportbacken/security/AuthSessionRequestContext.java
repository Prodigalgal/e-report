package com.pro.ereportbacken.security;

import com.pro.ereportbacken.model.pojo.AuthClientType;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public final class AuthSessionRequestContext {

    public static final String SESSION_ID_ATTRIBUTE = AuthSessionRequestContext.class.getName() + ".sessionId";
    public static final String CLIENT_TYPE_ATTRIBUTE = AuthSessionRequestContext.class.getName() + ".clientType";
    public static final String DEVICE_KEY_ID_ATTRIBUTE = AuthSessionRequestContext.class.getName() + ".deviceKeyId";
    public static final String PASSWORD_CHANGE_REQUIRED_ATTRIBUTE = AuthSessionRequestContext.class.getName() + ".passwordChangeRequired";

    private AuthSessionRequestContext() {
    }

    public static UUID requireSessionId(HttpServletRequest request) {
        Object value = request.getAttribute(SESSION_ID_ATTRIBUTE);
        if (value instanceof UUID sessionId) {
            return sessionId;
        }
        throw new IllegalStateException("Authenticated request is missing session context.");
    }

    public static AuthClientType clientType(HttpServletRequest request) {
        Object value = request.getAttribute(CLIENT_TYPE_ATTRIBUTE);
        return value instanceof AuthClientType clientType ? clientType : null;
    }
}
