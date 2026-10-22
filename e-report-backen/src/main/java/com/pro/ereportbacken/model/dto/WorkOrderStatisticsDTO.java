package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.WorkOrderType;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WorkOrderStatisticsDTO(
        long total,
        long pending,
        long resolved,
        long closed,
        long otherStatusTotal,
        double resolutionRate,
        long boxQuantityTotal,
        long replacementRequired,
        long replacementCompleted,
        long active,
        long within24Hours,
        long from24To48Hours,
        long from48To72Hours,
        long over72Hours,
        long currentSlaBreached,
        long everSlaBreached,
        long longestActiveElapsedSeconds,
        List<TypeCountDTO> byType,
        List<StatusCountDTO> byStatus,
        List<DailyCountDTO> dailyNewReports,
        List<CountyComparisonDTO> countyComparison
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public record TypeCountDTO(
            WorkOrderType reportType,
            String reportTypeName,
            long total,
            long pending,
            long resolved,
            long closed,
            long boxQuantityTotal,
            long replacementRequired,
            long replacementCompleted
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    public record StatusCountDTO(
            String status,
            long total
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    public record DailyCountDTO(
            LocalDate date,
            long total
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    public record CountyComparisonDTO(
            UUID countyOrgUnitId,
            String countyName,
            long total,
            long active,
            long closed,
            double resolutionRate,
            long currentSlaBreached,
            long over72Hours
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
