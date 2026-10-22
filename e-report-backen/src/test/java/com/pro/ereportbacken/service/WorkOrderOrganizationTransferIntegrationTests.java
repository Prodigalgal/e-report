package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.WorkOrderTransferCreateRequestDTO;
import com.pro.ereportbacken.model.dto.WorkOrderTransferDecisionRequestDTO;
import com.pro.ereportbacken.model.pojo.AssignmentSlot;
import com.pro.ereportbacken.model.pojo.AssignmentState;
import com.pro.ereportbacken.model.pojo.AssignmentType;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderAssignment;
import com.pro.ereportbacken.model.pojo.WorkOrderOrganizationRelationType;
import com.pro.ereportbacken.model.pojo.WorkOrderOrganizationTransferStatus;
import com.pro.ereportbacken.model.pojo.WorkOrderPriority;
import com.pro.ereportbacken.model.pojo.WorkOrderStatus;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.repository.WorkOrderAssignmentRepository;
import com.pro.ereportbacken.repository.WorkOrderOrganizationParticipationRepository;
import com.pro.ereportbacken.repository.WorkOrderOrganizationTransferRepository;
import com.pro.ereportbacken.repository.WorkOrderRepository;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantUserDetails;
import com.pro.ereportbacken.security.UserAuthorizationSnapshot;
import com.pro.ereportbacken.security.WorkOrderAccessPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorkOrderOrganizationTransferIntegrationTests {

    @Autowired
    private WorkOrderOrganizationTransferService transferService;

    @Autowired
    private WorkOrderOrganizationParticipationService participationService;

    @Autowired
    private WorkOrderAccessPolicy accessPolicy;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private WorkOrderAssignmentRepository assignmentRepository;

    @Autowired
    private WorkOrderOrganizationTransferRepository transferRepository;

    @Autowired
    private WorkOrderOrganizationParticipationRepository participationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DefaultTenantService defaultTenantService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptedTransferChangesUniqueOwnerRevokesHandlersAndRetainsSourceVisibility() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        User sourceOperator = userRepository.findByTenantIdAndUsername(tenantId, "nanjing").orElseThrow();
        User targetOperator = userRepository.findByTenantIdAndUsername(tenantId, "zhangpu").orElseThrow();
        User unrelatedOperator = userRepository.findByTenantIdAndUsername(tenantId, "xiangcheng").orElseThrow();
        WorkOrder workOrder = workOrder(sourceOperator);
        participationService.recordSubmitted(workOrder, sourceOperator.getId());
        WorkOrderAssignment assignment = assignment(workOrder, sourceOperator);
        workOrder.setCurrentAssignment(assignment);
        workOrder.transitionTo(WorkOrderStatus.IN_PROGRESS);
        workOrder = workOrderRepository.saveAndFlush(workOrder);

        authenticate(sourceOperator,
                permission(WorkOrderType.NETWORK_FAULT).viewPermission(),
                permission(WorkOrderType.NETWORK_FAULT).transferCreatePermission(),
                permission(WorkOrderType.NETWORK_FAULT).transferCancelPermission());
        var requested = transferService.request(workOrder.getId(), new WorkOrderTransferCreateRequestDTO(
                targetOperator.getOrgUnitId(), workOrder.getVersion(), "南靖完整移交漳浦处理"));
        assertThat(requested.status()).isEqualTo(WorkOrderOrganizationTransferStatus.PENDING.name());

        authenticate(targetOperator,
                permission(WorkOrderType.NETWORK_FAULT).viewPermission(),
                permission(WorkOrderType.NETWORK_FAULT).transferAcceptPermission(),
                permission(WorkOrderType.NETWORK_FAULT).transferRejectPermission());
        accessPolicy.requireReadAccess(workOrder);
        assertThat(participationRepository.findByTenantIdAndWorkOrderIdOrderByStartedAtAsc(
                tenantId, workOrder.getId()))
                .anySatisfy(participation -> {
                    assertThat(participation.getOrgUnitId()).isEqualTo(targetOperator.getOrgUnitId());
                    assertThat(participation.getRelationType())
                            .isEqualTo(WorkOrderOrganizationRelationType.PENDING_TRANSFER_TARGET);
                    assertThat(participation.getActive()).isTrue();
                });
        var accepted = transferService.accept(workOrder.getId(), requested.id(),
                new WorkOrderTransferDecisionRequestDTO(workOrder.getVersion(), "漳浦确认接收"));
        assertThat(accepted.status()).isEqualTo(WorkOrderOrganizationTransferStatus.ACCEPTED.name());

        WorkOrder moved = workOrderRepository.findByTenantIdAndId(tenantId, workOrder.getId()).orElseThrow();
        WorkOrderAssignment revoked = assignmentRepository.findById(assignment.getId()).orElseThrow();
        assertThat(moved.getOwnerOrgId()).isEqualTo(targetOperator.getOrgUnitId());
        assertThat(moved.getResponsibleOrgId()).isEqualTo(targetOperator.getOrgUnitId());
        assertThat(moved.getResponsibleDepartmentId()).isNull();
        assertThat(moved.getCurrentAssignment()).isNull();
        assertThat(moved.lifecycleStatus()).isEqualTo(WorkOrderStatus.SUBMITTED);
        assertThat(revoked.getState()).isEqualTo(AssignmentState.REVOKED);
        assertThat(revoked.getEndedAt()).isNotNull();
        assertThat(transferRepository.findById(requested.id()).orElseThrow().getStatus())
                .isEqualTo(WorkOrderOrganizationTransferStatus.ACCEPTED);
        assertThat(participationRepository.findByTenantIdAndWorkOrderIdOrderByStartedAtAsc(
                tenantId, workOrder.getId()))
                .anySatisfy(participation -> {
                    assertThat(participation.getOrgUnitId()).isEqualTo(sourceOperator.getOrgUnitId());
                    assertThat(participation.getRelationType()).isEqualTo(WorkOrderOrganizationRelationType.PREVIOUS_OWNER);
                    assertThat(participation.getActive()).isTrue();
                });

        authenticate(sourceOperator, permission(WorkOrderType.NETWORK_FAULT).viewPermission());
        accessPolicy.requireReadAccess(moved);

        authenticate(targetOperator, permission(WorkOrderType.NETWORK_FAULT).viewPermission());
        accessPolicy.requireReadAccess(moved);

        authenticate(unrelatedOperator, permission(WorkOrderType.NETWORK_FAULT).viewPermission());
        assertThatThrownBy(() -> accessPolicy.requireReadAccess(moved))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("没有查看");
    }

    @Test
    void terminalWorkOrderCancelsPendingTransferAndRejectsAcceptance() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        User sourceOperator = userRepository.findByTenantIdAndUsername(tenantId, "nanjing").orElseThrow();
        User targetOperator = userRepository.findByTenantIdAndUsername(tenantId, "zhangpu").orElseThrow();
        WorkOrder workOrder = workOrder(sourceOperator);
        participationService.recordSubmitted(workOrder, sourceOperator.getId());

        authenticate(sourceOperator,
                permission(WorkOrderType.NETWORK_FAULT).viewPermission(),
                permission(WorkOrderType.NETWORK_FAULT).transferCreatePermission(),
                permission(WorkOrderType.NETWORK_FAULT).transferCancelPermission());
        var requested = transferService.request(workOrder.getId(), new WorkOrderTransferCreateRequestDTO(
                targetOperator.getOrgUnitId(), workOrder.getVersion(), "终态移交保护测试"));

        WorkOrder terminal = workOrderRepository.findByTenantIdAndId(tenantId, workOrder.getId()).orElseThrow();
        terminal.setClosedAt(LocalDateTime.now());
        terminal.transitionTo(WorkOrderStatus.PENDING_VERIFICATION);
        terminal.transitionTo(WorkOrderStatus.CLOSED);
        terminal = workOrderRepository.saveAndFlush(terminal);
        transferService.cancelPendingForTerminal(terminal, sourceOperator, "工单已闭环");

        assertThat(transferRepository.findById(requested.id()).orElseThrow().getStatus())
                .isEqualTo(WorkOrderOrganizationTransferStatus.CANCELLED);
        assertThat(participationRepository.findByTenantIdAndWorkOrderIdOrderByStartedAtAsc(tenantId, workOrder.getId()))
                .anySatisfy(participation -> {
                    assertThat(participation.getOrgUnitId()).isEqualTo(targetOperator.getOrgUnitId());
                    assertThat(participation.getRelationType())
                            .isEqualTo(WorkOrderOrganizationRelationType.PENDING_TRANSFER_TARGET);
                    assertThat(participation.getActive()).isFalse();
                });

        authenticate(targetOperator,
                permission(WorkOrderType.NETWORK_FAULT).viewPermission(),
                permission(WorkOrderType.NETWORK_FAULT).transferAcceptPermission());
        Long terminalVersion = terminal.getVersion();
        assertThatThrownBy(() -> transferService.accept(workOrder.getId(), requested.id(),
                new WorkOrderTransferDecisionRequestDTO(terminalVersion, "终态不应接收移交")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已闭环或已取消");

        WorkOrder unchanged = workOrderRepository.findByTenantIdAndId(tenantId, workOrder.getId()).orElseThrow();
        assertThat(unchanged.getOwnerOrgId()).isEqualTo(sourceOperator.getOrgUnitId());
        assertThat(unchanged.lifecycleStatus()).isEqualTo(WorkOrderStatus.CLOSED);
    }

    @Test
    void processingOrganizationRetainsScopedReadAccessWithoutChangingOwnership() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        User sourceOperator = userRepository.findByTenantIdAndUsername(tenantId, "nanjing").orElseThrow();
        User processingOperator = userRepository.findByTenantIdAndUsername(tenantId, "zhangpu").orElseThrow();
        WorkOrder workOrder = workOrder(sourceOperator);
        UUID originalOwnerOrgId = workOrder.getOwnerOrgId();
        UUID originalResponsibleOrgId = workOrder.getResponsibleOrgId();
        participationService.recordSubmitted(workOrder, sourceOperator.getId());
        participationService.recordProcessingOrganization(
                workOrder, processingOperator.getOrgUnitId(), processingOperator.getId(), "跨组织处理可见性");

        authenticate(processingOperator,
                permission(WorkOrderType.NETWORK_FAULT).viewPermission());
        accessPolicy.requireReadAccess(workOrder);

        assertThat(workOrder.getOwnerOrgId()).isEqualTo(originalOwnerOrgId);
        assertThat(workOrder.getResponsibleOrgId()).isEqualTo(originalResponsibleOrgId);
        assertThat(participationRepository.findByTenantIdAndWorkOrderIdOrderByStartedAtAsc(
                        tenantId, workOrder.getId()))
                .anySatisfy(participation -> {
                    assertThat(participation.getOrgUnitId()).isEqualTo(processingOperator.getOrgUnitId());
                    assertThat(participation.getRelationType())
                            .isEqualTo(WorkOrderOrganizationRelationType.PROCESSING);
                    assertThat(participation.getActive()).isTrue();
                });
    }

    private WorkOrder workOrder(User reporter) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setTenantId(reporter.getTenantId());
        workOrder.setReportingOrgId(reporter.getOrgUnitId());
        workOrder.setResponsibleOrgId(reporter.getOrgUnitId());
        workOrder.setOwnerOrgId(reporter.getOrgUnitId());
        workOrder.setWorkOrderNo("TRANSFER-" + UUID.randomUUID());
        workOrder.setLocation("跨组织移交测试地点");
        workOrder.setReason("验证完整移交");
        workOrder.setPhone(reporter.getPhone() == null ? "13900000000" : reporter.getPhone());
        workOrder.setContactPerson(reporter.getName() == null ? reporter.getUsername() : reporter.getName());
        workOrder.setReportType(WorkOrderType.NETWORK_FAULT);
        workOrder.setPriority(WorkOrderPriority.ROUTINE);
        workOrder.setSubmittedAt(LocalDateTime.now());
        workOrder.transitionTo(WorkOrderStatus.SUBMITTED);
        workOrder.setReportedBy(reporter);
        return workOrderRepository.saveAndFlush(workOrder);
    }

    private WorkOrderAssignment assignment(WorkOrder workOrder, User assignee) {
        WorkOrderAssignment assignment = new WorkOrderAssignment();
        assignment.setTenantId(workOrder.getTenantId());
        assignment.setWorkOrder(workOrder);
        assignment.setAssignee(assignee);
        assignment.setResponsibleOrgId(workOrder.getResponsibleOrgId());
        assignment.setAssignedBy(assignee);
        assignment.setAssignmentType(AssignmentType.CLAIM);
        assignment.setState(AssignmentState.ACCEPTED);
        assignment.setActiveSlot(AssignmentSlot.CURRENT);
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setAcceptedAt(assignment.getAssignedAt());
        return assignmentRepository.saveAndFlush(assignment);
    }

    private PermissionCatalog.WorkOrderEntry permission(WorkOrderType type) {
        return PermissionCatalog.requireWorkOrderEntry(type);
    }

    private void authenticate(User user, String... permissions) {
        List<UserAuthorizationSnapshot.EffectivePermissionGrant> grants = Arrays.stream(permissions)
                .map(permission -> new UserAuthorizationSnapshot.EffectivePermissionGrant(
                        UUID.nameUUIDFromBytes(permission.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        permission, false, null, null))
                .toList();
        UserAuthorizationSnapshot snapshot = new UserAuthorizationSnapshot(
                user.getId(), user.getTenantId(), user.getOrgUnitId(), user.getAuthorizationVersion(),
                user.getUsername(), user.getSystemKey(), UserStatus.ACTIVE, grants);
        TenantUserDetails principal = new TenantUserDetails(
                user.getId(), user.getTenantId(), user.getOrgUnitId(), user.getAuthorizationVersion(),
                user.getUsername(), user.getPassword(), true, List.of(), snapshot);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));
    }
}
