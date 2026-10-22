package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.PermissionGrant;
import com.pro.ereportbacken.model.pojo.PermissionGrantStatus;
import com.pro.ereportbacken.model.pojo.GrantIssuerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionGrantRepository extends JpaRepository<PermissionGrant, UUID> {

    List<PermissionGrant> findByTenantIdAndMembershipIdAndStatus(
            UUID tenantId,
            UUID membershipId,
            PermissionGrantStatus status
    );

    List<PermissionGrant> findByTenantIdAndMembershipIdInAndStatus(
            UUID tenantId,
            Collection<UUID> membershipIds,
            PermissionGrantStatus status
    );

    List<PermissionGrant> findByTenantIdAndMembershipIdInAndStatusIn(
            UUID tenantId,
            Collection<UUID> membershipIds,
            Collection<PermissionGrantStatus> statuses
    );

    Optional<PermissionGrant> findByTenantIdAndMembershipIdAndPermissionIdAndStatus(
            UUID tenantId,
            UUID membershipId,
            UUID permissionId,
            PermissionGrantStatus status
    );

    List<PermissionGrant> findByTenantIdAndParentGrantIdAndStatus(
            UUID tenantId,
            UUID parentGrantId,
            PermissionGrantStatus status
    );

    List<PermissionGrant> findByTenantIdAndParentGrantIdInAndStatus(
            UUID tenantId,
            Collection<UUID> parentGrantIds,
            PermissionGrantStatus status
    );

    List<PermissionGrant> findByTenantIdAndParentGrantIdInAndStatusIn(
            UUID tenantId,
            Collection<UUID> parentGrantIds,
            Collection<PermissionGrantStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT permissionGrant
            FROM PermissionGrant permissionGrant
            WHERE permissionGrant.tenantId = :tenantId
              AND permissionGrant.membershipId = :membershipId
              AND permissionGrant.permissionId = :permissionId
              AND permissionGrant.status = com.pro.ereportbacken.model.pojo.PermissionGrantStatus.ACTIVE
            """)
    List<PermissionGrant> lockActiveGrant(
            @Param("tenantId") UUID tenantId,
            @Param("membershipId") UUID membershipId,
            @Param("permissionId") UUID permissionId
    );

    @Query("""
            SELECT DISTINCT permissionGrant.membershipId
            FROM PermissionGrant permissionGrant, Permission permission
            WHERE permissionGrant.tenantId = :tenantId
              AND permissionGrant.permissionId = permission.id
              AND permission.name = :permissionName
              AND permissionGrant.status = com.pro.ereportbacken.model.pojo.PermissionGrantStatus.ACTIVE
              AND (permissionGrant.validFrom IS NULL OR permissionGrant.validFrom <= :now)
              AND (permissionGrant.expiresAt IS NULL OR permissionGrant.expiresAt > :now)
            """)
    List<UUID> findActiveMembershipIdsByPermission(
            @Param("tenantId") UUID tenantId,
            @Param("permissionName") String permissionName,
            @Param("now") LocalDateTime now
    );

    @Query("""
            SELECT DISTINCT permissionGrant.membershipId
            FROM PermissionGrant permissionGrant, Permission permission
            WHERE permissionGrant.permissionId = permission.id
              AND permissionGrant.status = com.pro.ereportbacken.model.pojo.PermissionGrantStatus.ACTIVE
              AND permissionGrant.issuerType = :issuerType
              AND permission.name = :permissionName
            ORDER BY permissionGrant.membershipId
            """)
    List<UUID> findActiveMembershipIdsByPermissionAndIssuerType(
            @Param("permissionName") String permissionName,
            @Param("issuerType") GrantIssuerType issuerType
    );
}
