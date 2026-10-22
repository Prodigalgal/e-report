package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.security.SecurityHashingService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

@Service
public class UserExportPreviewTokenService {

    private static final String TOKEN_NAMESPACE = "user-export-preview-v2";

    private final SecurityHashingService hashingService;

    public UserExportPreviewTokenService(SecurityHashingService hashingService) {
        this.hashingService = hashingService;
    }

    public String issue(UUID tenantId,
                        UUID membershipId,
                        String requestHash,
                        UserExportQueryService.UserExportSnapshot snapshot,
                        LocalDateTime expiresAt) {
        String payload = payload(tenantId, membershipId, requestHash, snapshot, expiresAt);
        String signature = hashingService.protectedHash(TOKEN_NAMESPACE, payload);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + signature;
    }

    public Claims verify(String token,
                         UUID tenantId,
                         UUID membershipId,
                         String requestHash) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("导出预检已失效，请重新预览导出范围。");
        }
        int separator = token.lastIndexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            throw new AccessDeniedException("导出预检令牌无效，请重新预览。");
        }
        try {
            String payload = new String(
                    Base64.getUrlDecoder().decode(token.substring(0, separator)), StandardCharsets.UTF_8);
            String signature = token.substring(separator + 1);
            if (!hashingService.constantTimeEquals(
                    hashingService.protectedHash(TOKEN_NAMESPACE, payload), signature)) {
                throw new AccessDeniedException("导出预检令牌无效，请重新预览。");
            }
            String[] values = payload.split("\\|", -1);
            if (values.length != 8) {
                throw new AccessDeniedException("导出预检令牌无效，请重新预览。");
            }
            Claims claims = new Claims(
                    UUID.fromString(values[0]), UUID.fromString(values[1]), values[2], values[3],
                    Long.parseLong(values[4]), Long.parseLong(values[5]), Long.parseLong(values[6]),
                    LocalDateTime.ofEpochSecond(Long.parseLong(values[7]), 0, ZoneOffset.UTC));
            if (!tenantId.equals(claims.tenantId())
                    || !membershipId.equals(claims.membershipId())
                    || !hashingService.constantTimeEquals(requestHash, claims.requestHash())) {
                throw new AccessDeniedException("导出预检令牌与当前请求不匹配，请重新预览。");
            }
            if (!claims.expiresAt().isAfter(UtcTime.now())) {
                throw new IllegalStateException("导出预检已过期，请重新预览导出范围。");
            }
            return claims;
        } catch (AccessDeniedException | IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AccessDeniedException("导出预检令牌无效，请重新预览。", exception);
        }
    }

    private String payload(UUID tenantId,
                           UUID membershipId,
                           String requestHash,
                           UserExportQueryService.UserExportSnapshot snapshot,
                           LocalDateTime expiresAt) {
        return String.join("|",
                tenantId.toString(), membershipId.toString(), requestHash, snapshot.digest(),
                String.valueOf(snapshot.userCount()), String.valueOf(snapshot.grantCount()),
                String.valueOf(snapshot.estimatedFileSize()),
                String.valueOf(expiresAt.toEpochSecond(ZoneOffset.UTC)));
    }

    public record Claims(
            UUID tenantId,
            UUID membershipId,
            String requestHash,
            String snapshotDigest,
            long userCount,
            long grantCount,
            long estimatedFileSize,
            LocalDateTime expiresAt
    ) {
    }
}
