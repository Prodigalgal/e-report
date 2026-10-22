package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.WorkOrderStatisticsScopeDTO;
import com.pro.ereportbacken.model.pojo.OrgUnitType;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DashboardOrganizationScopeService {

    private final TenantContext tenantContext;
    private final OrganizationScopeAuthorizationService authorizationService;
    private final OrganizationHierarchyService hierarchyService;

    public DashboardOrganizationScopeService(TenantContext tenantContext,
                                             OrganizationScopeAuthorizationService authorizationService,
                                             OrganizationHierarchyService hierarchyService) {
        this.tenantContext = tenantContext;
        this.authorizationService = authorizationService;
        this.hierarchyService = hierarchyService;
    }

    public List<WorkOrderStatisticsScopeDTO> availableScopes() {
        return availableCountyScopes().stream()
                .map(scope -> new WorkOrderStatisticsScopeDTO(scope.county().id(), scope.county().name()))
                .toList();
    }

    public List<OrganizationHierarchyService.CountyScope> availableCountyScopes() {
        if (!hasTenantWideStatisticsAccess()) {
            return List.of();
        }
        return hierarchyService.activeCountyScopes(tenantContext.requireTenantId());
    }

    public Optional<OrganizationHierarchyService.CountyScope> resolveSelectedCounty(UUID countyOrgUnitId) {
        if (countyOrgUnitId == null) {
            return Optional.empty();
        }
        if (!hasTenantWideStatisticsAccess()) {
            throw new AccessDeniedException("当前账号无权查看其他区县统计数据。");
        }
        OrganizationHierarchyService.CountyScope selected = availableCountyScopes().stream()
                .filter(scope -> scope.county().id().equals(countyOrgUnitId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("选择的区县不存在、已停用或不属于当前市公司。"));
        return Optional.of(selected);
    }

    public boolean hasTenantWideStatisticsAccess() {
        return authorizationService.accessFor(statisticsPermissions()).tenantWide();
    }

    public Optional<OrganizationHierarchyService.CountyScope> currentCountyScope(UUID orgUnitId) {
        UUID tenantId = tenantContext.requireTenantId();
        return hierarchyService.nearestAncestorOfType(tenantId, orgUnitId, OrgUnitType.COUNTY)
                .flatMap(county -> hierarchyService.activeCountyScopes(tenantId).stream()
                        .filter(scope -> scope.county().id().equals(county.id()))
                        .findFirst());
    }

    private String[] statisticsPermissions() {
        return new String[]{PermissionCatalog.DASHBOARD_STATISTICS_VIEW};
    }
}
