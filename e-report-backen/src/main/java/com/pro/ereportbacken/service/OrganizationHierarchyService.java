package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.OrgUnitDTO;
import com.pro.ereportbacken.model.pojo.OrgUnitType;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class OrganizationHierarchyService {

    private final OrganizationDirectoryCache organizationDirectoryCache;

    public OrganizationHierarchyService(OrganizationDirectoryCache organizationDirectoryCache) {
        this.organizationDirectoryCache = organizationDirectoryCache;
    }

    public List<OrgUnitDTO> activeCounties(UUID tenantId) {
        return organizationDirectoryCache.get(tenantId).orgUnits().stream()
                .filter(orgUnit -> orgUnit.unitType() == OrgUnitType.COUNTY)
                .filter(orgUnit -> orgUnit.status() == TenantStatus.ACTIVE)
                .sorted(Comparator.comparing(OrgUnitDTO::path).thenComparing(OrgUnitDTO::name))
                .toList();
    }

    public List<CountyScope> activeCountyScopes(UUID tenantId) {
        List<OrgUnitDTO> orgUnits = organizationDirectoryCache.get(tenantId).orgUnits();
        Map<UUID, List<UUID>> childIds = childIds(orgUnits);
        return orgUnits.stream()
                .filter(orgUnit -> orgUnit.unitType() == OrgUnitType.COUNTY)
                .filter(orgUnit -> orgUnit.status() == TenantStatus.ACTIVE)
                .sorted(Comparator.comparing(OrgUnitDTO::path).thenComparing(OrgUnitDTO::name))
                .map(county -> new CountyScope(county, descendantIds(county.id(), childIds)))
                .toList();
    }

    public Optional<OrgUnitDTO> nearestAncestorOfType(UUID tenantId, UUID orgUnitId, OrgUnitType type) {
        if (orgUnitId == null) {
            return Optional.empty();
        }
        Map<UUID, OrgUnitDTO> byId = byId(tenantId);
        OrgUnitDTO current = byId.get(orgUnitId);
        Set<UUID> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current.id())) {
            if (current.unitType() == type) {
                return Optional.of(current);
            }
            current = current.parentId() == null ? null : byId.get(current.parentId());
        }
        return Optional.empty();
    }

    public UUID parentId(UUID tenantId, UUID orgUnitId) {
        OrgUnitDTO organization = byId(tenantId).get(orgUnitId);
        if (organization == null) {
            throw new EntityNotFoundException("组织不存在或不属于当前租户: " + orgUnitId);
        }
        return organization.parentId();
    }

    public Set<UUID> descendantIds(UUID tenantId, UUID rootOrgUnitId) {
        Map<UUID, OrgUnitDTO> byId = byId(tenantId);
        if (!byId.containsKey(rootOrgUnitId)) {
            throw new EntityNotFoundException("组织不存在或不属于当前租户: " + rootOrgUnitId);
        }
        return descendantIds(rootOrgUnitId, childIds(byId.values()));
    }

    private Set<UUID> descendantIds(UUID rootOrgUnitId, Map<UUID, List<UUID>> childIds) {
        Set<UUID> result = new LinkedHashSet<>();
        Deque<UUID> pending = new ArrayDeque<>();
        pending.add(rootOrgUnitId);
        while (!pending.isEmpty()) {
            UUID current = pending.removeFirst();
            if (result.add(current)) {
                pending.addAll(childIds.getOrDefault(current, List.of()));
            }
        }
        return Set.copyOf(result);
    }

    public Set<UUID> allIds(UUID tenantId) {
        return organizationDirectoryCache.get(tenantId).orgUnits().stream()
                .map(OrgUnitDTO::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Map<UUID, String> namesById(UUID tenantId, Collection<UUID> orgUnitIds) {
        if (orgUnitIds == null || orgUnitIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> requestedIds = Set.copyOf(orgUnitIds);
        return organizationDirectoryCache.get(tenantId).orgUnits().stream()
                .filter(orgUnit -> requestedIds.contains(orgUnit.id()))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(OrgUnitDTO::id, OrgUnitDTO::name));
    }

    public boolean treeContains(UUID tenantId, UUID scopeOrgUnitId, UUID targetOrgUnitId) {
        if (scopeOrgUnitId == null || targetOrgUnitId == null) {
            return false;
        }
        Map<UUID, OrgUnitDTO> byId = byId(tenantId);
        OrgUnitDTO scope = byId.get(scopeOrgUnitId);
        OrgUnitDTO target = byId.get(targetOrgUnitId);
        return scope != null && target != null && target.path().startsWith(scope.path());
    }

    private Map<UUID, List<UUID>> childIds(Iterable<OrgUnitDTO> orgUnits) {
        Map<UUID, List<UUID>> result = new HashMap<>();
        for (OrgUnitDTO orgUnit : orgUnits) {
            if (orgUnit.parentId() != null) {
                result.computeIfAbsent(orgUnit.parentId(), ignored -> new ArrayList<>()).add(orgUnit.id());
            }
        }
        return result;
    }

    private Map<UUID, OrgUnitDTO> byId(UUID tenantId) {
        Map<UUID, OrgUnitDTO> result = new HashMap<>();
        organizationDirectoryCache.get(tenantId).orgUnits().forEach(orgUnit -> result.put(orgUnit.id(), orgUnit));
        return result;
    }

    public record CountyScope(OrgUnitDTO county, Set<UUID> descendantIds) {
        public CountyScope {
            descendantIds = Set.copyOf(descendantIds);
        }
    }
}
