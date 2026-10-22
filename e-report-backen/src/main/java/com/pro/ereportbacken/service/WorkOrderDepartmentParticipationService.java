package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.pojo.Department;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderDepartmentParticipation;
import com.pro.ereportbacken.model.pojo.WorkOrderDepartmentRelationType;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.repository.DepartmentRepository;
import com.pro.ereportbacken.repository.WorkOrderDepartmentParticipationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class WorkOrderDepartmentParticipationService {

    private final DepartmentRepository departmentRepository;
    private final WorkOrderDepartmentParticipationRepository participationRepository;

    public WorkOrderDepartmentParticipationService(
            DepartmentRepository departmentRepository,
            WorkOrderDepartmentParticipationRepository participationRepository) {
        this.departmentRepository = departmentRepository;
        this.participationRepository = participationRepository;
    }

    @Transactional
    public void recordSubmitted(WorkOrder workOrder, User reporter) {
        UUID departmentId = reporter.getDepartmentId();
        workOrder.setReportingDepartmentId(departmentId);
        workOrder.setResponsibleDepartmentId(departmentId);
        if (departmentId == null) {
            return;
        }
        Department department = requireActiveDepartment(
                workOrder.getTenantId(), workOrder.getReportingOrgId(), departmentId);
        create(workOrder, department, WorkOrderDepartmentRelationType.REPORTING,
                reporter.getId(), "提单人提交工单", true);
        create(workOrder, department, WorkOrderDepartmentRelationType.RESPONSIBLE,
                reporter.getId(), "新工单进入提单部门共享池", true);
    }

    @Transactional
    public void changeResponsibleDepartment(WorkOrder workOrder,
                                            UUID departmentId,
                                            UUID operatorMembershipId,
                                            String reason) {
        LocalDateTime now = UtcTime.now();
        participationRepository.endActiveRelation(
                workOrder.getTenantId(), workOrder.getId(),
                WorkOrderDepartmentRelationType.RESPONSIBLE, now);
        workOrder.setResponsibleDepartmentId(departmentId);
        if (departmentId == null) {
            return;
        }
        Department department = requireActiveDepartment(
                workOrder.getTenantId(), workOrder.getResponsibleOrgId(), departmentId);
        create(workOrder, department, WorkOrderDepartmentRelationType.RESPONSIBLE,
                operatorMembershipId, reason, true);
    }

    @Transactional
    public void recordVerification(WorkOrder workOrder, User verifier) {
        if (verifier.getDepartmentId() == null) {
            return;
        }
        Department department = requireActiveDepartment(
                workOrder.getTenantId(), verifier.getOrgUnitId(), verifier.getDepartmentId());
        create(workOrder, department, WorkOrderDepartmentRelationType.VERIFICATION,
                verifier.getId(), "验收工单", false);
    }

    /**
     * 记录本次实际处理部门。处理部门是责任部门的履行方，不覆盖工单的责任部门，
     * 因此跨同级部门协作时仍可保留原责任部门用于统计和通知。
     */
    @Transactional
    public void recordProcessingDepartment(WorkOrder workOrder,
                                           UUID departmentId,
                                           UUID operatorMembershipId,
                                           String reason) {
        LocalDateTime now = UtcTime.now();
        participationRepository.endActiveRelation(
                workOrder.getTenantId(), workOrder.getId(),
                WorkOrderDepartmentRelationType.PROCESSING, now);
        if (departmentId == null) {
            return;
        }
        Department department = requireActiveDepartment(
                workOrder.getTenantId(), workOrder.getResponsibleOrgId(), departmentId);
        create(workOrder, department, WorkOrderDepartmentRelationType.PROCESSING,
                operatorMembershipId, reason, true);
    }

    @Transactional(readOnly = true)
    public List<WorkOrderDepartmentParticipation> history(UUID tenantId, UUID workOrderId) {
        return participationRepository.findByTenantIdAndWorkOrderIdOrderByStartedAtAsc(tenantId, workOrderId);
    }

    public Department requireActiveDepartment(UUID tenantId, UUID orgUnitId, UUID departmentId) {
        Department department = departmentRepository.findByTenantIdAndId(tenantId, departmentId)
                .orElseThrow(() -> new EntityNotFoundException("部门不存在：" + departmentId));
        if (!department.getOrgUnitId().equals(orgUnitId)) {
            throw new IllegalArgumentException("责任部门不属于所选组织。");
        }
        if (department.getStatus() != com.pro.ereportbacken.model.pojo.DepartmentStatus.ACTIVE) {
            throw new IllegalArgumentException("已停用或已合并的部门不能承接新工单。");
        }
        return department;
    }

    public boolean departmentContainsMember(UUID tenantId, UUID responsibleDepartmentId, User member) {
        if (responsibleDepartmentId == null) {
            return true;
        }
        if (member.getDepartmentId() == null) {
            return false;
        }
        Department responsible = departmentRepository.findByTenantIdAndId(tenantId, responsibleDepartmentId)
                .orElse(null);
        Department memberDepartment = departmentRepository.findByTenantIdAndId(tenantId, member.getDepartmentId())
                .orElse(null);
        return responsible != null && memberDepartment != null
                && responsible.getStatus() == com.pro.ereportbacken.model.pojo.DepartmentStatus.ACTIVE
                && memberDepartment.getStatus() == com.pro.ereportbacken.model.pojo.DepartmentStatus.ACTIVE
                && responsible.getOrgUnitId().equals(memberDepartment.getOrgUnitId())
                && memberDepartment.getPath().startsWith(responsible.getPath());
    }

    /**
     * 主动接单允许责任部门与处理人员部门在同一组织内横向协作，但不跨组织、不同级别或停用部门。
     */
    public boolean canCollaborateForClaim(UUID tenantId, UUID responsibleDepartmentId, User member) {
        if (responsibleDepartmentId == null) {
            return true;
        }
        if (member == null || member.getDepartmentId() == null) {
            return false;
        }
        Department responsible = departmentRepository.findByTenantIdAndId(tenantId, responsibleDepartmentId)
                .orElse(null);
        Department memberDepartment = departmentRepository.findByTenantIdAndId(tenantId, member.getDepartmentId())
                .orElse(null);
        if (responsible == null || memberDepartment == null
                || responsible.getStatus() != com.pro.ereportbacken.model.pojo.DepartmentStatus.ACTIVE
                || memberDepartment.getStatus() != com.pro.ereportbacken.model.pojo.DepartmentStatus.ACTIVE) {
            return false;
        }
        return responsible.getOrgUnitId().equals(memberDepartment.getOrgUnitId())
                && java.util.Objects.equals(responsible.getParentId(), memberDepartment.getParentId());
    }

    private void create(WorkOrder workOrder,
                        Department department,
                        WorkOrderDepartmentRelationType relationType,
                        UUID operatorMembershipId,
                        String reason,
                        boolean active) {
        WorkOrderDepartmentParticipation participation = new WorkOrderDepartmentParticipation();
        participation.setTenantId(workOrder.getTenantId());
        participation.setWorkOrderId(workOrder.getId());
        participation.setDepartmentId(department.getId());
        participation.setDepartmentNameSnapshot(department.getName());
        participation.setRelationType(relationType);
        participation.setActive(active);
        participation.setStartedAt(UtcTime.now());
        participation.setEndedAt(active ? null : participation.getStartedAt());
        participation.setCreatedByMembershipId(operatorMembershipId);
        participation.setReason(reason);
        participationRepository.save(participation);
    }
}
