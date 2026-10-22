package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.AuthorizationChangePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthorizationChangePlanRepository extends JpaRepository<AuthorizationChangePlan, UUID> {
    Optional<AuthorizationChangePlan> findByTenantIdAndId(UUID tenantId, UUID id);
}
