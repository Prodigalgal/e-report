package com.pro.ereportbacken.service;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.OrgUnit;
import com.pro.ereportbacken.model.pojo.Tenant;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.model.pojo.TenantSystemKey;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultTenantService {

    private final TenantRepository tenantRepository;
    private final OrgUnitRepository orgUnitRepository;

    public DefaultTenantService(TenantRepository tenantRepository,
                                OrgUnitRepository orgUnitRepository) {
        this.tenantRepository = tenantRepository;
        this.orgUnitRepository = orgUnitRepository;
    }

    @Transactional(readOnly = true)
    public Tenant requireDefaultTenant() {
        Tenant tenant = tenantRepository.findBySystemKey(TenantSystemKey.DEFAULT_TENANT)
                .orElseThrow(() -> new EntityNotFoundException("数据库未配置默认市公司租户。"));
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new IllegalStateException("默认市公司租户已停用。 ");
        }
        return tenant;
    }

    @Transactional(readOnly = true)
    public OrgUnit requireRootOrgUnit(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + tenantId));
        if (tenant.getRootOrgUnitId() == null) {
            throw new EntityNotFoundException("Root organization is not configured for tenant " + tenantId + ".");
        }
        return orgUnitRepository.findByTenantIdAndId(tenantId, tenant.getRootOrgUnitId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Configured root organization does not belong to tenant " + tenantId + "."));
    }

}

