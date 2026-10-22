package com.pro.ereportbacken.security;

import com.pro.ereportbacken.model.pojo.TrustedDevice;
import com.pro.ereportbacken.security.DeviceProofService.VerifiedDeviceKey;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DeviceRequestSignatureService {

    public static final String KEY_ID_HEADER = "X-Device-Key-Id";
    public static final String CREATED_HEADER = "X-Signature-Created";
    public static final String EXPIRES_HEADER = "X-Signature-Expires";
    public static final String NONCE_HEADER = "X-Signature-Nonce";
    public static final String BODY_DIGEST_HEADER = "X-Content-SHA256";
    public static final String SESSION_ID_HEADER = "X-Auth-Session-Id";
    public static final String SIGNATURE_HEADER = "X-Device-Signature";

    private static final long MAX_SIGNATURE_LIFETIME_SECONDS = 120;
    private static final long CLOCK_SKEW_SECONDS = 30;

    private final DeviceProofService deviceProofService;
    private final DeviceRequestNonceService nonceService;

    public DeviceRequestSignatureService(DeviceProofService deviceProofService,
                                         DeviceRequestNonceService nonceService) {
        this.deviceProofService = deviceProofService;
        this.nonceService = nonceService;
    }

    public VerifiedEnvelope verifyEnvelope(HttpServletRequest request,
                                           UUID expectedSessionId,
                                           TrustedDevice device) {
        SignatureHeaders headers = readHeaders(request);
        if (!expectedSessionId.equals(headers.sessionId()) || !device.getKeyId().equals(headers.keyId())) {
            throw invalid("设备签名与当前会话不匹配。");
        }
        requireValidTimeWindow(headers);
        VerifiedDeviceKey verifiedKey = deviceProofService.requireKey(device.getPublicKeySpki(), headers.keyId());
        deviceProofService.requireValidSignature(
                verifiedKey.publicKey(),
                canonicalPayload(request, headers),
                headers.signature()
        );
        return new VerifiedEnvelope(headers);
    }

    public void verifyBodyAndConsumeNonce(CachedBodyHttpServletRequest request, VerifiedEnvelope envelope) {
        String actualDigest = sha256Hex(request.body());
        if (!MessageDigest.isEqual(
                actualDigest.getBytes(StandardCharsets.US_ASCII),
                envelope.headers().bodyDigest().getBytes(StandardCharsets.US_ASCII))) {
            throw invalid("请求内容摘要不匹配。");
        }
        nonceService.requireUnused(envelope.headers().sessionId(), envelope.headers().nonce());
    }

    private SignatureHeaders readHeaders(HttpServletRequest request) {
        try {
            String keyId = required(request, KEY_ID_HEADER);
            long created = Long.parseLong(required(request, CREATED_HEADER));
            long expires = Long.parseLong(required(request, EXPIRES_HEADER));
            String nonce = required(request, NONCE_HEADER);
            String bodyDigest = required(request, BODY_DIGEST_HEADER).toLowerCase(java.util.Locale.ROOT);
            UUID sessionId = UUID.fromString(required(request, SESSION_ID_HEADER));
            String signature = required(request, SIGNATURE_HEADER);
            if (!keyId.matches("[A-Za-z0-9_-]{40,64}")
                    || !nonce.matches("[A-Za-z0-9_-]{22,100}")
                    || !bodyDigest.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("Invalid signature header format.");
            }
            return new SignatureHeaders(keyId, created, expires, nonce, bodyDigest, sessionId, signature);
        } catch (IllegalArgumentException exception) {
            throw invalid("设备签名请求头格式不正确。");
        }
    }

    private void requireValidTimeWindow(SignatureHeaders headers) {
        long now = Instant.now().getEpochSecond();
        if (headers.expires() <= headers.created()
                || headers.expires() - headers.created() > MAX_SIGNATURE_LIFETIME_SECONDS
                || headers.created() > now + CLOCK_SKEW_SECONDS
                || headers.expires() < now - CLOCK_SKEW_SECONDS) {
            throw invalid("设备签名已过期或时间范围不正确。");
        }
    }

    private String canonicalPayload(HttpServletRequest request, SignatureHeaders headers) {
        return String.join("\n",
                "e-report-request-v1",
                "@method:" + request.getMethod().toUpperCase(java.util.Locale.ROOT),
                "@target-uri:" + canonicalTarget(request),
                "content-sha256:" + headers.bodyDigest(),
                "created:" + headers.created(),
                "expires:" + headers.expires(),
                "nonce:" + headers.nonce(),
                "session-id:" + headers.sessionId()
        );
    }

    private String canonicalTarget(HttpServletRequest request) {
        String query = request.getQueryString();
        if (!StringUtils.hasText(query)) {
            return request.getRequestURI();
        }
        String sortedQuery = Arrays.stream(query.split("&"))
                .sorted()
                .collect(Collectors.joining("&"));
        return request.getRequestURI() + "?" + sortedQuery;
    }

    private String required(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Missing header: " + headerName);
        }
        return value.trim();
    }

    private String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    public record VerifiedEnvelope(SignatureHeaders headers) {
    }

    public record SignatureHeaders(String keyId,
                                   long created,
                                   long expires,
                                   String nonce,
                                   String bodyDigest,
                                   UUID sessionId,
                                   String signature) {
    }
}
