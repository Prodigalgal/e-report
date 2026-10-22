package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.dto.*;
import com.pro.ereportbacken.model.pojo.*;
import com.pro.ereportbacken.repository.*;
import com.pro.ereportbacken.security.WorkOrderAccessPolicy;
import com.pro.ereportbacken.security.WorkOrderAction;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WorkOrderLifecycleService {

    private static final int ASSIGNMENT_CANDIDATE_LIMIT = 20;
    private static final int ASSIGNMENT_CANDIDATE_QUERY_LIMIT = 200;

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderAssignmentRepository assignmentRepository;
    private final WorkOrderResponseRepository responseRepository;
    private final WorkOrderVerificationRepository verificationRepository;
    private final WorkOrderEventRepository eventRepository;
    private final UserRepository userRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final UserAuthorizationCache userAuthorizationCache;
    private final OrganizationHierarchyService hierarchyService;
    private final WorkOrderAttachmentService attachmentService;
    private final WorkOrderAccessPolicy accessPolicy;
    private final WorkOrderSlaService slaService;
    private final WorkOrderSlaBreachService slaBreachService;
    private final WorkOrderDepartmentParticipationService departmentParticipationService;
    private final WorkOrderOrganizationParticipationService organizationParticipationService;
    private final WorkOrderOrganizationTransferService organizationTransferService;
    private final OutboxService outboxService;

    public WorkOrderLifecycleService(WorkOrderRepository workOrderRepository,
                                     WorkOrderAssignmentRepository assignmentRepository,
                                     WorkOrderResponseRepository responseRepository,
                                     WorkOrderVerificationRepository verificationRepository,
                                     WorkOrderEventRepository eventRepository,
                                     UserRepository userRepository,
                                     OrgUnitRepository orgUnitRepository,
                                     UserAuthorizationCache userAuthorizationCache,
                                     OrganizationHierarchyService hierarchyService,
                                     WorkOrderAttachmentService attachmentService,
                                     WorkOrderAccessPolicy accessPolicy,
                                     WorkOrderSlaService slaService,
                                     WorkOrderSlaBreachService slaBreachService,
                                     WorkOrderDepartmentParticipationService departmentParticipationService,
                                     WorkOrderOrganizationParticipationService organizationParticipationService,
                                     WorkOrderOrganizationTransferService organizationTransferService,
                                     OutboxService outboxService) {
        this.workOrderRepository = workOrderRepository;
        this.assignmentRepository = assignmentRepository;
        this.responseRepository = responseRepository;
        this.verificationRepository = verificationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.userAuthorizationCache = userAuthorizationCache;
        this.hierarchyService = hierarchyService;
        this.attachmentService = attachmentService;
        this.accessPolicy = accessPolicy;
        this.slaService = slaService;
        this.slaBreachService = slaBreachService;
        this.departmentParticipationService = departmentParticipationService;
        this.organizationParticipationService = organizationParticipationService;
        this.organizationTransferService = organizationTransferService;
        this.outboxService = outboxService;
    }

    @Transactional
    public WorkOrder claim(UUID workOrderId, Long expectedVersion) {
        WorkOrder workOrder = findLocked(workOrderId);
        verifyVersion(workOrder, expectedVersion);
        normalizeAssignmentPointers(workOrder);
        requireAnyStatus(workOrder, WorkOrderStatus.SUBMITTED, WorkOrderStatus.IN_PROGRESS);
        accessPolicy.requireActionAccess(workOrder, WorkOrderAction.CLAIM);
        requireNoActiveAssignments(workOrder);
        requireActiveOrganization(workOrder.getTenantId(), workOrder.getResponsibleOrgId());

        User operator = accessPolicy.currentUserOrThrow();
        if (!departmentParticipationService.canCollaborateForClaim(
                workOrder.getTenantId(), workOrder.getResponsibleDepartmentId(), operator)) {
            throw new AccessDeniedException("当前部门不在工单责任部门的同级协作范围内，无法接单。");
        }
        UUID processingDepartmentId = operator.getDepartmentId();
        WorkOrderAssignment assignment = newAssignment(
                workOrder,
                operator,
                operator,
                workOrder.getResponsibleOrgId(),
                processingDepartmentId,
                AssignmentType.CLAIM,
                AssignmentState.ACCEPTED,
                AssignmentSlot.CURRENT,
                "处理人员主动接单"
        );
        assignment.setAcceptedAt(UtcTime.now());
        assignmentRepository.save(assignment);
        workOrder.setCurrentAssignment(assignment);
        departmentParticipationService.recordProcessingDepartment(
                workOrder, processingDepartmentId, operator.getId(), "处理部门主动接单");
        organizationParticipationService.recordProcessingOrganization(
                workOrder, operator.getOrgUnitId(), operator.getId(), "处理人员主动接单");
        workOrder.setAcceptedAt(workOrder.getAcceptedAt() == null ? assignment.getAcceptedAt() : workOrder.getAcceptedAt());
        WorkOrderStatus previous = workOrder.lifecycleStatus();
        slaBreachService.markBreachedIfDue(workOrder.getTenantId(), workOrder.getId(), assignment.getAcceptedAt());
        workOrder.transitionTo(WorkOrderStatus.IN_PROGRESS);
        slaService.startProcessing(workOrder, assignment.getAcceptedAt());
        WorkOrder saved = workOrderRepository.saveAndFlush(workOrder);
        appendEvent(saved, WorkOrderEventType.CLAIMED, previous, saved.lifecycleStatus(), operator, assignment.getId(), null, null, assignment.getReason());
        return saved;
    }

    @Transactional
    public WorkOrder assign(UUID workOrderId, AssignWorkOrderRequestDTO request) {
        WorkOrder workOrder = findLocked(workOrderId);
        verifyVersion(workOrder, request.getExpectedVersion());
        normalizeAssignmentPointers(workOrder);
        requireAnyStatus(workOrder, WorkOrderStatus.SUBMITTED, WorkOrderStatus.IN_PROGRESS);
        if (workOrder.lifecycleStatus() == WorkOrderStatus.IN_PROGRESS
                && workOrder.getReworkCount() != null
                && workOrder.getReworkCount() > 0
                && !hasCurrentAssignment(workOrder)
                && !hasPendingAssignment(workOrder)) {
            throw conflict("当前工单处于返工阶段，请使用调单操作。");
        }
        accessPolicy.requireActionAccess(workOrder, WorkOrderAction.CREATE_ASSIGNMENT);
        accessPolicy.requireOrganizationAccess(workOrder, WorkOrderAction.CREATE_ASSIGNMENT, request.getResponsibleOrgId());
        requireActiveOrganization(workOrder.getTenantId(), request.getResponsibleOrgId());
        if (hasCurrentAssignment(workOrder) || hasPendingAssignment(workOrder)) {
            throw conflict("工单已有责任人或待接收派单，不能重复派单。");
        }
        User operator = accessPolicy.currentUserOrThrow();
        User assignee = requireEligibleAssignee(
                workOrder, request.getAssigneeMembershipId(), request.getResponsibleOrgId(),
                request.getResponsibleDepartmentId());
        UUID responsibleDepartmentId = resolveResponsibleDepartmentId(
                workOrder, request.getResponsibleOrgId(), request.getResponsibleDepartmentId(), assignee);
        WorkOrderAssignment assignment = createPendingAssignment(
                workOrder,
                assignee,
                operator,
                request.getResponsibleOrgId(),
                responsibleDepartmentId,
                AssignmentType.ASSIGN,
                request.getReason()
        );
        workOrder.setPendingAssignment(assignment);
        slaBreachService.markBreachedIfDue(workOrder.getTenantId(), workOrder.getId(), assignment.getAssignedAt());
        slaService.startAssignmentAcceptance(workOrder, assignment.getAssignedAt());
        WorkOrder saved = workOrderRepository.saveAndFlush(workOrder);
        appendEvent(saved, WorkOrderEventType.ASSIGNED, saved.lifecycleStatus(), saved.lifecycleStatus(), operator, assignment.getId(), null, null, request.getReason());
        outboxService.enqueueAssignmentReceived(saved, assignment);
        return saved;
    }

    @Transactional
    public WorkOrder reassign(UUID workOrderId, AssignWorkOrderRequestDTO request) {
        WorkOrder workOrder = findLocked(workOrderId);
        verifyVersion(workOrder, request.getExpectedVersion());
        normalizeAssignmentPointers(workOrder);
        requireAnyStatus(workOrder,
                WorkOrderStatus.SUBMITTED,
                WorkOrderStatus.IN_PROGRESS,
                WorkOrderStatus.PENDING_VERIFICATION,
                WorkOrderStatus.ON_HOLD);
        requireReassignmentState(workOrder);
        accessPolicy.requireActionAccess(workOrder, WorkOrderAction.REASSIGN);
        accessPolicy.requireOrganizationAccess(workOrder, WorkOrderAction.REASSIGN, request.getResponsibleOrgId());
        requireActiveOrganization(workOrder.getTenantId(), request.getResponsibleOrgId());
        User operator = accessPolicy.currentUserOrThrow();
        User assignee = requireEligibleAssignee(
                workOrder, request.getAssigneeMembershipId(), request.getResponsibleOrgId(),
                request.getResponsibleDepartmentId());
        UUID responsibleDepartmentId = resolveResponsibleDepartmentId(
                workOrder, request.getResponsibleOrgId(), request.getResponsibleDepartmentId(), assignee);
        WorkOrderAssignment currentAssignment = currentAssignment(workOrder);
        if (currentAssignment != null
                && Objects.equals(currentAssignment.getAssignee().getId(), assignee.getId())) {
            throw unprocessable("新处理人员不能与当前处理人员相同。");
        }

        WorkOrderStatus previous = workOrder.lifecycleStatus();
        revokePendingAssignment(workOrder);
        if (currentAssignment != null) {
            finishAssignment(currentAssignment, AssignmentState.REVOKED);
            workOrder.setCurrentAssignment(null);
        }
        assignmentRepository.flush();

        LocalDateTime reassignedAt = UtcTime.now();
        WorkOrderAssignment assignment = newAssignment(
                workOrder,
                assignee,
                operator,
                request.getResponsibleOrgId(),
                responsibleDepartmentId,
                AssignmentType.REASSIGN,
                AssignmentState.ACCEPTED,
                AssignmentSlot.CURRENT,
                request.getReason()
        );
        assignment.setAcceptedAt(reassignedAt);
        assignmentRepository.save(assignment);
        workOrder.setCurrentAssignment(assignment);
        workOrder.setPendingAssignment(null);
        workOrder.setResponsibleOrgId(request.getResponsibleOrgId());
        departmentParticipationService.recordProcessingDepartment(
                workOrder, responsibleDepartmentId, operator.getId(), request.getReason());
        organizationParticipationService.recordProcessingOrganization(
                workOrder, request.getResponsibleOrgId(), operator.getId(), request.getReason());
        workOrder.setAcceptedAt(workOrder.getAcceptedAt() == null ? reassignedAt : workOrder.getAcceptedAt());
        if (previous == WorkOrderStatus.PENDING_VERIFICATION) {
            clearCurrentResponseProjection(workOrder);
            workOrder.transitionTo(WorkOrderStatus.IN_PROGRESS);
            slaService.startProcessing(workOrder, reassignedAt);
        } else if (previous == WorkOrderStatus.SUBMITTED) {
            workOrder.transitionTo(WorkOrderStatus.IN_PROGRESS);
        }
        slaBreachService.markBreachedIfDue(workOrder.getTenantId(), workOrder.getId(), reassignedAt);
        slaService.continueAfterImmediateReassignment(workOrder, reassignedAt);
        WorkOrder saved = workOrderRepository.saveAndFlush(workOrder);
        appendEvent(saved, WorkOrderEventType.REASSIGNED, previous, saved.lifecycleStatus(), operator, assignment.getId(), null, null, request.getReason());
        outboxService.enqueueAssignmentReceived(saved, assignment);
        return saved;
    }

    @Transactional
    public WorkOrder acceptAssignment(UUID workOrderId, UUID assignmentId, AssignmentDecisionRequestDTO request) {
        WorkOrder workOrder = findLocked(workOrderId);
        verifyVersion(workOrder, request.getExpectedVersion());
        normalizeAssignmentPointers(workOrder);
        requireAnyStatus(workOrder, WorkOrderStatus.SUBMITTED, WorkOrderStatus.IN_PROGRESS);
        WorkOrderAssignment pending = requirePendingAssignment(workOrder, assignmentId);
        User operator = accessPolicy.currentUserOrThrow();
        if (!Objects.equals(pending.getAssignee().getId(), operator.getId())) {
            throw new AccessDeniedException("只有派单目标人员可以接受该工单。");
        }
        accessPolicy.requireActionAccess(workOrder, WorkOrderAction.ACCEPT_ASSIGNMENT);
        requireEligibleAssignee(workOrder, operator.getId(), pending.getResponsibleOrgId(),
                pending.getResponsibleDepartmentId());

        WorkOrderAssignment currentAssignment = currentAssignment(workOrder);
        if (currentAssignment != null) {
            finishAssignment(currentAssignment, AssignmentState.REVOKED);
        }
        pending.setState(AssignmentState.ACCEPTED);
        pending.setActiveSlot(AssignmentSlot.CURRENT);
        pending.setAcceptedAt(UtcTime.now());
        assignmentRepository.save(pending);
        workOrder.setCurrentAssignment(pending);
        workOrder.setPendingAssignment(null);
        workOrder.setResponsibleOrgId(pending.getResponsibleOrgId());
        departmentParticipationService.recordProcessingDepartment(
                workOrder, pending.getResponsibleDepartmentId(), operator.getId(), "接受派单并进入处理");
        organizationParticipationService.recordProcessingOrganization(
                workOrder, pending.getResponsibleOrgId(), operator.getId(), "接受派单并进入处理");
        workOrder.setAcceptedAt(workOrder.getAcceptedAt() == null ? pending.getAcceptedAt() : workOrder.getAcceptedAt());
        WorkOrderStatus previous = workOrder.lifecycleStatus();
        slaBreachService.markBreachedIfDue(workOrder.getTenantId(), workOrder.getId(), pending.getAcceptedAt());
        if (previous == WorkOrderStatus.SUBMITTED) {
            workOrder.transitionTo(WorkOrderStatus.IN_PROGRESS);
        }
        slaService.finishAssignmentAcceptance(workOrder, pending.getAcceptedAt());
        WorkOrder saved = workOrderRepository.saveAndFlush(workOrder);
        appendEvent(saved, WorkOrderEventType.ASSIGNMENT_ACCEPTED, previous, saved.lifecycleStatus(), operator, pending.getId(), null, null, null);
        return saved;
    }

    @Transactional
    public WorkOrder rejectAssignment(UUID workOrderId, UUID assignmentId, AssignmentDecisionRequestDTO request) {
        if (!StringUtils.hasText(request.getReason())) {
            throw new IllegalArgumentException("拒绝派单必须填写原因。");
        }
        WorkOrder workOrder = findLocked(workOrderId);
        verifyVersion(workOrder, request.getExpectedVersion());
        normalizeAssignmentPointers(workOrder);
        requireAnyStatus(workOrder, WorkOrderStatus.SUBMITTED, WorkOrderStatus.IN_PROGRESS);
        WorkOrderAssignment pending = requirePendingAssignment(workOrder, assignmentId);
        User operator = accessPolicy.currentUserOrThrow();
        if (!Objects.equals(pending.getAssignee().getId(), operator.getId())) {
            throw new AccessDeniedException("只有派单目标人员可以拒绝该工单。");
        }
        accessPolicy.requireActionAccess(workOrder, WorkOrderAction.REJECT_ASSIGNMENT);
        LocalDateTime rejectedAt = UtcTime.now();
        pending.setState(AssignmentState.REJECTED);
        pending.setActiveSlot(null);
        pending.setRejectionReason(request.getReason().trim());
        pending.setEndedAt(rejectedAt);
        assignmentRepository.save(pending);
        workOrder.setPendingAssignment(null);
        slaBreachService.markBreachedIfDue(workOrder.getTenantId(), workOrder.getId(), rejectedAt);
        slaService.rejectAssignment(workOrder, rejectedAt);
        WorkOrder saved = workOrderRepository.saveAndFlush(workOrder);
        appendEvent(saved, WorkOrderEventType.ASSIGNMENT_REJECTED, saved.lifecycleStatus(), saved.lifecycleStatus(), operator, pending.getId(), null, null, request.getReason());
        return saved;
    }

    @Transactional
    public WorkOrder submitResponse(UUID workOrderId, RespondWorkOrderRequestDTO request) {
        WorkOrder workOrder = findLocked(workOrderId);
        verifyVersion(workOrder, request.getExpectedVersion());
        normalizeAssignmentPointers(workOrder);
        requireAnyStatus(workOrder, WorkOrderStatus.IN_PROGRESS);
        accessPolicy.requireActionAccess(workOrder, WorkOrderAction.SUBMIT_RESPONSE);
        User operator = accessPolicy.currentUserOrThrow();
        WorkOrderAssignment current = currentAssignment(workOrder);
        if (current == null || !Objects.equals(current.getAssignee().getId(), operator.getId())) {
            throw new AccessDeniedException("只有当前处理人员可以提交回单。");
        }

        WorkOrderResponse response = new WorkOrderResponse();
        response.setTenantId(workOrder.getTenantId());
        response.setWorkOrder(workOrder);
        response.setAssignment(current);
        response.setAttemptNo(Math.toIntExact(responseRepository.countByTenantIdAndWorkOrderId(workOrder.getTenantId(), workOrder.getId()) + 1));
        response.setSubmittedBy(operator);
        response.setResolutionDetails(request.getResolutionDetails().trim());
        response.setResolutionMethod(request.getResolutionMethod().trim());
        response.setReplacementCompleted(request.getReplacementCompleted());
        response.setReplacementResult(request.getReplacementResult());
        response.setSubmittedAt(UtcTime.now());
        responseRepository.save(response);

        attachmentService.attachTransferredFiles(workOrder, request.getUploadedAttachmentIds(), AttachmentStage.RESPONSE, true);
        workOrder.getAttachments().stream()
                .filter(attachment -> attachment.getAttachmentStage() == AttachmentStage.RESPONSE && attachment.getResponse() == null)
                .forEach(attachment -> attachment.setResponse(response));
        appendMaterialUsages(workOrder, response, request.getMaterialUsages());
        workOrder.type().applyTypeSpecificResponse(workOrder, request.getReplacementCompleted(), request.getReplacementResult());
        workOrder.setResolutionDetails(response.getResolutionDetails());
        workOrder.setResolutionMethod(response.getResolutionMethod());
        workOrder.setResolutionTime(response.getSubmittedAt());
        workOrder.setResolvedBy(operator);
        finishAssignment(current, AssignmentState.COMPLETED);
        workOrder.setCurrentAssignment(null);
        revokePendingAssignment(workOrder);
        workOrder.setCurrentResponse(response);
        WorkOrderStatus previous = workOrder.lifecycleStatus();
        slaBreachService.markBreachedIfDue(workOrder.getTenantId(), workOrder.getId(), response.getSubmittedAt());
        workOrder.transitionTo(WorkOrderStatus.PENDING_VERIFICATION);
        slaService.startVerification(workOrder, response.getSubmittedAt());
        WorkOrder saved = workOrderRepository.saveAndFlush(workOrder);
        appendEvent(saved, WorkOrderEventType.RESPONSE_SUBMITTED, previous, saved.lifecycleStatus(), operator, current.getId(), response.getId(), null, null);
        return saved;
    }

    @Transactional
    public WorkOrder verify(UUID workOrderId, VerifyWorkOrderRequestDTO request) {
        WorkOrder workOrder = findLocked(workOrderId);
        verifyVersion(workOrder, request.getExpectedVersion());
        normalizeAssignmentPointers(workOrder);
        requireAnyStatus(workOrder, WorkOrderStatus.PENDING_VERIFICATION);
        WorkOrderAction verificationAction = request.getDecision() == VerificationDecision.APPROVED
                ? WorkOrderAction.APPROVE_VERIFICATION
                : WorkOrderAction.REJECT_VERIFICATION;
        accessPolicy.requireActionAccess(workOrder, verificationAction);
        WorkOrderResponse response = responseRepository.findByTenantIdAndWorkOrderIdAndId(workOrder.getTenantId(), workOrder.getId(), request.getResponseId())
                .orElseThrow(() -> new EntityNotFoundException("当前工单的待验收回单不存在。"));
        if (workOrder.getCurrentResponse() == null || !Objects.equals(workOrder.getCurrentResponse().getId(), response.getId())) {
            throw conflict("只能验收当前待验收回单。");
        }
        if (verificationRepository.findByTenantIdAndResponseId(workOrder.getTenantId(), response.getId()).isPresent()) {
            throw conflict("当前回单已经完成验收。");
        }

        User verifier = accessPolicy.currentUserOrThrow();
        boolean selfVerification = accessPolicy.isSelfVerification(response.getSubmittedBy(), verifier);
        boolean supervisoryOverride = accessPolicy.requiresSupervisoryVerificationOverride(
                workOrder, verificationAction, response.getSubmittedBy(), verifier);
        if (selfVerification && !supervisoryOverride) {
            throw unprocessable("处理人员不能验收自己的回单。");
        }
        if (!StringUtils.hasText(request.getComment())) {
            throw new IllegalArgumentException("验收意见不能为空。");
        }

        WorkOrderVerification verification = new WorkOrderVerification();
        verification.setTenantId(workOrder.getTenantId());
        verification.setWorkOrder(workOrder);
        verification.setResponse(response);
        verification.setVerifiedBy(verifier);
        verification.setDecision(request.getDecision());
        verification.setComment(trimToNull(request.getComment()));
        verification.setVerificationOverride(supervisoryOverride);
        verification.setOverrideReason(null);
        verification.setVerifiedAt(UtcTime.now());
        verificationRepository.save(verification);

        WorkOrderStatus previous = workOrder.lifecycleStatus();
        slaBreachService.markBreachedIfDue(workOrder.getTenantId(), workOrder.getId(), verification.getVerifiedAt());
        revokePendingAssignment(workOrder);
        if (request.getDecision() == VerificationDecision.APPROVED) {
            workOrder.setClosureReason(trimToNull(request.getComment()));
            workOrder.setClosureTime(verification.getVerifiedAt());
            workOrder.setClosedAt(verification.getVerifiedAt());
            workOrder.setClosedBy(verifier);
            workOrder.transitionTo(WorkOrderStatus.CLOSED);
            slaService.complete(workOrder);
        } else {
            workOrder.setReworkCount(workOrder.getReworkCount() + 1);
            workOrder.transitionTo(WorkOrderStatus.IN_PROGRESS);
            slaService.startRework(workOrder, verification.getVerifiedAt());
            User previousAssignee = response.getAssignment().getAssignee();
            if (request.getReassignToMembershipId() != null
                    && previousAssignee != null
                    && Objects.equals(previousAssignee.getId(), request.getReassignToMembershipId())) {
                throw unprocessable("重新指定的处理人员不能与原处理人员相同。");
            }
            clearCurrentResponseProjection(workOrder);
            if (isEligibleAssignee(workOrder, previousAssignee,
                    response.getAssignment().getResponsibleOrgId(),
                    response.getAssignment().getResponsibleDepartmentId())) {
                WorkOrderAssignment rework = newAssignment(
                        workOrder,
                        previousAssignee,
                        verifier,
                        response.getAssignment().getResponsibleOrgId(),
                        response.getAssignment().getResponsibleDepartmentId(),
                        AssignmentType.REWORK,
                        AssignmentState.ACCEPTED,
                        AssignmentSlot.CURRENT,
                        request.getComment()
                );
                rework.setAcceptedAt(verification.getVerifiedAt());
                assignmentRepository.save(rework);
                workOrder.setCurrentAssignment(rework);
            }
            if (request.getReassignToMembershipId() != null) {
                accessPolicy.requireActionAccess(workOrder, WorkOrderAction.REASSIGN);
                UUID responsibleOrgId = request.getResponsibleOrgId() != null
                        ? request.getResponsibleOrgId()
                        : workOrder.getResponsibleOrgId();
                accessPolicy.requireOrganizationAccess(workOrder, WorkOrderAction.REASSIGN, responsibleOrgId);
                requireActiveOrganization(workOrder.getTenantId(), responsibleOrgId);
                User target = requireEligibleAssignee(
                        workOrder, request.getReassignToMembershipId(), responsibleOrgId,
                        request.getResponsibleDepartmentId());
                UUID responsibleDepartmentId = resolveResponsibleDepartmentId(
                        workOrder, responsibleOrgId, request.getResponsibleDepartmentId(), target);
                WorkOrderAssignment pending = createPendingAssignment(
                        workOrder,
                        target,
                        verifier,
                        responsibleOrgId,
                        responsibleDepartmentId,
                        AssignmentType.REASSIGN,
                        request.getComment()
                );
                workOrder.setPendingAssignment(pending);
                slaService.startAssignmentAcceptance(workOrder, pending.getAssignedAt());
            }
        }
        WorkOrder saved = workOrderRepository.saveAndFlush(workOrder);
        departmentParticipationService.recordVerification(saved, verifier);
        WorkOrderEventType eventType = request.getDecision() == VerificationDecision.APPROVED
                ? WorkOrderEventType.VERIFICATION_APPROVED
                : WorkOrderEventType.VERIFICATION_REJECTED;
        appendEvent(saved, eventType, previous, saved.lifecycleStatus(), verifier, null, response.getId(), verification.getId(), request.getComment());
        organizationTransferService.cancelPendingForTerminal(
                saved,
                verifier,
                "工单验收通过，待处理的组织移交申请已由系统自动取消。"
        );
        return saved;
    }

    @Transactional
    public WorkOrder hold(UUID workOrderId, ReasonedWorkOrderCommandDTO request) {
        WorkOrder workOrder = findLocked(workOrderId);
        verifyVersion(workOrder, request.getExpectedVersion());
        normalizeAssignmentPointers(workOrder);
        requireAnyStatus(workOrder, WorkOrderStatus.IN_PROGRESS);
        accessPolicy.requireActionAccess(workOrder, WorkOrderAction.HOLD);
        requireCurrentAssignee(workOrder);
        WorkOrderStatus previous = workOrder.lifecycleStatus();
        LocalDateTime heldAt = UtcTime.now();
        slaBreachService.markBreachedIfDue(workOrder.getTenantId(), workOrder.getId(), heldAt);
        slaService.pause(workOrder, heldAt);
        workOrder.transitionTo(WorkOrderStatus.ON_HOLD);
        WorkOrder saved = workOrderRepository.saveAndFlush(workOrder);
        appendEvent(saved, WorkOrderEventType.HELD, previous, saved.lifecycleStatus(), accessPolicy.currentUserOrThrow(), null, null, null, request.getReason());
        return saved;
    }

    @Transactional
    public WorkOrder resume(UUID workOrderId, VersionedWorkOrderCommandDTO request) {
        WorkOrder workOrder = findLocked(workOrderId);
        verifyVersion(workOrder, request.getExpectedVersion());
        normalizeAssignmentPointers(workOrder);
        requireAnyStatus(workOrder, WorkOrderStatus.ON_HOLD);
        accessPolicy.requireActionAccess(workOrder, WorkOrderAction.RESUME);
        requireCurrentAssignee(workOrder);
        WorkOrderStatus previous = workOrder.lifecycleStatus();
        LocalDateTime resumedAt = UtcTime.now();
        slaBreachService.markBreachedIfDue(workOrder.getTenantId(), workOrder.getId(), resumedAt);
        slaService.resume(workOrder, resumedAt);
        workOrder.transitionTo(WorkOrderStatus.IN_PROGRESS);
        WorkOrder saved = workOrderRepository.saveAndFlush(workOrder);
        appendEvent(saved, WorkOrderEventType.RESUMED, previous, saved.lifecycleStatus(), accessPolicy.currentUserOrThrow(), null, null, null, null);
        return saved;
    }

    @Transactional
    public WorkOrder cancel(UUID workOrderId, ReasonedWorkOrderCommandDTO request) {
        WorkOrder workOrder = findLocked(workOrderId);
        verifyVersion(workOrder, request.getExpectedVersion());
        normalizeAssignmentPointers(workOrder);
        if (workOrder.lifecycleStatus().isTerminal()) {
            throw conflict("终态工单不能再次取消。");
        }
        User operator = accessPolicy.currentUserOrThrow();
        boolean reporterWithdrawal = workOrder.lifecycleStatus() == WorkOrderStatus.SUBMITTED
                && workOrder.getCurrentAssignment() == null
                && workOrder.getReportedBy() != null
                && Objects.equals(workOrder.getReportedBy().getId(), operator.getId());
        accessPolicy.requireActionAccess(
                workOrder,
                reporterWithdrawal ? WorkOrderAction.WITHDRAW : WorkOrderAction.CANCEL
        );
        WorkOrderAssignment current = currentAssignment(workOrder);
        if (current != null) {
            finishAssignment(current, AssignmentState.CANCELLED);
            workOrder.setCurrentAssignment(null);
        }
        WorkOrderAssignment pending = pendingAssignment(workOrder);
        if (pending != null) {
            finishAssignment(pending, AssignmentState.CANCELLED);
            workOrder.setPendingAssignment(null);
        }
        WorkOrderStatus previous = workOrder.lifecycleStatus();
        workOrder.setCancelledAt(UtcTime.now());
        slaBreachService.markBreachedIfDue(workOrder.getTenantId(), workOrder.getId(), workOrder.getCancelledAt());
        workOrder.transitionTo(WorkOrderStatus.CANCELLED);
        slaService.complete(workOrder);
        WorkOrder saved = workOrderRepository.saveAndFlush(workOrder);
        appendEvent(saved, WorkOrderEventType.CANCELLED, previous, saved.lifecycleStatus(), operator, null, null, null, request.getReason());
        organizationTransferService.cancelPendingForTerminal(
                saved,
                operator,
                "工单已取消，待处理的组织移交申请已由系统自动取消。"
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AssignmentCandidateDTO> assignmentCandidates(UUID workOrderId, String searchTerm) {
        WorkOrder workOrder = findVisible(workOrderId);
        hydrateAssignmentPointersForRead(workOrder);
        if (workOrder.lifecycleStatus().isTerminal()) {
            throw conflict("当前工单已进入终态，不能查询可派发处理人员。");
        }
        User operator = accessPolicy.currentUserOrThrow();
        WorkOrderAction action = requiresReassignment(workOrder)
                ? WorkOrderAction.REASSIGN
                : WorkOrderAction.CREATE_ASSIGNMENT;
        accessPolicy.requireActionAccess(workOrder, action);
        String normalizedSearchTerm = trimToNull(searchTerm);
        if (normalizedSearchTerm == null) {
            return List.of();
        }
        if (normalizedSearchTerm.length() > 50) {
            throw new IllegalArgumentException("处理人员搜索关键词不能超过50个字符。");
        }

        Set<UUID> targetOrgUnitIds = assignmentTargetOrganizationIds(workOrder.getTenantId(), operator);
        if (targetOrgUnitIds.isEmpty()) {
            return List.of();
        }

        List<UUID> candidateIds = userRepository.searchActiveUserIdsInOrganizations(
                workOrder.getTenantId(),
                UserStatus.ACTIVE,
                targetOrgUnitIds,
                com.pro.ereportbacken.model.pojo.UserNameSearchKeys.escapeLikePattern(normalizedSearchTerm, '!'),
                com.pro.ereportbacken.model.pojo.UserNameSearchKeys.normalizeSearchTerm(normalizedSearchTerm),
                Pageable.ofSize(ASSIGNMENT_CANDIDATE_QUERY_LIMIT)
        );
        if (candidateIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> excludedIds = new HashSet<>();
        WorkOrderAssignment currentAssignment = currentAssignment(workOrder);
        if (currentAssignment != null) {
            excludedIds.add(currentAssignment.getAssignee().getId());
        }
        WorkOrderAssignment pendingAssignment = pendingAssignment(workOrder);
        if (pendingAssignment != null) {
            excludedIds.add(pendingAssignment.getAssignee().getId());
        }
        candidateIds = candidateIds.stream()
                .filter(candidateId -> !excludedIds.contains(candidateId))
                .limit(ASSIGNMENT_CANDIDATE_LIMIT)
                .toList();
        if (candidateIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, OrgUnit> orgs = orgUnitRepository.findByTenantId(workOrder.getTenantId()).stream()
                .filter(orgUnit -> orgUnit.getStatus() == TenantStatus.ACTIVE)
                .collect(Collectors.toMap(OrgUnit::getId, Function.identity()));
        Map<UUID, Long> activeCounts = assignmentRepository
                .countActiveAssignments(workOrder.getTenantId(), candidateIds).stream()
                .collect(Collectors.toMap(
                        WorkOrderAssignmentRepository.ActiveAssignmentCount::getAssigneeId,
                        WorkOrderAssignmentRepository.ActiveAssignmentCount::getTotal
                ));
        return userRepository.findByTenantIdAndIdIn(workOrder.getTenantId(), candidateIds).stream()
                .filter(user -> orgs.containsKey(user.getOrgUnitId()))
                .filter(user -> isEligibleAssignee(workOrder, user, user.getOrgUnitId(), null))
                .map(user -> new AssignmentCandidateDTO(
                        user.getId(),
                        user.getUsername(),
                        user.getName(),
                        user.getOrgUnitId(),
                        Optional.ofNullable(orgs.get(user.getOrgUnitId())).map(OrgUnit::getName).orElse("未分配组织"),
                        activeCounts.getOrDefault(user.getId(), 0L)
                ))
                .sorted(Comparator.comparing(AssignmentCandidateDTO::activeWorkOrderCount)
                        .thenComparing(candidate -> Optional.ofNullable(candidate.name()).orElse(candidate.username())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderEventDTO> events(UUID workOrderId, Pageable pageable) {
        WorkOrder workOrder = findVisible(workOrderId);
        accessPolicy.requireHistoryAccess(workOrder);
        boolean auditVisible = accessPolicy.hasAuditAccess(workOrder);
        return eventRepository.findByTenantIdAndWorkOrderIdOrderByOccurredAtDesc(workOrder.getTenantId(), workOrderId, pageable)
                .map(event -> new WorkOrderEventDTO(
                        event.getId(),
                        event.getEventType().name(),
                        event.getEventType().getLabel(),
                        event.getPreviousStatusCode(),
                        event.getNextStatusCode(),
                        auditVisible ? event.getOperatorMembershipId() : null,
                        event.getOperatorNameSnapshot(),
                        event.getReason(),
                        event.getOccurredAt()
                ));
    }

    @Transactional(readOnly = true)
    public List<WorkOrderResponseHistoryDTO> responses(UUID workOrderId) {
        WorkOrder workOrder = findVisible(workOrderId);
        accessPolicy.requireHistoryAccess(workOrder);
        Map<UUID, WorkOrderVerification> verificationByResponse = verificationRepository
                .findByTenantIdAndWorkOrderIdOrderByVerifiedAtAsc(workOrder.getTenantId(), workOrderId).stream()
                .collect(Collectors.toMap(verification -> verification.getResponse().getId(), Function.identity()));
        return responseRepository.findByTenantIdAndWorkOrderIdOrderByAttemptNoAsc(workOrder.getTenantId(), workOrderId).stream()
                .map(response -> {
                    WorkOrderVerification verification = verificationByResponse.get(response.getId());
                    return new WorkOrderResponseHistoryDTO(
                            response.getId(),
                            response.getAttemptNo(),
                            response.getResolutionDetails(),
                            response.getResolutionMethod(),
                            toUserInfo(response.getSubmittedBy()),
                            response.getSubmittedAt(),
                            verification == null ? null : verification.getDecision().name(),
                            verification == null ? null : verification.getDecision().getLabel(),
                            verification == null ? null : verification.getComment(),
                            verification == null ? null : toUserInfo(verification.getVerifiedBy()),
                            verification == null ? null : verification.getVerifiedAt()
                    );
                })
                .toList();
    }

    @Transactional
    public void recordSubmitted(WorkOrder workOrder, User operator) {
        appendEvent(workOrder, WorkOrderEventType.SUBMITTED, null, workOrder.lifecycleStatus(), operator, null, null, null, null);
    }

    private WorkOrder findLocked(UUID workOrderId) {
        return workOrderRepository.findForUpdate(accessPolicy.currentTenantId(), workOrderId)
                .orElseThrow(() -> new EntityNotFoundException("工单不存在：" + workOrderId));
    }

    private WorkOrder findVisible(UUID workOrderId) {
        return workOrderRepository.findByTenantIdAndId(accessPolicy.currentTenantId(), workOrderId)
                .orElseThrow(() -> new EntityNotFoundException("工单不存在：" + workOrderId));
    }

    private void verifyVersion(WorkOrder workOrder, Long expectedVersion) {
        if (expectedVersion == null || !Objects.equals(workOrder.getVersion(), expectedVersion)) {
            throw conflict("工单版本已变化，请刷新后重试。");
        }
    }

    private void requireAnyStatus(WorkOrder workOrder, WorkOrderStatus... statuses) {
        if (Arrays.stream(statuses).noneMatch(status -> status == workOrder.lifecycleStatus())) {
            throw conflict("当前工单状态不允许执行该操作。");
        }
    }

    private void requireNoActiveAssignments(WorkOrder workOrder) {
        if (currentAssignment(workOrder) != null || pendingAssignment(workOrder) != null) {
            throw conflict("工单已被接取或正在等待目标人员接收。");
        }
    }

    private WorkOrderAssignment requirePendingAssignment(WorkOrder workOrder, UUID assignmentId) {
        WorkOrderAssignment pending = pendingAssignment(workOrder);
        if (pending == null || !Objects.equals(pending.getId(), assignmentId)) {
            throw conflict("待接收派单不存在或状态已变化。");
        }
        return pending;
    }

    private User requireEligibleAssignee(WorkOrder workOrder,
                                         UUID membershipId,
                                         UUID responsibleOrgId,
                                         UUID responsibleDepartmentId) {
        User candidate = userRepository.findByTenantIdAndId(workOrder.getTenantId(), membershipId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> unprocessable("目标处理人员不存在、已停用或不属于当前市公司。"));
        if (!isEligibleAssignee(workOrder, candidate, responsibleOrgId, responsibleDepartmentId)) {
            throw unprocessable("目标人员没有对应工单类型的处理权限或数据范围不覆盖责任组织。");
        }
        return candidate;
    }

    private OrgUnit requireActiveOrganization(UUID tenantId, UUID orgUnitId) {
        if (orgUnitId == null) {
            throw unprocessable("责任组织不能为空。");
        }
        return orgUnitRepository.findByTenantIdAndId(tenantId, orgUnitId)
                .filter(orgUnit -> orgUnit.getStatus() == TenantStatus.ACTIVE)
                .orElseThrow(() -> unprocessable("目标责任组织不存在或已停用。"));
    }

    private Set<UUID> assignmentTargetOrganizationIds(UUID tenantId, User operator) {
        if (operator.getSystemKey() == UserSystemKey.SUPER_ADMINISTRATOR) {
            return orgUnitRepository.findByTenantId(tenantId).stream()
                    .filter(orgUnit -> orgUnit.getStatus() == TenantStatus.ACTIVE)
                    .map(OrgUnit::getId)
                    .collect(Collectors.toUnmodifiableSet());
        }
        if (operator.getOrgUnitId() == null) {
            return Set.of();
        }
        Set<UUID> accessibleOrgUnitIds = hierarchyService.descendantIds(tenantId, operator.getOrgUnitId());
        return orgUnitRepository.findByTenantId(tenantId).stream()
                .filter(orgUnit -> orgUnit.getStatus() == TenantStatus.ACTIVE)
                .map(OrgUnit::getId)
                .filter(accessibleOrgUnitIds::contains)
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean isActiveOrganization(UUID tenantId, UUID orgUnitId) {
        return orgUnitId != null && orgUnitRepository.findByTenantIdAndId(tenantId, orgUnitId)
                .map(orgUnit -> orgUnit.getStatus() == TenantStatus.ACTIVE)
                .orElse(false);
    }

    private boolean isEligibleAssignee(WorkOrder workOrder,
                                       User candidate,
                                       UUID responsibleOrgId,
                                       UUID responsibleDepartmentId) {
        if (!Objects.equals(candidate.getTenantId(), workOrder.getTenantId()) || candidate.getStatus() != UserStatus.ACTIVE) {
            return false;
        }
        if (!isActiveOrganization(workOrder.getTenantId(), candidate.getOrgUnitId())
                || !isActiveOrganization(workOrder.getTenantId(), responsibleOrgId)) {
            return false;
        }
        String respondPermission = com.pro.ereportbacken.security.PermissionCatalog
                .requireWorkOrderEntry(workOrder.type()).responseSubmitPermission();
        return userAuthorizationCache.snapshot(candidate).authorities().contains(respondPermission)
                && hierarchyService.treeContains(
                workOrder.getTenantId(), candidate.getOrgUnitId(), responsibleOrgId)
                && departmentParticipationService.departmentContainsMember(
                workOrder.getTenantId(), responsibleDepartmentId, candidate);
    }

    private WorkOrderAssignment createPendingAssignment(WorkOrder workOrder,
                                                        User assignee,
                                                        User assignedBy,
                                                        UUID responsibleOrgId,
                                                        UUID responsibleDepartmentId,
                                                        AssignmentType type,
                                                        String reason) {
        WorkOrderAssignment assignment = newAssignment(
                workOrder,
                assignee,
                assignedBy,
                responsibleOrgId,
                responsibleDepartmentId,
                type,
                AssignmentState.PENDING_ACCEPTANCE,
                AssignmentSlot.PENDING,
                reason
        );
        return assignmentRepository.save(assignment);
    }

    private WorkOrderAssignment newAssignment(WorkOrder workOrder,
                                              User assignee,
                                              User assignedBy,
                                              UUID responsibleOrgId,
                                              UUID responsibleDepartmentId,
                                              AssignmentType type,
                                              AssignmentState state,
                                              AssignmentSlot slot,
                                              String reason) {
        WorkOrderAssignment assignment = new WorkOrderAssignment();
        assignment.setTenantId(workOrder.getTenantId());
        assignment.setWorkOrder(workOrder);
        assignment.setAssignee(assignee);
        assignment.setResponsibleOrgId(responsibleOrgId);
        assignment.setResponsibleDepartmentId(responsibleDepartmentId);
        assignment.setAssignedBy(assignedBy);
        assignment.setAssignmentType(type);
        assignment.setState(state);
        assignment.setActiveSlot(slot);
        assignment.setReason(trimToNull(reason));
        assignment.setAssignedAt(UtcTime.now());
        return assignment;
    }

    private UUID resolveResponsibleDepartmentId(WorkOrder workOrder,
                                                UUID responsibleOrgId,
                                                UUID requestedDepartmentId,
                                                User assignee) {
        UUID departmentId = requestedDepartmentId != null
                ? requestedDepartmentId
                : assignee.getDepartmentId();
        if (departmentId != null) {
            departmentParticipationService.requireActiveDepartment(
                    workOrder.getTenantId(), responsibleOrgId, departmentId);
        }
        return departmentId;
    }

    private void finishAssignment(WorkOrderAssignment assignment, AssignmentState state) {
        assignment.setState(state);
        assignment.setActiveSlot(null);
        assignment.setEndedAt(UtcTime.now());
        assignmentRepository.save(assignment);
    }

    private void revokePendingAssignment(WorkOrder workOrder) {
        if (workOrder.getPendingAssignment() == null) {
            return;
        }
        finishAssignment(workOrder.getPendingAssignment(), AssignmentState.REVOKED);
        workOrder.setPendingAssignment(null);
    }

    /**
     * 当前回单投影只表示“待验收”的那一轮回单。验收驳回或管理员直接调单后，
     * 回单历史仍保留在 work_order_responses，但工单不能继续携带已驳回的当前回单字段。
     */
    private void clearCurrentResponseProjection(WorkOrder workOrder) {
        workOrder.setCurrentResponse(null);
        workOrder.setResolutionDetails(null);
        workOrder.setResolutionMethod(null);
        workOrder.setResolutionTime(null);
        workOrder.setResolvedBy(null);
        workOrder.setReplacementCompleted(null);
        workOrder.setReplacementResult(null);
    }

    private boolean requiresReassignment(WorkOrder workOrder) {
        return currentAssignment(workOrder) != null
                || pendingAssignment(workOrder) != null
                || workOrder.lifecycleStatus() == WorkOrderStatus.PENDING_VERIFICATION
                || workOrder.lifecycleStatus() == WorkOrderStatus.ON_HOLD
                || (workOrder.lifecycleStatus() == WorkOrderStatus.IN_PROGRESS
                && workOrder.getReworkCount() != null
                && workOrder.getReworkCount() > 0);
    }

    private void requireReassignmentState(WorkOrder workOrder) {
        if (!requiresReassignment(workOrder)) {
            throw conflict("当前工单没有可调整的处理人，请使用派单操作。");
        }
    }

    private void requireCurrentAssignee(WorkOrder workOrder) {
        User operator = accessPolicy.currentUserOrThrow();
        WorkOrderAssignment current = currentAssignment(workOrder);
        if (current == null || !Objects.equals(current.getAssignee().getId(), operator.getId())) {
            throw new AccessDeniedException("只有当前处理人员可以执行此操作。");
        }
    }

    private WorkOrderAssignment currentAssignment(WorkOrder workOrder) {
        WorkOrderAssignment assignment = workOrder.getCurrentAssignment();
        return assignment != null && assignment.isCurrent() ? assignment : null;
    }

    private boolean hasCurrentAssignment(WorkOrder workOrder) {
        return currentAssignment(workOrder) != null;
    }

    private WorkOrderAssignment pendingAssignment(WorkOrder workOrder) {
        WorkOrderAssignment assignment = workOrder.getPendingAssignment();
        return assignment != null && assignment.isPending() ? assignment : null;
    }

    private boolean hasPendingAssignment(WorkOrder workOrder) {
        return pendingAssignment(workOrder) != null;
    }

    /**
     * 清理历史脏数据在工单快照上的失效指针。指针不是责任关系的事实来源，
     * 状态与 active_slot 才是；命令开始时统一归一化，避免历史记录阻断接单或错误暴露处理人。
     */
    private void normalizeAssignmentPointers(WorkOrder workOrder) {
        if (workOrder.getId() == null) {
            return;
        }
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

    /**
     * 查询候选人员时也必须以 assignment 表的活动槽位为事实来源，
     * 但 GET 查询不应为了修复历史数据产生写入副作用。
     */
    private void hydrateAssignmentPointersForRead(WorkOrder workOrder) {
        if (workOrder.getId() == null) {
            return;
        }
        WorkOrderAssignment current = null;
        WorkOrderAssignment pending = null;
        for (WorkOrderAssignment assignment : assignmentRepository.findActiveByTenantIdAndWorkOrderIdIn(
                workOrder.getTenantId(), List.of(workOrder.getId()))) {
            if (assignment.isCurrent()) {
                current = assignment;
            } else if (assignment.isPending()) {
                pending = assignment;
            }
        }
        workOrder.setCurrentAssignment(current);
        workOrder.setPendingAssignment(pending);
    }

    private void appendMaterialUsages(WorkOrder workOrder,
                                      WorkOrderResponse response,
                                      List<MaterialUsageDTO> materialUsageDTOs) {
        if (materialUsageDTOs == null) {
            return;
        }
        for (MaterialUsageDTO dto : materialUsageDTOs) {
            validateMaterialUsage(dto);
            WorkOrderMaterialUsage usage = new WorkOrderMaterialUsage();
            usage.setCategory(dto.getCategory());
            usage.setSpecification(dto.getSpecification());
            usage.setQuantity(dto.getQuantity());
            usage.setResponse(response);
            workOrder.addMaterialUsage(usage);
        }
    }

    private void validateMaterialUsage(MaterialUsageDTO dto) {
        if (dto == null || dto.getCategory() == null || !StringUtils.hasText(dto.getSpecification())
                || dto.getQuantity() == null || dto.getQuantity() < 1) {
            throw new IllegalArgumentException("物料明细的类别、规格和正数数量均不能为空。");
        }
        if (!dto.getCategory().supports(dto.getSpecification())) {
            throw new IllegalArgumentException("物料规格 " + dto.getSpecification() + " 不属于 " + dto.getCategory().getDisplayName() + "。");
        }
    }

    private void appendEvent(WorkOrder workOrder,
                             WorkOrderEventType eventType,
                             WorkOrderStatus previous,
                             WorkOrderStatus next,
                             User operator,
                             UUID assignmentId,
                             UUID responseId,
                             UUID verificationId,
                             String reason) {
        WorkOrderEvent event = new WorkOrderEvent();
        event.setTenantId(workOrder.getTenantId());
        event.setWorkOrder(workOrder);
        event.setEventType(eventType);
        event.setPreviousStatusCode(previous == null ? null : previous.getCode());
        event.setNextStatusCode(next == null ? null : next.getCode());
        event.setAssignmentId(assignmentId);
        event.setResponseId(responseId);
        event.setVerificationId(verificationId);
        event.setOperatorMembershipId(operator == null ? null : operator.getId());
        event.setOperatorNameSnapshot(operator == null ? "SYSTEM" : operator.getName());
        event.setSource(WorkOrderEventSource.WEB);
        event.setReason(trimToNull(reason));
        event.setOccurredAt(UtcTime.now());
        eventRepository.save(event);
    }

    private WorkOrderAssignmentDTO toAssignmentDTO(WorkOrderAssignment assignment) {
        if (assignment == null || (!assignment.isCurrent() && !assignment.isPending())) {
            return null;
        }
        User assignee = assignment.getAssignee();
        return new WorkOrderAssignmentDTO(
                assignment.getId(),
                assignment.getState().name(),
                assignment.getAssignmentType().name(),
                assignee.getId(),
                assignee.getUsername(),
                assignee.getName(),
                assignment.getResponsibleOrgId(),
                assignment.getResponsibleDepartmentId(),
                assignment.getReason(),
                assignment.getAssignedAt(),
                assignment.getAcceptedAt()
        );
    }

    public WorkOrderAssignmentDTO assignmentDTO(WorkOrderAssignment assignment) {
        return toAssignmentDTO(assignment);
    }

    private UserBasicInfoDTO toUserInfo(User user) {
        return user == null ? null : new UserBasicInfoDTO(user.getId(), user.getUsername(), user.getName());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    private ResponseStatusException unprocessable(String reason) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
    }
}
