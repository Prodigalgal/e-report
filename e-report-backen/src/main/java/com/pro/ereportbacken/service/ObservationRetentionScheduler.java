package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.ClientObservationEvent;
import com.pro.ereportbacken.model.pojo.ObservationEventType;
import com.pro.ereportbacken.model.pojo.ObservationSeverity;
import com.pro.ereportbacken.repository.ClientObservationEventRepository;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.security.TenantContext;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.time.Duration;

@Service
@Log4j2
public class ObservationRetentionScheduler {

    private static final int MAX_BATCHES_PER_TENANT = 100;

    private final TenantRepository tenantRepository;
    private final TenantContext tenantContext;
    private final ObservationSettingsService settingsService;
    private final ClientObservationEventRepository eventRepository;
    private final RedisDistributedLockService lockService;

    public ObservationRetentionScheduler(TenantRepository tenantRepository,
                                         TenantContext tenantContext,
                                         ObservationSettingsService settingsService,
                                         ClientObservationEventRepository eventRepository,
                                         RedisDistributedLockService lockService) {
        this.tenantRepository = tenantRepository;
        this.tenantContext = tenantContext;
        this.settingsService = settingsService;
        this.eventRepository = eventRepository;
        this.lockService = lockService;
    }

    @Scheduled(cron = "${observability.retention.cron:0 15 3 * * *}", zone = "UTC")
    public void cleanExpiredEvents() {
        lockService.runWithLock("observability:retention", Duration.ofHours(1), this::cleanAllTenants);
    }

    private void cleanAllTenants() {
        tenantRepository.findAll().forEach(tenant -> {
            try {
                tenantContext.runInTenant(tenant.getId(), () -> cleanTenant(tenant.getId()));
            } catch (Exception exception) {
                log.error("清理租户运行诊断事件失败。tenantId={}", tenant.getId(), exception);
            }
        });
    }

    private void cleanTenant(UUID tenantId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime operationCutoff = now.minusDays(settingsService.operationRetentionDays());
        int deletedOperations = deleteExpiredSuccessfulOperations(tenantId, operationCutoff);
        LocalDateTime cutoff = now.minusDays(settingsService.retentionDays());
        int deleted = deleteExpiredEvents(tenantId, cutoff);
        if (deletedOperations > 0 || deleted > 0) {
            log.info(
                    "已清理过期观测事件。tenantId={}, operationCutoff={}, deletedOperations={}, cutoff={}, deleted={}",
                    tenantId, operationCutoff, deletedOperations, cutoff, deleted
            );
        }
    }

    private int deleteExpiredSuccessfulOperations(UUID tenantId, LocalDateTime cutoff) {
        return deleteInBatches(() -> eventRepository
                .findTop500ByTenantIdAndEventTypeAndSeverityAndReceivedAtBeforeOrderByReceivedAtAsc(
                        tenantId,
                        ObservationEventType.OPERATION,
                        ObservationSeverity.INFO,
                        cutoff
                ));
    }

    private int deleteExpiredEvents(UUID tenantId, LocalDateTime cutoff) {
        return deleteInBatches(() -> eventRepository
                .findTop500ByTenantIdAndReceivedAtBeforeOrderByReceivedAtAsc(tenantId, cutoff));
    }

    private int deleteInBatches(java.util.function.Supplier<List<ClientObservationEvent>> loader) {
        int deleted = 0;
        for (int batchIndex = 0; batchIndex < MAX_BATCHES_PER_TENANT; batchIndex++) {
            List<ClientObservationEvent> expired = loader.get();
            if (expired.isEmpty()) {
                break;
            }
            eventRepository.deleteAllByIdInBatch(expired.stream().map(ClientObservationEvent::getId).toList());
            deleted += expired.size();
            if (expired.size() < 500) {
                break;
            }
        }
        return deleted;
    }
}
