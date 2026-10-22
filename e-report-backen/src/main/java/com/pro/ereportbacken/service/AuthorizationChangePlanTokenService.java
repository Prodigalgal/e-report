package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.AuthorizationChangePlan;
import com.pro.ereportbacken.security.SecurityHashingService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.Base64;

@Service
public class AuthorizationChangePlanTokenService {

    private static final String TOKEN_NAMESPACE = "authorization-change-plan-v1";
    private final SecurityHashingService hashingService;

    public AuthorizationChangePlanTokenService(SecurityHashingService hashingService) {
        this.hashingService = hashingService;
    }

    public String issue(AuthorizationChangePlan plan) {
        String payload = payload(plan);
        String signature = hashingService.protectedHash(TOKEN_NAMESPACE, payload);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + signature;
    }

    public String planId(String token) {
        String payload = verifiedPayload(token);
        return payload.substring(0, payload.indexOf('|'));
    }

    public boolean matches(AuthorizationChangePlan plan, String token) {
        try {
            return hashingService.constantTimeEquals(
                    hashingService.sha256(verifiedPayload(token)),
                    hashingService.sha256(payload(plan))
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String verifiedPayload(String token) {
        if (token == null) {
            throw new IllegalArgumentException("授权变更计划令牌不能为空。" );
        }
        int separator = token.lastIndexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            throw new IllegalArgumentException("授权变更计划令牌格式错误。" );
        }
        try {
            String payload = new String(
                    Base64.getUrlDecoder().decode(token.substring(0, separator)),
                    StandardCharsets.UTF_8
            );
            String signature = token.substring(separator + 1);
            if (!hashingService.constantTimeEquals(
                    hashingService.protectedHash(TOKEN_NAMESPACE, payload), signature)) {
                throw new IllegalArgumentException("授权变更计划令牌校验失败。" );
            }
            return payload;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("授权变更计划令牌无效。", exception);
        }
    }

    private String payload(AuthorizationChangePlan plan) {
        return String.join("|",
                plan.getId().toString(),
                plan.getTenantId().toString(),
                plan.getTargetMembershipId().toString(),
                plan.getOperatorMembershipId().toString(),
                plan.getExpectedAuthorizationVersion().toString(),
                plan.getRequestDigest(),
                String.valueOf(plan.getExpiresAt().toEpochSecond(ZoneOffset.UTC))
        );
    }
}
