package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.AuthorizationProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthorizationProfileRepository extends JpaRepository<AuthorizationProfile, UUID> {

    Optional<AuthorizationProfile> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<AuthorizationProfile> findByTenantIdAndCode(UUID tenantId, String code);

    Optional<AuthorizationProfile> findByTenantIdAndSystemKey(UUID tenantId, String systemKey);

    List<AuthorizationProfile> findByTenantIdAndActiveTrueOrderBySortOrderAscDisplayNameAsc(UUID tenantId);

    Page<AuthorizationProfile> findByTenantId(UUID tenantId, Pageable pageable);
}
