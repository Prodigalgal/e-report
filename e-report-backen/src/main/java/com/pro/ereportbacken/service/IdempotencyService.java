package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.pojo.CommandIdempotencyRecord;
import com.pro.ereportbacken.security.TenantUserDetails;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

@Service
@Log4j2
public class IdempotencyService {

    private static final int MAX_KEY_LENGTH = 120;
    private static final String COMPLETED = "COMPLETED";
    private static final long RETENTION_HOURS = 24;

    private final IdempotencyRecordStore recordStore;
    private final ObjectMapper objectMapper;
    private final IdempotencyResponseCache responseCache;

    public IdempotencyService(IdempotencyRecordStore recordStore,
                              ObjectMapper objectMapper,
                              IdempotencyResponseCache responseCache) {
        this.recordStore = recordStore;
        this.objectMapper = objectMapper;
        this.responseCache = responseCache;
    }

    public <T> T execute(String rawKey,
                         String endpoint,
                         Object request,
                         Class<T> responseType,
                         Supplier<T> operation) {
        if (!StringUtils.hasText(rawKey)) {
            log.debug("Command without Idempotency-Key: {}", endpoint);
            return operation.get();
        }

        String key = rawKey.trim();
        if (key.length() > MAX_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key 不能超过120字符。");
        }
        TenantUserDetails principal = currentPrincipal();
        String requestHash = hash(request);
        var cachedResponse = responseCache.find(
                principal.tenantId(), principal.membershipId(), endpoint, key
        );
        if (cachedResponse.isPresent()) {
            requireSameRequest(cachedResponse.get().requestHash(), requestHash);
            return deserialize(cachedResponse.get().responseBody(), responseType);
        }
        IdempotencyRecordStore.Reservation reservation;
        try {
            reservation = recordStore.reserve(
                    principal.tenantId(),
                    principal.membershipId(),
                    endpoint,
                    key,
                    requestHash,
                    UtcTime.now().plusHours(RETENTION_HOURS)
            );
        } catch (DataIntegrityViolationException conflict) {
            reservation = new IdempotencyRecordStore.Reservation(
                    recordStore.find(principal.tenantId(), principal.membershipId(), endpoint, key)
                            .orElseThrow(() -> conflict),
                    false
            );
        }

        CommandIdempotencyRecord record = reservation.record();
        requireSameRequest(record, requestHash);
        if (COMPLETED.equals(record.getState())) {
            responseCache.put(
                    principal.tenantId(), principal.membershipId(), endpoint, key,
                    requestHash, record.getResponseBody()
            );
            return deserialize(record.getResponseBody(), responseType);
        }
        if (!reservation.owner()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "相同请求正在处理中，请稍后查询结果或重试。");
        }

        try {
            T response = operation.get();
            String responseBody = serialize(response);
            recordStore.complete(record.getId(), responseBody);
            responseCache.put(
                    principal.tenantId(), principal.membershipId(), endpoint, key,
                    requestHash, responseBody
            );
            return response;
        } catch (RuntimeException | Error failure) {
            try {
                recordStore.abandon(record.getId());
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private void requireSameRequest(CommandIdempotencyRecord reservation, String requestHash) {
        requireSameRequest(reservation.getRequestHash(), requestHash);
    }

    private void requireSameRequest(String storedRequestHash, String requestHash) {
        if (!requestHash.equals(storedRequestHash)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "相同 Idempotency-Key 已用于不同请求，请生成新键后重试。"
            );
        }
    }

    private TenantUserDetails currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantUserDetails principal)) {
            throw new AccessDeniedException("Authenticated tenant membership is required for idempotent commands.");
        }
        return principal;
    }

    private String hash(Object request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法生成请求幂等摘要。", exception);
        }
    }

    private String serialize(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存幂等响应。", exception);
        }
    }

    private <T> T deserialize(String responseBody, Class<T> responseType) {
        if (!StringUtils.hasText(responseBody)) {
            throw new IllegalStateException("已完成的幂等请求缺少响应快照。");
        }
        try {
            return objectMapper.readValue(responseBody, responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取幂等响应快照。", exception);
        }
    }
}
