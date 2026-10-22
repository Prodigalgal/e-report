package com.pro.ereportbacken.service;

import com.pro.ereportbacken.config.CacheNames;
import com.pro.ereportbacken.model.pojo.Permission;
import com.pro.ereportbacken.model.pojo.PermissionGrant;
import com.pro.ereportbacken.model.pojo.PermissionGrantStatus;
import com.pro.ereportbacken.model.pojo.Tenant;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.repository.PermissionRepository;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAuthorizationCacheTests {

    @Test
    void compilesExplicitPermissionGrantsAndReloadsAfterEviction() {
        UUID tenantId = uuid(1);
        UUID userId = uuid(2);
        UUID organizationId = uuid(3);
        UUID permissionId = uuid(5);

        UserRepository userRepository = mock(UserRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        PermissionRepository permissionRepository = mock(PermissionRepository.class);
        PermissionGrantChainService grantChainService = mock(PermissionGrantChainService.class);
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(CacheNames.AUTHORIZATION_SNAPSHOT);
        CacheInvalidationService invalidationService = new CacheInvalidationService(
                cacheManager, mock(ApplicationEventPublisher.class));
        UserAuthorizationCache cache = new UserAuthorizationCache(
                userRepository, tenantRepository, permissionRepository, grantChainService,
                cacheManager, invalidationService);

        User user = user(tenantId, userId, organizationId);
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);
        Permission permission = new Permission("work-order:network-fault:view", "网络故障查看", "查看工单");
        permission.setId(permissionId);
        PermissionGrant grant = new PermissionGrant();
        grant.setId(uuid(7));
        grant.setTenantId(tenantId);
        grant.setMembershipId(userId);
        grant.setPermissionId(permissionId);
        grant.setValidFrom(LocalDateTime.now().minusHours(1));
        grant.setExpiresAt(LocalDateTime.now().plusHours(1));
        grant.setStatus(PermissionGrantStatus.ACTIVE);

        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(grantChainService.effectiveGrants(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(List.of(grant));
        when(permissionRepository.findAllById(Set.of(permissionId))).thenReturn(List.of(permission));
        var first = cache.get(tenantId, userId, "worker");
        var second = cache.get(tenantId, userId, "worker");

        assertThat(second).isEqualTo(first);
        assertThat(first.authorities()).containsExactly("work-order:network-fault:view");
        assertThat(first.grants()).singleElement().satisfies(effective -> {
            assertThat(effective.permissionName()).isEqualTo("work-order:network-fault:view");
            assertThat(effective.grantOption()).isFalse();
        });
        verify(userRepository).findByTenantIdAndId(tenantId, userId);
        verify(grantChainService).effectiveGrants(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));

        cache.evictAfterCommit(tenantId, userId);
        cache.get(tenantId, userId, "worker");
        verify(userRepository, times(2)).findByTenantIdAndId(tenantId, userId);
    }

    private User user(UUID tenantId, UUID userId, UUID organizationId) {
        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setOrgUnitId(organizationId);
        user.setUsername("worker");
        user.setPassword("encoded-password-must-not-enter-snapshot");
        user.setAuthorizationVersion(3L);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private UUID uuid(long seed) {
        return com.pro.ereportbacken.TestIds.uuid(seed);
    }
}
