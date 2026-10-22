package com.pro.ereportbacken.security;

import com.pro.ereportbacken.exception.ApiErrorResponseWriter;
import com.pro.ereportbacken.model.pojo.AuthClientType;
import com.pro.ereportbacken.service.AuthSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

@Component
public class DeviceRequestSignatureFilter extends OncePerRequestFilter {

    private static final int MAX_SIGNED_BODY_BYTES = 8 * 1024 * 1024;

    private final AuthSessionService authSessionService;
    private final DeviceRequestSignatureService signatureService;
    private final ApiErrorResponseWriter errorResponseWriter;

    public DeviceRequestSignatureFilter(AuthSessionService authSessionService,
                                        DeviceRequestSignatureService signatureService,
                                        ApiErrorResponseWriter errorResponseWriter) {
        this.authSessionService = authSessionService;
        this.signatureService = signatureService;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean refreshRequest = "/auth/refresh".equals(request.getServletPath());
        AuthClientType clientType = AuthSessionRequestContext.clientType(request);
        if (!refreshRequest && clientType != AuthClientType.ANDROID) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UUID sessionId = refreshRequest
                    ? UUID.fromString(requiredHeader(request, DeviceRequestSignatureService.SESSION_ID_HEADER))
                    : AuthSessionRequestContext.requireSessionId(request);
            String keyId = requiredHeader(request, DeviceRequestSignatureService.KEY_ID_HEADER);
            AuthSessionService.RefreshContext context = authSessionService.requireRefreshContext(sessionId, keyId);
            DeviceRequestSignatureService.VerifiedEnvelope envelope = signatureService.verifyEnvelope(
                    request, sessionId, context.trustedDevice());
            CachedBodyHttpServletRequest cachedRequest = CachedBodyHttpServletRequest.capture(
                    request, MAX_SIGNED_BODY_BYTES);
            signatureService.verifyBodyAndConsumeNonce(cachedRequest, envelope);
            cachedRequest.setAttribute(AuthSessionRequestContext.SESSION_ID_ATTRIBUTE, sessionId);
            cachedRequest.setAttribute(AuthSessionRequestContext.CLIENT_TYPE_ATTRIBUTE, AuthClientType.ANDROID);
            cachedRequest.setAttribute(AuthSessionRequestContext.DEVICE_KEY_ID_ATTRIBUTE, keyId);
            filterChain.doFilter(cachedRequest, response);
        } catch (ResponseStatusException exception) {
            HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
            errorResponseWriter.write(
                    request,
                    response,
                    status == null ? HttpStatus.UNAUTHORIZED : status,
                    exception.getReason() == null ? "设备请求签名校验失败。" : exception.getReason()
            );
        } catch (RuntimeException exception) {
            errorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED, "设备请求签名校验失败。");
        }
    }

    private String requiredHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (!org.springframework.util.StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "设备请求签名不完整。");
        }
        return value.trim();
    }
}
