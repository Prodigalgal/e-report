package com.pro.ereportbacken.repository;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.Tenant;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.model.pojo.TenantSystemKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findByCode(String code);

    Optional<Tenant> findBySystemKey(TenantSystemKey systemKey);

    List<Tenant> findAllByOrderByNameAsc();

    List<Tenant> findByStatusOrderByNameAsc(TenantStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tenant from Tenant tenant where tenant.id = :id")
    Optional<Tenant> findByIdForUpdate(@Param("id") UUID id);
}


