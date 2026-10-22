package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.WorkOrderType;
import lombok.Data;

import java.util.Set;
import java.util.Map;

@Data
public class WorkOrderFilterDTO {
    private String searchTerm;
    private String location;
    private Set<String> statuses;
    private String contactPerson;
    private String phone;
    private String reason;
    private Set<String> reportTypes;
    private String reportDateFrom;
    private String reportDateTo;

    private Set<WorkOrderType> accessibleReportTypes;
    private UUID accessMembershipId;
    private Set<WorkOrderType> tenantWideReporterTypes;
    private Map<WorkOrderType, Set<UUID>> reporterOrgUnitIdsByType;
    private Set<WorkOrderType> tenantWideParticipantTypes;
    private Map<WorkOrderType, Set<UUID>> participantOrgUnitIdsByType;
    private UUID tenantId;
    private Map<WorkOrderType, Set<UUID>> accessibleOrgUnitIdsByType;
    private Set<WorkOrderType> tenantWideClaimableTypes;
    private Map<WorkOrderType, Set<UUID>> claimableOrgUnitIdsByType;
    private Set<UUID> reportingOrgUnitIds;
    private Set<String> queues;
    private Set<UUID> selectedWorkOrderIds;
}

