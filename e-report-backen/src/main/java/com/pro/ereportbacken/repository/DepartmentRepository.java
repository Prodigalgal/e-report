package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.Department;
import com.pro.ereportbacken.model.pojo.DepartmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findByTenantIdOrderByPathAsc(UUID tenantId);

    List<Department> findByTenantIdAndOrgUnitIdInOrderByPathAsc(UUID tenantId, Collection<UUID> orgUnitIds);

    List<Department> findByTenantIdAndOrgUnitIdAndPathStartingWithOrderByPathAsc(
            UUID tenantId, UUID orgUnitId, String path);

    Optional<Department> findByTenantIdAndId(UUID tenantId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Department d where d.tenantId = :tenantId and d.id = :id")
    Optional<Department> findByTenantIdAndIdForUpdate(
            @Param("tenantId") UUID tenantId, @Param("id") UUID id);

    List<Department> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    boolean existsByTenantIdAndOrgUnitIdAndCodeIgnoreCase(
            UUID tenantId, UUID orgUnitId, String code);

    long countByTenantIdAndParentId(UUID tenantId, UUID parentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Department d where d.tenantId = :tenantId and d.id in :ids order by d.id")
    List<Department> findAllByTenantIdAndIdInForUpdate(
            @Param("tenantId") UUID tenantId, @Param("ids") Collection<UUID> ids);

    List<Department> findByTenantIdAndStatus(UUID tenantId, DepartmentStatus status);
}
