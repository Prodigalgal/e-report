package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.WorkOrderType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Data
public class WorkOrderListItemDTO {
    private UUID id;
    private UUID tenantId;
    private String workOrderNo;
    private String location;
    private String gpsCoordinates;
    private String reason;
    private String phone;
    private String contactPerson;
    private String status;
    private String statusCode;
    private String statusLabel;
    private String phaseCode;
    private Long version;
    private UUID responsibleOrgId;
    private UUID ownerOrgId;
    private UUID responsibleDepartmentId;
    private Set<String> allowedActions = Collections.emptySet();
    private WorkOrderAssignmentDTO currentAssignment;
    private WorkOrderAssignmentDTO pendingAssignment;
    private UserBasicInfoDTO handler;
    private String handlerState;
    private String handlerStateName;
    private String reportType;
    private String reportTypeName;
    private String priority;
    private String priorityName;
    private String slaStage;
    private String slaStageName;
    private String slaRiskLevel;
    private String slaRiskName;
    private LocalDateTime slaDueAt;
    private Boolean slaPaused;
    private Boolean slaBreached;
    private Boolean slaEverBreached;
    private Long slaRemainingSeconds;
    private Long slaBreachSeconds;
    private Long elapsedSeconds;
    private Long totalHoldSeconds;
    private Integer boxQuantity;
    private Boolean replacementRequired;
    private Boolean replacementCompleted;
    private LocalDateTime reportTime;
    private LocalDateTime lastUpdatedTime;
    private LocalDateTime resolutionTime;
    private UserBasicInfoDTO reportedBy;
    private UserBasicInfoDTO resolvedBy;
    private long attachmentCount;
    private List<AttachmentDTO> attachments = Collections.emptyList();

    public WorkOrderListItemDTO(
            UUID id,
            String location,
            String gpsCoordinates,
            String reason,
            String phone,
            String contactPerson,
            String status,
            WorkOrderType reportType,
            Integer boxQuantity,
            Boolean replacementRequired,
            Boolean replacementCompleted,
            LocalDateTime reportTime,
            LocalDateTime lastUpdatedTime,
            LocalDateTime resolutionTime,
            UserBasicInfoDTO reportedBy,
            UserBasicInfoDTO resolvedBy,
            long attachmentCount
    ) {
        this.id = id;
        this.location = location;
        this.gpsCoordinates = gpsCoordinates;
        this.reason = reason;
        this.phone = phone;
        this.contactPerson = contactPerson;
        this.status = status;
        WorkOrderType resolvedReportType = reportType != null ? reportType : WorkOrderType.NETWORK_FAULT;
        this.reportType = resolvedReportType.name();
        this.reportTypeName = resolvedReportType.getDisplayName();
        this.boxQuantity = boxQuantity;
        this.replacementRequired = replacementRequired;
        this.replacementCompleted = replacementCompleted;
        this.reportTime = reportTime;
        this.lastUpdatedTime = lastUpdatedTime;
        this.resolutionTime = resolutionTime;
        this.reportedBy = reportedBy;
        this.resolvedBy = resolvedBy;
        this.attachmentCount = attachmentCount;
    }
}


