package com.pro.ereportbacken.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class SecurityHashingService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] contextPepper;

    public SecurityHashingService(@Value("${app.security.context-pepper}") String contextPepper) {
        if (!StringUtils.hasText(contextPepper) || contextPepper.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.security.context-pepper must contain at least 32 bytes.");
        }
        this.contextPepper = contextPepper.getBytes(StandardCharsets.UTF_8);
    }

    public String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    public String protectedHash(String namespace, String value) {
        if (!StringUtils.hasText(namespace) || !StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Hash namespace and value must not be blank.");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(contextPepper, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal((namespace + "\n" + value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to protect security context.", exception);
        }
    }

    public boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII)
        );
    }
}
