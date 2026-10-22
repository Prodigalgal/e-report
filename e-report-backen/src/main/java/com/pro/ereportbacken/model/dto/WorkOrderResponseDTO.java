package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import lombok.Data;
import lombok.NoArgsConstructor; // 需要无参构造函数给 Jackson 或 Mapper

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor // 添加无参构造函数
public class WorkOrderResponseDTO {
    private UUID id;
    private UUID tenantId;
    private String workOrderNo;
    private String location;
    private String gpsCoordinates;
    private String reason;
    private String phone;
    private String contactPerson;
    private String status; // 故障状态
    private String statusCode;
    private String statusLabel;
    private String phaseCode;
    private Long version;
    private UUID reportingOrgId;
    private UUID responsibleOrgId;
    private UUID ownerOrgId;
    private UUID reportingDepartmentId;
    private UUID responsibleDepartmentId;
    private List<WorkOrderDepartmentParticipationDTO> departmentParticipations;
    private List<WorkOrderOrganizationParticipationDTO> organizationParticipations;
    private Integer reworkCount;
    private Set<String> allowedActions;
    private Set<String> allowedViews;
    private WorkOrderAssignmentDTO currentAssignment;
    private WorkOrderAssignmentDTO pendingAssignment;
    private UserBasicInfoDTO handler;
    private String handlerState;
    private String handlerStateName;
    private UUID currentResponseId;
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
    private String replacementResult;
    private LocalDateTime reportTime;
    private LocalDateTime lastUpdatedTime;
    private String resolutionDetails;
    private String resolutionMethod;
    private LocalDateTime resolutionTime;
    private String closureReason;
    private LocalDateTime closureTime;

    private UserBasicInfoDTO reportedBy;
    private UserBasicInfoDTO resolvedBy;
    private UserBasicInfoDTO closedBy;

    private List<AttachmentDTO> attachments;
    private List<MaterialUsageDTO> materialUsages;

    // 可以添加一个构造函数或使用 Builder 模式，但手动映射也行
    public WorkOrderResponseDTO(UUID id, String location, String gpsCoordinates,
                                  String reason, String phone,
                                  String contactPerson, String status, LocalDateTime reportTime,
                                  LocalDateTime lastUpdatedTime, String resolutionDetails, String resolutionMethod,
                                  LocalDateTime resolutionTime, String closureReason, LocalDateTime closureTime,
                                  UserBasicInfoDTO reportedBy, UserBasicInfoDTO resolvedBy, UserBasicInfoDTO closedBy,
                                  List<AttachmentDTO> attachments) {
        this.id = id;
        this.location = location;
        this.gpsCoordinates = gpsCoordinates;
        this.reason = reason;
        this.phone = phone;
        this.contactPerson = contactPerson;
        this.status = status;
        this.reportTime = reportTime;
        this.lastUpdatedTime = lastUpdatedTime;
        this.resolutionDetails = resolutionDetails;
        this.resolutionMethod = resolutionMethod;
        this.resolutionTime = resolutionTime;
        this.closureReason = closureReason;
        this.closureTime = closureTime;
        this.reportedBy = reportedBy;
        this.resolvedBy = resolvedBy;
        this.closedBy = closedBy;
        this.attachments = attachments;
    }
}


