package com.pro.ereportbacken.reporting;

import com.pro.ereportbacken.model.dto.OrgUnitDTO;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OutstandingWorkOrderReport(
        UUID tenantId,
        UUID countyOrgUnitId,
        String countyName,
        LocalDate reportDate,
        LocalDateTime generatedAt,
        List<OutstandingWorkOrderReportRow> rows,
        Map<String, Long> statusCounts,
        Map<String, Long> typeCounts,
        long currentOverdueCount,
        Duration oldestElapsed
) {

    public OutstandingWorkOrderReport {
        rows = List.copyOf(rows);
        statusCounts = Collections.unmodifiableMap(new LinkedHashMap<>(statusCounts));
        typeCounts = Collections.unmodifiableMap(new LinkedHashMap<>(typeCounts));
    }

    public static OutstandingWorkOrderReport create(UUID tenantId,
                                                    OrgUnitDTO county,
                                                    LocalDate reportDate,
                                                    LocalDateTime generatedAt,
                                                    List<OutstandingWorkOrderReportRow> rows) {
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        Map<String, Long> typeCounts = new LinkedHashMap<>();
        LocalDateTime oldestSubmittedAt = null;
        long currentOverdueCount = 0;

        for (OutstandingWorkOrderReportRow row : rows) {
            statusCounts.merge(row.statusLabel(), 1L, Long::sum);
            typeCounts.merge(row.typeLabel(), 1L, Long::sum);
            if (row.currentlyOverdue()) {
                currentOverdueCount++;
            }
            if (row.submittedAt() != null
                    && (oldestSubmittedAt == null || row.submittedAt().isBefore(oldestSubmittedAt))) {
                oldestSubmittedAt = row.submittedAt();
            }
        }

        Duration oldestElapsed = oldestSubmittedAt == null
                ? Duration.ZERO
                : nonNegative(Duration.between(oldestSubmittedAt, generatedAt));
        return new OutstandingWorkOrderReport(
                tenantId,
                county.id(),
                county.name(),
                reportDate,
                generatedAt,
                rows,
                statusCounts,
                typeCounts,
                currentOverdueCount,
                oldestElapsed
        );
    }

    private static Duration nonNegative(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
    }
}
