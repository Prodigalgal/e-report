package com.pro.ereportbacken.service;

import com.pro.ereportbacken.config.CacheNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CacheInvalidationServiceTests {

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(CacheNames.APP_CONFIG);
    private final CacheInvalidationService service = new CacheInvalidationService(cacheManager, publisher);

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void invalidationAndNotificationWaitUntilTheTransactionCommits() {
        cacheManager.getCache(CacheNames.APP_CONFIG).put("key", "before");
        beginTransaction();

        service.clearAfterCommit(CacheNames.APP_CONFIG);

        verifyNoInteractions(publisher);
        assertThat(cacheManager.getCache(CacheNames.APP_CONFIG).get("key", String.class)).isEqualTo("before");
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(cacheManager.getCache(CacheNames.APP_CONFIG).get("key")).isNull();
        verify(publisher).publishEvent(new CacheInvalidatedEvent(CacheNames.APP_CONFIG));
    }

    @Test
    void rolledBackChangesDoNotNotifySchedulers() {
        beginTransaction();
        service.clearAfterCommit(CacheNames.APP_CONFIG);

        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(publisher);
    }

    @Test
    void aFailedRedisEvictionStillNotifiesConsumersToReadTheDatabase() {
        CacheManager failingManager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        when(failingManager.getCache(CacheNames.APP_CONFIG)).thenReturn(cache);
        doThrow(new IllegalStateException("cache unavailable")).when(cache).clear();

        new CacheInvalidationService(failingManager, publisher).clearAfterCommit(CacheNames.APP_CONFIG);

        verify(publisher).publishEvent(new CacheInvalidatedEvent(CacheNames.APP_CONFIG));
    }

    @Test
    void directEvictionPublishesAfterRemovingTheEntry() {
        cacheManager.getCache(CacheNames.APP_CONFIG).put("key", "before");

        service.evictNow(CacheNames.APP_CONFIG, "key");

        assertThat(cacheManager.getCache(CacheNames.APP_CONFIG).get("key")).isNull();
        verify(publisher).publishEvent(new CacheInvalidatedEvent(CacheNames.APP_CONFIG));
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }
}
