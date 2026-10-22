package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.TrustedDevice;
import com.pro.ereportbacken.model.pojo.TrustedDeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, UUID> {

    Optional<TrustedDevice> findByTenantIdAndMembershipIdAndKeyId(UUID tenantId, UUID membershipId, String keyId);

    List<TrustedDevice> findByTenantIdAndMembershipIdAndStatusOrderByLastSeenAtDesc(
            UUID tenantId,
            UUID membershipId,
            TrustedDeviceStatus status
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TrustedDevice device
            SET device.status = :status,
                device.revokedAt = :revokedAt,
                device.revokeReason = :reason
            WHERE device.tenantId = :tenantId
              AND device.membershipId = :membershipId
              AND device.status = com.pro.ereportbacken.model.pojo.TrustedDeviceStatus.TRUSTED
            """)
    int requireReauthenticationByOwner(@Param("tenantId") UUID tenantId,
                                       @Param("membershipId") UUID membershipId,
                                       @Param("status") TrustedDeviceStatus status,
                                       @Param("revokedAt") LocalDateTime revokedAt,
                                       @Param("reason") String reason);
}
