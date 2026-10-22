package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.AuthSession;
import com.pro.ereportbacken.model.pojo.AuthSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT session FROM AuthSession session WHERE session.id = :sessionId")
    Optional<AuthSession> findByIdForUpdate(@Param("sessionId") UUID sessionId);

    Page<AuthSession> findByTenantIdAndMembershipId(UUID tenantId, UUID membershipId, Pageable pageable);

    long countByTenantIdAndMembershipIdAndStatusAndIdNot(
            UUID tenantId,
            UUID membershipId,
            AuthSessionStatus status,
            UUID excludedSessionId
    );

    List<AuthSession> findTop500ByStatusNotAndRevokedAtBeforeOrderByRevokedAtAsc(
            AuthSessionStatus status,
            LocalDateTime cutoff
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AuthSession session
            SET session.status = :status,
                session.revokedAt = :revokedAt,
                session.revokeReason = :reason,
                session.webSecretHash = NULL,
                session.refreshTokenHash = NULL,
                session.previousRefreshTokenHash = NULL
            WHERE session.tenantId = :tenantId
              AND session.membershipId = :membershipId
              AND session.status = com.pro.ereportbacken.model.pojo.AuthSessionStatus.ACTIVE
              AND (:exceptSessionId IS NULL OR session.id <> :exceptSessionId)
            """)
    int revokeActiveSessions(@Param("tenantId") UUID tenantId,
                             @Param("membershipId") UUID membershipId,
                             @Param("exceptSessionId") UUID exceptSessionId,
                             @Param("status") AuthSessionStatus status,
                             @Param("revokedAt") LocalDateTime revokedAt,
                             @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AuthSession session
            SET session.status = com.pro.ereportbacken.model.pojo.AuthSessionStatus.EXPIRED,
                session.revokedAt = :expiredAt,
                session.revokeReason = :reason,
                session.webSecretHash = NULL,
                session.refreshTokenHash = NULL,
                session.previousRefreshTokenHash = NULL
            WHERE session.status = com.pro.ereportbacken.model.pojo.AuthSessionStatus.ACTIVE
              AND (session.idleExpiresAt <= :expiredAt OR session.absoluteExpiresAt <= :expiredAt)
            """)
    int expireActiveSessions(@Param("expiredAt") LocalDateTime expiredAt,
                             @Param("reason") String reason);
}
