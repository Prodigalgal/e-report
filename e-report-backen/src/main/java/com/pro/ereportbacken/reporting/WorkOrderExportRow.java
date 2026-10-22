package com.pro.ereportbacken.reporting;

import com.pro.ereportbacken.model.pojo.WorkOrderPriority;
import com.pro.ereportbacken.model.pojo.WorkOrderSlaStage;
import com.pro.ereportbacken.model.pojo.WorkOrderStatus;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

public record WorkOrderExportRow(
        UUID id,
        String workOrderNo,
        WorkOrderType reportType,
        String statusCode,
        WorkOrderPriority priority,
        UUID reportingOrgId,
        UUID responsibleOrgId,
        String location,
        String reason,
        String contactPerson,
        String phone,
        String reporterName,
        String reporterUsername,
        String currentAssigneeName,
        String currentAssigneeUsername,
        String pendingAssigneeName,
        String pendingAssigneeUsername,
        LocalDateTime submittedAt,
        LocalDateTime lastUpdatedAt,
        WorkOrderSlaStage slaStage,
        LocalDateTime slaDueAt,
        LocalDateTime slaBreachedAt,
        Boolean slaEverBreached,
        LocalDateTime closedAt,
        LocalDateTime cancelledAt
) {
    public String typeLabel() {
        return reportType == null ? "网络故障" : reportType.getDisplayName();
    }

    public String statusLabel() {
        try {
            return WorkOrderStatus.fromValue(statusCode).getLabel();
        } catch (IllegalArgumentException exception) {
            return "未知状态";
        }
    }

    public String priorityLabel() {
        return priority == null ? WorkOrderPriority.ROUTINE.getDisplayName() : priority.getDisplayName();
    }

    public String reporterDisplayName() {
        return displayName(reporterName, reporterUsername, "未知");
    }

    public String currentAssigneeDisplayName() {
        return displayName(currentAssigneeName, currentAssigneeUsername, "未分配");
    }

    public String pendingAssigneeDisplayName() {
        return displayName(pendingAssigneeName, pendingAssigneeUsername, "无");
    }

    public String slaStageLabel() {
        return slaStage == null ? "" : slaStage.getDisplayName();
    }

    public LocalDateTime elapsedUntil(LocalDateTime generatedAt) {
        if (closedAt != null) return closedAt;
        if (cancelledAt != null) return cancelledAt;
        return generatedAt;
    }

    public boolean currentlyOverdue(LocalDateTime generatedAt) {
        WorkOrderStatus status;
        try {
            status = WorkOrderStatus.fromValue(statusCode);
        } catch (IllegalArgumentException exception) {
            return slaBreachedAt != null;
        }
        return !status.isTerminal() && (slaBreachedAt != null
                || slaDueAt != null && !slaDueAt.isAfter(generatedAt));
    }

    private String displayName(String name, String username, String fallback) {
        if (StringUtils.hasText(name)) return name.trim();
        if (StringUtils.hasText(username)) return username.trim();
        return fallback;
    }
}
