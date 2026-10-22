package com.pro.ereportbacken.security;

import com.pro.ereportbacken.model.dto.WorkOrderFilterDTO;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderStatus;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.repository.WorkOrderAssignmentRepository;
import com.pro.ereportbacken.repository.WorkOrderOrganizationParticipationRepository;
import com.pro.ereportbacken.service.UserAuthorizationCache;
import com.pro.ereportbacken.service.OrganizationHierarchyService;
import com.pro.ereportbacken.service.WorkOrderDepartmentParticipationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class WorkOrderAccessPolicy {

    private final UserRepository userRepository;
    private final TenantContext tenantContext;
    private final WorkOrderAssignmentRepository assignmentRepository;
    private final UserAuthorizationCache authorizationCache;
    private final OrganizationHierarchyService hierarchyService;
    private final WorkOrderOrganizationParticipationRepository organizationParticipationRepository;
    private final WorkOrderDepartmentParticipationService departmentParticipationService;

    public WorkOrderAccessPolicy(UserRepository userRepository,
                                 TenantContext tenantContext,
                                 WorkOrderAssignmentRepository assignmentRepository,
                                 UserAuthorizationCache authorizationCache,
                                 OrganizationHierarchyService hierarchyService,
                                 WorkOrderOrganizationParticipationRepository organizationParticipationRepository,
                                 WorkOrderDepartmentParticipationService departmentParticipationService) {
        this.userRepository = userRepository;
        this.tenantContext = tenantContext;
        this.assignmentRepository = assignmentRepository;
        this.authorizationCache = authorizationCache;
        this.hierarchyService = hierarchyService;
        this.organizationParticipationRepository = organizationParticipationRepository;
        this.departmentParticipationService = departmentParticipationService;
    }

    public User currentUserOrThrow() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("必须登录后才能访问工单。" );
        }
        UUID tenantId = tenantContext.requireTenantId();
        if (authentication.getPrincipal() instanceof TenantUserDetails tenantPrincipal) {
            return userRepository.findByTenantIdAndId(tenantId, tenantPrincipal.membershipId())
                    .orElseThrow(() -> new EntityNotFoundException("当前租户中不存在登录用户。"));
        }
        String username = resolveUsername(authentication.getPrincipal());
        return userRepository.findByTenantIdAndUsername(tenantId, username)
                .orElseThrow(() -> new EntityNotFoundException("当前租户中不存在登录用户。"));
    }

    public UUID currentTenantId() {
        return tenantContext.requireTenantId();
    }

    public String currentUsernameOrUnknown() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "UNKNOWN_USER" : authentication.getName();
    }

    public UUID currentMembershipId() {
        return currentAuthorizationSnapshot().membershipId();
    }

    public Long currentAuthorizationVersion() {
        return currentAuthorizationSnapshot().authorizationVersion();
    }

    public Set<WorkOrderType> allowedTypesFor(WorkOrderAction... actions) {
        Set<String> authorities = currentAuthorizationSnapshot().authorities();
        Set<WorkOrderAction> requestedActions = Set.copyOf(Arrays.asList(actions));
        return PermissionCatalog.workOrderEntries().stream()
                .filter(entry -> requestedActions.stream()
                        .map(action -> permissionFor(entry, action))
                        .anyMatch(authorities::contains))
                .map(PermissionCatalog.WorkOrderEntry::type)
                .collect(Collectors.toUnmodifiableSet());
    }

    public void requirePermission(WorkOrderType type, WorkOrderAction action) {
        UserAuthorizationSnapshot snapshot = currentAuthorizationSnapshot();
        String permission = permissionFor(PermissionCatalog.requireWorkOrderEntry(type), action);
        if (!snapshot.authorities().contains(permission)) {
            throw new AccessDeniedException("当前账号没有执行该工单动作的权限：" + permission);
        }
    }

    public void requireActionAccess(WorkOrder workOrder, WorkOrderAction action) {
        requireSameTenant(workOrder);
        if (!hasScopedPermission(workOrder, action)) {
            throw new AccessDeniedException("当前账号没有执行该工单动作的权限：" + action);
        }
    }

    public boolean hasActionAccess(WorkOrder workOrder, WorkOrderAction action) {
        return Objects.equals(workOrder.getTenantId(), tenantContext.requireTenantId())
                && hasScopedPermission(workOrder, action);
    }

    public void requireOrganizationAccess(WorkOrder workOrder, WorkOrderAction action, UUID targetOrgId) {
        requireSameTenant(workOrder);
        String permission = permissionFor(PermissionCatalog.requireWorkOrderEntry(workOrder.type()), action);
        UserAuthorizationSnapshot snapshot = currentAuthorizationSnapshot();
        if (!snapshot.authorities().contains(permission)
                || !organizationAccessible(snapshot, targetOrgId)) {
            throw new AccessDeniedException("当前工单动作权限未覆盖目标组织。" );
        }
    }

    public void requireReadAccess(WorkOrder workOrder) {
        requireSameTenant(workOrder);
        if (!hasScopedPermission(workOrder, WorkOrderAction.VIEW)) {
            throw new AccessDeniedException("当前账号没有查看该工单的权限。" );
        }
    }

    public void requireAttachmentAccess(WorkOrder workOrder) {
        requireSameTenant(workOrder);
        if (!hasScopedPermission(workOrder, WorkOrderAction.READ_ATTACHMENT)) {
            throw new AccessDeniedException("当前账号没有读取该工单附件的权限。" );
        }
    }

    public void requireHistoryAccess(WorkOrder workOrder) {
        requireSameTenant(workOrder);
        if (!hasScopedPermission(workOrder, WorkOrderAction.HISTORY)) {
            throw new AccessDeniedException("当前账号没有查看该工单流转记录的权限。" );
        }
    }

    public boolean hasAuditAccess(WorkOrder workOrder) {
        return Objects.equals(workOrder.getTenantId(), tenantContext.requireTenantId())
                && hasScopedPermission(workOrder, WorkOrderAction.AUDIT);
    }

    public Set<String> allowedViews(WorkOrder workOrder) {
        requireSameTenant(workOrder);
        WorkOrderRelationships relationships = relationships(workOrder, currentAuthorizationSnapshot());
        Set<String> views = new LinkedHashSet<>();
        if (hasScopedPermission(workOrder, WorkOrderAction.VIEW, relationships)) views.add("DETAIL");
        if (hasScopedPermission(workOrder, WorkOrderAction.HISTORY, relationships)) views.add("HISTORY");
        if (hasScopedPermission(workOrder, WorkOrderAction.READ_ATTACHMENT, relationships)) views.add("ATTACHMENTS");
        if (hasScopedPermission(workOrder, WorkOrderAction.AUDIT, relationships)) views.add("AUDIT");
        return Set.copyOf(views);
    }

    public WorkOrderFilterDTO applySearchScope(WorkOrderFilterDTO filter) {
        return applyReadScope(filter, PermissionCatalog.WorkOrderEntry::viewPermission, false);
    }

    public WorkOrderFilterDTO applyExportScope(WorkOrderFilterDTO filter) {
        return applyReadScope(filter, PermissionCatalog.WorkOrderEntry::exportPermission, true);
    }

    private WorkOrderFilterDTO applyReadScope(
            WorkOrderFilterDTO filter,
            Function<PermissionCatalog.WorkOrderEntry, String> permissionSelector,
            boolean requireAtLeastOneGrant) {
        WorkOrderFilterDTO scopedFilter = filter == null ? new WorkOrderFilterDTO() : filter;
        UserAuthorizationSnapshot snapshot = currentAuthorizationSnapshot();
        scopedFilter.setTenantId(snapshot.tenantId());
        scopedFilter.setAccessMembershipId(snapshot.membershipId());

        Set<WorkOrderType> tenantWideTypes = new LinkedHashSet<>();
        Set<WorkOrderType> tenantWideReporterTypes = new LinkedHashSet<>();
        Set<WorkOrderType> tenantWideParticipantTypes = new LinkedHashSet<>();
        Map<WorkOrderType, Set<UUID>> orgUnitsByType = new LinkedHashMap<>();
        Map<WorkOrderType, Set<UUID>> reporterOrgUnitsByType = new LinkedHashMap<>();
        Map<WorkOrderType, Set<UUID>> participantOrgUnitsByType = new LinkedHashMap<>();

        boolean hasGrant = false;
        Set<String> authorities = snapshot.authorities();
        Set<UUID> naturalOrganizations = hierarchyService.descendantIds(snapshot.tenantId(), snapshot.orgUnitId());
        boolean systemRoot = snapshot.systemKey() == com.pro.ereportbacken.model.pojo.UserSystemKey.SUPER_ADMINISTRATOR;
        for (PermissionCatalog.WorkOrderEntry entry : PermissionCatalog.workOrderEntries()) {
            if (!authorities.contains(permissionSelector.apply(entry))) {
                continue;
            }
            hasGrant = true;
            if (systemRoot) {
                tenantWideTypes.add(entry.type());
            } else {
                orgUnitsByType.put(entry.type(), naturalOrganizations);
            }
            tenantWideReporterTypes.add(entry.type());
            tenantWideParticipantTypes.add(entry.type());
        }
        if (requireAtLeastOneGrant && !hasGrant) {
            throw new AccessDeniedException("当前账号没有导出工单的权限。");
        }
        scopedFilter.setAccessibleReportTypes(tenantWideTypes);
        scopedFilter.setAccessibleOrgUnitIdsByType(orgUnitsByType);
        scopedFilter.setTenantWideReporterTypes(tenantWideReporterTypes);
        scopedFilter.setReporterOrgUnitIdsByType(reporterOrgUnitsByType);
        scopedFilter.setTenantWideParticipantTypes(tenantWideParticipantTypes);
        scopedFilter.setParticipantOrgUnitIdsByType(participantOrgUnitsByType);
        scopedFilter.setTenantWideClaimableTypes(Set.of());
        scopedFilter.setClaimableOrgUnitIdsByType(Map.of());

        if (scopedFilter.getReportTypes() != null) {
            for (String reportType : scopedFilter.getReportTypes()) {
                if (!StringUtils.hasText(reportType) || "all".equalsIgnoreCase(reportType)) continue;
                try {
                    WorkOrderType.valueOf(reportType.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("工单类型无效：" + reportType, exception);
                }
            }
        }
        return scopedFilter;
    }

    public Set<String> allowedActions(WorkOrder workOrder) {
        requireSameTenant(workOrder);
        WorkOrderStatus status = workOrder.lifecycleStatus();
        if (status.isTerminal()) return Collections.emptySet();

        UserAuthorizationSnapshot snapshot = currentAuthorizationSnapshot();
        WorkOrderRelationships relationships = relationships(workOrder, snapshot);
        return allowedActions(workOrder, snapshot, relationships);
    }

    public Map<UUID, Set<String>> allowedActionsByWorkOrderId(Collection<WorkOrder> workOrders) {
        if (workOrders == null || workOrders.isEmpty()) {
            return Map.of();
        }
        List<WorkOrder> rows = List.copyOf(workOrders);
        UUID tenantId = tenantContext.requireTenantId();
        if (rows.stream().anyMatch(workOrder -> !tenantId.equals(workOrder.getTenantId()))) {
            throw new EntityNotFoundException("工单列表包含其他租户数据。" );
        }
        UserAuthorizationSnapshot snapshot = currentAuthorizationSnapshot();
        User operator = currentUserOrThrow();
        Set<UUID> activeWorkOrderIds = rows.stream()
                .filter(workOrder -> !workOrder.lifecycleStatus().isTerminal())
                .map(WorkOrder::getId)
                .collect(Collectors.toSet());
        Set<UUID> historicalWorkOrderIds = activeWorkOrderIds.isEmpty()
                ? Set.of()
                : Set.copyOf(assignmentRepository.findAcceptedWorkOrderIds(
                tenantId, activeWorkOrderIds, snapshot.membershipId()));
        Map<UUID, Set<String>> actionsByWorkOrderId = new LinkedHashMap<>();
        for (WorkOrder workOrder : rows) {
            if (workOrder.lifecycleStatus().isTerminal()) {
                actionsByWorkOrderId.put(workOrder.getId(), Set.of());
                continue;
            }
            WorkOrderRelationships relationships = relationships(
                    workOrder, snapshot, historicalWorkOrderIds.contains(workOrder.getId()));
            actionsByWorkOrderId.put(
                    workOrder.getId(),
                    allowedActions(workOrder, snapshot, relationships, operator));
        }
        return Map.copyOf(actionsByWorkOrderId);
    }

    private Set<String> allowedActions(WorkOrder workOrder,
                                       UserAuthorizationSnapshot snapshot,
                                       WorkOrderRelationships relationships) {
        return allowedActions(workOrder, snapshot, relationships, currentUserOrThrow());
    }

    private Set<String> allowedActions(WorkOrder workOrder,
                                       UserAuthorizationSnapshot snapshot,
                                       WorkOrderRelationships relationships,
                                       User operator) {
        WorkOrderStatus status = workOrder.lifecycleStatus();
        Set<String> actions = new LinkedHashSet<>();
        boolean hasCurrent = workOrder.getCurrentAssignment() != null
                && workOrder.getCurrentAssignment().isCurrent();
        boolean hasPending = workOrder.getPendingAssignment() != null
                && workOrder.getPendingAssignment().isPending();
        if ((status == WorkOrderStatus.SUBMITTED || status == WorkOrderStatus.IN_PROGRESS)
                && !hasCurrent && !hasPending && hasScopedPermission(workOrder, WorkOrderAction.CLAIM, relationships, operator)) {
            actions.add("CLAIM");
        }
        boolean unassignedRework = status == WorkOrderStatus.IN_PROGRESS
                && !hasCurrent
                && !hasPending
                && workOrder.getReworkCount() != null
                && workOrder.getReworkCount() > 0;
        if ((status == WorkOrderStatus.SUBMITTED || status == WorkOrderStatus.IN_PROGRESS)
                && !hasPending
                && !unassignedRework
                && hasScopedPermission(workOrder,
                hasCurrent ? WorkOrderAction.REASSIGN : WorkOrderAction.CREATE_ASSIGNMENT, relationships, operator)) {
            actions.add(hasCurrent ? "REASSIGN" : "ASSIGN");
        }
        if (status == WorkOrderStatus.SUBMITTED && hasPending
                && hasScopedPermission(workOrder, WorkOrderAction.REASSIGN, relationships, operator)) {
            actions.add("REASSIGN");
        }
        if (hasPending && Objects.equals(workOrder.getPendingAssignment().getAssignee().getId(), snapshot.membershipId())) {
            if (hasScopedPermission(workOrder, WorkOrderAction.ACCEPT_ASSIGNMENT, relationships, operator)) actions.add("ACCEPT_ASSIGNMENT");
            if (hasScopedPermission(workOrder, WorkOrderAction.REJECT_ASSIGNMENT, relationships, operator)) actions.add("REJECT_ASSIGNMENT");
        }
        if (status == WorkOrderStatus.IN_PROGRESS && isCurrentAssignee(workOrder)) {
            if (hasScopedPermission(workOrder, WorkOrderAction.SUBMIT_RESPONSE, relationships, operator)) actions.add("RESPOND");
            if (hasScopedPermission(workOrder, WorkOrderAction.HOLD, relationships, operator)) actions.add("HOLD");
        }
        if ((status == WorkOrderStatus.IN_PROGRESS
                || status == WorkOrderStatus.ON_HOLD
                || status == WorkOrderStatus.PENDING_VERIFICATION)
                && hasScopedPermission(workOrder, WorkOrderAction.REASSIGN, relationships, operator)) {
            actions.add("REASSIGN");
        }
        if (status == WorkOrderStatus.ON_HOLD && isCurrentAssignee(workOrder)
                && hasScopedPermission(workOrder, WorkOrderAction.RESUME, relationships, operator)) {
            actions.add("RESUME");
        }
        if (status == WorkOrderStatus.PENDING_VERIFICATION) {
            boolean ownResponse = workOrder.getCurrentResponse() != null
                    && workOrder.getCurrentResponse().getSubmittedBy() != null
                    && Objects.equals(workOrder.getCurrentResponse().getSubmittedBy().getId(), snapshot.membershipId());
            if (hasScopedPermission(workOrder, WorkOrderAction.APPROVE_VERIFICATION, relationships, operator)
                    && (!ownResponse || hasScopedPermission(
                    workOrder, WorkOrderAction.SELF_APPROVE_VERIFICATION, relationships, operator))) {
                actions.add("VERIFY_APPROVE");
            }
            if (hasScopedPermission(workOrder, WorkOrderAction.REJECT_VERIFICATION, relationships, operator)
                    && (!ownResponse || hasScopedPermission(
                    workOrder, WorkOrderAction.SELF_REJECT_VERIFICATION, relationships, operator))) {
                actions.add("VERIFY_REJECT");
            }
        }
        if (hasScopedPermission(workOrder, WorkOrderAction.CANCEL, relationships, operator)) actions.add("CANCEL");
        if (hasScopedPermission(workOrder, WorkOrderAction.TRANSFER_CREATE, relationships, operator)) {
            actions.add("TRANSFER_CREATE");
        }
        if (status == WorkOrderStatus.SUBMITTED && !hasCurrent
                && hasScopedPermission(workOrder, WorkOrderAction.WITHDRAW, relationships, operator)) {
            actions.add("WITHDRAW");
        }
        return Collections.unmodifiableSet(actions);
    }

    public boolean requiresSupervisoryVerificationOverride(WorkOrder workOrder,
                                                           WorkOrderAction verificationAction,
                                                           User submittedBy,
                                                           User verifier) {
        if (!isSelfVerification(submittedBy, verifier)) {
            return false;
        }
        WorkOrderAction selfAction = verificationAction == WorkOrderAction.APPROVE_VERIFICATION
                ? WorkOrderAction.SELF_APPROVE_VERIFICATION
                : WorkOrderAction.SELF_REJECT_VERIFICATION;
        return hasActionAccess(workOrder, selfAction);
    }

    public boolean isSelfVerification(User submittedBy, User verifier) {
        return submittedBy != null && Objects.equals(submittedBy.getId(), verifier.getId());
    }

    private boolean hasScopedPermission(WorkOrder workOrder, WorkOrderAction action) {
        UserAuthorizationSnapshot snapshot = currentAuthorizationSnapshot();
        return hasScopedPermission(workOrder, action, relationships(workOrder, snapshot));
    }

    private boolean hasScopedPermission(WorkOrder workOrder,
                                        WorkOrderAction action,
                                        WorkOrderRelationships relationships) {
        User operator = action == WorkOrderAction.CLAIM ? currentUserOrThrow() : null;
        return hasScopedPermission(workOrder, action, relationships, operator);
    }

    private boolean hasScopedPermission(WorkOrder workOrder,
                                        WorkOrderAction action,
                                        WorkOrderRelationships relationships,
                                        User operator) {
        UserAuthorizationSnapshot snapshot = currentAuthorizationSnapshot();
        String permission = permissionFor(PermissionCatalog.requireWorkOrderEntry(workOrder.type()), action);
        if (!snapshot.authorities().contains(permission)) {
            return false;
        }
        boolean ownerAccessible = organizationAccessible(snapshot, workOrder.getOwnerOrgId());
        return switch (action) {
            case VIEW, EXPORT, HISTORY, AUDIT, READ_ATTACHMENT ->
                    readOrganizationAccessible(snapshot, workOrder, ownerAccessible)
                            || relationships.reporter() || relationships.participant();
            case ACCEPT_ASSIGNMENT, REJECT_ASSIGNMENT -> relationships.pendingHandler();
            case SUBMIT_RESPONSE, HOLD, RESUME -> relationships.currentHandler();
            case WITHDRAW -> relationships.reporter();
            case SELF_APPROVE_VERIFICATION, SELF_REJECT_VERIFICATION ->
                    ownerAccessible && relationships.responseSubmitter();
            case REPORT -> true;
            case CLAIM -> ownerAccessible
                    && operator != null
                    && departmentParticipationService.canCollaborateForClaim(
                    workOrder.getTenantId(), workOrder.getResponsibleDepartmentId(), operator);
            case CREATE_ASSIGNMENT, REASSIGN, APPROVE_VERIFICATION,
                    REJECT_VERIFICATION, CANCEL, TRANSFER_CREATE, TRANSFER_CANCEL -> ownerAccessible;
            case TRANSFER_ACCEPT, TRANSFER_REJECT -> true;
        };
    }

    private boolean readOrganizationAccessible(UserAuthorizationSnapshot snapshot,
                                               WorkOrder workOrder,
                                               boolean ownerAccessible) {
        if (ownerAccessible) {
            return true;
        }
        Set<UUID> naturalOrganizations = hierarchyService.descendantIds(
                snapshot.tenantId(), snapshot.orgUnitId());
        return organizationParticipationRepository
                .existsByTenantIdAndWorkOrderIdAndOrgUnitIdInAndActiveTrue(
                        snapshot.tenantId(), workOrder.getId(), naturalOrganizations);
    }

    private boolean organizationAccessible(UserAuthorizationSnapshot snapshot, UUID targetOrgId) {
        return snapshot.systemKey() == com.pro.ereportbacken.model.pojo.UserSystemKey.SUPER_ADMINISTRATOR
                || hierarchyService.treeContains(snapshot.tenantId(), snapshot.orgUnitId(), targetOrgId);
    }

    private void requireSameTenant(WorkOrder workOrder) {
        if (!Objects.equals(workOrder.getTenantId(), tenantContext.requireTenantId())) {
            throw new EntityNotFoundException("工单不存在：" + workOrder.getId());
        }
    }

    private boolean isCurrentAssignee(WorkOrder workOrder) {
        return isUserAssignee(workOrder, currentAuthorizationSnapshot());
    }

    private boolean isUserReporter(WorkOrder workOrder, UserAuthorizationSnapshot snapshot) {
        return workOrder.getReportedBy() != null
                && Objects.equals(workOrder.getReportedBy().getId(), snapshot.membershipId());
    }

    private boolean isUserAssignee(WorkOrder workOrder, UserAuthorizationSnapshot snapshot) {
        return workOrder.getCurrentAssignment() != null
                && workOrder.getCurrentAssignment().isCurrent()
                && Objects.equals(workOrder.getCurrentAssignment().getAssignee().getId(), snapshot.membershipId());
    }

    private boolean isCurrentResponsibleHandler(WorkOrder workOrder, UserAuthorizationSnapshot snapshot) {
        return workOrder.getCurrentAssignment() != null
                && workOrder.getCurrentAssignment().isCurrent()
                && Objects.equals(workOrder.getCurrentAssignment().getAssignee().getId(), snapshot.membershipId());
    }

    private WorkOrderRelationships relationships(WorkOrder workOrder, UserAuthorizationSnapshot snapshot) {
        boolean historicalHandler = assignmentRepository
                .existsByTenantIdAndWorkOrderIdAndAssigneeIdAndAcceptedAtIsNotNull(
                        workOrder.getTenantId(), workOrder.getId(), snapshot.membershipId());
        return relationships(workOrder, snapshot, historicalHandler);
    }

    private WorkOrderRelationships relationships(WorkOrder workOrder,
                                                 UserAuthorizationSnapshot snapshot,
                                                 boolean historicalHandler) {
        boolean pendingHandler = workOrder.getPendingAssignment() != null
                && workOrder.getPendingAssignment().isPending()
                && Objects.equals(workOrder.getPendingAssignment().getAssignee().getId(), snapshot.membershipId());
        boolean currentHandler = isCurrentResponsibleHandler(workOrder, snapshot);
        boolean responseSubmitter = workOrder.getCurrentResponse() != null
                && workOrder.getCurrentResponse().getSubmittedBy() != null
                && Objects.equals(workOrder.getCurrentResponse().getSubmittedBy().getId(), snapshot.membershipId());
        return new WorkOrderRelationships(
                isUserReporter(workOrder, snapshot),
                pendingHandler,
                currentHandler,
                responseSubmitter,
                pendingHandler || currentHandler || historicalHandler
        );
    }

    private UserAuthorizationSnapshot currentAuthorizationSnapshot() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TenantUserDetails principal
                && principal.authorizationSnapshot() != null) {
            return principal.authorizationSnapshot();
        }
        return authorizationCache.snapshot(currentUserOrThrow());
    }

    private String resolveUsername(Object principal) {
        String username = switch (principal) {
            case UserDetails userDetails -> userDetails.getUsername();
            case String value -> value;
            default -> "";
        };
        if (!StringUtils.hasText(username)) {
            throw new AccessDeniedException("无法识别当前登录用户。" );
        }
        return username;
    }

    private String permissionFor(PermissionCatalog.WorkOrderEntry entry, WorkOrderAction action) {
        return switch (action) {
            case REPORT -> entry.reportPermission();
            case CLAIM -> entry.claimPermission();
            case ACCEPT_ASSIGNMENT -> entry.assignmentAcceptPermission();
            case REJECT_ASSIGNMENT -> entry.assignmentRejectPermission();
            case SUBMIT_RESPONSE -> entry.responseSubmitPermission();
            case CREATE_ASSIGNMENT -> entry.assignmentCreatePermission();
            case REASSIGN -> entry.assignmentReassignPermission();
            case APPROVE_VERIFICATION -> entry.verificationApprovePermission();
            case REJECT_VERIFICATION -> entry.verificationRejectPermission();
            case SELF_APPROVE_VERIFICATION -> entry.verificationSelfApprovePermission();
            case SELF_REJECT_VERIFICATION -> entry.verificationSelfRejectPermission();
            case CANCEL -> entry.cancelPermission();
            case WITHDRAW -> entry.withdrawPermission();
            case HOLD -> entry.holdPermission();
            case RESUME -> entry.resumePermission();
            case TRANSFER_CREATE -> entry.transferCreatePermission();
            case TRANSFER_ACCEPT -> entry.transferAcceptPermission();
            case TRANSFER_REJECT -> entry.transferRejectPermission();
            case TRANSFER_CANCEL -> entry.transferCancelPermission();
            case VIEW -> entry.viewPermission();
            case EXPORT -> entry.exportPermission();
            case HISTORY -> entry.historyPermission();
            case AUDIT -> entry.auditPermission();
            case READ_ATTACHMENT -> entry.attachmentReadPermission();
        };
    }

    private record WorkOrderRelationships(
            boolean reporter,
            boolean pendingHandler,
            boolean currentHandler,
            boolean responseSubmitter,
            boolean participant
    ) {
    }
}
