package com.pro.ereportbacken.service;

import com.pro.ereportbacken.config.DataInitializer;
import com.pro.ereportbacken.model.dto.AssignWorkOrderRequestDTO;
import com.pro.ereportbacken.model.dto.AssignmentCandidateDTO;
import com.pro.ereportbacken.model.dto.AssignmentDecisionRequestDTO;
import com.pro.ereportbacken.model.dto.CreateWorkOrderRequestDTO;
import com.pro.ereportbacken.model.dto.FileTransferInitRequestDTO;
import com.pro.ereportbacken.model.dto.RespondWorkOrderRequestDTO;
import com.pro.ereportbacken.model.dto.ReasonedWorkOrderCommandDTO;
import com.pro.ereportbacken.model.dto.TenantCreateRequestDTO;
import com.pro.ereportbacken.model.dto.VerifyWorkOrderRequestDTO;
import com.pro.ereportbacken.model.dto.VersionedWorkOrderCommandDTO;
import com.pro.ereportbacken.model.dto.WorkOrderResponseDTO;
import com.pro.ereportbacken.model.dto.WorkOrderFilterDTO;
import com.pro.ereportbacken.model.pojo.AssignmentSlot;
import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.OrgUnit;
import com.pro.ereportbacken.model.pojo.OrgUnitType;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.model.pojo.VerificationDecision;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderStatus;
import com.pro.ereportbacken.model.pojo.WorkOrderSlaStage;
import com.pro.ereportbacken.model.pojo.WorkOrderPriority;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import com.pro.ereportbacken.model.pojo.WorkOrderDuty;
import com.pro.ereportbacken.model.pojo.WorkOrderHandlerState;
import com.pro.ereportbacken.model.pojo.PermissionGrantStatus;
import com.pro.ereportbacken.model.pojo.OutboxEvent;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.PermissionGrantRepository;
import com.pro.ereportbacken.repository.AuthorizationProfileRepository;
import com.pro.ereportbacken.repository.TenantGroupRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.repository.WorkOrderRepository;
import com.pro.ereportbacken.repository.WorkOrderQueryRepository;
import com.pro.ereportbacken.repository.WorkOrderAssignmentRepository;
import com.pro.ereportbacken.repository.OutboxEventRepository;
import com.pro.ereportbacken.repository.WorkOrderVerificationRepository;
import com.pro.ereportbacken.security.TenantUserDetails;
import com.pro.ereportbacken.security.BuiltInAuthorizationProfileCatalog;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.time.DisplayTime;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class WorkOrderLifecycleIntegrationTests {

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private WorkOrderQueryRepository workOrderQueryRepository;

    @Autowired
    private WorkOrderAssignmentRepository workOrderAssignmentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private WorkOrderVerificationRepository workOrderVerificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SystemGrantProvisioningService authorizationSystemProvisioningService;

    @Autowired
    private PermissionGrantRepository permissionGrantRepository;

    @Autowired
    private AuthorizationProfileRepository authorizationProfileRepository;

    @Autowired
    private OrgUnitRepository orgUnitRepository;

    @Autowired
    private OrganizationDirectoryCache organizationDirectoryCache;

    @Autowired
    private TenantGroupRepository tenantGroupRepository;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private DefaultTenantService defaultTenantService;

    @Autowired
    private TenantProvisioningService tenantProvisioningService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private AppConfigService appConfigService;

    @Autowired
    private FileTransferService fileTransferService;

    @Autowired
    private WebApplicationContext applicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void completesAssignmentReworkAndVerificationLifecycle() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        User dispatcher = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.DISPATCHER));
        User worker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        User verifier = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.VERIFIER));

        authenticate(reporter);
        WorkOrderResponseDTO created = workOrderService.createWorkOrder(createNetworkFaultRequest("完整流转"));
        assertThat(created.getStatusCode()).isEqualTo(WorkOrderStatus.SUBMITTED.getCode());
        assertThat(created.getSlaStage()).isEqualTo(WorkOrderSlaStage.CLAIM.name());
        assertThat(created.getSlaDueAt()).isNotNull();
        LocalDateTime reportTime = created.getReportTime();
        assertThat(reportTime).isNotNull();

        authenticate(dispatcher);
        AssignWorkOrderRequestDTO assign = new AssignWorkOrderRequestDTO();
        assign.setAssigneeMembershipId(worker.getId());
        assign.setResponsibleOrgId(rootOrgId);
        assign.setReason("按专业能力派单");
        assign.setExpectedVersion(created.getVersion());
        WorkOrderResponseDTO assigned = workOrderService.assignWorkOrder(created.getId(), assign);
        assertThat(assigned.getPendingAssignment()).isNotNull();
        assertThat(assigned.getHandler().getId()).isEqualTo(worker.getId());
        assertThat(assigned.getHandlerState()).isEqualTo(WorkOrderHandlerState.PENDING_ACCEPTANCE.name());
        assertThat(assigned.getSlaStage()).isEqualTo(WorkOrderSlaStage.ASSIGNMENT_ACCEPTANCE.name());
        assertThat(assigned.getReportTime()).isCloseTo(reportTime, within(1, ChronoUnit.MICROS));

        authenticate(worker);
        AssignmentDecisionRequestDTO acceptance = new AssignmentDecisionRequestDTO();
        acceptance.setExpectedVersion(assigned.getVersion());
        WorkOrderResponseDTO accepted = workOrderService.acceptAssignment(
                created.getId(), assigned.getPendingAssignment().id(), acceptance);
        assertThat(accepted.getStatusCode()).isEqualTo(WorkOrderStatus.IN_PROGRESS.getCode());
        assertThat(accepted.getHandler().getId()).isEqualTo(worker.getId());
        assertThat(accepted.getHandlerState()).isEqualTo(WorkOrderHandlerState.CURRENT.name());
        assertThat(accepted.getSlaStage()).isEqualTo(WorkOrderSlaStage.RESPONSE.name());
        assertThat(accepted.getReportTime()).isCloseTo(reportTime, within(1, ChronoUnit.MICROS));

        var listItem = workOrderService.searchVisibleWorkOrders(
                        new WorkOrderFilterDTO(),
                        PageRequest.of(0, 100)
                ).stream()
                .filter(item -> item.getId().equals(created.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(listItem.getHandler().getId()).isEqualTo(worker.getId());
        assertThat(listItem.getHandlerState()).isEqualTo(WorkOrderHandlerState.CURRENT.name());

        WorkOrderResponseDTO firstResponse = workOrderService.respondToWorkOrder(
                created.getId(), responseRequest(accepted.getVersion(), "第一次处理"));
        assertThat(firstResponse.getStatusCode()).isEqualTo(WorkOrderStatus.PENDING_VERIFICATION.getCode());
        assertThat(firstResponse.getHandler().getId()).isEqualTo(worker.getId());
        assertThat(firstResponse.getHandlerState()).isEqualTo(WorkOrderHandlerState.RESPONDED.name());
        assertThat(firstResponse.getSlaStage()).isEqualTo(WorkOrderSlaStage.VERIFICATION.name());
        assertThat(firstResponse.getReportTime()).isCloseTo(reportTime, within(1, ChronoUnit.MICROS));

        authenticate(verifier);
        VerifyWorkOrderRequestDTO reject = verificationRequest(
                firstResponse,
                VerificationDecision.REJECTED,
                "现场照片不完整，请补充后重新回单"
        );
        WorkOrderResponseDTO rejected = workOrderService.verifyWorkOrder(created.getId(), reject);
        assertThat(rejected.getStatusCode()).isEqualTo(WorkOrderStatus.IN_PROGRESS.getCode());
        assertThat(rejected.getReworkCount()).isEqualTo(1);
        assertThat(rejected.getCurrentAssignment()).isNotNull();
        assertThat(rejected.getSlaStage()).isEqualTo(WorkOrderSlaStage.REWORK.name());
        assertThat(rejected.getReportTime()).isCloseTo(reportTime, within(1, ChronoUnit.MICROS));

        authenticate(worker);
        WorkOrderResponseDTO secondResponse = workOrderService.respondToWorkOrder(
                created.getId(), responseRequest(rejected.getVersion(), "补充照片后再次处理"));

        authenticate(verifier);
        VerifyWorkOrderRequestDTO approve = verificationRequest(
                secondResponse,
                VerificationDecision.APPROVED,
                "验收通过"
        );
        WorkOrderResponseDTO closed = workOrderService.verifyWorkOrder(created.getId(), approve);
        assertThat(closed.getStatusCode()).isEqualTo(WorkOrderStatus.CLOSED.getCode());
        assertThat(closed.getAllowedActions()).isEmpty();
        assertThat(closed.getSlaStage()).isEqualTo(WorkOrderSlaStage.COMPLETED.name());
        assertThat(closed.getSlaDueAt()).isNull();
        assertThat(closed.getReportTime()).isCloseTo(reportTime, within(1, ChronoUnit.MICROS));

        assertThat(workOrderService.workOrderResponses(created.getId()))
                .extracting(history -> history.verificationDecision())
                .containsExactly(VerificationDecision.REJECTED.name(), VerificationDecision.APPROVED.name());
        assertThat(workOrderService.workOrderResponses(created.getId()))
                .extracting(history -> history.verificationDecisionLabel())
                .containsExactly("验收驳回", "验收通过");
        var events = workOrderService.workOrderEvents(created.getId(), org.springframework.data.domain.PageRequest.of(0, 30));
        assertThat(events.getTotalElements()).isGreaterThanOrEqualTo(7);
        assertThat(events.getContent())
                .extracting(event -> event.eventTypeLabel())
                .contains("验收通过", "验收驳回", "已提交回单", "工单已提交");
    }

    @Test
    void reassignsImmediatelyAndReleasesPreviousAssignmentSlots() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        User dispatcher = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.DISPATCHER));
        User firstWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        User secondWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));

        authenticate(reporter);
        WorkOrderResponseDTO created = workOrderService.createWorkOrder(createNetworkFaultRequest("即时调单"));

        authenticate(dispatcher);
        AssignWorkOrderRequestDTO firstAssignment = assignmentRequest(firstWorker, rootOrgId, created.getVersion(), "首次派单");
        WorkOrderResponseDTO pending = workOrderService.assignWorkOrder(created.getId(), firstAssignment);
        assertThat(pending.getPendingAssignment()).isNotNull();

        AssignWorkOrderRequestDTO replacePending = assignmentRequest(secondWorker, rootOrgId, pending.getVersion(), "调整待接收人员");
        WorkOrderResponseDTO firstReassignment = workOrderService.reassignWorkOrder(created.getId(), replacePending);
        assertThat(firstReassignment.getPendingAssignment()).isNull();
        assertThat(firstReassignment.getCurrentAssignment()).isNotNull();
        assertThat(firstReassignment.getCurrentAssignment().membershipId()).isEqualTo(secondWorker.getId());
        assertThat(firstReassignment.getStatusCode()).isEqualTo(WorkOrderStatus.IN_PROGRESS.getCode());

        AssignWorkOrderRequestDTO replaceCurrent = assignmentRequest(firstWorker, rootOrgId, firstReassignment.getVersion(), "调整当前处理人员");
        WorkOrderResponseDTO secondReassignment = workOrderService.reassignWorkOrder(created.getId(), replaceCurrent);
        assertThat(secondReassignment.getPendingAssignment()).isNull();
        assertThat(secondReassignment.getCurrentAssignment().membershipId()).isEqualTo(firstWorker.getId());
        assertThat(workOrderAssignmentRepository.findByTenantIdAndWorkOrderIdOrderByAssignedAtAsc(tenantId, created.getId()))
                .filteredOn(assignment -> assignment.getActiveSlot() == AssignmentSlot.CURRENT)
                .hasSize(1);
        List<OutboxEvent> reassignmentNotices = outboxEventRepository.findAll().stream()
                .filter(event -> created.getId().equals(event.getAggregateId()))
                .filter(event -> event.getEventType().equals("WORK_ORDER_ASSIGNMENT_RECEIVED:email"))
                .toList();
        assertThat(reassignmentNotices).hasSize(3);
        assertThat(reassignmentNotices)
                .extracting(OutboxEvent::getPayload)
                .anySatisfy(payload -> assertThat(payload).contains(pending.getPendingAssignment().id().toString()))
                .anySatisfy(payload -> assertThat(payload).contains(firstReassignment.getCurrentAssignment().id().toString()))
                .anySatisfy(payload -> assertThat(payload).contains(secondReassignment.getCurrentAssignment().id().toString()));

        assertThat(workOrderService.assignmentCandidates(created.getId(), "")).isEmpty();
        assertThat(workOrderService.assignmentCandidates(created.getId(), firstWorker.getUsername())).isEmpty();
        assertThat(workOrderService.assignmentCandidates(created.getId(), secondWorker.getUsername()))
                .extracting(candidate -> candidate.membershipId())
                .containsExactly(secondWorker.getId());
    }

    @Test
    void rejectsDirectReassignmentOfUnassignedSubmittedWorkOrder() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(tenantId, rootOrgId,
                roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        User reassignmentOperator = createUser(tenantId, rootOrgId,
                roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.CONTROLLER));
        User worker = createUser(tenantId, rootOrgId,
                roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));

        authenticate(reporter);
        WorkOrderResponseDTO created = workOrderService.createWorkOrder(createNetworkFaultRequest("禁止空单调单"));

        authenticate(reassignmentOperator);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions())
                .doesNotContain("REASSIGN");
        assertThatThrownBy(() -> workOrderService.reassignWorkOrder(
                created.getId(), assignmentRequest(worker, rootOrgId, created.getVersion(), "空单调单")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("没有可调整的处理人");
    }

    @Test
    void candidateLookupUsesActiveAssignmentWhenSnapshotPointerIsMissing() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(tenantId, rootOrgId,
                roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        User dispatcher = createUser(tenantId, rootOrgId,
                roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.DISPATCHER));
        User currentWorker = createUser(tenantId, rootOrgId,
                roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        User targetWorker = createUser(tenantId, rootOrgId,
                roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));

        authenticate(reporter);
        WorkOrderResponseDTO created = workOrderService.createWorkOrder(createNetworkFaultRequest("活动处理人指针回填"));
        authenticate(currentWorker);
        WorkOrderResponseDTO claimed = workOrderService.claimWorkOrder(
                created.getId(), versionCommand(created.getVersion()));

        WorkOrder staleSnapshot = workOrderRepository.findByTenantIdAndId(tenantId, claimed.getId()).orElseThrow();
        staleSnapshot.setCurrentAssignment(null);
        workOrderRepository.saveAndFlush(staleSnapshot);

        authenticate(dispatcher);
        assertThat(workOrderService.assignmentCandidates(created.getId(), targetWorker.getUsername()))
                .extracting(AssignmentCandidateDTO::membershipId)
                .containsExactly(targetWorker.getId());
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions())
                .containsExactly("REASSIGN");
    }

    @Test
    void allowsAdministratorToVerifyOwnResponseWithoutSpecialInput() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        User administrator = createUser(
                tenantId,
                rootOrgId,
                Set.of(
                        roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER),
                        roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.VERIFIER),
                        roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.CONTROLLER)
                ));

        authenticate(reporter);
        WorkOrderResponseDTO created = workOrderService.createWorkOrder(createNetworkFaultRequest("自验约束"));

        authenticate(administrator);
        VersionedWorkOrderCommandDTO claim = new VersionedWorkOrderCommandDTO();
        claim.setExpectedVersion(created.getVersion());
        WorkOrderResponseDTO claimed = workOrderService.claimWorkOrder(created.getId(), claim);
        WorkOrderResponseDTO response = workOrderService.respondToWorkOrder(
                created.getId(), responseRequest(claimed.getVersion(), "管理员紧急处理"));
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .contains(
                        PermissionCatalog.requireWorkOrderEntry(WorkOrderType.NETWORK_FAULT)
                                .verificationApprovePermission(),
                        PermissionCatalog.requireWorkOrderEntry(WorkOrderType.NETWORK_FAULT)
                                .verificationSelfApprovePermission());
        assertThat(response.getAllowedActions()).contains("VERIFY_APPROVE");

        VerifyWorkOrderRequestDTO verification = verificationRequest(
                response,
                VerificationDecision.APPROVED,
                "紧急验收"
        );
        WorkOrderResponseDTO closed = workOrderService.verifyWorkOrder(created.getId(), verification);
        assertThat(closed.getStatusCode()).isEqualTo(WorkOrderStatus.CLOSED.getCode());
        assertThat(workOrderVerificationRepository.findByTenantIdAndResponseId(tenantId, response.getCurrentResponseId()))
                .hasValueSatisfying(savedVerification -> {
                    assertThat(savedVerification.getVerificationOverride()).isTrue();
                    assertThat(savedVerification.getOverrideReason()).isNull();
                });
    }

    @Test
    void rejectsPendingAssignmentAndAllowsAnotherWorkerToAccept() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        User dispatcher = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.DISPATCHER));
        User firstWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        User secondWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        User observingWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));

        authenticate(reporter);
        WorkOrderResponseDTO created = workOrderService.createWorkOrder(createNetworkFaultRequest("拒绝后重派"));
        authenticate(dispatcher);
        WorkOrderResponseDTO pending = workOrderService.assignWorkOrder(
                created.getId(), assignmentRequest(firstWorker, rootOrgId, created.getVersion(), "首次派单"));
        assertThat(pending.getSlaStage()).isEqualTo(WorkOrderSlaStage.ASSIGNMENT_ACCEPTANCE.name());

        authenticate(secondWorker);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).isEmpty();
        assertThatThrownBy(() -> workOrderService.acceptAssignment(
                created.getId(), pending.getPendingAssignment().id(), assignmentDecision(pending.getVersion(), null)))
                .isInstanceOf(AccessDeniedException.class);

        authenticate(firstWorker);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions())
                .containsExactlyInAnyOrder("ACCEPT_ASSIGNMENT", "REJECT_ASSIGNMENT");
        assertThatThrownBy(() -> workOrderService.rejectAssignment(
                created.getId(), pending.getPendingAssignment().id(), assignmentDecision(pending.getVersion(), " ")))
                .isInstanceOf(IllegalArgumentException.class);
        WorkOrderResponseDTO rejected = workOrderService.rejectAssignment(
                created.getId(), pending.getPendingAssignment().id(), assignmentDecision(pending.getVersion(), "现场任务冲突"));
        assertThat(rejected.getStatusCode()).isEqualTo(WorkOrderStatus.SUBMITTED.getCode());
        assertThat(rejected.getPendingAssignment()).isNull();
        assertThat(rejected.getSlaStage()).isEqualTo(WorkOrderSlaStage.CLAIM.name());
        assertThat(rejected.getSlaDueAt())
                .isCloseTo(created.getSlaDueAt(), within(1, ChronoUnit.MICROS));

        assertThatThrownBy(() -> workOrderService.acceptAssignment(
                created.getId(), pending.getPendingAssignment().id(), assignmentDecision(rejected.getVersion(), null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        authenticate(dispatcher);
        WorkOrderResponseDTO reassigned = workOrderService.assignWorkOrder(
                created.getId(), assignmentRequest(secondWorker, rootOrgId, rejected.getVersion(), "改派其他人员"));
        authenticate(secondWorker);
        WorkOrderResponseDTO accepted = workOrderService.acceptAssignment(
                created.getId(), reassigned.getPendingAssignment().id(), assignmentDecision(reassigned.getVersion(), null));
        assertThat(accepted.getCurrentAssignment().membershipId()).isEqualTo(secondWorker.getId());
        assertThat(accepted.getPendingAssignment()).isNull();
        assertThat(accepted.getStatusCode()).isEqualTo(WorkOrderStatus.IN_PROGRESS.getCode());

        authenticate(observingWorker);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).isEmpty();
        assertThat(workOrderService.searchVisibleWorkOrders(
                new com.pro.ereportbacken.model.dto.WorkOrderFilterDTO(),
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent())
                .extracting(com.pro.ereportbacken.model.dto.WorkOrderListItemDTO::getId)
                .contains(created.getId());
    }

    @Test
    void keepsHoldStateAcrossReassignmentAndOnlyNewWorkerCanResume() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        User dispatcher = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.DISPATCHER));
        User firstWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        User secondWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));

        authenticate(reporter);
        WorkOrderResponseDTO created = workOrderService.createWorkOrder(createNetworkFaultRequest("挂起调单恢复"));
        authenticate(firstWorker);
        WorkOrderResponseDTO claimed = workOrderService.claimWorkOrder(created.getId(), versionCommand(created.getVersion()));
        WorkOrderResponseDTO held = workOrderService.holdWorkOrder(
                created.getId(), reasonCommand(claimed.getVersion(), "等待现场具备施工条件"));
        assertThat(held.getStatusCode()).isEqualTo(WorkOrderStatus.ON_HOLD.getCode());
        assertThat(held.getSlaPaused()).isTrue();
        assertThat(held.getAllowedActions()).containsExactly("RESUME");
        assertThatThrownBy(() -> workOrderService.respondToWorkOrder(
                created.getId(), responseRequest(held.getVersion(), "挂起期间错误回单")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        authenticate(dispatcher);
        WorkOrderResponseDTO reassigned = workOrderService.reassignWorkOrder(
                created.getId(), assignmentRequest(secondWorker, rootOrgId, held.getVersion(), "转交可到场人员"));
        assertThat(reassigned.getStatusCode()).isEqualTo(WorkOrderStatus.ON_HOLD.getCode());
        assertThat(reassigned.getSlaPaused()).isTrue();
        assertThat(reassigned.getCurrentAssignment().membershipId()).isEqualTo(secondWorker.getId());
        assertThat(reassigned.getPendingAssignment()).isNull();

        authenticate(firstWorker);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).isEmpty();
        assertThatThrownBy(() -> workOrderService.resumeWorkOrder(
                created.getId(), versionCommand(reassigned.getVersion())))
                .isInstanceOf(AccessDeniedException.class);

        authenticate(secondWorker);
        WorkOrderResponseDTO resumed = workOrderService.resumeWorkOrder(
                created.getId(), versionCommand(reassigned.getVersion()));
        assertThat(resumed.getStatusCode()).isEqualTo(WorkOrderStatus.IN_PROGRESS.getCode());
        assertThat(resumed.getSlaPaused()).isFalse();
        assertThat(resumed.getAllowedActions()).containsExactlyInAnyOrder("RESPOND", "HOLD");
        WorkOrderResponseDTO responded = workOrderService.respondToWorkOrder(
                created.getId(), responseRequest(resumed.getVersion(), "恢复后完成处理"));

        authenticate(reporter);
        WorkOrderResponseDTO closed = workOrderService.verifyWorkOrder(
                created.getId(), verificationRequest(responded, VerificationDecision.APPROVED, "验收通过"));
        assertThat(closed.getStatusCode()).isEqualTo(WorkOrderStatus.CLOSED.getCode());
    }

    @Test
    void exposesOnlyActionsOwnedByEachRoleAtEveryLifecycleStage() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        User worker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        User dispatcher = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.DISPATCHER));
        User verifier = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.VERIFIER));
        User controller = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.CONTROLLER));
        User auditor = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.AUDITOR));

        authenticate(reporter);
        WorkOrderResponseDTO created = workOrderService.createWorkOrder(createNetworkFaultRequest("角色动作矩阵"));
        assertThat(created.getAllowedActions()).containsExactly("WITHDRAW");
        authenticate(worker);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).containsExactly("CLAIM");
        authenticate(dispatcher);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).containsExactly("ASSIGN");
        authenticate(verifier);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).isEmpty();
        authenticate(controller);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).containsExactly("CANCEL");
        authenticate(auditor);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).isEmpty();
        assertThatThrownBy(() -> workOrderService.claimWorkOrder(created.getId(), versionCommand(created.getVersion())))
                .isInstanceOf(AccessDeniedException.class);

        authenticate(dispatcher);
        WorkOrderResponseDTO pending = workOrderService.assignWorkOrder(
                created.getId(), assignmentRequest(worker, rootOrgId, created.getVersion(), "角色矩阵派单"));
        assertThat(pending.getAllowedActions()).containsExactly("REASSIGN");
        authenticate(reporter);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).containsExactly("WITHDRAW");
        authenticate(worker);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions())
                .containsExactlyInAnyOrder("ACCEPT_ASSIGNMENT", "REJECT_ASSIGNMENT");
        WorkOrderResponseDTO accepted = workOrderService.acceptAssignment(
                created.getId(), pending.getPendingAssignment().id(), assignmentDecision(pending.getVersion(), null));
        assertThat(accepted.getAllowedActions()).containsExactlyInAnyOrder("RESPOND", "HOLD");

        authenticate(dispatcher);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).containsExactly("REASSIGN");
        authenticate(controller);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).containsExactly("CANCEL");
        authenticate(verifier);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).isEmpty();

        authenticate(worker);
        WorkOrderResponseDTO responded = workOrderService.respondToWorkOrder(
                created.getId(), responseRequest(accepted.getVersion(), "角色矩阵回单"));
        assertThat(responded.getAllowedActions()).isEmpty();
        authenticate(dispatcher);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).containsExactly("REASSIGN");
        authenticate(controller);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).containsExactly("CANCEL");
        authenticate(reporter);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions())
                .containsExactlyInAnyOrder("VERIFY_APPROVE", "VERIFY_REJECT");
        authenticate(verifier);
        WorkOrderResponseDTO verifierView = workOrderService.getWorkOrderById(created.getId());
        assertThat(verifierView.getAllowedActions()).containsExactlyInAnyOrder("VERIFY_APPROVE", "VERIFY_REJECT");
        assertThatThrownBy(() -> workOrderService.verifyWorkOrder(
                created.getId(), verificationRequest(verifierView, VerificationDecision.REJECTED, " ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workOrderService.verifyWorkOrder(
                created.getId(), verificationRequest(verifierView, VerificationDecision.APPROVED, " ")))
                .isInstanceOf(IllegalArgumentException.class);
        WorkOrderResponseDTO closed = workOrderService.verifyWorkOrder(
                created.getId(), verificationRequest(verifierView, VerificationDecision.APPROVED, "角色矩阵验收"));
        assertThat(closed.getAllowedActions()).isEmpty();

        authenticate(controller);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).isEmpty();
        assertThatThrownBy(() -> workOrderService.cancelWorkOrder(
                created.getId(), reasonCommand(closed.getVersion(), "重复终态操作")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void authorizedDispatcherCanReassignAfterResponseAndRestartProcessing() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        User dispatcher = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.DISPATCHER));
        User firstWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        User secondWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));

        authenticate(reporter);
        WorkOrderResponseDTO created = workOrderService.createWorkOrder(createNetworkFaultRequest("回单后管理员调单"));
        authenticate(firstWorker);
        WorkOrderResponseDTO claimed = workOrderService.claimWorkOrder(
                created.getId(), versionCommand(created.getVersion()));
        WorkOrderResponseDTO responded = workOrderService.respondToWorkOrder(
                created.getId(), responseRequest(claimed.getVersion(), "第一处理人已回单"));
        assertThat(responded.getStatusCode()).isEqualTo(WorkOrderStatus.PENDING_VERIFICATION.getCode());
        assertThat(responded.getCurrentResponseId()).isNotNull();

        authenticate(firstWorker);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).isEmpty();
        assertThatThrownBy(() -> workOrderService.reassignWorkOrder(
                created.getId(), assignmentRequest(secondWorker, rootOrgId, responded.getVersion(), "无权调单")))
                .isInstanceOf(AccessDeniedException.class);

        authenticate(dispatcher);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).containsExactly("REASSIGN");
        assertThat(workOrderService.assignmentCandidates(created.getId(), secondWorker.getUsername()))
                .extracting(candidate -> candidate.membershipId())
                .containsExactly(secondWorker.getId());
        WorkOrderResponseDTO reassigned = workOrderService.reassignWorkOrder(
                created.getId(), assignmentRequest(secondWorker, rootOrgId, responded.getVersion(), "回单后调整处理人员"));

        assertThat(reassigned.getStatusCode()).isEqualTo(WorkOrderStatus.IN_PROGRESS.getCode());
        assertThat(reassigned.getCurrentAssignment().membershipId()).isEqualTo(secondWorker.getId());
        assertThat(reassigned.getCurrentResponseId()).isNull();
        assertThat(reassigned.getResolutionDetails()).isNull();
        assertThat(workOrderService.workOrderResponses(created.getId())).hasSize(1);

        authenticate(firstWorker);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).isEmpty();
        authenticate(secondWorker);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions())
                .containsExactlyInAnyOrder("RESPOND", "HOLD");
        WorkOrderResponseDTO secondResponse = workOrderService.respondToWorkOrder(
                created.getId(), responseRequest(reassigned.getVersion(), "第二处理人重新回单"));
        assertThat(secondResponse.getStatusCode()).isEqualTo(WorkOrderStatus.PENDING_VERIFICATION.getCode());
        assertThat(workOrderService.workOrderResponses(created.getId())).hasSize(2);
    }

    @Test
    void authorizedDispatcherCanReassignAfterVerificationRejection() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        User dispatcher = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.DISPATCHER));
        User firstWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        User secondWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        User verifier = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.VERIFIER));

        authenticate(reporter);
        WorkOrderResponseDTO created = workOrderService.createWorkOrder(createNetworkFaultRequest("驳回后管理员调单"));
        authenticate(firstWorker);
        WorkOrderResponseDTO claimed = workOrderService.claimWorkOrder(
                created.getId(), versionCommand(created.getVersion()));
        WorkOrderResponseDTO responded = workOrderService.respondToWorkOrder(
                created.getId(), responseRequest(claimed.getVersion(), "第一处理人已回单"));
        authenticate(verifier);
        WorkOrderResponseDTO rejected = workOrderService.verifyWorkOrder(
                created.getId(), verificationRequest(responded, VerificationDecision.REJECTED, "请更换处理人员"));
        assertThat(rejected.getStatusCode()).isEqualTo(WorkOrderStatus.IN_PROGRESS.getCode());
        assertThat(rejected.getCurrentAssignment().membershipId()).isEqualTo(firstWorker.getId());
        assertThat(rejected.getCurrentResponseId()).isNull();
        assertThat(rejected.getResolutionDetails()).isNull();

        authenticate(dispatcher);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).containsExactly("REASSIGN");
        WorkOrderResponseDTO reassigned = workOrderService.reassignWorkOrder(
                created.getId(), assignmentRequest(secondWorker, rootOrgId, rejected.getVersion(), "驳回后调整处理人员"));

        assertThat(reassigned.getStatusCode()).isEqualTo(WorkOrderStatus.IN_PROGRESS.getCode());
        assertThat(reassigned.getCurrentAssignment().membershipId()).isEqualTo(secondWorker.getId());
        assertThat(reassigned.getCurrentResponseId()).isNull();
        assertThat(reassigned.getSlaStage()).isEqualTo(WorkOrderSlaStage.REWORK.name());
    }

    @Test
    void rejectedResponseWithoutEligiblePreviousWorkerUsesReassignPermissionForCandidates() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        User dispatcher = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.DISPATCHER));
        User firstWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        User secondWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        User verifier = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.VERIFIER));

        authenticate(reporter);
        WorkOrderResponseDTO created = workOrderService.createWorkOrder(createNetworkFaultRequest("返工无原处理人调单"));
        authenticate(firstWorker);
        WorkOrderResponseDTO claimed = workOrderService.claimWorkOrder(
                created.getId(), versionCommand(created.getVersion()));
        WorkOrderResponseDTO responded = workOrderService.respondToWorkOrder(
                created.getId(), responseRequest(claimed.getVersion(), "原处理人已回单"));

        firstWorker.setStatus(UserStatus.INACTIVE);
        userRepository.saveAndFlush(firstWorker);
        authenticate(verifier);
        WorkOrderResponseDTO rejected = workOrderService.verifyWorkOrder(
                created.getId(), verificationRequest(responded, VerificationDecision.REJECTED, "原处理人已停用，请重新派单"));
        assertThat(rejected.getStatusCode()).isEqualTo(WorkOrderStatus.IN_PROGRESS.getCode());
        assertThat(rejected.getCurrentAssignment()).isNull();
        assertThat(rejected.getReworkCount()).isEqualTo(1);

        authenticate(dispatcher);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedActions()).containsExactly("REASSIGN");
        assertThatThrownBy(() -> workOrderService.assignWorkOrder(
                created.getId(), assignmentRequest(secondWorker, rootOrgId, rejected.getVersion(), "返工阶段误用派单")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("返工阶段");
        assertThat(workOrderService.assignmentCandidates(created.getId(), secondWorker.getUsername()))
                .extracting(candidate -> candidate.membershipId())
                .containsExactly(secondWorker.getId());
        WorkOrderResponseDTO reassigned = workOrderService.reassignWorkOrder(
                created.getId(), assignmentRequest(secondWorker, rootOrgId, rejected.getVersion(), "返工阶段重新派单"));
        assertThat(reassigned.getCurrentAssignment().membershipId()).isEqualTo(secondWorker.getId());
        assertThat(reassigned.getSlaStage()).isEqualTo(WorkOrderSlaStage.REWORK.name());
    }

    @Test
    void preservesScopedHistoryForReporterAndFormerHandlerWhileMaskingAuditIdentity() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(tenantId, rootOrgId,
                roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        User dispatcher = createUser(tenantId, rootOrgId,
                roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.DISPATCHER));
        User firstWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        User secondWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        User auditor = createUser(tenantId, rootOrgId,
                roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.AUDITOR));
        User expiredAuditor = createUser(tenantId, rootOrgId,
                roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.AUDITOR));
        permissionGrantRepository.findByTenantIdAndMembershipIdAndStatus(
                        tenantId, expiredAuditor.getId(), PermissionGrantStatus.ACTIVE)
                .forEach(grant -> {
                    grant.setExpiresAt(LocalDateTime.now().minusMinutes(1));
                    permissionGrantRepository.save(grant);
                });
        permissionGrantRepository.flush();

        authenticate(reporter);
        WorkOrderResponseDTO created = workOrderService.createWorkOrder(createNetworkFaultRequest("历史查看边界"));

        authenticate(dispatcher);
        WorkOrderResponseDTO pending = workOrderService.assignWorkOrder(
                created.getId(), assignmentRequest(firstWorker, rootOrgId, created.getVersion(), "先派第一处理人"));
        authenticate(firstWorker);
        WorkOrderResponseDTO accepted = workOrderService.acceptAssignment(
                created.getId(), pending.getPendingAssignment().id(), assignmentDecision(pending.getVersion(), null));

        authenticate(dispatcher);
        WorkOrderResponseDTO reassigned = workOrderService.reassignWorkOrder(
                created.getId(), assignmentRequest(secondWorker, rootOrgId, accepted.getVersion(), "现场任务调整"));
        authenticate(secondWorker);
        WorkOrderResponseDTO responded = workOrderService.respondToWorkOrder(
                created.getId(), responseRequest(reassigned.getVersion(), "第二处理人完成回单"));
        authenticate(reporter);
        WorkOrderResponseDTO closed = workOrderService.verifyWorkOrder(
                created.getId(), verificationRequest(responded, VerificationDecision.APPROVED, "提单人确认闭环"));
        assertThat(closed.getStatusCode()).isEqualTo(WorkOrderStatus.CLOSED.getCode());

        WorkOrderResponseDTO reporterDetail = workOrderService.getWorkOrderById(created.getId());
        assertThat(reporterDetail.getAllowedViews())
                .containsExactlyInAnyOrder("DETAIL", "HISTORY", "ATTACHMENTS")
                .doesNotContain("AUDIT");
        assertThat(workOrderService.workOrderResponses(created.getId())).hasSize(1);
        assertThat(workOrderService.workOrderEvents(
                created.getId(), org.springframework.data.domain.PageRequest.of(0, 30)).getContent())
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.operatorMembershipId()).isNull());

        authenticate(firstWorker);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedViews())
                .containsExactlyInAnyOrder("DETAIL", "HISTORY", "ATTACHMENTS")
                .doesNotContain("AUDIT");
        assertThat(workOrderService.workOrderEvents(
                created.getId(), org.springframework.data.domain.PageRequest.of(0, 30)).getTotalElements())
                .isGreaterThan(0);

        authenticate(auditor);
        assertThat(workOrderService.getWorkOrderById(created.getId()).getAllowedViews())
                .containsExactlyInAnyOrder("DETAIL", "HISTORY", "ATTACHMENTS", "AUDIT");
        assertThat(workOrderService.workOrderEvents(
                created.getId(), org.springframework.data.domain.PageRequest.of(0, 30)).getContent())
                .anySatisfy(event -> assertThat(event.operatorMembershipId()).isNotNull());

        authenticate(expiredAuditor);
        assertThatThrownBy(() -> workOrderService.getWorkOrderById(created.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(workOrderService.searchVisibleWorkOrders(
                new com.pro.ereportbacken.model.dto.WorkOrderFilterDTO(),
                org.springframework.data.domain.PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    void hidesAndRejectsOrdinarySelfVerificationForCombinedRoles() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User combinedUser = createUser(
                tenantId,
                rootOrgId,
                Set.of(roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER),
                        roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER)));
        User verifier = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.VERIFIER));

        authenticate(combinedUser);
        WorkOrderResponseDTO created = workOrderService.createWorkOrder(createNetworkFaultRequest("普通用户禁止自验"));
        WorkOrderResponseDTO claimed = workOrderService.claimWorkOrder(created.getId(), versionCommand(created.getVersion()));
        WorkOrderResponseDTO responded = workOrderService.respondToWorkOrder(
                created.getId(), responseRequest(claimed.getVersion(), "本人处理回单"));
        assertThat(responded.getAllowedActions()).isEmpty();
        assertThatThrownBy(() -> workOrderService.verifyWorkOrder(
                created.getId(), verificationRequest(responded, VerificationDecision.APPROVED, "本人自验")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        authenticate(verifier);
        WorkOrderResponseDTO verifierView = workOrderService.getWorkOrderById(created.getId());
        assertThat(verifierView.getAllowedActions()).containsExactlyInAnyOrder("VERIFY_APPROVE", "VERIFY_REJECT");
    }

    @Test
    void cancellationRevokesActiveSlotsAndBlocksFurtherLifecycleChanges() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        User dispatcher = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.DISPATCHER));
        User worker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        User controller = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.CONTROLLER));

        authenticate(reporter);
        WorkOrderResponseDTO pendingCancellation = workOrderService.createWorkOrder(createNetworkFaultRequest("待接收取消"));
        authenticate(dispatcher);
        WorkOrderResponseDTO assigned = workOrderService.assignWorkOrder(
                pendingCancellation.getId(), assignmentRequest(worker, rootOrgId, pendingCancellation.getVersion(), "取消前派单"));
        authenticate(reporter);
        WorkOrderResponseDTO cancelledByReporter = workOrderService.cancelWorkOrder(
                pendingCancellation.getId(), reasonCommand(assigned.getVersion(), "现场已自行恢复"));
        assertThat(cancelledByReporter.getStatusCode()).isEqualTo(WorkOrderStatus.CANCELLED.getCode());
        assertThat(cancelledByReporter.getCurrentAssignment()).isNull();
        assertThat(cancelledByReporter.getPendingAssignment()).isNull();
        assertThat(cancelledByReporter.getAllowedActions()).isEmpty();
        authenticate(worker);
        assertThatThrownBy(() -> workOrderService.acceptAssignment(
                pendingCancellation.getId(), assigned.getPendingAssignment().id(), assignmentDecision(cancelledByReporter.getVersion(), null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        authenticate(reporter);
        WorkOrderResponseDTO activeCancellation = workOrderService.createWorkOrder(createNetworkFaultRequest("处理中取消"));
        authenticate(worker);
        WorkOrderResponseDTO claimed = workOrderService.claimWorkOrder(
                activeCancellation.getId(), versionCommand(activeCancellation.getVersion()));
        authenticate(controller);
        WorkOrderResponseDTO cancelledByController = workOrderService.cancelWorkOrder(
                activeCancellation.getId(), reasonCommand(claimed.getVersion(), "风险管控终止"));
        assertThat(cancelledByController.getStatusCode()).isEqualTo(WorkOrderStatus.CANCELLED.getCode());
        assertThat(cancelledByController.getCurrentAssignment()).isNull();
        authenticate(worker);
        assertThatThrownBy(() -> workOrderService.respondToWorkOrder(
                activeCancellation.getId(), responseRequest(cancelledByController.getVersion(), "取消后错误回单")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void cancellationHttpRouteDelegatesReporterWithdrawalToDomainAuthorization() throws Exception {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        User worker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));

        authenticate(reporter);
        WorkOrderResponseDTO submitted = workOrderService.createWorkOrder(createNetworkFaultRequest("HTTP撤回授权"));
        TenantUserDetails reporterDetails = userDetailsService.loadForToken(tenantId, reporter.getId(), reporter.getUsername());
        UsernamePasswordAuthenticationToken reporterAuthentication = new UsernamePasswordAuthenticationToken(
                reporterDetails,
                null,
                reporterDetails.getAuthorities()
        );

        mockMvc.perform(post("/work-orders/{id}/cancellations", submitted.getId())
                        .with(authentication(reporterAuthentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + submitted.getVersion() + ",\"reason\":\"误报撤回\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(WorkOrderStatus.CANCELLED.getCode()));

        authenticate(reporter);
        WorkOrderResponseDTO activeOrder = workOrderService.createWorkOrder(createNetworkFaultRequest("HTTP处理中取消限制"));
        authenticate(worker);
        WorkOrderResponseDTO claimed = workOrderService.claimWorkOrder(
                activeOrder.getId(), versionCommand(activeOrder.getVersion()));

        mockMvc.perform(post("/work-orders/{id}/cancellations", activeOrder.getId())
                        .with(authentication(reporterAuthentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + claimed.getVersion() + ",\"reason\":\"越权取消\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchHttpRouteBindsRepeatedQueueTypeAndStatusParameters() throws Exception {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(
                tenantId,
                rootOrgId,
                Set.of(
                        roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER),
                        roleName(WorkOrderType.BOX_DOOR_OPEN, WorkOrderDuty.REPORTER)
                ));
        User worker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        String marker = "HTTP多选查询" + System.nanoTime();

        authenticate(reporter);
        WorkOrderResponseDTO submittedNormal = workOrderService.createWorkOrder(
                createWorkOrderRequest(WorkOrderType.NETWORK_FAULT, marker));
        workOrderService.createWorkOrder(createWorkOrderRequest(WorkOrderType.BOX_DOOR_OPEN, marker));
        authenticate(worker);
        workOrderService.claimWorkOrder(submittedNormal.getId(), versionCommand(submittedNormal.getVersion()));

        TenantUserDetails reporterDetails = userDetailsService.loadForToken(tenantId, reporter.getId(), reporter.getUsername());
        UsernamePasswordAuthenticationToken reporterAuthentication = new UsernamePasswordAuthenticationToken(
                reporterDetails,
                null,
                reporterDetails.getAuthorities()
        );
        mockMvc.perform(get("/work-orders/search")
                        .with(authentication(reporterAuthentication))
                        .param("searchTerm", marker)
                        .param("status", WorkOrderStatus.SUBMITTED.name(), WorkOrderStatus.IN_PROGRESS.name())
                        .param("reportType", WorkOrderType.NETWORK_FAULT.name(), WorkOrderType.BOX_DOOR_OPEN.name())
                        .param("queue", "REPORTED_BY_ME", "CLAIMABLE")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void completesEveryWorkOrderTypeWithItsDedicatedWorkerRole() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(
                tenantId,
                rootOrgId,
                java.util.Arrays.stream(WorkOrderType.values())
                        .map(type -> roleName(type, WorkOrderDuty.REPORTER))
                        .collect(java.util.stream.Collectors.toSet()));

        for (WorkOrderType type : WorkOrderType.values()) {
            User worker = createUser(tenantId, rootOrgId, workerProfileKey(type));
            authenticate(reporter);
            WorkOrderResponseDTO created = workOrderService.createWorkOrder(createWorkOrderRequest(type, "三类型流转" + type.name()));
            authenticate(worker);
            WorkOrderResponseDTO claimed = workOrderService.claimWorkOrder(created.getId(), versionCommand(created.getVersion()));
            RespondWorkOrderRequestDTO responseRequest = responseRequest(claimed.getVersion(), type.getDisplayName() + "处理完成");
            if (type == WorkOrderType.DAMAGED_BOX_REPLACEMENT) {
                responseRequest.setReplacementCompleted(true);
                responseRequest.setReplacementResult("箱体已更换并恢复正常");
            }
            WorkOrderResponseDTO responded = workOrderService.respondToWorkOrder(created.getId(), responseRequest);
            authenticate(reporter);
            WorkOrderResponseDTO closed = workOrderService.verifyWorkOrder(
                    created.getId(), verificationRequest(responded, VerificationDecision.APPROVED, "三类型验收通过"));
            assertThat(closed.getStatusCode()).isEqualTo(WorkOrderStatus.CLOSED.getCode());
            assertThat(closed.getReportType()).isEqualTo(type.name());
            if (type == WorkOrderType.BOX_DOOR_OPEN) {
                assertThat(closed.getBoxQuantity()).isEqualTo(2);
            }
            if (type == WorkOrderType.DAMAGED_BOX_REPLACEMENT) {
                assertThat(closed.getReplacementCompleted()).isTrue();
                assertThat(closed.getReplacementResult()).isEqualTo("箱体已更换并恢复正常");
            }
        }
    }

    @Test
    void rejectsWrongTypeWorkerFromViewingClaimingAndAssignment() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(tenantId, rootOrgId, roleName(WorkOrderType.BOX_DOOR_OPEN, WorkOrderDuty.REPORTER));
        User dispatcher = createUser(tenantId, rootOrgId, roleName(WorkOrderType.BOX_DOOR_OPEN, WorkOrderDuty.DISPATCHER));
        User networkFaultWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        User boxDoorOpenWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.BOX_DOOR_OPEN, WorkOrderDuty.WORKER));

        authenticate(reporter);
        WorkOrderResponseDTO created = workOrderService.createWorkOrder(
                createWorkOrderRequest(WorkOrderType.BOX_DOOR_OPEN, "类型权限隔离"));
        authenticate(networkFaultWorker);
        assertThatThrownBy(() -> workOrderService.getWorkOrderById(created.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> workOrderService.claimWorkOrder(created.getId(), versionCommand(created.getVersion())))
                .isInstanceOf(AccessDeniedException.class);

        authenticate(dispatcher);
        assertThat(workOrderService.assignmentCandidates(created.getId(), networkFaultWorker.getUsername())).isEmpty();
        assertThatThrownBy(() -> workOrderService.assignWorkOrder(
                created.getId(), assignmentRequest(networkFaultWorker, rootOrgId, created.getVersion(), "错误专业派单")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        WorkOrderResponseDTO assigned = workOrderService.assignWorkOrder(
                created.getId(), assignmentRequest(boxDoorOpenWorker, rootOrgId, created.getVersion(), "正确专业派单"));
        assertThat(assigned.getPendingAssignment().membershipId()).isEqualTo(boxDoorOpenWorker.getId());
    }

    @Test
    void onlyOneConcurrentClaimCanSucceed() throws Exception {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        User firstWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        User secondWorker = createUser(tenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.WORKER));
        authenticate(reporter);
        WorkOrderResponseDTO created = workOrderService.createWorkOrder(createNetworkFaultRequest("并发抢单"));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> attempts = List.of(
                    executor.submit(() -> claimConcurrently(firstWorker, created, ready, start)),
                    executor.submit(() -> claimConcurrently(secondWorker, created, ready, start))
            );
            ready.await();
            start.countDown();
            long successfulClaims = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get()) successfulClaims++;
            }
            assertThat(successfulClaims).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        WorkOrder current = workOrderRepository.findByTenantIdAndId(tenantId, created.getId()).orElseThrow();
        assertThat(current.getCurrentAssignment()).isNotNull();
        assertThat(current.lifecycleStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
    }

    @Test
    void isolatesTenantRowsAndTenantConfiguration() {
        UUID defaultTenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(defaultTenantId).getId();
        User defaultReporter = createUser(
                defaultTenantId, rootOrgId, roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));

        TenantCreateRequestDTO request = new TenantCreateRequestDTO();
        request.setTenantGroupId(tenantGroupRepository.findAll().getFirst().getId());
        request.setCode("CITY_" + UUID.randomUUID().toString().substring(0, 8));
        request.setName("隔离测试市公司");
        request.setRootOrgCode("TEST_ROOT_" + UUID.randomUUID().toString().substring(0, 8));
        request.setRootOrgName("隔离测试市公司本部");
        request.setAdminUsername("tenant-admin");
        request.setAdminPassword("Tenant-password-123!");
        request.setAdminName("隔离测试管理员");
        request.setAdminPhone("13900000001");
        request.setAdminEmail("tenant-admin@example.com");
        var tenant = tenantProvisioningService.create(request);
        User tenantAdmin = userRepository.findByTenantIdAndUsername(tenant.id(), request.getAdminUsername()).orElseThrow();
        Set<String> expectedProfileCodes = com.pro.ereportbacken.security.BuiltInAuthorizationProfileCatalog
                .templates(false).stream()
                .map(com.pro.ereportbacken.security.BuiltInAuthorizationProfileCatalog.Template::code)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(authorizationProfileRepository
                .findByTenantIdAndActiveTrueOrderBySortOrderAscDisplayNameAsc(tenant.id()).stream()
                .filter(profile -> Boolean.TRUE.equals(profile.getBuiltIn())))
                .extracting(com.pro.ereportbacken.model.pojo.AuthorizationProfile::getCode)
                .containsExactlyInAnyOrderElementsOf(expectedProfileCodes);

        TenantCreateRequestDTO duplicateUsername = new TenantCreateRequestDTO();
        duplicateUsername.setTenantGroupId(request.getTenantGroupId());
        duplicateUsername.setCode("CITY_" + UUID.randomUUID().toString().substring(0, 8));
        duplicateUsername.setName("重复账号测试市公司");
        duplicateUsername.setRootOrgCode("DUP_ROOT_" + UUID.randomUUID().toString().substring(0, 8));
        duplicateUsername.setRootOrgName("重复账号测试市公司本部");
        duplicateUsername.setAdminUsername(request.getAdminUsername());
        duplicateUsername.setAdminPassword("Tenant-password-456!");
        duplicateUsername.setAdminName("重复账号测试管理员");
        duplicateUsername.setAdminPhone("13900000002");
        assertThatThrownBy(() -> tenantProvisioningService.create(duplicateUsername))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户名已在系统中使用");

        authenticate(tenantAdmin);
        WorkOrderResponseDTO foreignWorkOrder = workOrderService.createWorkOrder(createNetworkFaultRequest("租户B工单"));
        appConfigService.saveOrUpdateConfig("test.tenant.isolation", "tenant-b", "租户隔离测试");

        authenticate(defaultReporter);
        appConfigService.saveOrUpdateConfig("test.tenant.isolation", "tenant-a", "租户隔离测试");
        assertThat(appConfigService.getConfigValue("test.tenant.isolation")).isEqualTo("tenant-a");
        assertThatThrownBy(() -> workOrderService.getWorkOrderById(foreignWorkOrder.getId()))
                .isInstanceOf(EntityNotFoundException.class);

        authenticate(tenantAdmin);
        assertThat(appConfigService.getConfigValue("test.tenant.isolation")).isEqualTo("tenant-b");
    }

    @Test
    void statisticsRespectOrganizationScopeAndIdempotencyReplaysFirstResult() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        OrgUnit root = defaultTenantService.requireRootOrgUnit(tenantId);
        OrgUnit visibleOrg = createOrg(tenantId, root, "VISIBLE");
        OrgUnit hiddenOrg = createOrg(tenantId, root, "HIDDEN");
        User auditor = createUser(tenantId, visibleOrg.getId(), roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.AUDITOR));
        User reporter = createUser(tenantId, root.getId(), roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        workOrderRepository.saveAllAndFlush(List.of(
                directWorkOrder(tenantId, visibleOrg.getId(), reporter, "可见组织工单"),
                directWorkOrder(tenantId, hiddenOrg.getId(), reporter, "不可见组织工单")
        ));

        authenticate(auditor);
        assertThat(workOrderService.getWorkOrderStatistics(null).total()).isEqualTo(1);

        AtomicInteger executions = new AtomicInteger();
        String key = "idem-" + UUID.randomUUID();
        String first = idempotencyService.execute(key, "POST:/test/idempotency", Set.of("request"), String.class, () -> {
            executions.incrementAndGet();
            return "first-result";
        });
        String replay = idempotencyService.execute(key, "POST:/test/idempotency", Set.of("request"), String.class, () -> {
            executions.incrementAndGet();
            return "unexpected-result";
        });
        assertThat(first).isEqualTo("first-result");
        assertThat(replay).isEqualTo("first-result");
        assertThat(executions).hasValue(1);

        WorkOrderResponseDTO datedResponse = new WorkOrderResponseDTO();
        datedResponse.setId(com.pro.ereportbacken.TestIds.uuid(9001));
        datedResponse.setSlaDueAt(LocalDateTime.of(2026, 8, 6, 20, 30));
        String datedKey = "idem-dated-" + UUID.randomUUID();
        WorkOrderResponseDTO firstDatedResponse = idempotencyService.execute(
                datedKey,
                "POST:/test/dated-idempotency",
                Set.of("dated-request"),
                WorkOrderResponseDTO.class,
                () -> datedResponse
        );
        WorkOrderResponseDTO replayedDatedResponse = idempotencyService.execute(
                datedKey,
                "POST:/test/dated-idempotency",
                Set.of("dated-request"),
                WorkOrderResponseDTO.class,
                () -> new WorkOrderResponseDTO()
        );
        assertThat(firstDatedResponse.getSlaDueAt()).isEqualTo(datedResponse.getSlaDueAt());
        assertThat(replayedDatedResponse.getSlaDueAt()).isEqualTo(datedResponse.getSlaDueAt());

        assertThatThrownBy(() -> idempotencyService.execute(
                key,
                "POST:/test/idempotency",
                Set.of("different-request"),
                String.class,
                () -> "never"
        )).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void searchAndDailyStatisticsUseChinaCalendarDay() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        User reporter = createUser(tenantId, rootOrgId,
                roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        LocalDate displayDate = LocalDate.now(DisplayTime.ZONE_ID).minusDays(1);
        LocalDateTime displayDayStartUtc = DisplayTime.startOfDisplayDateUtc(displayDate);
        String marker = "北京时间跨日-" + UUID.randomUUID();

        WorkOrder previousDay = directWorkOrder(tenantId, rootOrgId, reporter, marker + "前");
        previousDay.setSubmittedAt(displayDayStartUtc.minusSeconds(1));
        previousDay.setReportTime(displayDayStartUtc.minusSeconds(1));
        WorkOrder currentDay = directWorkOrder(tenantId, rootOrgId, reporter, marker + "后");
        currentDay.setSubmittedAt(displayDayStartUtc.plusSeconds(1));
        currentDay.setReportTime(displayDayStartUtc.plusSeconds(1));
        List<WorkOrder> saved = workOrderRepository.saveAllAndFlush(List.of(previousDay, currentDay));

        WorkOrderFilterDTO filter = new WorkOrderFilterDTO();
        filter.setTenantId(tenantId);
        filter.setAccessibleReportTypes(Set.of(WorkOrderType.NETWORK_FAULT));
        filter.setLocation(marker);
        filter.setReportDateFrom(displayDate.toString());
        filter.setReportDateTo(displayDate.toString());
        assertThat(workOrderQueryRepository.findIdsByCriteria(filter, PageRequest.of(0, 10)).getContent())
                .containsExactly(saved.get(1).getId());

        filter.setReportDateFrom(null);
        filter.setReportDateTo(null);
        var daily = workOrderQueryRepository.summarizeByCriteria(filter, displayDate.minusDays(1), 3).daily();
        assertThat(daily.get(displayDate.minusDays(1))).isEqualTo(1L);
        assertThat(daily.get(displayDate)).isEqualTo(1L);
        assertThat(daily.get(displayDate.plusDays(1))).isZero();
    }

    @Test
    void cityDashboardSupportsCountyDrillDownAndCountyComparisonWithoutExpandingCountyAccess() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        OrgUnit root = defaultTenantService.requireRootOrgUnit(tenantId);
        OrgUnit firstCounty = createOrg(tenantId, root, "DASH_A");
        OrgUnit secondCounty = createOrg(tenantId, root, "DASH_B");
        User reporter = createUser(tenantId, firstCounty.getId(), roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        User cityAdmin = createUser(
                tenantId,
                root.getId(),
                Set.of(
                        roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.AUDITOR),
                        BuiltInAuthorizationProfileCatalog.DASHBOARD_VIEWER
                ));
        User countyAuditor = createUser(
                tenantId,
                firstCounty.getId(),
                Set.of(
                        roleName(WorkOrderType.NETWORK_FAULT, WorkOrderDuty.AUDITOR),
                        BuiltInAuthorizationProfileCatalog.DASHBOARD_VIEWER
                ));
        workOrderRepository.saveAllAndFlush(List.of(
                directWorkOrder(tenantId, firstCounty.getId(), reporter, "区县甲统计工单"),
                directWorkOrder(tenantId, secondCounty.getId(), reporter, "区县乙统计工单")
        ));

        authenticate(cityAdmin);
        assertThat(workOrderService.getWorkOrderStatisticsScopes())
                .extracting("countyOrgUnitId")
                .contains(firstCounty.getId(), secondCounty.getId());
        var cityStatistics = workOrderService.getWorkOrderStatistics(null);
        assertThat(cityStatistics.countyComparison())
                .filteredOn(item -> item.countyOrgUnitId().equals(firstCounty.getId()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.total()).isEqualTo(1);
                    assertThat(item.active()).isEqualTo(1);
                    assertThat(item.closed()).isZero();
                });
        assertThat(workOrderService.getWorkOrderStatistics(firstCounty.getId()).total()).isEqualTo(1);

        authenticate(countyAuditor);
        assertThat(workOrderService.getWorkOrderStatisticsScopes()).isEmpty();
        assertThat(workOrderService.getWorkOrderStatistics(null).total()).isEqualTo(1);
        assertThatThrownBy(() -> workOrderService.getWorkOrderStatistics(secondCounty.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("无权查看其他区县");
    }

    private boolean claimConcurrently(User worker,
                                      WorkOrderResponseDTO created,
                                      CountDownLatch ready,
                                      CountDownLatch start) {
        authenticate(worker);
        ready.countDown();
        try {
            start.await();
            VersionedWorkOrderCommandDTO command = new VersionedWorkOrderCommandDTO();
            command.setExpectedVersion(created.getVersion());
            workOrderService.claimWorkOrder(created.getId(), command);
            return true;
        } catch (Exception expectedConflict) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private User createUser(UUID tenantId, UUID orgUnitId, String templateKey) {
        return createUser(tenantId, orgUnitId, Set.of(templateKey));
    }

    private User createUser(UUID tenantId,
                            UUID orgUnitId,
                            Set<String> templateKeys) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setOrgUnitId(orgUnitId);
        user.setAuthorizationVersion(1L);
        user.setUsername("test-" + UUID.randomUUID().toString().substring(0, 12));
        user.setPassword("test-password");
        user.setName("测试用户");
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.saveAndFlush(user);

        for (String templateKey : templateKeys) {
            authorizationSystemProvisioningService.applyTemplateBaseline(
                    user, templateKey, "工单生命周期集成测试岗位权限基线");
        }
        return user;
    }

    private void authenticate(User user) {
        TenantUserDetails details = userDetailsService.loadForToken(user.getTenantId(), user.getId(), user.getUsername());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                details,
                null,
                details.getAuthorities()
        ));
    }

    private CreateWorkOrderRequestDTO createNetworkFaultRequest(String marker) {
        return createWorkOrderRequest(WorkOrderType.NETWORK_FAULT, marker);
    }

    private CreateWorkOrderRequestDTO createWorkOrderRequest(WorkOrderType type, String marker) {
        CreateWorkOrderRequestDTO request = new CreateWorkOrderRequestDTO();
        request.setLocation(marker + "测试点");
        request.setGpsCoordinates("117.650000,24.520000");
        request.setReason(marker + "原因");
        request.setPhone("13900000000");
        request.setContactPerson("测试联系人");
        request.setReportType(type);
        request.setPriority(WorkOrderPriority.ROUTINE);
        if (type == WorkOrderType.BOX_DOOR_OPEN) {
            request.setBoxQuantity(2);
        }
        request.setUploadedAttachmentIds(List.of(completeTestUpload()));
        return request;
    }

    private String workerProfileKey(WorkOrderType type) {
        return roleName(type, WorkOrderDuty.WORKER);
    }

    private static String roleName(WorkOrderType type, WorkOrderDuty duty) {
        return BuiltInAuthorizationProfileCatalog.workOrderProfileKey(type, duty);
    }

    private VersionedWorkOrderCommandDTO versionCommand(Long version) {
        VersionedWorkOrderCommandDTO request = new VersionedWorkOrderCommandDTO();
        request.setExpectedVersion(version);
        return request;
    }

    private ReasonedWorkOrderCommandDTO reasonCommand(Long version, String reason) {
        ReasonedWorkOrderCommandDTO request = new ReasonedWorkOrderCommandDTO();
        request.setExpectedVersion(version);
        request.setReason(reason);
        return request;
    }

    private AssignmentDecisionRequestDTO assignmentDecision(Long version, String reason) {
        AssignmentDecisionRequestDTO request = new AssignmentDecisionRequestDTO();
        request.setExpectedVersion(version);
        request.setReason(reason);
        return request;
    }

    private AssignWorkOrderRequestDTO assignmentRequest(User assignee,
                                                        UUID responsibleOrgId,
                                                        Long expectedVersion,
                                                        String reason) {
        AssignWorkOrderRequestDTO request = new AssignWorkOrderRequestDTO();
        request.setAssigneeMembershipId(assignee.getId());
        request.setResponsibleOrgId(responsibleOrgId);
        request.setExpectedVersion(expectedVersion);
        request.setReason(reason);
        return request;
    }

    private RespondWorkOrderRequestDTO responseRequest(Long version, String details) {
        RespondWorkOrderRequestDTO request = new RespondWorkOrderRequestDTO();
        request.setExpectedVersion(version);
        request.setResolutionDetails(details);
        request.setResolutionMethod("现场处理");
        request.setUploadedAttachmentIds(List.of(completeTestUpload()));
        return request;
    }

    private UUID completeTestUpload() {
        byte[] content = ("test-attachment-" + UUID.randomUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var session = fileTransferService.initiate(new FileTransferInitRequestDTO(
                FileTransferPurpose.WORK_ORDER_ATTACHMENT,
                "测试材料.txt",
                "text/plain",
                (long) content.length,
                null,
                (long) content.length
        ));
        fileTransferService.uploadChunk(
                session.uploadId(),
                0,
                "bytes 0-" + (content.length - 1) + "/" + content.length,
                content
        );
        fileTransferService.complete(session.uploadId());
        return session.uploadId();
    }

    private VerifyWorkOrderRequestDTO verificationRequest(WorkOrderResponseDTO response,
                                                           VerificationDecision decision,
                                                           String comment) {
        VerifyWorkOrderRequestDTO request = new VerifyWorkOrderRequestDTO();
        request.setResponseId(response.getCurrentResponseId());
        request.setDecision(decision);
        request.setComment(comment);
        request.setExpectedVersion(response.getVersion());
        return request;
    }

    private OrgUnit createOrg(UUID tenantId, OrgUnit parent, String marker) {
        OrgUnit org = new OrgUnit();
        org.setTenantId(tenantId);
        org.setParentId(parent.getId());
        org.setCode(marker + "_" + UUID.randomUUID().toString().substring(0, 8));
        org.setName(marker + "组织");
        org.setUnitType(OrgUnitType.COUNTY);
        org.setStatus(TenantStatus.ACTIVE);
        org.setPath(parent.getPath() + "pending/");
        org = orgUnitRepository.saveAndFlush(org);
        org.setPath(parent.getPath() + org.getId() + "/");
        OrgUnit saved = orgUnitRepository.saveAndFlush(org);
        organizationDirectoryCache.invalidateAfterCommit(tenantId);
        return saved;
    }

    private WorkOrder directWorkOrder(UUID tenantId, UUID orgUnitId, User reporter, String marker) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setTenantId(tenantId);
        workOrder.setReportingOrgId(orgUnitId);
        workOrder.setResponsibleOrgId(orgUnitId);
        workOrder.setLocation(marker);
        workOrder.setReason(marker);
        workOrder.setPhone("13900000001");
        workOrder.setContactPerson("统计测试联系人");
        workOrder.setReportType(WorkOrderType.NETWORK_FAULT);
        workOrder.setReportedBy(reporter);
        workOrder.transitionTo(WorkOrderStatus.SUBMITTED);
        workOrder.setSubmittedAt(LocalDateTime.now());
        return workOrder;
    }
}
