package com.pro.ereportbacken.repository;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.TenantAppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantAppConfigRepository extends JpaRepository<TenantAppConfig, UUID> {
    Optional<TenantAppConfig> findByTenantIdAndConfigKey(UUID tenantId, String configKey);

    List<TenantAppConfig> findByTenantId(UUID tenantId);

    void deleteByTenantIdAndConfigKey(UUID tenantId, String configKey);
}


