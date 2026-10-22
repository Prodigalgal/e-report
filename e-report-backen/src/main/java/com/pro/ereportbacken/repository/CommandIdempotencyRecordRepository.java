package com.pro.ereportbacken.repository;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.CommandIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CommandIdempotencyRecordRepository extends JpaRepository<CommandIdempotencyRecord, UUID> {
    Optional<CommandIdempotencyRecord> findByTenantIdAndMembershipIdAndEndpointAndIdempotencyKey(
            UUID tenantId,
            UUID membershipId,
            String endpoint,
            String idempotencyKey
    );

    long deleteByExpiresAtBefore(LocalDateTime expiry);
}


