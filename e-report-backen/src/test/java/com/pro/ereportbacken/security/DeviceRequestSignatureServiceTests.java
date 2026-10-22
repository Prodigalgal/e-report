package com.pro.ereportbacken.security;

import com.pro.ereportbacken.TestIds;
import com.pro.ereportbacken.model.pojo.TrustedDevice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

class DeviceRequestSignatureServiceTests {

    private final DeviceRequestNonceService nonceService = mock(DeviceRequestNonceService.class);
    private final DeviceRequestSignatureService service = new DeviceRequestSignatureService(
            new DeviceProofService(), nonceService);
    private final UUID sessionId = TestIds.uuid(920);
    private KeyPair keyPair;
    private TrustedDevice device;

    @BeforeEach
    void setUp() throws Exception {
        reset(nonceService);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
        byte[] publicKey = keyPair.getPublic().getEncoded();
        device = new TrustedDevice();
        device.setKeyId(encode(MessageDigest.getInstance("SHA-256").digest(publicKey)));
        device.setPublicKeySpki(encode(publicKey));
    }

    @Test
    void acceptsSignedEnvelopeAndConsumesNonceAfterBodyVerification() throws Exception {
        byte[] body = "{\"expectedVersion\":7}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = signedRequest(body, body, Instant.now().getEpochSecond(), 60);

        DeviceRequestSignatureService.VerifiedEnvelope envelope = service.verifyEnvelope(
                request, sessionId, device);
        CachedBodyHttpServletRequest cached = CachedBodyHttpServletRequest.capture(request, 1024);
        service.verifyBodyAndConsumeNonce(cached, envelope);

        verify(nonceService).requireUnused(sessionId, "nonce-value-1234567890123456");
    }

    @Test
    void rejectsTamperedBodyBeforeNonceIsConsumed() throws Exception {
        byte[] signedBody = "{\"expectedVersion\":7}".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedBody = "{\"expectedVersion\":8}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = signedRequest(
                tamperedBody, signedBody, Instant.now().getEpochSecond(), 60);
        DeviceRequestSignatureService.VerifiedEnvelope envelope = service.verifyEnvelope(
                request, sessionId, device);
        CachedBodyHttpServletRequest cached = CachedBodyHttpServletRequest.capture(request, 1024);

        assertThatThrownBy(() -> service.verifyBodyAndConsumeNonce(cached, envelope))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("摘要不匹配");
    }

    @Test
    void rejectsExpiredSignatureWindow() throws Exception {
        long created = Instant.now().minusSeconds(300).getEpochSecond();
        MockHttpServletRequest request = signedRequest(new byte[0], new byte[0], created, 60);

        assertThatThrownBy(() -> service.verifyEnvelope(request, sessionId, device))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("已过期");
    }

    @Test
    void propagatesNonceReplayConflict() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = signedRequest(body, body, Instant.now().getEpochSecond(), 60);
        DeviceRequestSignatureService.VerifiedEnvelope envelope = service.verifyEnvelope(
                request, sessionId, device);
        CachedBodyHttpServletRequest cached = CachedBodyHttpServletRequest.capture(request, 1024);
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "检测到重复请求"))
                .when(nonceService).requireUnused(sessionId, "nonce-value-1234567890123456");

        assertThatThrownBy(() -> service.verifyBodyAndConsumeNonce(cached, envelope))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("重复请求");
    }

    private MockHttpServletRequest signedRequest(byte[] actualBody,
                                                 byte[] signedBody,
                                                 long created,
                                                 long lifetimeSeconds) throws Exception {
        long expires = created + lifetimeSeconds;
        String nonce = "nonce-value-1234567890123456";
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(signedBody));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/work-orders/search");
        request.setQueryString("status=WAITING&page=1&status=OPEN");
        request.setContent(actualBody);
        request.addHeader(DeviceRequestSignatureService.KEY_ID_HEADER, device.getKeyId());
        request.addHeader(DeviceRequestSignatureService.CREATED_HEADER, String.valueOf(created));
        request.addHeader(DeviceRequestSignatureService.EXPIRES_HEADER, String.valueOf(expires));
        request.addHeader(DeviceRequestSignatureService.NONCE_HEADER, nonce);
        request.addHeader(DeviceRequestSignatureService.BODY_DIGEST_HEADER, digest);
        request.addHeader(DeviceRequestSignatureService.SESSION_ID_HEADER, sessionId.toString());
        String payload = String.join("\n",
                "e-report-request-v1",
                "@method:POST",
                "@target-uri:/api/work-orders/search?page=1&status=OPEN&status=WAITING",
                "content-sha256:" + digest,
                "created:" + created,
                "expires:" + expires,
                "nonce:" + nonce,
                "session-id:" + sessionId
        );
        request.addHeader(DeviceRequestSignatureService.SIGNATURE_HEADER, sign(payload));
        return request;
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
