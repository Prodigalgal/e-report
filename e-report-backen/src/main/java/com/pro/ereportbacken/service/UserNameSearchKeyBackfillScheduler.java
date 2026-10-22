package com.pro.ereportbacken.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Log4j2
public class UserNameSearchKeyBackfillScheduler {

    private final UserNameSearchKeyBackfillService backfillService;
    private final int batchSize;
    private final int maximumBatchesPerRun;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public UserNameSearchKeyBackfillScheduler(
            UserNameSearchKeyBackfillService backfillService,
            @Value("${app.user-search-key-backfill.batch-size:200}") int batchSize,
            @Value("${app.user-search-key-backfill.maximum-batches-per-run:20}") int maximumBatchesPerRun) {
        this.backfillService = backfillService;
        this.batchSize = Math.max(1, Math.min(batchSize, 1000));
        this.maximumBatchesPerRun = Math.max(1, Math.min(maximumBatchesPerRun, 100));
    }

    @Scheduled(
            initialDelayString = "${app.user-search-key-backfill.initial-delay-ms:5000}",
            fixedDelayString = "${app.user-search-key-backfill.fixed-delay-ms:60000}")
    public void backfillMissingSearchKeys() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            int updated = 0;
            for (int batch = 0; batch < maximumBatchesPerRun; batch++) {
                int batchUpdated = backfillService.backfillNextBatch(batchSize);
                updated += batchUpdated;
                if (batchUpdated < batchSize) {
                    break;
                }
            }
            long remaining = backfillService.remainingCount();
            if (updated > 0 || remaining > 0) {
                log.info("用户姓名拼音检索键回填完成一轮：updated={}, remaining={}", updated, remaining);
            }
        } catch (RuntimeException exception) {
            log.error("用户姓名拼音检索键回填失败，将在下一轮重试。", exception);
        } finally {
            running.set(false);
        }
    }
}
