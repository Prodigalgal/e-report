package com.pro.ereportbacken.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Log4j2
public class CacheInvalidationService {

    private final CacheManager cacheManager;
    private final ApplicationEventPublisher eventPublisher;

    public CacheInvalidationService(CacheManager cacheManager, ApplicationEventPublisher eventPublisher) {
        this.cacheManager = cacheManager;
        this.eventPublisher = eventPublisher;
    }

    public void evictAfterCommit(String cacheName, Object key) {
        scheduleAfterCommit(() -> evict(cacheName, key));
    }

    public void clearAfterCommit(String cacheName) {
        scheduleAfterCommit(() -> clear(cacheName));
    }

    public void evictNow(String cacheName, Object key) {
        evict(cacheName, key);
    }

    private void scheduleAfterCommit(Runnable invalidation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidation.run();
                }
            });
            return;
        }
        invalidation.run();
    }

    private void evict(String cacheName, Object key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        } catch (RuntimeException exception) {
            log.warn("Redis cache eviction failed. cache={}, key={}", cacheName, key, exception);
        }
        eventPublisher.publishEvent(new CacheInvalidatedEvent(cacheName));
    }

    private void clear(String cacheName) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        } catch (RuntimeException exception) {
            log.warn("Redis cache clear failed. cache={}", cacheName, exception);
        }
        eventPublisher.publishEvent(new CacheInvalidatedEvent(cacheName));
    }
}
