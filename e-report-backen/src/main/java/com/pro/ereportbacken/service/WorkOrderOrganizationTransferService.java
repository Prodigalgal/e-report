package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.dto.WorkOrderTransferCreateRequestDTO;
import com.pro.ereportbacken.model.dto.WorkOrderTransferDTO;
import com.pro.ereportbacken.model.dto.WorkOrderTransferDecisionRequestDTO;
import com.pro.ereportbacken.model.dto.WorkOrderTransferTargetDTO;
import com.pro.ereportbacken.model.pojo.AssignmentState;
import com.pro.ereportbacken.model.pojo.AssignmentSlot;
import com.pro.ereportbacken.model.pojo.WorkOrderAssignment;
import com.pro.ereportbacken.model.pojo.OrgUnit;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderEvent;
import com.pro.ereportbacken.model.pojo.WorkOrderEventSource;
import com.pro.ereportbacken.model.pojo.WorkOrderEventType;
import com.pro.ereportbacken.model.pojo.WorkOrderOrganizationTransfer;
import com.pro.ereportbacken.model.pojo.WorkOrderOrganizationTransferStatus;
import com.pro.ereportbacken.model.pojo.WorkOrderStatus;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.WorkOrderAssignmentRepository;
import com.pro.ereportbacken.repository.WorkOrderEventRepository;
import com.pro.ereportbacken.repository.WorkOrderOrganizationTransferRepository;
import com.pro.ereportbacken.repository.WorkOrderRepository;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.WorkOrderAccessPolicy;
import com.pro.ereportbacken.security.WorkOrderAction;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkOrderOrganizationTransferService {

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderOrganizationTransferRepository transferRepository;
    private final WorkOrderAssignmentRepository assignmentRepository;
    private final WorkOrderEventRepository eventRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final WorkOrderAccessPolicy accessPolicy;
    private final UserAuthorizationCache authorizationCache;
    private final OrganizationHierarchyService hierarchyService;
    private final WorkOrderOrganizationParticipationService organizationParticipationService;
    private final WorkOrderDepartmentParticipationService departmentParticipationService;
    private final WorkOrderSlaService slaService;
    private final WorkOrderSlaBreachService slaBreachService;

    public WorkOrderOrganizationTransferService(
            WorkOrderRepository workOrderRepository,
            WorkOrderOrganizationTransferRepository transferRepository,
            WorkOrderAssignmentRepository assignmentRepository,
            WorkOrderEventRepository eventRepository,
            OrgUnitRepository orgUnitRepository,
            WorkOrderAccessPolicy accessPolicy,
            UserAuthorizationCache authorizationCache,
            OrganizationHierarchyService hierarchyService,
            WorkOrderOrganizationParticipationService organizationParticipationService,
            WorkOrderDepartmentParticipationService departmentParticipationService,
            WorkOrderSlaService slaService,
            WorkOrderSlaBreachService slaBreachService) {
        this.workOrderRepository = workOrderRepository;
        this.transferRepository = transferRepository;
        this.assignmentRepository = assignmentRepository;
        this.eventRepository = eventRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.accessPolicy = accessPolicy;
        this.authorizationCache = authorizationCache;
        this.hierarchyService = hierarchyService;
        this.organizationParticipationService = organizationParticipationService;
        this.departmentParticipationService = departmentParticipationService;
        this.slaService = slaService;
        this.slaBreachService = slaBreachService;
    }

    @Transactional
    public WorkOrderTransferDTO request(UUID workOrderId, WorkOrderTransferCreateRequestDTO request) {
        WorkOrder workOrder = lockedWorkOrder(workOrderId);
        requireVersion(workOrder, request.expectedWorkOrderVersion());
        requireNonTerminal(workOrder);
        accessPolicy.requireActionAccess(workOrder, WorkOrderAction.TRANSFER_CREATE);
        if (!transferRepository.findAllByTenantIdAndWorkOrderIdAndStatusOrderByRequestedAtAsc(
                workOrder.getTenantId(), workOrderId, WorkOrderOrganizationTransferStatus.PENDING).isEmpty()) {
            throw new IllegalStateException("工单已有待处理的组织移交申请。");
        }
        OrgUnit target = requireActiveOrganization(workOrder.getTenantId(), request.targetOrgUnitId());
        if (target.getId().equals(workOrder.getOwnerOrgId())) {
            throw new IllegalArgumentException("目标组织不能与当前归属组织相同。");
        }
        User operator = accessPolicy.currentUserOrThrow();
        WorkOrderOrganizationTransfer transfer = new WorkOrderOrganizationTransfer();
        transfer.setTenantId(workOrder.getTenantId());
        transfer.setWorkOrderId(workOrder.getId());
        transfer.setSourceOrgUnitId(workOrder.getOwnerOrgId());
        transfer.setTargetOrgUnitId(target.getId());
        transfer.setStatus(WorkOrderOrganizationTransferStatus.PENDING);
        transfer.setExpectedWorkOrderVersion(workOrder.getVersion());
        transfer.setRequestedByMembershipId(operator.getId());
        transfer.setRequestedAt(UtcTime.now());
        transfer.setRequestReason(request.reason().trim());
        transfer = transferRepository.save(transfer);
        organizationParticipationService.recordPendingTransferTarget(
                workOrder, target.getId(), operator.getId(), request.reason().trim());
        appendEvent(workOrder, WorkOrderEventType.ORGANIZATION_TRANSFER_REQUESTED, operator,
                "申请完整移交至" + target.getName() + "：" + request.reason().trim());
        return toDTO(transfer, workOrder, operator);
    }

    @Transactional
    public WorkOrderTransferDTO accept(UUID workOrderId,
                                       UUID transferId,
                                       WorkOrderTransferDecisionRequestDTO request) {
        WorkOrder workOrder = lockedWorkOrder(workOrderId);
        normalizeAssignmentPointers(workOrder);
        requireNonTerminal(workOrder);
        WorkOrderOrganizationTransfer transfer = pendingTransfer(workOrder, transferId);
        requireVersion(workOrder, request.expectedWorkOrderVersion());
        if (!Objects.equals(transfer.getExpectedWorkOrderVersion(), workOrder.getVersion())) {
            throw new IllegalStateException("工单已在移交申请后发生变化，请撤销后重新发起。");
        }
        accessPolicy.requireActionAccess(workOrder, WorkOrderAction.TRANSFER_ACCEPT);
        User operator = accessPolicy.currentUserOrThrow();
        requireTargetRepresentative(operator, transfer.getTargetOrgUnitId());
        OrgUnit target = requireActiveOrganization(workOrder.getTenantId(), transfer.getTargetOrgUnitId());

        LocalDateTime transferredAt = UtcTime.now();
        slaBreachService.markBreachedIfDue(workOrder.getTenantId(), workOrder.getId(), transferredAt);
        revokeActiveAssignments(workOrder, transferredAt);
        UUID previousOwner = workOrder.getOwnerOrgId();
        workOrder.setOwnerOrgId(target.getId());
        workOrder.setResponsibleOrgId(target.getId());
        departmentParticipationService.changeResponsibleDepartment(
                workOrder, null, operator.getId(), "跨组织完整移交");
        if (workOrder.lifecycleStatus() == WorkOrderStatus.IN_PROGRESS
                || workOrder.lifecycleStatus() == WorkOrderStatus.ON_HOLD) {
            workOrder.transitionTo(WorkOrderStatus.SUBMITTED);
            slaService.startClaim(workOrder, transferredAt);
        }
        WorkOrder saved = workOrderRepository.saveAndFlush(workOrder);
        organizationParticipationService.transferOwnership(
                saved, previousOwner, target.getId(), operator.getId(), request.comment().trim());
        decide(transfer, WorkOrderOrganizationTransferStatus.ACCEPTED, operator, request.comment());
        appendEvent(saved, WorkOrderEventType.ORGANIZATION_TRANSFER_ACCEPTED, operator,
                "完整移交至" + target.getName() + "：" + request.comment().trim());
        return toDTO(transfer, saved, operator);
    }

    @Transactional
    public WorkOrderTransferDTO reject(UUID workOrderId,
                                       UUID transferId,
                                       WorkOrderTransferDecisionRequestDTO request) {
        WorkOrder workOrder = lockedWorkOrder(workOrderId);
        requireNonTerminal(workOrder);
        WorkOrderOrganizationTransfer transfer = pendingTransfer(workOrder, transferId);
        requireVersion(workOrder, request.expectedWorkOrderVersion());
        accessPolicy.requireActionAccess(workOrder, WorkOrderAction.TRANSFER_REJECT);
        User operator = accessPolicy.currentUserOrThrow();
        requireTargetRepresentative(operator, transfer.getTargetOrgUnitId());
        organizationParticipationService.endPendingTransferTarget(workOrder);
        decide(transfer, WorkOrderOrganizationTransferStatus.REJECTED, operator, request.comment());
        appendEvent(workOrder, WorkOrderEventType.ORGANIZATION_TRANSFER_REJECTED, operator,
                request.comment().trim());
        return toDTO(transfer, workOrder, operator);
    }

    @Transactional
    public WorkOrderTransferDTO cancel(UUID workOrderId,
                                       UUID transferId,
                                       WorkOrderTransferDecisionRequestDTO request) {
        WorkOrder workOrder = lockedWorkOrder(workOrderId);
        requireNonTerminal(workOrder);
        WorkOrderOrganizationTransfer transfer = pendingTransfer(workOrder, transferId);
        requireVersion(workOrder, request.expectedWorkOrderVersion());
        accessPolicy.requireActionAccess(workOrder, WorkOrderAction.TRANSFER_CANCEL);
        User operator = accessPolicy.currentUserOrThrow();
        organizationParticipationService.endPendingTransferTarget(workOrder);
        decide(transfer, WorkOrderOrganizationTransferStatus.CANCELLED, operator, request.comment());
        appendEvent(workOrder, WorkOrderEventType.ORGANIZATION_TRANSFER_CANCELLED, operator,
                request.comment().trim());
        return toDTO(transfer, workOrder, operator);
    }

    @Transactional(readOnly = true)
    public List<WorkOrderTransferDTO> history(UUID workOrderId) {
        WorkOrder workOrder = workOrderRepository.findByTenantIdAndId(
                        accessPolicy.currentTenantId(), workOrderId)
                .orElseThrow(() -> new EntityNotFoundException("工单不存在：" + workOrderId));
        accessPolicy.requireReadAccess(workOrder);
        User operator = accessPolicy.currentUserOrThrow();
        return transferRepository.findByTenantIdAndWorkOrderIdOrderByRequestedAtDesc(
                        workOrder.getTenantId(), workOrderId).stream()
                .map(transfer -> toDTO(transfer, workOrder, operator))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkOrderTransferTargetDTO> targets() {
        User operator = accessPolicy.currentUserOrThrow();
        Set<String> authorities = authorizationCache.snapshot(operator).authorities();
        boolean allowed = PermissionCatalog.workOrderEntries().stream().anyMatch(entry ->
                authorities.contains(entry.transferCreatePermission())
                        || authorities.contains(entry.transferAcceptPermission())
                        || authorities.contains(entry.transferRejectPermission()));
        if (!allowed) {
            throw new AccessDeniedException("当前账号没有组织移交权限。");
        }
        return orgUnitRepository.findByTenantIdOrderByPathAsc(operator.getTenantId()).stream()
                .filter(orgUnit -> orgUnit.getStatus() == TenantStatus.ACTIVE)
                .map(orgUnit -> new WorkOrderTransferTargetDTO(
                        orgUnit.getId(), orgUnit.getParentId(), orgUnit.getCode(),
                        orgUnit.getName(), orgUnit.getPath()))
                .toList();
    }

    /**
     * 终态工单不能保留可执行的组织移交申请。该方法由工单终态命令在同一事务内调用，
     * 将尚未决策的申请收敛为取消状态，并结束目标组织的临时可见关系。
     */
    @Transactional
    public void cancelPendingForTerminal(WorkOrder workOrder, User operator, String reason) {
        if (!workOrder.lifecycleStatus().isTerminal()) {
            return;
        }
        User effectiveOperator = Objects.requireNonNull(operator, "终态工单移交收敛需要操作人。");
        List<WorkOrderOrganizationTransfer> transfers = transferRepository
                .findAllByTenantIdAndWorkOrderIdAndStatusOrderByRequestedAtAsc(
                        workOrder.getTenantId(), workOrder.getId(), WorkOrderOrganizationTransferStatus.PENDING);
        if (transfers.isEmpty()) {
            organizationParticipationService.endPendingTransferTarget(workOrder);
            return;
        }
        String normalizedReason = reason == null || reason.isBlank()
                ? "工单已进入终态，待处理的组织移交申请已由系统自动取消。"
                : reason.trim();
        LocalDateTime decidedAt = UtcTime.now();
        transfers.forEach(transfer -> {
            transfer.setStatus(WorkOrderOrganizationTransferStatus.CANCELLED);
            transfer.setDecidedByMembershipId(effectiveOperator.getId());
            transfer.setDecidedAt(decidedAt);
            transfer.setDecisionComment(normalizedReason);
        });
        transferRepository.saveAll(transfers);
        organizationParticipationService.endPendingTransferTarget(workOrder);
        appendEvent(workOrder, WorkOrderEventType.ORGANIZATION_TRANSFER_CANCELLED, effectiveOperator, normalizedReason);
    }

    private WorkOrder lockedWorkOrder(UUID workOrderId) {
        return workOrderRepository.findForUpdate(accessPolicy.currentTenantId(), workOrderId)
                .orElseThrow(() -> new EntityNotFoundException("工单不存在：" + workOrderId));
    }

    private WorkOrderOrganizationTransfer pendingTransfer(WorkOrder workOrder, UUID transferId) {
        WorkOrderOrganizationTransfer transfer = transferRepository.findForUpdate(
                        workOrder.getTenantId(), workOrder.getId(), transferId)
                .orElseThrow(() -> new EntityNotFoundException("组织移交申请不存在：" + transferId));
        if (transfer.getStatus() != WorkOrderOrganizationTransferStatus.PENDING) {
            throw new IllegalStateException("组织移交申请已经处理。");
        }
        return transfer;
    }

    private void requireVersion(WorkOrder workOrder, Long expectedVersion) {
        if (!Objects.equals(workOrder.getVersion(), expectedVersion)) {
            throw new IllegalStateException("工单版本已变化，请刷新后重试。");
        }
    }

    private void requireNonTerminal(WorkOrder workOrder) {
        if (workOrder.lifecycleStatus().isTerminal()) {
            throw new IllegalStateException("已闭环或已取消工单不能发起组织移交。");
        }
    }

    private OrgUnit requireActiveOrganization(UUID tenantId, UUID orgUnitId) {
        return orgUnitRepository.findByTenantIdAndId(tenantId, orgUnitId)
                .filter(orgUnit -> orgUnit.getStatus() == TenantStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("目标组织不存在或已停用。"));
    }

    private void requireTargetRepresentative(User operator, UUID targetOrgUnitId) {
        if (!hierarchyService.treeContains(operator.getTenantId(), operator.getOrgUnitId(), targetOrgUnitId)) {
            throw new AccessDeniedException("只有目标组织或其严格上级组织的授权人员可以处理移交申请。");
        }
    }

    private void revokeActiveAssignments(WorkOrder workOrder, LocalDateTime endedAt) {
        WorkOrderAssignment pending = pendingAssignment(workOrder);
        if (pending != null) {
            pending.setState(AssignmentState.REVOKED);
            pending.setActiveSlot(null);
            pending.setEndedAt(endedAt);
            assignmentRepository.save(pending);
            workOrder.setPendingAssignment(null);
        }
        WorkOrderAssignment current = currentAssignment(workOrder);
        if (current != null) {
            current.setState(AssignmentState.REVOKED);
            current.setActiveSlot(null);
            current.setEndedAt(endedAt);
            assignmentRepository.save(current);
            workOrder.setCurrentAssignment(null);
        }
    }

    private WorkOrderAssignment currentAssignment(WorkOrder workOrder) {
        WorkOrderAssignment assignment = workOrder.getCurrentAssignment();
        return assignment != null && assignment.isCurrent() ? assignment : null;
    }

    private WorkOrderAssignment pendingAssignment(WorkOrder workOrder) {
        WorkOrderAssignment assignment = workOrder.getPendingAssignment();
        return assignment != null && assignment.isPending() ? assignment : null;
    }

    private void normalizeAssignmentPointers(WorkOrder workOrder) {
        List<WorkOrderAssignment> activeAssignments = assignmentRepository
                .findByTenantIdAndWorkOrderIdAndActiveSlotInForUpdate(
                        workOrder.getTenantId(),
                        workOrder.getId(),
                        List.of(AssignmentSlot.CURRENT, AssignmentSlot.PENDING));
        WorkOrderAssignment current = null;
        WorkOrderAssignment pending = null;
        boolean cleaned = false;
        for (WorkOrderAssignment assignment : activeAssignments) {
            if (assignment.isCurrent()) {
                current = assignment;
            } else if (assignment.isPending()) {
                pending = assignment;
            } else {
                assignment.setActiveSlot(null);
                if (assignment.getEndedAt() == null) {
                    assignment.setEndedAt(UtcTime.now());
                }
                assignmentRepository.save(assignment);
                cleaned = true;
            }
        }
        workOrder.setCurrentAssignment(current);
        workOrder.setPendingAssignment(pending);
        if (cleaned) {
            assignmentRepository.flush();
        }
    }

    private void decide(WorkOrderOrganizationTransfer transfer,
                        WorkOrderOrganizationTransferStatus status,
                        User operator,
                        String comment) {
        transfer.setStatus(status);
        transfer.setDecidedByMembershipId(operator.getId());
        transfer.setDecidedAt(UtcTime.now());
        transfer.setDecisionComment(comment.trim());
        transferRepository.save(transfer);
    }

    private WorkOrderTransferDTO toDTO(
            WorkOrderOrganizationTransfer transfer, WorkOrder workOrder, User operator) {
        Map<UUID, String> names = hierarchyService.namesById(
                transfer.getTenantId(), Set.of(transfer.getSourceOrgUnitId(), transfer.getTargetOrgUnitId()));
        Set<String> actions = new LinkedHashSet<>();
        if (transfer.getStatus() == WorkOrderOrganizationTransferStatus.PENDING
                && !workOrder.lifecycleStatus().isTerminal()) {
            if (accessPolicy.hasActionAccess(workOrder, WorkOrderAction.TRANSFER_CANCEL)) actions.add("CANCEL");
            if (hierarchyService.treeContains(operator.getTenantId(), operator.getOrgUnitId(), transfer.getTargetOrgUnitId())) {
                if (accessPolicy.hasActionAccess(workOrder, WorkOrderAction.TRANSFER_ACCEPT)) actions.add("ACCEPT");
                if (accessPolicy.hasActionAccess(workOrder, WorkOrderAction.TRANSFER_REJECT)) actions.add("REJECT");
            }
        }
        return new WorkOrderTransferDTO(
                transfer.getId(), transfer.getWorkOrderId(), transfer.getSourceOrgUnitId(),
                names.get(transfer.getSourceOrgUnitId()), transfer.getTargetOrgUnitId(),
                names.get(transfer.getTargetOrgUnitId()), transfer.getStatus().name(),
                transfer.getExpectedWorkOrderVersion(), transfer.getRequestedByMembershipId(),
                transfer.getRequestedAt(), transfer.getRequestReason(), transfer.getDecidedByMembershipId(),
                transfer.getDecidedAt(), transfer.getDecisionComment(), actions);
    }

    private void appendEvent(WorkOrder workOrder, WorkOrderEventType type, User operator, String reason) {
        WorkOrderEvent event = new WorkOrderEvent();
        event.setTenantId(workOrder.getTenantId());
        event.setWorkOrder(workOrder);
        event.setEventType(type);
        event.setPreviousStatusCode(workOrder.lifecycleStatus().getCode());
        event.setNextStatusCode(workOrder.lifecycleStatus().getCode());
        event.setOperatorMembershipId(operator.getId());
        event.setOperatorNameSnapshot(operator.getName());
        event.setSource(WorkOrderEventSource.WEB);
        event.setReason(reason);
        event.setOccurredAt(UtcTime.now());
        eventRepository.save(event);
    }
}
