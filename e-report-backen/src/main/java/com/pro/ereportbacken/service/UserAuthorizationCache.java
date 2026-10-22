package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.config.CacheNames;
import com.pro.ereportbacken.model.pojo.Permission;
import com.pro.ereportbacken.model.pojo.PermissionGrant;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.repository.PermissionRepository;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.UserAuthorizationSnapshot;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Log4j2
public class UserAuthorizationCache {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionGrantChainService grantChainService;
    private final CacheManager cacheManager;
    private final CacheInvalidationService cacheInvalidationService;

    public UserAuthorizationCache(UserRepository userRepository,
                                  TenantRepository tenantRepository,
                                  PermissionRepository permissionRepository,
                                  PermissionGrantChainService grantChainService,
                                  CacheManager cacheManager,
                                  CacheInvalidationService cacheInvalidationService) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.permissionRepository = permissionRepository;
        this.grantChainService = grantChainService;
        this.cacheManager = cacheManager;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @Transactional(readOnly = true)
    public UserAuthorizationSnapshot get(UUID tenantId, UUID membershipId, String expectedUsername) {
        UserAuthorizationSnapshot snapshot = get(tenantId, membershipId);
        if (!snapshot.username().equals(expectedUsername)) {
            throw new UsernameNotFoundException("登录凭据与当前账号不匹配。" );
        }
        return snapshot;
    }

    @Transactional(readOnly = true)
    public UserAuthorizationSnapshot get(UUID tenantId, UUID membershipId) {
        String cacheKey = cacheKey(tenantId, membershipId);
        UserAuthorizationSnapshot snapshot = getCached(cacheKey);
        if (snapshot == null) {
            User user = userRepository.findByTenantIdAndId(tenantId, membershipId)
                    .orElseThrow(() -> new UsernameNotFoundException("当前登录账号不存在。"));
            snapshot = snapshot(user);
        }
        if (!snapshot.tenantId().equals(tenantId)
                || !snapshot.membershipId().equals(membershipId)) {
            throw new UsernameNotFoundException("登录凭据与当前账号不匹配。" );
        }
        requireActive(snapshot);
        return snapshot;
    }

    @Transactional(readOnly = true)
    public UserAuthorizationSnapshot snapshot(User user) {
        requireActiveTenant(user.getTenantId());
        UUID tenantId = user.getTenantId();
        LocalDateTime now = UtcTime.now();
        List<PermissionGrant> permissionGrants = grantChainService.effectiveGrants(tenantId, user.getId(), now);

        Map<UUID, Permission> permissionsById = permissionRepository
                .findAllById(permissionGrants.stream().map(PermissionGrant::getPermissionId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Permission::getId, Function.identity()));
        List<UserAuthorizationSnapshot.EffectivePermissionGrant> effectiveGrants = permissionGrants.stream()
                .map(grant -> toEffectiveGrant(grant, permissionsById))
                .filter(java.util.Objects::nonNull)
                .toList();
        UserAuthorizationSnapshot snapshot = new UserAuthorizationSnapshot(
                user.getId(),
                tenantId,
                user.getOrgUnitId(),
                user.getAuthorizationVersion(),
                user.getUsername(),
                user.getSystemKey(),
                user.getStatus(),
                effectiveGrants
        );
        requireActive(snapshot);
        putCached(cacheKey(tenantId, user.getId()), snapshot);
        return snapshot;
    }

    public void evictAfterCommit(UUID tenantId, UUID membershipId) {
        cacheInvalidationService.evictAfterCommit(
                CacheNames.AUTHORIZATION_SNAPSHOT,
                cacheKey(tenantId, membershipId)
        );
    }

    public void clearAfterCommit() {
        cacheInvalidationService.clearAfterCommit(CacheNames.AUTHORIZATION_SNAPSHOT);
    }

    private UserAuthorizationSnapshot.EffectivePermissionGrant toEffectiveGrant(
            PermissionGrant grant,
            Map<UUID, Permission> permissionsById) {
        Permission permission = permissionsById.get(grant.getPermissionId());
        if (permission == null) {
            throw new IllegalStateException("权限授权引用了不存在的权限：" + grant.getId());
        }
        if (!PermissionCatalog.contains(permission.getName())) {
            log.debug("忽略已退役权限 Grant，等待权限目录迁移治理。tenantId={}, membershipId={}, grantId={}, permission={}",
                    grant.getTenantId(), grant.getMembershipId(), grant.getId(), permission.getName());
            return null;
        }
        return new UserAuthorizationSnapshot.EffectivePermissionGrant(
                grant.getId(),
                permission.getName(),
                Boolean.TRUE.equals(grant.getGrantOption()),
                grant.getValidFrom(),
                grant.getExpiresAt()
        );
    }

    private UserAuthorizationSnapshot getCached(String cacheKey) {
        try {
            Cache cache = cacheManager.getCache(CacheNames.AUTHORIZATION_SNAPSHOT);
            return cache == null ? null : cache.get(cacheKey, UserAuthorizationSnapshot.class);
        } catch (RuntimeException exception) {
            log.warn("鉴权快照缓存读取失败，将回源数据库。cacheKey={}", cacheKey, exception);
            return null;
        }
    }

    private void putCached(String cacheKey, UserAuthorizationSnapshot snapshot) {
        try {
            Cache cache = cacheManager.getCache(CacheNames.AUTHORIZATION_SNAPSHOT);
            if (cache != null) {
                cache.put(cacheKey, snapshot);
            }
        } catch (RuntimeException exception) {
            log.warn("鉴权快照缓存写入失败，本次继续使用数据库结果。cacheKey={}", cacheKey, exception);
        }
    }

    private void requireActiveTenant(UUID tenantId) {
        tenantRepository.findById(tenantId)
                .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                .orElseThrow(() -> new UsernameNotFoundException("账号所属市公司已停用。"));
    }

    private void requireActive(UserAuthorizationSnapshot snapshot) {
        if (snapshot.status() != UserStatus.ACTIVE) {
            throw new UsernameNotFoundException("账号当前不可用。" );
        }
    }

    static String cacheKey(UUID tenantId, UUID membershipId) {
        return tenantId + ":" + membershipId;
    }
}
