package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import java.util.UUID;

import com.pro.ereportbacken.model.pojo.CommandIdempotencyRecord;
import com.pro.ereportbacken.repository.CommandIdempotencyRecordRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class IdempotencyRecordStore {

    private static final String PROCESSING = "PROCESSING";
    private static final String COMPLETED = "COMPLETED";

    private final CommandIdempotencyRecordRepository repository;

    public IdempotencyRecordStore(CommandIdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(UUID tenantId,
                               UUID membershipId,
                               String endpoint,
                               String key,
                               String requestHash,
                               LocalDateTime expiresAt) {
        Optional<CommandIdempotencyRecord> existing = repository
                .findByTenantIdAndMembershipIdAndEndpointAndIdempotencyKey(tenantId, membershipId, endpoint, key);
        if (existing.isPresent()) {
            CommandIdempotencyRecord record = existing.get();
            if (record.getExpiresAt().isAfter(UtcTime.now())) {
                return new Reservation(record, false);
            }
            repository.delete(record);
            repository.flush();
        }

        CommandIdempotencyRecord record = new CommandIdempotencyRecord();
        record.setTenantId(tenantId);
        record.setMembershipId(membershipId);
        record.setEndpoint(endpoint);
        record.setIdempotencyKey(key);
        record.setRequestHash(requestHash);
        record.setState(PROCESSING);
        record.setCreatedAt(UtcTime.now());
        record.setExpiresAt(expiresAt);
        return new Reservation(repository.saveAndFlush(record), true);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<CommandIdempotencyRecord> find(UUID tenantId, UUID membershipId, String endpoint, String key) {
        return repository.findByTenantIdAndMembershipIdAndEndpointAndIdempotencyKey(
                tenantId, membershipId, endpoint, key);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID recordId, String responseBody) {
        CommandIdempotencyRecord record = repository.findById(recordId)
                .orElseThrow(() -> new EntityNotFoundException("Idempotency record not found: " + recordId));
        record.setState(COMPLETED);
        record.setResponseBody(responseBody);
        record.setCompletedAt(UtcTime.now());
        repository.save(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void abandon(UUID recordId) {
        repository.deleteById(recordId);
    }

    public record Reservation(CommandIdempotencyRecord record, boolean owner) {
    }
}

