package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderOrganizationParticipation;
import com.pro.ereportbacken.model.pojo.WorkOrderOrganizationRelationType;
import com.pro.ereportbacken.repository.WorkOrderOrganizationParticipationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class WorkOrderOrganizationParticipationService {

    private final WorkOrderOrganizationParticipationRepository participationRepository;
    private final OrganizationHierarchyService hierarchyService;

    public WorkOrderOrganizationParticipationService(
            WorkOrderOrganizationParticipationRepository participationRepository,
            OrganizationHierarchyService hierarchyService) {
        this.participationRepository = participationRepository;
        this.hierarchyService = hierarchyService;
    }

    @Transactional
    public void recordSubmitted(WorkOrder workOrder, UUID operatorMembershipId) {
        createIfAbsent(workOrder, workOrder.getReportingOrgId(), WorkOrderOrganizationRelationType.REPORTING,
                operatorMembershipId, "提单组织", true);
        createIfAbsent(workOrder, workOrder.getOwnerOrgId(), WorkOrderOrganizationRelationType.OWNER,
                operatorMembershipId, "工单初始归属组织", true);
    }

    @Transactional
    public void transferOwnership(WorkOrder workOrder,
                                  UUID previousOwnerOrgId,
                                  UUID targetOrgUnitId,
                                  UUID operatorMembershipId,
                                  String reason) {
        LocalDateTime transferredAt = UtcTime.now();
        endActiveRelations(workOrder, WorkOrderOrganizationRelationType.OWNER, transferredAt);
        endPendingTransferTarget(workOrder);
        createIfAbsent(workOrder, previousOwnerOrgId, WorkOrderOrganizationRelationType.PREVIOUS_OWNER,
                operatorMembershipId, "完整移交后保留查看权：" + reason, true);
        createIfAbsent(workOrder, targetOrgUnitId, WorkOrderOrganizationRelationType.OWNER,
                operatorMembershipId, "接受完整移交：" + reason, true);
    }

    @Transactional
    public void recordPendingTransferTarget(WorkOrder workOrder,
                                            UUID targetOrgUnitId,
                                            UUID operatorMembershipId,
                                            String reason) {
        createIfAbsent(workOrder, targetOrgUnitId,
                WorkOrderOrganizationRelationType.PENDING_TRANSFER_TARGET,
                operatorMembershipId, "待确认完整移交：" + reason, true);
    }

    /**
     * 记录实际处理组织并保留其查看关系。处理组织不是数据归属组织，
     * 因此不会覆盖工单的 ownerOrgId 或 responsibleOrgId。
     */
    @Transactional
    public void recordProcessingOrganization(WorkOrder workOrder,
                                             UUID processingOrgUnitId,
                                             UUID operatorMembershipId,
                                             String reason) {
        createIfAbsent(workOrder, processingOrgUnitId,
                WorkOrderOrganizationRelationType.PROCESSING,
                operatorMembershipId, "实际处理组织：" + reason, true);
    }

    @Transactional
    public void endPendingTransferTarget(WorkOrder workOrder) {
        endActiveRelations(workOrder, WorkOrderOrganizationRelationType.PENDING_TRANSFER_TARGET,
                UtcTime.now());
    }

    @Transactional(readOnly = true)
    public List<WorkOrderOrganizationParticipation> history(UUID tenantId, UUID workOrderId) {
        return participationRepository.findByTenantIdAndWorkOrderIdOrderByStartedAtAsc(tenantId, workOrderId);
    }

    private void createIfAbsent(WorkOrder workOrder,
                                UUID orgUnitId,
                                WorkOrderOrganizationRelationType relationType,
                                UUID operatorMembershipId,
                                String reason,
                                boolean active) {
        if (orgUnitId == null || participationRepository
                .existsByTenantIdAndWorkOrderIdAndOrgUnitIdAndRelationTypeAndActiveTrue(
                        workOrder.getTenantId(), workOrder.getId(), orgUnitId, relationType)) {
            return;
        }
        Map<UUID, String> names = hierarchyService.namesById(workOrder.getTenantId(), Set.of(orgUnitId));
        WorkOrderOrganizationParticipation participation = new WorkOrderOrganizationParticipation();
        participation.setTenantId(workOrder.getTenantId());
        participation.setWorkOrderId(workOrder.getId());
        participation.setOrgUnitId(orgUnitId);
        participation.setOrganizationNameSnapshot(names.getOrDefault(orgUnitId, "未知组织"));
        participation.setRelationType(relationType);
        participation.setActive(active);
        participation.setStartedAt(UtcTime.now());
        participation.setCreatedByMembershipId(operatorMembershipId);
        participation.setReason(reason);
        participationRepository.save(participation);
    }

    private void endActiveRelations(WorkOrder workOrder,
                                    WorkOrderOrganizationRelationType relationType,
                                    LocalDateTime endedAt) {
        List<WorkOrderOrganizationParticipation> relations = participationRepository
                .findByTenantIdAndWorkOrderIdAndRelationTypeAndActiveTrueOrderByStartedAtAsc(
                        workOrder.getTenantId(), workOrder.getId(), relationType);
        relations.forEach(relation -> {
            relation.setActive(false);
            relation.setEndedAt(endedAt);
        });
        if (!relations.isEmpty()) {
            participationRepository.saveAll(relations);
        }
    }
}
