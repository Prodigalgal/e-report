package com.pro.ereportbacken.service;

import com.pro.ereportbacken.config.CacheNames;
import com.pro.ereportbacken.model.pojo.OrgUnit;
import com.pro.ereportbacken.model.pojo.OrgUnitType;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.model.pojo.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationDirectoryCacheTests {

    @Test
    void loadsTenantDirectoryOnceAndReloadsAfterEviction() {
        UUID tenantId = uuid(1);
        OrgUnitRepository repository = mock(OrgUnitRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        OrgUnit county = county(tenantId);
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setRootOrgUnitId(county.getId());
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(CacheNames.ORGANIZATION_DIRECTORY);
        OrganizationDirectoryCache cache = new OrganizationDirectoryCache(
                repository,
                tenantRepository,
                cacheManager,
                new CacheInvalidationService(cacheManager, mock(ApplicationEventPublisher.class))
        );
        when(tenantRepository.findById(tenantId)).thenReturn(java.util.Optional.of(tenant));
        when(repository.findByTenantIdOrderByPathAsc(tenantId)).thenReturn(List.of(county));

        assertThat(cache.get(tenantId).orgUnits()).extracting(org -> org.name()).containsExactly("南靖县");
        assertThat(cache.get(tenantId).orgUnits().getFirst().tenantRoot()).isTrue();
        assertThat(cache.get(tenantId).orgUnits()).hasSize(1);
        verify(repository).findByTenantIdOrderByPathAsc(tenantId);

        cache.invalidateAfterCommit(tenantId);
        cache.get(tenantId);

        verify(repository, times(2)).findByTenantIdOrderByPathAsc(tenantId);
    }

    private OrgUnit county(UUID tenantId) {
        OrgUnit orgUnit = new OrgUnit();
        orgUnit.setId(uuid(2));
        orgUnit.setTenantId(tenantId);
        orgUnit.setCode("NANJING");
        orgUnit.setName("南靖县");
        orgUnit.setUnitType(OrgUnitType.COUNTY);
        orgUnit.setPath("/" + uuid(2) + "/");
        orgUnit.setStatus(TenantStatus.ACTIVE);
        return orgUnit;
    }

    private UUID uuid(long seed) {
        return com.pro.ereportbacken.TestIds.uuid(seed);
    }
}
