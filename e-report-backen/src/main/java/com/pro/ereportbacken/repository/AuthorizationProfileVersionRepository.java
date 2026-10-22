package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.AuthorizationProfileVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthorizationProfileVersionRepository extends JpaRepository<AuthorizationProfileVersion, UUID> {

    Optional<AuthorizationProfileVersion> findByTenantIdAndId(UUID tenantId, UUID id);

    List<AuthorizationProfileVersion> findByTenantIdAndProfileIdOrderByVersionNoDesc(UUID tenantId, UUID profileId);

    List<AuthorizationProfileVersion> findByTenantIdAndProfileIdInOrderByProfileIdAscVersionNoDesc(
            UUID tenantId,
            Collection<UUID> profileIds
    );
}
