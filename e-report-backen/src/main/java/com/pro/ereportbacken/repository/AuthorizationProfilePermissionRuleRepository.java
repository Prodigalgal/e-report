package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.AuthorizationProfilePermissionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AuthorizationProfilePermissionRuleRepository
        extends JpaRepository<AuthorizationProfilePermissionRule, UUID> {

    List<AuthorizationProfilePermissionRule> findByTenantIdAndProfileVersionIdOrderBySortOrderAsc(
            UUID tenantId,
            UUID profileVersionId
    );

    List<AuthorizationProfilePermissionRule> findByTenantIdAndProfileVersionIdInOrderBySortOrderAsc(
            UUID tenantId,
            Collection<UUID> profileVersionIds
    );
}
