package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.AuthorizationPlanRequestDTO;
import com.pro.ereportbacken.model.dto.AuthorizationPlanResponseDTO;
import com.pro.ereportbacken.model.dto.UserOrganizationMovePreviewDTO;
import com.pro.ereportbacken.model.dto.UserOrganizationMovePreviewRequestDTO;
import com.pro.ereportbacken.model.pojo.AssignmentState;
import com.pro.ereportbacken.model.pojo.GrantIssuerType;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserOrganizationMoveStrategy;
import com.pro.ereportbacken.model.pojo.WorkOrderStatus;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.repository.WorkOrderAssignmentRepository;
import com.pro.ereportbacken.repository.WorkOrderRepository;
import com.pro.ereportbacken.security.AdministrativeControlAction;
import com.pro.ereportbacken.security.AdministrativeControlService;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UserOrganizationMovePreviewService {

    private static final Set<String> TERMINAL_WORK_ORDER_STATUSES = Set.of(
            WorkOrderStatus.CLOSED.getCode(),
            WorkOrderStatus.CANCELLED.getCode());

    private final TenantContext tenantContext;
    private final UserRepository userRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final DefaultTenantService defaultTenantService;
    private final OrganizationScopeAuthorizationService scopeAuthorizationService;
    private final AdministrativeControlService administrativeControlService;
    private final UserOrganizationMoveAuthorizationPlanner authorizationPlanner;
    private final AuthorizationAdministrationService authorizationAdministrationService;
    private final WorkOrderAssignmentRepository assignmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final OrganizationHierarchyService hierarchyService;

    public UserOrganizationMovePreviewService(
            TenantContext tenantContext,
            UserRepository userRepository,
            OrgUnitRepository orgUnitRepository,
            DefaultTenantService defaultTenantService,
            OrganizationScopeAuthorizationService scopeAuthorizationService,
            AdministrativeControlService administrativeControlService,
            UserOrganizationMoveAuthorizationPlanner authorizationPlanner,
            AuthorizationAdministrationService authorizationAdministrationService,
            WorkOrderAssignmentRepository assignmentRepository,
            WorkOrderRepository workOrderRepository,
            OrganizationHierarchyService hierarchyService) {
        this.tenantContext = tenantContext;
        this.userRepository = userRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.defaultTenantService = defaultTenantService;
        this.scopeAuthorizationService = scopeAuthorizationService;
        this.administrativeControlService = administrativeControlService;
        this.authorizationPlanner = authorizationPlanner;
        this.authorizationAdministrationService = authorizationAdministrationService;
        this.assignmentRepository = assignmentRepository;
        this.workOrderRepository = workOrderRepository;
        this.hierarchyService = hierarchyService;
    }

    @Transactional(readOnly = true)
    public UserOrganizationMovePreviewDTO preview(
            UUID membershipId,
            UserOrganizationMovePreviewRequestDTO request) {
        UUID tenantId = tenantContext.requireTenantId();
        User target = userRepository.findByTenantIdAndId(tenantId, membershipId)
                .orElseThrow(() -> new IllegalArgumentException("目标用户不存在。"));
        if (target.getOrgUnitId().equals(request.targetOrgUnitId())) {
            throw new IllegalArgumentException("目标组织必须不同于当前所属组织。");
        }
        if (!target.getAuthorizationVersion().equals(request.expectedAuthorizationVersion())) {
            throw new IllegalStateException("用户资料或授权已发生变化，请刷新详情后重新预览。");
        }

        var sourceOrganization = orgUnitRepository.findByTenantIdAndId(tenantId, target.getOrgUnitId())
                .orElseThrow(() -> new IllegalStateException("用户当前所属组织不存在。"));
        var targetOrganization = orgUnitRepository.findByTenantIdAndId(tenantId, request.targetOrgUnitId())
                .orElseThrow(() -> new IllegalArgumentException("目标组织不存在或不属于当前租户。"));
        scopeAuthorizationService.requireOrganizationAccess(
                PermissionCatalog.USER_MANAGE_ORGANIZATION_MOVE, sourceOrganization.getId());
        scopeAuthorizationService.requireOrganizationAccess(
                PermissionCatalog.USER_MANAGE_ORGANIZATION_MOVE, targetOrganization.getId());
        administrativeControlService.require(target, AdministrativeControlAction.MOVE_ORGANIZATION);

        requireValidSystemIdentityDestination(target, targetOrganization.getId());

        UserOrganizationMoveAuthorizationPlanner.Plan authorizationPlan =
                authorizationPlanner.prepare(target, targetOrganization.getId(), request.strategy());
        AuthorizationPlanResponseDTO authorizationPreview = authorizationPlan.operations().isEmpty()
                ? null
                : authorizationAdministrationService.previewPlan(target.getId(),
                new AuthorizationPlanRequestDTO(target.getAuthorizationVersion(), authorizationPlan.operations()));
        List<AuthorizationPlanResponseDTO.PermissionChangePreview> permissionChanges = authorizationPreview == null
                ? List.of()
                : authorizationPreview.operations().stream()
                .flatMap(operation -> operation.permissionChanges().stream())
                .toList();
        int resultingGrantCount = authorizationPreview == null
                ? authorizationPlan.beforeActiveGrantCount()
                : authorizationPreview.resultingActiveGrantCount();

        long pendingAssignments = assignmentRepository.countByTenantIdAndAssigneeIdAndState(
                tenantId, target.getId(), AssignmentState.PENDING_ACCEPTANCE);
        long acceptedAssignments = assignmentRepository.countByTenantIdAndAssigneeIdAndState(
                tenantId, target.getId(), AssignmentState.ACCEPTED);
        long openReported = workOrderRepository.countByTenantIdAndReportedByIdAndStatusCodeNotIn(
                tenantId, target.getId(), TERMINAL_WORK_ORDER_STATUSES);

        var currentAuthorization = authorizationPlan.currentAuthorization();
        long systemManagedGrantCount = currentAuthorization.grants().stream()
                .filter(grant -> grant.issuerType() == GrantIssuerType.SYSTEM_ROOT
                        || grant.issuerType() == GrantIssuerType.SYSTEM_BASELINE
                        || grant.issuerType() == GrantIssuerType.SYSTEM_SELF_BASELINE)
                .count();
        UserOrganizationMoveStrategy recommendedStrategy = hierarchyService.treeContains(
                tenantId, sourceOrganization.getId(), targetOrganization.getId())
                ? UserOrganizationMoveStrategy.KEEP
                : UserOrganizationMoveStrategy.REVOKE_ALL;
        List<UserOrganizationMovePreviewDTO.Notice> notices = notices(
                request.strategy(), openReported,
                pendingAssignments + acceptedAssignments, permissionChanges.size(), systemManagedGrantCount);

        return new UserOrganizationMovePreviewDTO(
                target.getId(), target.getAuthorizationVersion(), request.strategy(),
                recommendedStrategy,
                new UserOrganizationMovePreviewDTO.OrganizationChange(
                        sourceOrganization.getId(), sourceOrganization.getName(),
                        targetOrganization.getId(), targetOrganization.getName()),
                new UserOrganizationMovePreviewDTO.AuthorizationImpact(
                        authorizationPlan.beforeActiveGrantCount(), resultingGrantCount, permissionChanges),
                new UserOrganizationMovePreviewDTO.ManagementImpact(
                        target.getSystemKey() != null, currentAuthorization.effectivePermissionCount(),
                        recommendedStrategy != UserOrganizationMoveStrategy.KEEP),
                new UserOrganizationMovePreviewDTO.WorkOrderImpact(
                        openReported, pendingAssignments, acceptedAssignments,
                        pendingAssignments + acceptedAssignments, false),
                notices);
    }

    private void requireValidSystemIdentityDestination(User target, UUID targetOrganizationId) {
        if (target.getSystemKey() != null
                && !targetOrganizationId.equals(
                defaultTenantService.requireRootOrgUnit(target.getTenantId()).getId())) {
            throw new IllegalArgumentException("系统管理员必须归属租户根组织。");
        }
    }

    private List<UserOrganizationMovePreviewDTO.Notice> notices(
            UserOrganizationMoveStrategy strategy,
            long openReported,
            long activeResponsibilities,
            int authorizationChangeCount,
            long systemManagedGrantCount) {
        List<UserOrganizationMovePreviewDTO.Notice> notices = new ArrayList<>();
        notices.add(new UserOrganizationMovePreviewDTO.Notice(
                UserOrganizationMovePreviewDTO.NoticeSeverity.INFO,
                "HISTORICAL_WORK_ORDER_ORGANIZATION_RETAINED",
                "历史工单归属保持不变",
                "迁移只影响用户今后的组织归属；历史工单仍保留原报单组织和责任组织。"));
        if (openReported > 0) {
            notices.add(new UserOrganizationMovePreviewDTO.Notice(
                    UserOrganizationMovePreviewDTO.NoticeSeverity.WARNING,
                    "OPEN_REPORTED_WORK_ORDERS_RETAINED",
                    "存在未闭环提单",
                    "该用户有 " + openReported + " 条未闭环提单，迁移后仍归属原工单组织。"));
        }
        if (activeResponsibilities > 0) {
            notices.add(new UserOrganizationMovePreviewDTO.Notice(
                    UserOrganizationMovePreviewDTO.NoticeSeverity.WARNING,
                    "ACTIVE_ASSIGNMENTS_NOT_REASSIGNED",
                    "当前处理责任不会自动改派",
                    "该用户仍有 " + activeResponsibilities + " 条待接或处理中责任，需要另行调单。"));
        }
        if (systemManagedGrantCount > 0) {
            notices.add(new UserOrganizationMovePreviewDTO.Notice(
                    UserOrganizationMovePreviewDTO.NoticeSeverity.WARNING,
                    "SYSTEM_MANAGED_GRANTS_RETAINED",
                    "系统基线授权不由迁移策略修改",
                    "该账号有 " + systemManagedGrantCount + " 条系统维护的 Grant；如需调整其管理员级别，须使用专门的系统管理流程。"));
        }
        if (strategy == UserOrganizationMoveStrategy.KEEP) {
            notices.add(new UserOrganizationMovePreviewDTO.Notice(
                    UserOrganizationMovePreviewDTO.NoticeSeverity.WARNING,
                    "ALL_AUTHORIZATION_RETAINED",
                    strategy.displayName(),
                    strategy.outcome() + " 请确认该用户迁移后仍需要原组织的数据访问能力。"));
        } else {
            notices.add(new UserOrganizationMovePreviewDTO.Notice(
                    strategy == UserOrganizationMoveStrategy.REVOKE_ALL
                            ? UserOrganizationMovePreviewDTO.NoticeSeverity.WARNING
                            : UserOrganizationMovePreviewDTO.NoticeSeverity.INFO,
                    "AUTHORIZATION_STRATEGY_OUTCOME",
                    strategy.displayName(),
                    strategy.outcome()));
        }
        if (authorizationChangeCount > 0) {
            notices.add(new UserOrganizationMovePreviewDTO.Notice(
                    UserOrganizationMovePreviewDTO.NoticeSeverity.INFO,
                    "AUTHORIZATION_DIFF_READY",
                    "授权差异已生成",
                    "确认迁移时将执行下方列出的 " + authorizationChangeCount + " 项原子权限变化。"));
        }
        return List.copyOf(notices);
    }
}
