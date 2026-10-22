package com.pro.ereportbacken.service;

import com.pro.ereportbacken.config.CacheNames;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthorizationVersionInvalidationService {

    private static final Object TRANSACTION_RESOURCE_KEY =
            AuthorizationVersionInvalidationService.class.getName() + ".changed-memberships";

    private final UserRepository userRepository;
    private final CacheInvalidationService cacheInvalidationService;

    public AuthorizationVersionInvalidationService(UserRepository userRepository,
                                                   CacheInvalidationService cacheInvalidationService) {
        this.userRepository = userRepository;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    public void markChanged(UUID tenantId, UUID membershipId) {
        markChanged(tenantId, Set.of(membershipId));
    }

    public void markChanged(UUID tenantId, Collection<UUID> membershipIds) {
        Set<UUID> requested = membershipIds == null
                ? Set.of()
                : membershipIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (requested.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            bumpVersions(tenantId, requested);
            evict(tenantId, requested);
            return;
        }

        MutationAccumulator accumulator = accumulator();
        Set<UUID> changed = accumulator.add(tenantId, requested);
        if (!changed.isEmpty()) {
            bumpVersions(tenantId, changed);
        }
    }

    private MutationAccumulator accumulator() {
        Object existing = TransactionSynchronizationManager.getResource(TRANSACTION_RESOURCE_KEY);
        if (existing instanceof MutationAccumulator accumulator) {
            return accumulator;
        }
        MutationAccumulator accumulator = new MutationAccumulator();
        TransactionSynchronizationManager.bindResource(TRANSACTION_RESOURCE_KEY, accumulator);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                accumulator.changedByTenant.forEach(AuthorizationVersionInvalidationService.this::evict);
            }

            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.hasResource(TRANSACTION_RESOURCE_KEY)) {
                    TransactionSynchronizationManager.unbindResource(TRANSACTION_RESOURCE_KEY);
                }
            }
        });
        return accumulator;
    }

    private void bumpVersions(UUID tenantId, Set<UUID> membershipIds) {
        List<User> users = userRepository.findByTenantIdAndIdIn(tenantId, List.copyOf(membershipIds));
        if (users.size() != membershipIds.size()) {
            throw new IllegalStateException("授权变更引用了不存在或跨租户的用户。");
        }
        users.forEach(user -> {
            long version = user.getAuthorizationVersion() == null ? 0L : user.getAuthorizationVersion();
            user.setAuthorizationVersion(version + 1L);
        });
        userRepository.saveAllAndFlush(users);
    }

    private void evict(UUID tenantId, Collection<UUID> membershipIds) {
        membershipIds.forEach(membershipId -> cacheInvalidationService.evictNow(
                CacheNames.AUTHORIZATION_SNAPSHOT,
                UserAuthorizationCache.cacheKey(tenantId, membershipId)));
    }

    private static final class MutationAccumulator {
        private final Map<UUID, Set<UUID>> changedByTenant = new LinkedHashMap<>();

        private Set<UUID> add(UUID tenantId, Set<UUID> membershipIds) {
            Set<UUID> changed = changedByTenant.computeIfAbsent(
                    tenantId, ignored -> new LinkedHashSet<>());
            Set<UUID> added = new LinkedHashSet<>(membershipIds);
            added.removeAll(changed);
            changed.addAll(added);
            return Set.copyOf(added);
        }
    }
}
