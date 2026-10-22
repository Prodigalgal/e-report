package com.pro.ereportbacken.service;

import com.pro.ereportbacken.config.CacheNames;
import com.pro.ereportbacken.model.dto.OrgUnitDTO;
import com.pro.ereportbacken.model.dto.OrganizationDirectorySnapshot;
import com.pro.ereportbacken.model.pojo.OrgUnit;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.TenantRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Log4j2
public class OrganizationDirectoryCache {

    private final OrgUnitRepository orgUnitRepository;
    private final TenantRepository tenantRepository;
    private final CacheManager cacheManager;
    private final CacheInvalidationService cacheInvalidationService;

    public OrganizationDirectoryCache(OrgUnitRepository orgUnitRepository,
                                      TenantRepository tenantRepository,
                                      CacheManager cacheManager,
                                      CacheInvalidationService cacheInvalidationService) {
        this.orgUnitRepository = orgUnitRepository;
        this.tenantRepository = tenantRepository;
        this.cacheManager = cacheManager;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @Transactional(readOnly = true)
    public OrganizationDirectorySnapshot get(UUID tenantId) {
        String cacheKey = tenantId.toString();
        OrganizationDirectorySnapshot cached = getCached(cacheKey);
        if (cached != null) {
            return cached;
        }
        UUID rootOrgUnitId = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Tenant not found with id: " + tenantId))
                .getRootOrgUnitId();
        List<OrgUnitDTO> orgUnits = orgUnitRepository.findByTenantIdOrderByPathAsc(tenantId).stream()
                .map(orgUnit -> toDTO(orgUnit, rootOrgUnitId))
                .toList();
        OrganizationDirectorySnapshot snapshot = new OrganizationDirectorySnapshot(orgUnits);
        putCached(cacheKey, snapshot);
        return snapshot;
    }

    public void invalidateAfterCommit(UUID tenantId) {
        cacheInvalidationService.evictAfterCommit(CacheNames.ORGANIZATION_DIRECTORY, tenantId.toString());
    }

    private OrganizationDirectorySnapshot getCached(String cacheKey) {
        try {
            Cache cache = cacheManager.getCache(CacheNames.ORGANIZATION_DIRECTORY);
            return cache == null ? null : cache.get(cacheKey, OrganizationDirectorySnapshot.class);
        } catch (RuntimeException exception) {
            log.warn("组织目录缓存读取失败，将回源数据库。cacheKey={}", cacheKey, exception);
            return null;
        }
    }

    private void putCached(String cacheKey, OrganizationDirectorySnapshot snapshot) {
        try {
            Cache cache = cacheManager.getCache(CacheNames.ORGANIZATION_DIRECTORY);
            if (cache != null) {
                cache.put(cacheKey, snapshot);
            }
        } catch (RuntimeException exception) {
            log.warn("组织目录缓存写入失败，本次继续使用数据库结果。cacheKey={}", cacheKey, exception);
        }
    }

    private OrgUnitDTO toDTO(OrgUnit orgUnit, UUID rootOrgUnitId) {
        return new OrgUnitDTO(
                orgUnit.getId(),
                orgUnit.getTenantId(),
                orgUnit.getParentId(),
                orgUnit.getCode(),
                orgUnit.getName(),
                orgUnit.getUnitType(),
                orgUnit.getPath(),
                orgUnit.getStatus(),
                orgUnit.getId().equals(rootOrgUnitId)
        );
    }
}
