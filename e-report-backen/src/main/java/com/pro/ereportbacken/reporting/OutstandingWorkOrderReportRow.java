package com.pro.ereportbacken.reporting;

import com.pro.ereportbacken.model.pojo.WorkOrderPriority;
import com.pro.ereportbacken.model.pojo.WorkOrderStatus;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

public record OutstandingWorkOrderReportRow(
        UUID id,
        String workOrderNo,
        WorkOrderType reportType,
        String statusCode,
        WorkOrderPriority priority,
        String location,
        String reason,
        String contactPerson,
        String phone,
        String reporterName,
        String reporterUsername,
        String assigneeName,
        String assigneeUsername,
        LocalDateTime submittedAt,
        LocalDateTime lastUpdatedAt,
        LocalDateTime slaDueAt,
        LocalDateTime slaBreachedAt
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
        return displayName(reporterName, reporterUsername);
    }

    public String assigneeDisplayName() {
        return displayName(assigneeName, assigneeUsername);
    }

    public boolean currentlyOverdue() {
        return slaBreachedAt != null;
    }

    private String displayName(String name, String username) {
        if (StringUtils.hasText(name)) {
            return name.trim();
        }
        return StringUtils.hasText(username) ? username.trim() : "未分配";
    }
}
