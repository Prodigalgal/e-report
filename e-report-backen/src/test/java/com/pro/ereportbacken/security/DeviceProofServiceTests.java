package com.pro.ereportbacken.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceProofServiceTests {

    private final DeviceProofService service = new DeviceProofService();
    private KeyPair keyPair;
    private String publicKey;
    private String keyId;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
        publicKey = encode(keyPair.getPublic().getEncoded());
        keyId = encode(MessageDigest.getInstance("SHA-256").digest(keyPair.getPublic().getEncoded()));
    }

    @Test
    void verifiesP256KeyIdentityAndSignature() throws Exception {
        DeviceProofService.VerifiedDeviceKey verified = service.requireKey(publicKey, keyId);
        String payload = "e-report-device-proof\n安全请求";

        service.requireValidSignature(verified.publicKey(), payload, sign(payload));

        assertThat(verified.keyId()).isEqualTo(keyId);
    }

    @Test
    void rejectsMismatchedKeyIdentityAndTamperedPayload() throws Exception {
        assertThatThrownBy(() -> service.requireKey(publicKey, "A".repeat(43)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("指纹不匹配");

        DeviceProofService.VerifiedDeviceKey verified = service.requireKey(publicKey, keyId);
        String signature = sign("original");
        assertThatThrownBy(() -> service.requireValidSignature(verified.publicKey(), "tampered", signature))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("校验失败");
    }

    private String sign(String payload) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(payload.getBytes(StandardCharsets.UTF_8));
        return encode(signature.sign());
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
