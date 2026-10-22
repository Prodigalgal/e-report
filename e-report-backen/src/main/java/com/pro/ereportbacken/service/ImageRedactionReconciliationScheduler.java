package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.Tenant;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.security.TenantExecutionContext;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "app.image-redaction.reconciler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
@Log4j2
public class ImageRedactionReconciliationScheduler {

    private final TenantRepository tenantRepository;
    private final TenantExecutionContext tenantExecutionContext;
    private final ImageRedactionReconciliationService reconciliationService;
    private final ImageRedactionService imageRedactionService;
    private final RedisDistributedLockService lockService;
    private final ImageRedactionReconciliationCursorStore cursorStore;
    private final int batchSize;
    private final int maxBatchesPerTenant;

    public ImageRedactionReconciliationScheduler(
            TenantRepository tenantRepository,
            TenantExecutionContext tenantExecutionContext,
            ImageRedactionReconciliationService reconciliationService,
            ImageRedactionService imageRedactionService,
            RedisDistributedLockService lockService,
            ImageRedactionReconciliationCursorStore cursorStore,
            @Value("${app.image-redaction.reconciler-batch-size:50}") int batchSize,
            @Value("${app.image-redaction.reconciler-max-batches-per-run:2}") int maxBatchesPerTenant) {
        this.tenantRepository = tenantRepository;
        this.tenantExecutionContext = tenantExecutionContext;
        this.reconciliationService = reconciliationService;
        this.imageRedactionService = imageRedactionService;
        this.lockService = lockService;
        this.cursorStore = cursorStore;
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
        this.maxBatchesPerTenant = Math.max(1, Math.min(maxBatchesPerTenant, 20));
    }

    @Scheduled(
            initialDelayString = "${app.image-redaction.reconciler-initial-delay-ms:120000}",
            fixedDelayString = "${app.image-redaction.reconciler-interval-ms:300000}"
    )
    public void reconcile() {
        lockService.runWithLock(
                "image-redaction:reconcile",
                Duration.ofMinutes(5),
                this::reconcileActiveTenants
        );
    }

    private void reconcileActiveTenants() {
        for (Tenant tenant : tenantRepository.findByStatusOrderByNameAsc(TenantStatus.ACTIVE)) {
            try {
                tenantExecutionContext.runInTenant(
                        tenant.getId(),
                        () -> reconcileTenant(tenant.getId())
                );
            } catch (RuntimeException exception) {
                log.error("图片脱敏巡检失败。tenantId={}", tenant.getId(), exception);
            }
        }
    }

    private void reconcileTenant(UUID tenantId) {
        int candidates = 0;
        int linked = 0;
        int released = imageRedactionService.releaseDeferred(tenantId, batchSize);
        int missingSession = 0;
        int incompleteSession = 0;
        int cancelledJob = 0;
        UUID afterAttachmentId = cursorStore.load(tenantId).orElse(null);
        boolean scanCompleted = false;
        for (int batch = 0; batch < maxBatchesPerTenant; batch++) {
            ImageRedactionReconciliationService.ReconciliationBatch result =
                    reconciliationService.reconcileTenant(tenantId, batchSize, afterAttachmentId);
            candidates += result.candidates();
            linked += result.linked();
            missingSession += result.missingSession();
            incompleteSession += result.incompleteSession();
            cancelledJob += result.cancelledJob();
            if (result.candidates() == 0) {
                cursorStore.clear(tenantId);
                scanCompleted = true;
                break;
            }
            if (result.candidates() < batchSize) {
                cursorStore.clear(tenantId);
                scanCompleted = true;
                break;
            }
            if (result.lastAttachmentId() == null || result.lastAttachmentId().equals(afterAttachmentId)) {
                log.warn("图片脱敏巡检游标未前进，提前结束本轮。tenantId={}, cursor={}", tenantId, afterAttachmentId);
                break;
            }
            afterAttachmentId = result.lastAttachmentId();
        }
        if (!scanCompleted) {
            cursorStore.save(tenantId, afterAttachmentId);
        }
        if (released > 0 || candidates > 0 || missingSession > 0 || incompleteSession > 0 || cancelledJob > 0) {
            log.info(
                    "图片脱敏巡检完成。tenantId={}, released={}, candidates={}, linked={}, missingSession={}, "
                            + "incompleteSession={}, cancelledJob={}, batchSize={}, maxBatchesPerRun={}",
                    tenantId, released, candidates, linked, missingSession, incompleteSession, cancelledJob,
                    batchSize, maxBatchesPerTenant
            );
            if (missingSession > 0 || incompleteSession > 0 || cancelledJob > 0) {
                log.warn(
                        "图片脱敏巡检发现无法自动关联的附件。tenantId={}, missingSession={}, "
                                + "incompleteSession={}, cancelledJob={}",
                        tenantId, missingSession, incompleteSession, cancelledJob
                );
            }
        }
    }
}
