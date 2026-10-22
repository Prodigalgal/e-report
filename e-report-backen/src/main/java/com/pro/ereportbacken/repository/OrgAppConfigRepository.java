package com.pro.ereportbacken.repository;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.OrgAppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrgAppConfigRepository extends JpaRepository<OrgAppConfig, UUID> {
    Optional<OrgAppConfig> findByTenantIdAndOrgUnitIdAndConfigKey(UUID tenantId, UUID orgUnitId, String configKey);

    List<OrgAppConfig> findByTenantIdAndOrgUnitId(UUID tenantId, UUID orgUnitId);

    List<OrgAppConfig> findByTenantIdAndOrgUnitIdIn(UUID tenantId, Collection<UUID> orgUnitIds);

    List<OrgAppConfig> findByTenantIdAndOrgUnitIdInAndConfigKey(
            UUID tenantId,
            Collection<UUID> orgUnitIds,
            String configKey
    );

    void deleteByTenantIdAndOrgUnitIdAndConfigKey(UUID tenantId, UUID orgUnitId, String configKey);
}


