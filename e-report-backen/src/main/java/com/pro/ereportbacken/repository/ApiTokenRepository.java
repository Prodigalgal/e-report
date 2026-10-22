package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.ApiToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiTokenRepository extends JpaRepository<ApiToken, UUID> {

    @EntityGraph(attributePaths = "permissionNames")
    Optional<ApiToken> findByLookupKey(String lookupKey);

    @EntityGraph(attributePaths = "permissionNames")
    List<ApiToken> findTop100ByTenantIdAndMembershipIdOrderByCreatedAtDesc(UUID tenantId, UUID membershipId);

    Optional<ApiToken> findByTenantIdAndMembershipIdAndId(UUID tenantId, UUID membershipId, UUID id);

    @Query("""
            SELECT COUNT(token)
            FROM ApiToken token
            WHERE token.tenantId = :tenantId
              AND token.membershipId = :membershipId
              AND token.revokedAt IS NULL
              AND (token.expiresAt IS NULL OR token.expiresAt > :now)
            """)
    long countActiveByOwner(@Param("tenantId") UUID tenantId,
                            @Param("membershipId") UUID membershipId,
                            @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ApiToken token
            SET token.lastUsedAt = :usedAt
            WHERE token.id = :tokenId
              AND (token.lastUsedAt IS NULL OR token.lastUsedAt < :updateBefore)
            """)
    int touchLastUsedAt(@Param("tokenId") UUID tokenId,
                        @Param("usedAt") LocalDateTime usedAt,
                        @Param("updateBefore") LocalDateTime updateBefore);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ApiToken token
            SET token.revokedAt = :revokedAt,
                token.revokeReason = :reason
            WHERE token.tenantId = :tenantId
              AND token.membershipId = :membershipId
              AND token.revokedAt IS NULL
            """)
    int revokeAllByOwner(@Param("tenantId") UUID tenantId,
                         @Param("membershipId") UUID membershipId,
                         @Param("revokedAt") LocalDateTime revokedAt,
                         @Param("reason") String reason);
}
