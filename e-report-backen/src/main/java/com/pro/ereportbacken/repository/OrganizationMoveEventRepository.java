package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.OrganizationMoveEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface OrganizationMoveEventRepository extends JpaRepository<OrganizationMoveEvent, UUID> {
    List<OrganizationMoveEvent> findByTenantIdAndOrgUnitIdOrderByCreatedAtDesc(UUID tenantId, UUID orgUnitId);
}
