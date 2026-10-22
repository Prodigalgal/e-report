package com.pro.ereportbacken.security;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Service
public class DeviceProofService {

    private static final int MAX_PUBLIC_KEY_BYTES = 512;
    private static final int MAX_SIGNATURE_BYTES = 144;
    private static final int MAX_PAYLOAD_BYTES = 8192;

    public VerifiedDeviceKey requireKey(String publicKeySpki, String submittedKeyId) {
        if (!StringUtils.hasText(publicKeySpki) || !StringUtils.hasText(submittedKeyId)) {
            throw new IllegalArgumentException("设备公钥和公钥指纹不能为空。");
        }
        try {
            byte[] encodedKey = decode(publicKeySpki, MAX_PUBLIC_KEY_BYTES, "设备公钥格式不正确。");
            PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(encodedKey));
            if (!(publicKey instanceof ECPublicKey ecPublicKey)
                    || ecPublicKey.getParams().getCurve().getField().getFieldSize() < 256) {
                throw new IllegalArgumentException("设备公钥必须使用至少 256 位的 EC 密钥。");
            }
            String calculatedKeyId = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(encodedKey));
            if (!MessageDigest.isEqual(
                    calculatedKeyId.getBytes(StandardCharsets.US_ASCII),
                    submittedKeyId.getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("设备公钥指纹不匹配。");
            }
            return new VerifiedDeviceKey(calculatedKeyId, publicKeySpki, publicKey);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("设备公钥格式不正确。", exception);
        }
    }

    public void requireValidSignature(PublicKey publicKey, String payload, String encodedSignature) {
        if (!StringUtils.hasText(payload)
                || payload.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES
                || !StringUtils.hasText(encodedSignature)) {
            throw new IllegalArgumentException("设备签名内容不完整。");
        }
        try {
            byte[] signatureBytes = decode(encodedSignature, MAX_SIGNATURE_BYTES, "设备签名格式不正确。");
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(signatureBytes)) {
                throw new IllegalArgumentException("设备签名校验失败。");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("设备签名校验失败。", exception);
        }
    }

    private byte[] decode(String value, int maximumBytes, String message) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (decoded.length == 0 || decoded.length > maximumBytes) {
                throw new IllegalArgumentException(message);
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(message, exception);
        }
    }

    public record VerifiedDeviceKey(String keyId, String publicKeySpki, PublicKey publicKey) {
    }
}
