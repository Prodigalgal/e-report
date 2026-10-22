package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.UserTransferJob;
import com.pro.ereportbacken.security.SecurityHashingService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.Base64;

@Service
public class UserTransferPreviewTokenService {

    private static final String TOKEN_NAMESPACE = "user-transfer-preview-v1";
    private final SecurityHashingService hashingService;

    public UserTransferPreviewTokenService(SecurityHashingService hashingService) {
        this.hashingService = hashingService;
    }

    public String issue(UserTransferJob job) {
        String payload = payload(job);
        String signature = hashingService.protectedHash(TOKEN_NAMESPACE, payload);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + signature;
    }

    public boolean matches(UserTransferJob job, String token) {
        if (token == null) {
            return false;
        }
        int separator = token.lastIndexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            return false;
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(token.substring(0, separator)),
                    StandardCharsets.UTF_8);
            String expectedPayload = payload(job);
            return hashingService.constantTimeEquals(
                    hashingService.protectedHash(TOKEN_NAMESPACE, payload),
                    token.substring(separator + 1)
            ) && hashingService.constantTimeEquals(
                    hashingService.sha256(payload), hashingService.sha256(expectedPayload));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String payload(UserTransferJob job) {
        if (job.getPreviewExpiresAt() == null || job.getPreviewSnapshotDigest() == null) {
            throw new IllegalStateException("任务还没有可执行的预览快照。");
        }
        return String.join("|",
                job.getId().toString(),
                value(job.getInputSha256()),
                value(job.getCatalogDigest()),
                value(job.getUserSnapshotDigest()),
                job.getPreviewSnapshotDigest(),
                String.valueOf(job.getPreviewExpiresAt().toEpochSecond(ZoneOffset.UTC))
        );
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
