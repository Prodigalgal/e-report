package com.pro.ereportbacken.repository;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.OrgUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, UUID> {
    Optional<OrgUnit> findByTenantIdAndCode(UUID tenantId, String code);

    Optional<OrgUnit> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndId(UUID tenantId, UUID id);

    List<OrgUnit> findByTenantId(UUID tenantId);

    List<OrgUnit> findByTenantIdOrderByPathAsc(UUID tenantId);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT orgUnit FROM OrgUnit orgUnit WHERE orgUnit.tenantId = :tenantId AND orgUnit.id IN :ids ORDER BY orgUnit.id")
    List<OrgUnit> findAllByTenantIdAndIdInForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("ids") List<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OrgUnit> findByTenantIdAndPathStartingWithOrderByPathAsc(UUID tenantId, String pathPrefix);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}


