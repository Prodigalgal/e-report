package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.pojo.GrantIssuerType;
import com.pro.ereportbacken.model.pojo.Permission;
import com.pro.ereportbacken.model.pojo.PermissionGrant;
import com.pro.ereportbacken.model.pojo.PermissionGrantStatus;
import com.pro.ereportbacken.repository.PermissionGrantRepository;
import com.pro.ereportbacken.repository.PermissionRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.PermissionCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PermissionGrantChainService {

    private static final int MAX_LINEAGE_DEPTH = 10_000;

    private final PermissionGrantRepository grantRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final OrganizationHierarchyService hierarchyService;
    private final AuthorizationVersionInvalidationService versionInvalidationService;

    public PermissionGrantChainService(PermissionGrantRepository grantRepository,
                                       PermissionRepository permissionRepository,
                                       UserRepository userRepository,
                                       OrganizationHierarchyService hierarchyService,
                                       AuthorizationVersionInvalidationService versionInvalidationService) {
        this.grantRepository = grantRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.hierarchyService = hierarchyService;
        this.versionInvalidationService = versionInvalidationService;
    }

    @Transactional(readOnly = true)
    public List<PermissionGrant> effectiveGrants(UUID tenantId, UUID membershipId, LocalDateTime at) {
        return grantRepository.findByTenantIdAndMembershipIdAndStatus(
                        tenantId, membershipId, PermissionGrantStatus.ACTIVE).stream()
                .filter(grant -> isEffectivelyActive(grant, at))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isEffectivelyActive(PermissionGrant grant, LocalDateTime at) {
        Objects.requireNonNull(grant, "grant");
        Objects.requireNonNull(at, "at");
        Set<UUID> visited = new HashSet<>();
        List<PermissionGrant> lineage = new ArrayList<>();
        PermissionGrant cursor = grant;
        for (int depth = 0; depth < MAX_LINEAGE_DEPTH; depth++) {
            if (cursor.getStatus() != PermissionGrantStatus.ACTIVE || !withinValidity(cursor, at)) {
                return false;
            }
            if (!visited.add(cursor.getId())) {
                throw new IllegalStateException("授权链存在循环：" + cursor.getId());
            }
            lineage.add(cursor);
            if (cursor.getParentGrantId() == null) {
                return organizationDirectionIsValid(lineage);
            }
            UUID childPermissionId = cursor.getPermissionId();
            UUID childTenantId = cursor.getTenantId();
            cursor = grantRepository.findById(cursor.getParentGrantId())
                    .filter(parent -> parent.getTenantId().equals(childTenantId))
                    .filter(parent -> parent.getPermissionId().equals(childPermissionId))
                    .orElse(null);
            if (cursor == null) {
                return false;
            }
        }
        throw new IllegalStateException("授权链深度超过安全上限：" + grant.getId());
    }

    private boolean organizationDirectionIsValid(List<PermissionGrant> lineage) {
        UUID tenantId = lineage.getFirst().getTenantId();
        Set<UUID> membershipIds = lineage.stream()
                .map(PermissionGrant::getMembershipId)
                .collect(Collectors.toSet());
        Map<UUID, UUID> orgUnitIdsByMembership = userRepository
                .findMembershipOrganizationsByTenantIdAndMembershipIds(tenantId, membershipIds).stream()
                .collect(Collectors.toMap(
                        UserRepository.MembershipOrganization::getMembershipId,
                        UserRepository.MembershipOrganization::getOrgUnitId));
        if (!orgUnitIdsByMembership.keySet().containsAll(membershipIds)) {
            return false;
        }

        for (int index = 0; index < lineage.size() - 1; index++) {
            PermissionGrant child = lineage.get(index);
            PermissionGrant parent = lineage.get(index + 1);
            UUID childOrgUnitId = orgUnitIdsByMembership.get(child.getMembershipId());
            UUID parentOrgUnitId = orgUnitIdsByMembership.get(parent.getMembershipId());
            if (parentOrgUnitId == null || childOrgUnitId == null) {
                return false;
            }
            if (!parentOrgUnitId.equals(childOrgUnitId)
                    && !hierarchyService.treeContains(tenantId, parentOrgUnitId, childOrgUnitId)) {
                return false;
            }
        }
        return true;
    }

    @Transactional
    public PermissionGrant issueChildGrant(UUID tenantId,
                                           UUID grantorMembershipId,
                                           UUID targetMembershipId,
                                           String permissionCode,
                                           LocalDateTime validFrom,
                                           LocalDateTime expiresAt,
                                           boolean grantOption,
                                           String reason) {
        Permission permission = requirePermission(permissionCode);
        LocalDateTime now = UtcTime.now();
        PermissionGrant parent = requireSingleActiveGrant(
                tenantId, grantorMembershipId, permission.getId());
        if (!Boolean.TRUE.equals(parent.getGrantOption()) || !isEffectivelyActive(parent, now)) {
            throw new IllegalArgumentException("当前账号没有该原子权限的有效转授权能力：" + permissionCode);
        }
        validateValidity(validFrom, expiresAt, parent.getExpiresAt(), now);
        ensureNoActiveGrant(tenantId, targetMembershipId, permission.getId());
        PermissionGrant child = newGrant(
                tenantId, targetMembershipId, permission.getId(), parent.getId(), grantOption,
                GrantIssuerType.USER, grantorMembershipId, validFrom, expiresAt, reason, now);
        PermissionGrant saved = grantRepository.save(child);
        versionInvalidationService.markChanged(tenantId, targetMembershipId);
        return saved;
    }

    @Transactional
    public PermissionGrant issueRootGrant(UUID tenantId,
                                          UUID targetMembershipId,
                                          String permissionCode,
                                          boolean grantOption,
                                          GrantIssuerType issuerType,
                                          LocalDateTime expiresAt,
                                          String reason) {
        if (issuerType != GrantIssuerType.SYSTEM_ROOT
                && issuerType != GrantIssuerType.SYSTEM_SELF_BASELINE
                && issuerType != GrantIssuerType.SYSTEM_BASELINE
                && issuerType != GrantIssuerType.MIGRATION) {
            throw new IllegalArgumentException("根授权必须由系统信任源发行。");
        }
        Permission permission = requirePermission(permissionCode);
        ensureNoActiveGrant(tenantId, targetMembershipId, permission.getId());
        LocalDateTime now = UtcTime.now();
        validateValidity(now, expiresAt, null, now);
        PermissionGrant saved = grantRepository.save(newGrant(
                tenantId, targetMembershipId, permission.getId(), null, grantOption,
                issuerType, null, now, expiresAt, reason, now));
        versionInvalidationService.markChanged(tenantId, targetMembershipId);
        return saved;
    }

    @Transactional
    public PermissionGrant replace(UUID tenantId,
                                   UUID grantId,
                                   UUID newParentGrantId,
                                   UUID operatorMembershipId,
                                   LocalDateTime validFrom,
                                   LocalDateTime expiresAt,
                                   boolean grantOption,
                                   String reason) {
        LocalDateTime now = UtcTime.now();
        PermissionGrant current = requireGrant(tenantId, grantId);
        if (current.getStatus() != PermissionGrantStatus.ACTIVE) {
            throw new IllegalArgumentException("只有 ACTIVE 授权可以调整。");
        }
        PermissionGrant newParent = requireReplacementParent(
                tenantId, current, newParentGrantId, operatorMembershipId, now);
        validateValidity(validFrom, expiresAt,
                newParent == null ? null : newParent.getExpiresAt(), now);
        List<PermissionGrant> children = grantRepository.findByTenantIdAndParentGrantIdInAndStatusIn(
                tenantId,
                List.of(current.getId()),
                List.of(PermissionGrantStatus.ACTIVE, PermissionGrantStatus.SUSPENDED_UPSTREAM));
        validateChildrenRemainCovered(children, expiresAt, grantOption);

        current.setStatus(PermissionGrantStatus.REPLACED);
        current.setStatusChangedAt(now);
        grantRepository.saveAndFlush(current);

        PermissionGrant replacement = newGrant(
                tenantId,
                current.getMembershipId(),
                current.getPermissionId(),
                newParent == null ? null : newParent.getId(),
                grantOption,
                current.getIssuerType(),
                operatorMembershipId,
                validFrom,
                expiresAt,
                reason,
                now);
        replacement = grantRepository.saveAndFlush(replacement);
        current.setReplacedByGrantId(replacement.getId());
        grantRepository.save(current);
        for (PermissionGrant child : children) {
            child.setParentGrantId(replacement.getId());
            grantRepository.save(child);
        }
        versionInvalidationService.markChanged(tenantId, current.getMembershipId());
        return replacement;
    }

    @Transactional
    public PermissionGrant replaceWithSystemRoot(UUID tenantId,
                                                 UUID grantId,
                                                 boolean grantOption,
                                                 GrantIssuerType issuerType,
                                                 String reason) {
        if (issuerType != GrantIssuerType.SYSTEM_ROOT
                && issuerType != GrantIssuerType.SYSTEM_SELF_BASELINE
                && issuerType != GrantIssuerType.SYSTEM_BASELINE
                && issuerType != GrantIssuerType.MIGRATION) {
            throw new IllegalArgumentException("系统根授权只能使用系统或迁移发行类型。");
        }
        LocalDateTime now = UtcTime.now();
        PermissionGrant current = requireGrant(tenantId, grantId);
        if (current.getStatus() != PermissionGrantStatus.ACTIVE) {
            throw new IllegalArgumentException("只有 ACTIVE 授权可以转换为系统根授权。");
        }
        List<PermissionGrant> children = grantRepository.findByTenantIdAndParentGrantIdInAndStatusIn(
                tenantId,
                List.of(current.getId()),
                List.of(PermissionGrantStatus.ACTIVE, PermissionGrantStatus.SUSPENDED_UPSTREAM));
        validateChildrenRemainCovered(children, null, grantOption);

        current.setStatus(PermissionGrantStatus.REPLACED);
        current.setStatusChangedAt(now);
        grantRepository.saveAndFlush(current);

        PermissionGrant replacement = newGrant(
                tenantId,
                current.getMembershipId(),
                current.getPermissionId(),
                null,
                grantOption,
                issuerType,
                null,
                now,
                null,
                reason,
                now);
        replacement = grantRepository.saveAndFlush(replacement);
        current.setReplacedByGrantId(replacement.getId());
        grantRepository.save(current);
        for (PermissionGrant child : children) {
            child.setParentGrantId(replacement.getId());
            grantRepository.save(child);
        }
        versionInvalidationService.markChanged(tenantId, current.getMembershipId());
        return replacement;
    }

    @Transactional
    public PermissionGrant consolidateWithSystemRoot(UUID tenantId,
                                                     Collection<UUID> grantIds,
                                                     boolean grantOption,
                                                     GrantIssuerType issuerType,
                                                     String reason) {
        if (grantIds == null || grantIds.size() < 2) {
            throw new IllegalArgumentException("合并系统根授权至少需要两个 ACTIVE Grant。");
        }
        if (issuerType != GrantIssuerType.SYSTEM_ROOT
                && issuerType != GrantIssuerType.SYSTEM_SELF_BASELINE
                && issuerType != GrantIssuerType.SYSTEM_BASELINE
                && issuerType != GrantIssuerType.MIGRATION) {
            throw new IllegalArgumentException("系统根授权只能使用系统或迁移发行类型。");
        }
        List<PermissionGrant> grants = grantRepository.findAllById(grantIds);
        if (grants.size() != Set.copyOf(grantIds).size()) {
            throw new IllegalArgumentException("待合并 Grant 不完整。");
        }
        PermissionGrant seed = grants.get(0);
        boolean compatible = grants.stream().allMatch(grant ->
                grant.getTenantId().equals(tenantId)
                        && grant.getMembershipId().equals(seed.getMembershipId())
                        && grant.getPermissionId().equals(seed.getPermissionId())
                        && grant.getStatus() == PermissionGrantStatus.ACTIVE);
        if (!compatible) {
            throw new IllegalArgumentException("只能合并同一用户、同一权限的 ACTIVE Grant。");
        }
        LocalDateTime now = UtcTime.now();
        List<PermissionGrant> children = grantRepository.findByTenantIdAndParentGrantIdInAndStatusIn(
                tenantId,
                grants.stream().map(PermissionGrant::getId).toList(),
                List.of(PermissionGrantStatus.ACTIVE, PermissionGrantStatus.SUSPENDED_UPSTREAM));
        validateChildrenRemainCovered(children, null, grantOption);
        grants.forEach(grant -> {
            grant.setStatus(PermissionGrantStatus.REPLACED);
            grant.setStatusChangedAt(now);
        });
        grantRepository.saveAllAndFlush(grants);
        PermissionGrant replacement = grantRepository.saveAndFlush(newGrant(
                tenantId, seed.getMembershipId(), seed.getPermissionId(), null, grantOption,
                issuerType, null, now, null, reason, now));
        grants.forEach(grant -> grant.setReplacedByGrantId(replacement.getId()));
        grantRepository.saveAll(grants);
        children.forEach(child -> {
            child.setParentGrantId(replacement.getId());
            grantRepository.save(child);
        });
        versionInvalidationService.markChanged(tenantId, seed.getMembershipId());
        return replacement;
    }

    private PermissionGrant requireReplacementParent(UUID tenantId,
                                                     PermissionGrant current,
                                                     UUID newParentGrantId,
                                                     UUID operatorMembershipId,
                                                     LocalDateTime now) {
        if (newParentGrantId == null) {
            if (current.getParentGrantId() != null
                    || current.getIssuerType() == GrantIssuerType.USER) {
                throw new IllegalArgumentException("普通授权调整后必须挂接到有效父 Grant。");
            }
            return null;
        }
        PermissionGrant parent = requireGrant(tenantId, newParentGrantId);
        if (!parent.getMembershipId().equals(operatorMembershipId)
                || !parent.getPermissionId().equals(current.getPermissionId())
                || !Boolean.TRUE.equals(parent.getGrantOption())
                || !isEffectivelyActive(parent, now)) {
            throw new IllegalArgumentException("新父 Grant 不属于操作者或不具备兼容的有效转授权能力。");
        }
        requireNoCycle(current.getId(), parent);
        return parent;
    }

    @Transactional
    public void revoke(UUID tenantId,
                       UUID grantId,
                       UUID operatorMembershipId,
                       String reason) {
        LocalDateTime now = UtcTime.now();
        PermissionGrant grant = requireGrant(tenantId, grantId);
        if (grant.getStatus() != PermissionGrantStatus.ACTIVE
                && grant.getStatus() != PermissionGrantStatus.SUSPENDED_UPSTREAM) {
            throw new IllegalArgumentException("该授权已不是可撤销状态。");
        }
        grant.setStatus(PermissionGrantStatus.REVOKED);
        grant.setRevokedByMembershipId(operatorMembershipId);
        grant.setRevokedAt(now);
        grant.setRevokeReason(requireReason(reason));
        grant.setStatusChangedAt(now);
        grantRepository.save(grant);
        Set<UUID> changedMembershipIds = new HashSet<>();
        changedMembershipIds.add(grant.getMembershipId());
        changedMembershipIds.addAll(suspendDescendants(tenantId, List.of(grant.getId()), now));
        versionInvalidationService.markChanged(tenantId, changedMembershipIds);
    }

    @Transactional
    public void reattachSubtree(UUID tenantId,
                                UUID suspendedGrantId,
                                UUID newParentGrantId,
                                UUID operatorMembershipId,
                                String reason) {
        LocalDateTime now = UtcTime.now();
        PermissionGrant suspended = requireGrant(tenantId, suspendedGrantId);
        PermissionGrant parent = requireGrant(tenantId, newParentGrantId);
        if (suspended.getStatus() != PermissionGrantStatus.SUSPENDED_UPSTREAM) {
            throw new IllegalArgumentException("只有因上游失效而暂停的授权可以重新挂接。");
        }
        if (!Objects.equals(suspended.getPermissionId(), parent.getPermissionId())
                || !Boolean.TRUE.equals(parent.getGrantOption())
                || !isEffectivelyActive(parent, now)) {
            throw new IllegalArgumentException("新父授权不具备兼容的有效转授权能力。");
        }
        validateValidity(suspended.getValidFrom(), suspended.getExpiresAt(), parent.getExpiresAt(), now);
        requireNoCycle(suspended.getId(), parent);
        suspended.setParentGrantId(parent.getId());
        suspended.setStatus(PermissionGrantStatus.ACTIVE);
        suspended.setReason(appendAuditReason(suspended.getReason(), operatorMembershipId, reason));
        suspended.setStatusChangedAt(now);
        grantRepository.save(suspended);
        Set<UUID> changedMembershipIds = new HashSet<>();
        changedMembershipIds.add(suspended.getMembershipId());
        changedMembershipIds.addAll(restoreCompatibleDescendants(tenantId, List.of(suspended.getId()), now));
        versionInvalidationService.markChanged(tenantId, changedMembershipIds);
    }

    private PermissionGrant newGrant(UUID tenantId,
                                     UUID membershipId,
                                     UUID permissionId,
                                     UUID parentGrantId,
                                     boolean grantOption,
                                     GrantIssuerType issuerType,
                                     UUID grantedByMembershipId,
                                     LocalDateTime validFrom,
                                     LocalDateTime expiresAt,
                                     String reason,
                                     LocalDateTime now) {
        PermissionGrant grant = new PermissionGrant();
        grant.setTenantId(tenantId);
        grant.setMembershipId(membershipId);
        grant.setPermissionId(permissionId);
        grant.setParentGrantId(parentGrantId);
        grant.setGrantOption(grantOption);
        grant.setIssuerType(issuerType);
        grant.setValidFrom(validFrom);
        grant.setExpiresAt(expiresAt);
        grant.setStatus(PermissionGrantStatus.ACTIVE);
        grant.setCustomized(true);
        grant.setReason(requireReason(reason));
        grant.setGrantedByMembershipId(grantedByMembershipId);
        grant.setGrantedAt(now);
        grant.setStatusChangedAt(now);
        return grant;
    }

    private Set<UUID> suspendDescendants(UUID tenantId, Collection<UUID> parentIds, LocalDateTime now) {
        ArrayDeque<UUID> queue = new ArrayDeque<>(parentIds);
        Set<UUID> visited = new HashSet<>(parentIds);
        Set<UUID> changedMembershipIds = new HashSet<>();
        while (!queue.isEmpty()) {
            List<UUID> batch = drain(queue, 500);
            List<PermissionGrant> children = grantRepository.findByTenantIdAndParentGrantIdInAndStatusIn(
                    tenantId,
                    batch,
                    List.of(PermissionGrantStatus.ACTIVE, PermissionGrantStatus.SUSPENDED_UPSTREAM));
            for (PermissionGrant child : children) {
                if (!visited.add(child.getId())) {
                    throw new IllegalStateException("授权链存在循环：" + child.getId());
                }
                if (child.getStatus() == PermissionGrantStatus.ACTIVE) {
                    child.setStatus(PermissionGrantStatus.SUSPENDED_UPSTREAM);
                    child.setStatusChangedAt(now);
                    grantRepository.save(child);
                    changedMembershipIds.add(child.getMembershipId());
                }
                queue.add(child.getId());
            }
        }
        return Set.copyOf(changedMembershipIds);
    }

    private Set<UUID> restoreCompatibleDescendants(UUID tenantId, Collection<UUID> parentIds, LocalDateTime now) {
        ArrayDeque<UUID> queue = new ArrayDeque<>(parentIds);
        Set<UUID> visited = new HashSet<>(parentIds);
        Set<UUID> changedMembershipIds = new HashSet<>();
        while (!queue.isEmpty()) {
            List<UUID> batch = drain(queue, 500);
            List<PermissionGrant> children = grantRepository.findByTenantIdAndParentGrantIdInAndStatus(
                    tenantId, batch, PermissionGrantStatus.SUSPENDED_UPSTREAM);
            for (PermissionGrant child : children) {
                if (!visited.add(child.getId())) {
                    throw new IllegalStateException("授权链存在循环：" + child.getId());
                }
                if (upstreamActive(child, now) && withinValidity(child, now)) {
                    child.setStatus(PermissionGrantStatus.ACTIVE);
                    child.setStatusChangedAt(now);
                    grantRepository.save(child);
                    changedMembershipIds.add(child.getMembershipId());
                    queue.add(child.getId());
                }
            }
        }
        return Set.copyOf(changedMembershipIds);
    }

    private boolean upstreamActive(PermissionGrant grant, LocalDateTime at) {
        if (grant.getParentGrantId() == null) {
            return true;
        }
        return grantRepository.findById(grant.getParentGrantId())
                .filter(parent -> parent.getTenantId().equals(grant.getTenantId()))
                .filter(parent -> parent.getPermissionId().equals(grant.getPermissionId()))
                .filter(parent -> isEffectivelyActive(parent, at))
                .isPresent();
    }

    private List<UUID> drain(ArrayDeque<UUID> queue, int maximum) {
        java.util.ArrayList<UUID> result = new java.util.ArrayList<>(Math.min(queue.size(), maximum));
        while (!queue.isEmpty() && result.size() < maximum) {
            result.add(queue.removeFirst());
        }
        return result;
    }

    private void validateChildrenRemainCovered(List<PermissionGrant> children,
                                               LocalDateTime expiresAt,
                                               boolean grantOption) {
        if (!children.isEmpty() && !grantOption) {
            throw new IllegalArgumentException("当前授权存在下游授权，取消转授权能力前必须显式处理下游授权。");
        }
        for (PermissionGrant child : children) {
            if (expiresAt != null && (child.getExpiresAt() == null || child.getExpiresAt().isAfter(expiresAt))) {
                throw new IllegalArgumentException("调整后的有效期无法覆盖下游授权：" + child.getId());
            }
        }
    }

    private void validateValidity(LocalDateTime validFrom,
                                  LocalDateTime expiresAt,
                                  LocalDateTime parentExpiresAt,
                                  LocalDateTime now) {
        LocalDateTime effectiveFrom = validFrom == null ? now : validFrom;
        if (expiresAt != null && !expiresAt.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("授权结束时间必须晚于开始时间。");
        }
        if (parentExpiresAt != null && (expiresAt == null || expiresAt.isAfter(parentExpiresAt))) {
            throw new IllegalArgumentException("下游授权有效期不能超过上游授权。");
        }
    }

    private LocalDateTime parentExpiry(PermissionGrant grant) {
        if (grant.getParentGrantId() == null) {
            return null;
        }
        PermissionGrant parent = requireGrant(grant.getTenantId(), grant.getParentGrantId());
        if (!Objects.equals(parent.getPermissionId(), grant.getPermissionId())) {
            throw new IllegalStateException("父子 Grant 的原子权限不一致：" + grant.getId());
        }
        return parent.getExpiresAt();
    }

    private void requireNoCycle(UUID subtreeRootId, PermissionGrant newParent) {
        PermissionGrant cursor = newParent;
        Set<UUID> visited = new HashSet<>();
        for (int depth = 0; depth < MAX_LINEAGE_DEPTH; depth++) {
            if (cursor.getId().equals(subtreeRootId)) {
                throw new IllegalArgumentException("重新挂接会形成授权环。");
            }
            if (!visited.add(cursor.getId())) {
                throw new IllegalStateException("现有授权链已经存在循环：" + cursor.getId());
            }
            if (cursor.getParentGrantId() == null) {
                return;
            }
            cursor = requireGrant(cursor.getTenantId(), cursor.getParentGrantId());
        }
        throw new IllegalStateException("授权链深度超过安全上限。");
    }

    private Permission requirePermission(String permissionCode) {
        PermissionCatalog.require(permissionCode);
        return permissionRepository.findByName(permissionCode)
                .orElseThrow(() -> new IllegalStateException("数据库缺少权限目录项：" + permissionCode));
    }

    private PermissionGrant requireSingleActiveGrant(UUID tenantId, UUID membershipId, UUID permissionId) {
        List<PermissionGrant> active = grantRepository.lockActiveGrant(tenantId, membershipId, permissionId);
        if (active.size() != 1) {
            throw new IllegalArgumentException(active.isEmpty()
                    ? "当前账号没有对应原子权限。"
                    : "当前账号存在重复 ACTIVE Grant，授权目录需要治理。");
        }
        return active.getFirst();
    }

    private void ensureNoActiveGrant(UUID tenantId, UUID membershipId, UUID permissionId) {
        if (!grantRepository.lockActiveGrant(tenantId, membershipId, permissionId).isEmpty()) {
            throw new IllegalArgumentException("目标用户已经持有该原子权限的 ACTIVE Grant，请使用调整操作。");
        }
    }

    private PermissionGrant requireGrant(UUID tenantId, UUID grantId) {
        return grantRepository.findById(grantId)
                .filter(grant -> grant.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("授权不存在或不属于当前租户。"));
    }

    private boolean withinValidity(PermissionGrant grant, LocalDateTime at) {
        return (grant.getValidFrom() == null || !grant.getValidFrom().isAfter(at))
                && (grant.getExpiresAt() == null || grant.getExpiresAt().isAfter(at));
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("授权变更原因不能为空。");
        }
        String normalized = reason.trim();
        if (normalized.length() > 1000) {
            throw new IllegalArgumentException("授权变更原因不能超过1000个字符。");
        }
        return normalized;
    }

    private String appendAuditReason(String current, UUID operatorMembershipId, String reason) {
        String normalized = requireReason(reason);
        String prefix = current == null || current.isBlank() ? "" : current.trim() + "；";
        String appended = prefix + "重新挂接：" + normalized + "（操作者 " + operatorMembershipId + "）";
        return appended.length() <= 1000 ? appended : appended.substring(appended.length() - 1000);
    }
}
