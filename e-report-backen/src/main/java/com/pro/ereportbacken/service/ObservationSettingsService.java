package com.pro.ereportbacken.service;

import com.pro.ereportbacken.config.AppConfigConstant;
import com.pro.ereportbacken.model.dto.ObservationPolicyDTO;
import org.springframework.stereotype.Service;

@Service
public class ObservationSettingsService {

    private static final int DEFAULT_BATCH_SIZE = 20;
    private static final int DEFAULT_FLUSH_INTERVAL_SECONDS = 15;
    private static final int DEFAULT_RETENTION_DAYS = 30;
    private static final int DEFAULT_OPERATION_RETENTION_DAYS = 14;
    private static final int DEFAULT_OPERATION_SUCCESS_SAMPLE_RATE_PERCENT = 20;

    private final AppConfigService appConfigService;

    public ObservationSettingsService(AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
    }

    public Policy currentPolicy() {
        return new Policy(
                appConfigService.getConfigValueAsBoolean(AppConfigConstant.OBSERVABILITY_CLIENT_ENABLED, true),
                appConfigService.getConfigValueAsBoolean(AppConfigConstant.OBSERVABILITY_WARNING_ENABLED, true),
                appConfigService.getConfigValueAsBoolean(AppConfigConstant.OBSERVABILITY_OPERATION_ENABLED, true),
                clamp(appConfigService.getConfigValueAsInt(
                        AppConfigConstant.OBSERVABILITY_OPERATION_SUCCESS_SAMPLE_RATE_PERCENT,
                        DEFAULT_OPERATION_SUCCESS_SAMPLE_RATE_PERCENT
                ), 0, 100),
                clamp(appConfigService.getConfigValueAsInt(AppConfigConstant.OBSERVABILITY_BATCH_SIZE, DEFAULT_BATCH_SIZE), 1, 50),
                clamp(appConfigService.getConfigValueAsInt(
                        AppConfigConstant.OBSERVABILITY_FLUSH_INTERVAL_SECONDS,
                        DEFAULT_FLUSH_INTERVAL_SECONDS
                ), 5, 300)
        );
    }

    public int retentionDays() {
        return clamp(appConfigService.getConfigValueAsInt(
                AppConfigConstant.OBSERVABILITY_RETENTION_DAYS,
                DEFAULT_RETENTION_DAYS
        ), 1, 365);
    }

    public int operationRetentionDays() {
        return clamp(appConfigService.getConfigValueAsInt(
                AppConfigConstant.OBSERVABILITY_OPERATION_RETENTION_DAYS,
                DEFAULT_OPERATION_RETENTION_DAYS
        ), 1, 90);
    }

    public ObservationPolicyDTO currentPolicyDto() {
        Policy policy = currentPolicy();
        return new ObservationPolicyDTO(
                policy.enabled(),
                policy.warningEnabled(),
                policy.operationEnabled(),
                policy.operationSuccessSampleRatePercent(),
                policy.batchSize(),
                policy.flushIntervalSeconds()
        );
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    public record Policy(
            boolean enabled,
            boolean warningEnabled,
            boolean operationEnabled,
            int operationSuccessSampleRatePercent,
            int batchSize,
            int flushIntervalSeconds
    ) {
    }
}
