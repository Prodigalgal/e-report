package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.dto.AuthorizationOperationRequestDTO;
import com.pro.ereportbacken.model.dto.AuthorizationOptionsDTO;
import com.pro.ereportbacken.model.dto.AuthorizationPlanRequestDTO;
import com.pro.ereportbacken.model.dto.AuthorizationPlanResponseDTO;
import com.pro.ereportbacken.model.dto.PermissionGrantViewDTO;
import com.pro.ereportbacken.model.dto.UserAuthorizationViewDTO;
import com.pro.ereportbacken.model.pojo.AuthorizationChangePlan;
import com.pro.ereportbacken.model.pojo.AuthorizationChangePlanStatus;
import com.pro.ereportbacken.model.pojo.AuthorizationOperationType;
import com.pro.ereportbacken.model.pojo.AuthorizationProfile;
import com.pro.ereportbacken.model.pojo.AuthorizationProfilePermissionRule;
import com.pro.ereportbacken.model.pojo.GrantIssuerType;
import com.pro.ereportbacken.model.pojo.Permission;
import com.pro.ereportbacken.model.pojo.PermissionGrant;
import com.pro.ereportbacken.model.pojo.PermissionGrantStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserSystemKey;
import com.pro.ereportbacken.repository.AuthorizationChangePlanRepository;
import com.pro.ereportbacken.repository.AuthorizationProfilePermissionRuleRepository;
import com.pro.ereportbacken.repository.AuthorizationProfileRepository;
import com.pro.ereportbacken.repository.PermissionGrantRepository;
import com.pro.ereportbacken.repository.PermissionRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.AdministrativeControlAction;
import com.pro.ereportbacken.security.AdministrativeControlService;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.SecurityHashingService;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.security.TenantUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AuthorizationAdministrationService {

    private static final int MAX_PLAN_OPERATIONS = 200;
    private static final long PLAN_TTL_MINUTES = 15;

    private final TenantContext tenantContext;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionGrantRepository grantRepository;
    private final AuthorizationProfileRepository templateRepository;
    private final AuthorizationProfilePermissionRuleRepository templateRuleRepository;
    private final AuthorizationChangePlanRepository planRepository;
    private final PermissionGrantChainService grantChainService;
    private final AdministrativeControlService administrativeControlService;
    private final OrganizationHierarchyService hierarchyService;
    private final AuthorizationChangePlanTokenService planTokenService;
    private final SecurityHashingService hashingService;
    private final ObjectMapper objectMapper;

    public AuthorizationAdministrationService(
            TenantContext tenantContext,
            UserRepository userRepository,
            PermissionRepository permissionRepository,
            PermissionGrantRepository grantRepository,
            AuthorizationProfileRepository templateRepository,
            AuthorizationProfilePermissionRuleRepository templateRuleRepository,
            AuthorizationChangePlanRepository planRepository,
            PermissionGrantChainService grantChainService,
            AdministrativeControlService administrativeControlService,
            OrganizationHierarchyService hierarchyService,
            AuthorizationChangePlanTokenService planTokenService,
            SecurityHashingService hashingService,
            ObjectMapper objectMapper) {
        this.tenantContext = tenantContext;
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.grantRepository = grantRepository;
        this.templateRepository = templateRepository;
        this.templateRuleRepository = templateRuleRepository;
        this.planRepository = planRepository;
        this.grantChainService = grantChainService;
        this.administrativeControlService = administrativeControlService;
        this.hierarchyService = hierarchyService;
        this.planTokenService = planTokenService;
        this.hashingService = hashingService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public UserAuthorizationViewDTO view(UUID membershipId) {
        UUID tenantId = tenantContext.requireTenantId();
        User target = requireTarget(tenantId, membershipId);
        requireAuthority(PermissionCatalog.USER_AUTHORIZATION_VIEW);
        administrativeControlService.require(target, AdministrativeControlAction.VIEW_AUTHORIZATION);
        LocalDateTime now = UtcTime.now();
        List<PermissionGrant> currentGrants = currentGrants(tenantId, membershipId);
        Map<UUID, Permission> permissionsById = permissionsById(currentGrants);
        List<PermissionGrantViewDTO> views = currentGrants.stream()
                .map(grant -> toView(target, grant, permissionsById.get(grant.getPermissionId()), now))
                .sorted(Comparator.comparing(PermissionGrantViewDTO::permissionDisplayName)
                        .thenComparing(PermissionGrantViewDTO::permissionName))
                .toList();
        int effectiveCount = Math.toIntExact(views.stream().filter(PermissionGrantViewDTO::effective).count());
        return new UserAuthorizationViewDTO(
                target.getId(), target.getAuthorizationVersion(), views, effectiveCount);
    }

    @Transactional(readOnly = true)
    public AuthorizationOptionsDTO options(UUID membershipId) {
        UUID tenantId = tenantContext.requireTenantId();
        User target = requireTarget(tenantId, membershipId);
        requireAuthority(PermissionCatalog.USER_AUTHORIZATION_VIEW);
        administrativeControlService.require(target, AdministrativeControlAction.VIEW_AUTHORIZATION);
        TenantUserDetails actor = currentPrincipal();
        Set<String> grantablePermissions = grantablePermissionNames(actor, UtcTime.now());

        List<AuthorizationOptionsDTO.PermissionOption> permissions = PermissionCatalog.entries().stream()
                .filter(PermissionCatalog.Entry::delegable)
                .filter(entry -> grantablePermissions.contains(entry.name()))
                .map(entry -> new AuthorizationOptionsDTO.PermissionOption(
                        entry.name(), entry.displayName(), entry.description(), entry.riskLevel(),
                        entry.systemScoped(), entry.apiTokenAssignable()))
                .sorted(Comparator.comparing(AuthorizationOptionsDTO.PermissionOption::displayName)
                        .thenComparing(AuthorizationOptionsDTO.PermissionOption::name))
                .toList();
        List<AuthorizationOptionsDTO.TemplateOption> templates = templateOptions(tenantId, grantablePermissions);
        List<PermissionGrant> actorParentGrants = grantChainService
                .effectiveGrants(actor.tenantId(), actor.membershipId(), UtcTime.now()).stream()
                .filter(grant -> Boolean.TRUE.equals(grant.getGrantOption()))
                .toList();
        Map<UUID, Permission> parentPermissions = permissionsById(actorParentGrants);
        List<AuthorizationOptionsDTO.ParentGrantOption> parentGrants = actorParentGrants.stream()
                .map(grant -> {
                    Permission permission = parentPermissions.get(grant.getPermissionId());
                    if (permission == null || !PermissionCatalog.contains(permission.getName())) {
                        return null;
                    }
                    PermissionCatalog.Entry definition = PermissionCatalog.require(permission.getName());
                    return new AuthorizationOptionsDTO.ParentGrantOption(
                            grant.getId(), definition.name(), definition.displayName(), grant.getExpiresAt());
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(AuthorizationOptionsDTO.ParentGrantOption::permissionDisplayName)
                        .thenComparing(AuthorizationOptionsDTO.ParentGrantOption::permissionName))
                .toList();
        return new AuthorizationOptionsDTO(templates, permissions, parentGrants);
    }

    @Transactional
    public AuthorizationPlanResponseDTO createPlan(UUID membershipId, AuthorizationPlanRequestDTO request) {
        TenantUserDetails actor = currentPrincipal();
        UUID tenantId = tenantContext.requireTenantId();
        User target = requireTarget(tenantId, membershipId);
        administrativeControlService.require(target, AdministrativeControlAction.MANAGE_ACCESS);
        AuthorizationPlanRequestDTO normalized = normalizeAndExpand(tenantId, target.getId(), request);
        PlanAnalysis analysis = analyze(target, actor, normalized, UtcTime.now());
        String requestJson = writeJson(normalized);
        AuthorizationPlanResponseDTO unsignedPreview = new AuthorizationPlanResponseDTO(
                "", UtcTime.now().plusMinutes(PLAN_TTL_MINUTES),
                target.getAuthorizationVersion(), analysis.previews(), analysis.resultingActiveGrantCount());

        AuthorizationChangePlan plan = new AuthorizationChangePlan();
        plan.setTenantId(tenantId);
        plan.setTargetMembershipId(target.getId());
        plan.setOperatorMembershipId(actor.membershipId());
        plan.setExpectedAuthorizationVersion(target.getAuthorizationVersion());
        plan.setStatus(AuthorizationChangePlanStatus.READY);
        plan.setRequestJson(requestJson);
        plan.setPreviewJson(writeJson(unsignedPreview));
        plan.setRequestDigest(hashingService.sha256(requestJson));
        plan.setExpiresAt(UtcTime.now().plusMinutes(PLAN_TTL_MINUTES));
        plan = planRepository.saveAndFlush(plan);
        String token = planTokenService.issue(plan);
        return new AuthorizationPlanResponseDTO(
                token, plan.getExpiresAt(), plan.getExpectedAuthorizationVersion(),
                analysis.previews(), analysis.resultingActiveGrantCount());
    }

    @Transactional(readOnly = true)
    public AuthorizationPlanResponseDTO previewPlan(UUID membershipId, AuthorizationPlanRequestDTO request) {
        TenantUserDetails actor = currentPrincipal();
        UUID tenantId = tenantContext.requireTenantId();
        User target = requireTarget(tenantId, membershipId);
        administrativeControlService.require(target, AdministrativeControlAction.MANAGE_ACCESS);
        AuthorizationPlanRequestDTO normalized = normalizeAndExpand(tenantId, target.getId(), request);
        PlanAnalysis analysis = analyze(target, actor, normalized, UtcTime.now());
        return new AuthorizationPlanResponseDTO(
                "", UtcTime.now().plusMinutes(PLAN_TTL_MINUTES),
                target.getAuthorizationVersion(), analysis.previews(), analysis.resultingActiveGrantCount());
    }

    @Transactional(readOnly = true)
    public AuthorizationPlanResponseDTO previewImportPlan(
            UUID membershipId, AuthorizationPlanRequestDTO request) {
        return previewPlan(membershipId, request);
    }

    @Transactional(readOnly = true)
    public AuthorizationPlanResponseDTO previewNewUserImportPlan(
            UUID targetOrgUnitId, List<AuthorizationOperationRequestDTO> operations) {
        TenantUserDetails actor = currentPrincipal();
        UUID tenantId = tenantContext.requireTenantId();
        if (!actor.tenantId().equals(tenantId)
                || (!isSameOrDescendant(tenantId, actor.orgUnitId(), targetOrgUnitId)
                && actor.authorizationSnapshot().systemKey() != UserSystemKey.SUPER_ADMINISTRATOR)) {
            throw new AccessDeniedException("新用户目标组织不在当前账号的组织治理范围内。");
        }
        User target = new User();
        target.setId(UUID.randomUUID());
        target.setTenantId(tenantId);
        target.setOrgUnitId(targetOrgUnitId);
        target.setAuthorizationVersion(0L);
        AuthorizationPlanRequestDTO normalized = normalizeAndExpand(
                tenantId, null, new AuthorizationPlanRequestDTO(0L, operations));
        PlanAnalysis analysis = analyze(target, actor, normalized, UtcTime.now());
        return new AuthorizationPlanResponseDTO(
                "", UtcTime.now().plusMinutes(PLAN_TTL_MINUTES), 0L,
                analysis.previews(), analysis.resultingActiveGrantCount());
    }

    @Transactional(readOnly = true)
    public boolean canDelegateProfile(AuthorizationProfile template) {
        if (template == null || !Boolean.TRUE.equals(template.getActive())
                || template.getCurrentVersionId() == null) {
            return false;
        }
        TenantUserDetails actor = currentPrincipal();
        Set<String> grantable = grantablePermissionNames(actor, UtcTime.now());
        List<AuthorizationProfilePermissionRule> rules = templateRuleRepository
                .findByTenantIdAndProfileVersionIdOrderBySortOrderAsc(
                        actor.tenantId(), template.getCurrentVersionId());
        Map<UUID, String> permissionNames = permissionRepository.findAllById(
                        rules.stream().map(AuthorizationProfilePermissionRule::getPermissionId)
                                .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Permission::getId, Permission::getName));
        return !rules.isEmpty() && rules.stream()
                .map(rule -> permissionNames.get(rule.getPermissionId()))
                .allMatch(grantable::contains);
    }

    @Transactional
    public UserAuthorizationViewDTO applyPlan(UUID membershipId, String planToken, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.trim().length() > 120) {
            throw new IllegalArgumentException("必须提供长度不超过120的 Idempotency-Key。");
        }
        TenantUserDetails actor = currentPrincipal();
        UUID tenantId = tenantContext.requireTenantId();
        UUID planId = UUID.fromString(planTokenService.planId(planToken));
        AuthorizationChangePlan plan = planRepository.findByTenantIdAndId(tenantId, planId)
                .orElseThrow(() -> new IllegalArgumentException("授权变更计划不存在。"));
        if (!planTokenService.matches(plan, planToken)
                || !plan.getTargetMembershipId().equals(membershipId)
                || !plan.getOperatorMembershipId().equals(actor.membershipId())) {
            throw new AccessDeniedException("授权变更计划不属于当前操作人或目标用户。");
        }
        User target = requireTarget(tenantId, membershipId);
        administrativeControlService.require(target, AdministrativeControlAction.MANAGE_ACCESS);
        String normalizedKey = idempotencyKey.trim();
        if (plan.getStatus() == AuthorizationChangePlanStatus.APPLIED) {
            if (!normalizedKey.equals(plan.getIdempotencyKey())) {
                throw new IllegalStateException("该授权计划已使用另一幂等键执行。");
            }
            return viewAfterMutation(membershipId);
        }
        if (plan.getStatus() != AuthorizationChangePlanStatus.READY
                || !plan.getExpiresAt().isAfter(UtcTime.now())) {
            plan.setStatus(AuthorizationChangePlanStatus.EXPIRED);
            planRepository.save(plan);
            throw new IllegalStateException("授权变更计划已失效，请重新预览。");
        }
        if (!Objects.equals(plan.getExpectedAuthorizationVersion(), target.getAuthorizationVersion())) {
            throw new IllegalStateException("用户授权已发生变化，请刷新后重新预览。");
        }
        AuthorizationPlanRequestDTO request = readRequest(plan.getRequestJson());
        analyze(target, actor, request, UtcTime.now());
        for (AuthorizationOperationRequestDTO operation : request.operations()) {
            applyOperation(target, actor, operation);
        }
        plan.setStatus(AuthorizationChangePlanStatus.APPLIED);
        plan.setAppliedAt(UtcTime.now());
        plan.setIdempotencyKey(normalizedKey);
        planRepository.save(plan);
        return viewAfterMutation(membershipId);
    }

    private PlanAnalysis analyze(User target,
                                 TenantUserDetails actor,
                                 AuthorizationPlanRequestDTO request,
                                 LocalDateTime now) {
        administrativeControlService.require(target, AdministrativeControlAction.MANAGE_ACCESS);
        if (!Objects.equals(request.expectedAuthorizationVersion(), target.getAuthorizationVersion())) {
            throw new IllegalStateException("用户授权已发生变化，请刷新后重新预览。");
        }
        List<PermissionGrant> current = currentGrants(target.getTenantId(), target.getId());
        Map<UUID, PermissionGrant> grantsById = current.stream()
                .collect(Collectors.toMap(PermissionGrant::getId, Function.identity()));
        Map<UUID, Permission> permissionsById = permissionsById(current);
        Set<UUID> removed = new HashSet<>();
        Set<String> addedPermissions = new HashSet<>();
        int resultingCount = Math.toIntExact(current.stream()
                .filter(grant -> grant.getStatus() == PermissionGrantStatus.ACTIVE).count());
        List<AuthorizationPlanResponseDTO.OperationPreview> previews = new ArrayList<>();

        for (AuthorizationOperationRequestDTO operation : request.operations()) {
            switch (operation.type()) {
                case GRANT_PERMISSION -> {
                    requireAuthority(PermissionCatalog.USER_AUTHORIZATION_GRANT);
                    Permission permission = requirePermission(operation.permissionName());
                    ensureReason(operation.reason());
                    if (current.stream().anyMatch(grant -> grant.getStatus() == PermissionGrantStatus.ACTIVE
                            && grant.getPermissionId().equals(permission.getId()))
                            || !addedPermissions.add(permission.getName())) {
                        throw new IllegalArgumentException("目标用户已经持有该原子权限，请使用调整操作：" + permission.getName());
                    }
                    PermissionGrant parent = requireDelegableActorGrant(
                            actor, permission, operation.expiresAt(), now);
                    requireGrantOptionAuthority(operation.grantOption());
                    requireTargetOrganizationScope(target, actor);
                    AuthorizationPlanResponseDTO.GrantSnapshot after = requestedSnapshot(
                            null, parent.getId(), permission, PermissionGrantStatus.ACTIVE,
                            Boolean.TRUE.equals(operation.grantOption()), operation.validFrom(), operation.expiresAt());
                    previews.add(preview(operation.type(), "新增原子权限", permission.getDisplayName(), true,
                            new AuthorizationPlanResponseDTO.PermissionChangePreview(
                                    AuthorizationPlanResponseDTO.ChangeKind.ADD, null, after)));
                    resultingCount++;
                }
                case ADJUST_PERMISSION -> {
                    requireAuthority(PermissionCatalog.USER_AUTHORIZATION_ADJUST);
                    PermissionGrant grant = requireCurrentGrant(grantsById, operation.grantId(), removed);
                    Permission sourcePermission = requirePermission(permissionsById, grant);
                    Permission targetPermission = StringUtils.hasText(operation.permissionName())
                            ? requirePermission(operation.permissionName())
                            : sourcePermission;
                    requireGrantManageable(target, actor, grant, sourcePermission, true);
                    PermissionGrant targetParent = requireDelegableActorGrant(
                            actor, targetPermission, operation.expiresAt(), now);
                    boolean adjustedGrantOption = operation.grantOption() == null
                            ? Boolean.TRUE.equals(grant.getGrantOption())
                            : Boolean.TRUE.equals(operation.grantOption());
                    requireGrantOptionAuthority(adjustedGrantOption);
                    ensureReason(operation.reason());
                    boolean permissionChanging = !sourcePermission.getId().equals(targetPermission.getId());
                    if (permissionChanging) {
                        requireAuthority(PermissionCatalog.USER_AUTHORIZATION_GRANT);
                        requireAuthority(PermissionCatalog.USER_AUTHORIZATION_REVOKE);
                        if (current.stream().anyMatch(existing -> existing.getStatus() == PermissionGrantStatus.ACTIVE
                                && existing.getPermissionId().equals(targetPermission.getId()))
                                || !addedPermissions.add(targetPermission.getName())) {
                            throw new IllegalArgumentException(
                                    "目标用户已经持有替换后的原子权限：" + targetPermission.getName());
                        }
                        requireTargetOrganizationScope(target, actor);
                        removed.add(grant.getId());
                    }
                    previews.add(preview(operation.type(),
                            permissionChanging ? "替换原子权限" : "调整原子权限",
                            permissionChanging
                                    ? sourcePermission.getDisplayName() + " → " + targetPermission.getDisplayName()
                                    : sourcePermission.getDisplayName(),
                            permissionChanging,
                            new AuthorizationPlanResponseDTO.PermissionChangePreview(
                                    AuthorizationPlanResponseDTO.ChangeKind.UPDATE,
                                    snapshot(grant, sourcePermission, now),
                                    requestedSnapshot(permissionChanging ? null : grant.getId(),
                                            targetParent.getId(), targetPermission,
                                            PermissionGrantStatus.ACTIVE,
                                            adjustedGrantOption,
                                            operation.validFrom(), operation.expiresAt()))));
                }
                case REVOKE_PERMISSION -> {
                    requireAuthority(PermissionCatalog.USER_AUTHORIZATION_REVOKE);
                    PermissionGrant grant = requireCurrentGrant(grantsById, operation.grantId(), removed);
                    Permission permission = requirePermission(permissionsById, grant);
                    requireGrantManageable(target, actor, grant, permission, false);
                    ensureReason(operation.reason());
                    previews.add(preview(operation.type(), "撤销原子权限", permission.getDisplayName(), false,
                            new AuthorizationPlanResponseDTO.PermissionChangePreview(
                                    AuthorizationPlanResponseDTO.ChangeKind.REMOVE,
                                    snapshot(grant, permission, now), null)));
                    removed.add(grant.getId());
                    resultingCount--;
                }
                case REATTACH_PERMISSION -> {
                    requireAuthority(PermissionCatalog.USER_AUTHORIZATION_REATTACH);
                    PermissionGrant grant = grantsById.get(operation.grantId());
                    if (grant == null || grant.getStatus() != PermissionGrantStatus.SUSPENDED_UPSTREAM) {
                        throw new IllegalArgumentException("只有上游失效而暂停的授权可以重新挂接。");
                    }
                    requireUserManagedGrant(grant);
                    Permission permission = requirePermission(permissionsById, grant);
                    requireTargetOrganizationScope(target, actor);
                    PermissionGrant parent = requireGrantForActor(actor, operation.parentGrantId(), permission, now);
                    ensureReason(operation.reason());
                    previews.add(preview(operation.type(), "重新挂接授权", permission.getDisplayName(), true,
                            new AuthorizationPlanResponseDTO.PermissionChangePreview(
                                    AuthorizationPlanResponseDTO.ChangeKind.UPDATE,
                                    snapshot(grant, permission, now),
                                    requestedSnapshot(grant.getId(), parent.getId(), permission,
                                            PermissionGrantStatus.ACTIVE, Boolean.TRUE.equals(grant.getGrantOption()),
                                            grant.getValidFrom(), grant.getExpiresAt()))));
                    resultingCount++;
                }
                case APPLY_TEMPLATE -> throw new IllegalStateException("模板操作必须在预检前展开。");
            }
        }
        if (previews.isEmpty()) {
            throw new IllegalArgumentException("授权清单没有产生任何变化。");
        }
        return new PlanAnalysis(List.copyOf(previews), resultingCount);
    }

    private void applyOperation(User target,
                                TenantUserDetails actor,
                                AuthorizationOperationRequestDTO operation) {
        switch (operation.type()) {
            case GRANT_PERMISSION -> grantChainService.issueChildGrant(
                    target.getTenantId(), actor.membershipId(), target.getId(), operation.permissionName(),
                    operation.validFrom(), operation.expiresAt(), Boolean.TRUE.equals(operation.grantOption()),
                    operation.reason());
            case ADJUST_PERMISSION -> {
                PermissionGrant current = grantRepository.findById(operation.grantId())
                        .filter(grant -> grant.getTenantId().equals(target.getTenantId()))
                        .orElseThrow(() -> new IllegalArgumentException("目标 Grant 不存在。"));
                Permission currentPermission = permissionRepository.findById(current.getPermissionId())
                        .orElseThrow(() -> new IllegalStateException("目标 Grant 引用了不存在的权限。"));
                boolean grantOption = operation.grantOption() == null
                        ? Boolean.TRUE.equals(current.getGrantOption())
                        : Boolean.TRUE.equals(operation.grantOption());
                if (StringUtils.hasText(operation.permissionName())
                        && !operation.permissionName().equals(currentPermission.getName())) {
                    grantChainService.revoke(
                            target.getTenantId(), current.getId(), actor.membershipId(), operation.reason());
                    grantChainService.issueChildGrant(
                            target.getTenantId(), actor.membershipId(), target.getId(), operation.permissionName(),
                            operation.validFrom(), operation.expiresAt(), grantOption, operation.reason());
                } else {
                    PermissionGrant parent = requireDelegableActorGrant(
                            actor, currentPermission, operation.expiresAt(), UtcTime.now());
                    grantChainService.replace(
                            target.getTenantId(), operation.grantId(), parent.getId(), actor.membershipId(), operation.validFrom(),
                            operation.expiresAt(), grantOption, operation.reason());
                }
            }
            case REVOKE_PERMISSION -> grantChainService.revoke(
                    target.getTenantId(), operation.grantId(), actor.membershipId(), operation.reason());
            case REATTACH_PERMISSION -> grantChainService.reattachSubtree(
                    target.getTenantId(), operation.grantId(), operation.parentGrantId(),
                    actor.membershipId(), operation.reason());
            case APPLY_TEMPLATE -> throw new IllegalStateException("模板操作必须在执行前展开。");
        }
    }

    private AuthorizationPlanRequestDTO normalizeAndExpand(UUID tenantId,
                                                            UUID targetMembershipId,
                                                            AuthorizationPlanRequestDTO request) {
        if (request == null || request.operations() == null || request.operations().isEmpty()) {
            throw new IllegalArgumentException("授权变更清单不能为空。");
        }
        if (request.operations().size() > MAX_PLAN_OPERATIONS) {
            throw new IllegalArgumentException("单次授权变更不能超过" + MAX_PLAN_OPERATIONS + "项。");
        }
        List<AuthorizationOperationRequestDTO> expanded = new ArrayList<>();
        Set<String> existingPermissionNames = targetMembershipId == null
                ? Set.of()
                : currentActivePermissionNames(tenantId, targetMembershipId);
        Set<String> templatePlannedPermissionNames = new HashSet<>(existingPermissionNames);
        for (AuthorizationOperationRequestDTO operation : request.operations()) {
            if (operation.type() != AuthorizationOperationType.APPLY_TEMPLATE) {
                expanded.add(normalize(operation));
                continue;
            }
            requireAuthority(PermissionCatalog.AUTHORIZATION_TEMPLATE_APPLY);
            AuthorizationProfile template = templateRepository.findByTenantIdAndId(tenantId, operation.templateId())
                    .filter(profile -> Boolean.TRUE.equals(profile.getActive()))
                    .orElseThrow(() -> new IllegalArgumentException("岗位模板不存在或已停用。"));
            if (template.getCurrentVersionId() == null) {
                throw new IllegalStateException("岗位模板没有可使用的权限清单。");
            }
            List<AuthorizationProfilePermissionRule> rules = templateRuleRepository
                    .findByTenantIdAndProfileVersionIdOrderBySortOrderAsc(tenantId, template.getCurrentVersionId());
            Map<UUID, Permission> permissions = permissionRepository.findAllById(
                            rules.stream().map(AuthorizationProfilePermissionRule::getPermissionId)
                                    .collect(Collectors.toSet())).stream()
                    .collect(Collectors.toMap(Permission::getId, Function.identity()));
            for (AuthorizationProfilePermissionRule rule : rules) {
                Permission permission = permissions.get(rule.getPermissionId());
                if (permission == null || !PermissionCatalog.contains(permission.getName())) {
                    throw new IllegalStateException("岗位模板引用了无效权限。" );
                }
                if (!templatePlannedPermissionNames.add(permission.getName())) {
                    continue;
                }
                expanded.add(new AuthorizationOperationRequestDTO(
                        AuthorizationOperationType.GRANT_PERMISSION,
                        null,
                        null,
                        null,
                        permission.getName(),
                        operation.validFrom(),
                        operation.expiresAt(),
                        Boolean.TRUE.equals(operation.grantOption()),
                        StringUtils.hasText(operation.reason())
                                ? operation.reason().trim()
                                : "应用岗位模板：" + template.getDisplayName()));
            }
        }
        if (expanded.isEmpty()) {
            throw new IllegalArgumentException("授权清单没有产生任何变化。" );
        }
        if (expanded.size() > MAX_PLAN_OPERATIONS) {
            throw new IllegalArgumentException("模板展开后单次授权变更不能超过" + MAX_PLAN_OPERATIONS + "项。");
        }
        return new AuthorizationPlanRequestDTO(request.expectedAuthorizationVersion(), List.copyOf(expanded));
    }

    private Set<String> currentActivePermissionNames(UUID tenantId, UUID membershipId) {
        List<PermissionGrant> grants = grantRepository.findByTenantIdAndMembershipIdAndStatus(
                tenantId, membershipId, PermissionGrantStatus.ACTIVE);
        Map<UUID, String> permissionNames = permissionRepository.findAllById(
                        grants.stream().map(PermissionGrant::getPermissionId).collect(Collectors.toSet()))
                .stream()
                .filter(permission -> PermissionCatalog.contains(permission.getName()))
                .collect(Collectors.toMap(Permission::getId, Permission::getName));
        return grants.stream()
                .map(grant -> permissionNames.get(grant.getPermissionId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private AuthorizationOperationRequestDTO normalize(AuthorizationOperationRequestDTO operation) {
        return new AuthorizationOperationRequestDTO(
                operation.type(), operation.grantId(), operation.parentGrantId(), operation.templateId(),
                operation.permissionName() == null ? null : operation.permissionName().trim(),
                truncate(operation.validFrom()), truncate(operation.expiresAt()), operation.grantOption(),
                operation.reason() == null ? null : operation.reason().trim());
    }

    private List<AuthorizationOptionsDTO.TemplateOption> templateOptions(
            UUID tenantId, Set<String> grantablePermissions) {
        List<AuthorizationProfile> templates = templateRepository
                .findByTenantIdAndActiveTrueOrderBySortOrderAscDisplayNameAsc(tenantId);
        Map<UUID, AuthorizationProfile> byVersion = templates.stream()
                .filter(template -> template.getCurrentVersionId() != null)
                .collect(Collectors.toMap(AuthorizationProfile::getCurrentVersionId, Function.identity()));
        List<AuthorizationProfilePermissionRule> rules = byVersion.isEmpty()
                ? List.of()
                : templateRuleRepository.findByTenantIdAndProfileVersionIdInOrderBySortOrderAsc(
                tenantId, byVersion.keySet());
        Map<UUID, String> permissionNames = permissionRepository.findAllById(
                        rules.stream().map(AuthorizationProfilePermissionRule::getPermissionId)
                                .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Permission::getId, Permission::getName));
        Map<UUID, List<String>> namesByTemplate = new LinkedHashMap<>();
        for (AuthorizationProfilePermissionRule rule : rules) {
            AuthorizationProfile template = byVersion.get(rule.getProfileVersionId());
            String permissionName = permissionNames.get(rule.getPermissionId());
            if (template != null && permissionName != null && PermissionCatalog.contains(permissionName)) {
                namesByTemplate.computeIfAbsent(template.getId(), ignored -> new ArrayList<>()).add(permissionName);
            }
        }
        return templates.stream()
                .map(template -> new AuthorizationOptionsDTO.TemplateOption(
                        template.getId(), template.getCode(), template.getDisplayName(), template.getDescription(),
                        Boolean.TRUE.equals(template.getBuiltIn()), Boolean.TRUE.equals(template.getActive()),
                        namesByTemplate.getOrDefault(template.getId(), List.of())))
                .filter(template -> !template.permissionNames().isEmpty()
                        && template.permissionNames().stream().allMatch(grantablePermissions::contains))
                .toList();
    }

    private PermissionGrantViewDTO toView(
            User target, PermissionGrant grant, Permission permission, LocalDateTime now) {
        if (permission == null) {
            throw new IllegalStateException("Grant 引用了不存在的权限：" + grant.getId());
        }
        PermissionCatalog.Entry definition = PermissionCatalog.require(permission.getName());
        Manageability manageability = grantManageability(target, currentPrincipal(), grant, permission);
        return new PermissionGrantViewDTO(
                grant.getId(), grant.getParentGrantId(), definition.name(), definition.displayName(),
                definition.description(), definition.riskLevel(),
                grant.getStatus(), grantChainService.isEffectivelyActive(grant, now),
                Boolean.TRUE.equals(grant.getGrantOption()), grant.getValidFrom(), grant.getExpiresAt(),
                grant.getIssuerType(), grant.getGrantedByMembershipId(), grant.getGrantedAt(), grant.getReason(),
                manageability.manageable(), manageability.reason());
    }

    private Manageability grantManageability(
            User target, TenantUserDetails actor, PermissionGrant grant, Permission permission) {
        if (isSystemManagedGrant(grant)) {
            return new Manageability(false, "系统基线 Grant 只能由系统授权流程维护。");
        }
        if (!actor.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals(PermissionCatalog.USER_AUTHORIZATION_ADJUST)
                        || authority.getAuthority().equals(PermissionCatalog.USER_AUTHORIZATION_REVOKE))) {
            return new Manageability(false, "缺少授权调整或撤销权限。");
        }
        if (actor.authorizationSnapshot().systemKey() == UserSystemKey.SUPER_ADMINISTRATOR) {
            return new Manageability(true, null);
        }
        PermissionGrant actorGrant = effectiveActorGrant(actor, permission, UtcTime.now());
        if (actorGrant == null || !Boolean.TRUE.equals(actorGrant.getGrantOption())) {
            return new Manageability(false, "当前账号没有该原子权限的转授权能力。");
        }
        if (!isSameOrDescendant(target.getTenantId(), actor.orgUnitId(), target.getOrgUnitId())) {
            return new Manageability(false, "目标用户不在当前账号的组织治理范围内。");
        }
        return new Manageability(true, null);
    }

    private void requireGrantManageable(
            User target,
            TenantUserDetails actor,
            PermissionGrant grant,
            Permission permission,
            boolean adjust) {
        Manageability manageability = grantManageability(target, actor, grant, permission);
        if (!manageability.manageable()) {
            throw new AccessDeniedException(manageability.reason());
        }
        requireAuthority(adjust
                ? PermissionCatalog.USER_AUTHORIZATION_ADJUST
                : PermissionCatalog.USER_AUTHORIZATION_REVOKE);
    }

    private void requireUserManagedGrant(PermissionGrant grant) {
        if (isSystemManagedGrant(grant)) {
            throw new AccessDeniedException("系统基线 Grant 只能由系统授权流程维护。");
        }
    }

    private boolean isSystemManagedGrant(PermissionGrant grant) {
        GrantIssuerType issuerType = grant.getIssuerType();
        return issuerType == GrantIssuerType.SYSTEM_ROOT
                || issuerType == GrantIssuerType.SYSTEM_BASELINE
                || issuerType == GrantIssuerType.SYSTEM_SELF_BASELINE;
    }

    private void requireTargetOrganizationScope(User target, TenantUserDetails actor) {
        if (!actor.tenantId().equals(target.getTenantId())
                || (!isSameOrDescendant(target.getTenantId(), actor.orgUnitId(), target.getOrgUnitId())
                && actor.authorizationSnapshot().systemKey() != UserSystemKey.SUPER_ADMINISTRATOR)) {
            throw new AccessDeniedException("目标用户不在当前账号的组织治理范围内。");
        }
    }

    private boolean isSameOrDescendant(UUID tenantId, UUID actorOrgUnitId, UUID targetOrgUnitId) {
        return actorOrgUnitId.equals(targetOrgUnitId)
                || hierarchyService.treeContains(tenantId, actorOrgUnitId, targetOrgUnitId);
    }

    private PermissionGrant requireDelegableActorGrant(
            TenantUserDetails actor, Permission permission, LocalDateTime expiresAt, LocalDateTime now) {
        PermissionGrant actorGrant = effectiveActorGrant(actor, permission, now);
        if (actorGrant == null || !Boolean.TRUE.equals(actorGrant.getGrantOption())) {
            throw new AccessDeniedException("当前账号没有该原子权限的有效转授权能力：" + permission.getName());
        }
        if (actorGrant.getExpiresAt() != null
                && (expiresAt == null || expiresAt.isAfter(actorGrant.getExpiresAt()))) {
            throw new IllegalArgumentException("下游授权有效期不能超过当前账号自身授权有效期。");
        }
        return actorGrant;
    }

    private PermissionGrant requireGrantForActor(
            TenantUserDetails actor, UUID parentGrantId, Permission permission, LocalDateTime now) {
        if (parentGrantId == null) {
            throw new IllegalArgumentException("重新挂接必须选择新的父 Grant。");
        }
        PermissionGrant parent = grantRepository.findById(parentGrantId)
                .filter(grant -> grant.getTenantId().equals(actor.tenantId()))
                .filter(grant -> grant.getMembershipId().equals(actor.membershipId()))
                .filter(grant -> grant.getPermissionId().equals(permission.getId()))
                .orElseThrow(() -> new AccessDeniedException("新的父 Grant 不属于当前账号。"));
        if (!Boolean.TRUE.equals(parent.getGrantOption()) || !grantChainService.isEffectivelyActive(parent, now)) {
            throw new AccessDeniedException("新的父 Grant 没有有效转授权能力。");
        }
        return parent;
    }

    private PermissionGrant effectiveActorGrant(
            TenantUserDetails actor, Permission permission, LocalDateTime now) {
        return grantChainService.effectiveGrants(actor.tenantId(), actor.membershipId(), now).stream()
                .filter(grant -> grant.getPermissionId().equals(permission.getId()))
                .findFirst()
                .orElse(null);
    }

    private Set<String> grantablePermissionNames(TenantUserDetails actor, LocalDateTime now) {
        return grantChainService.effectiveGrants(actor.tenantId(), actor.membershipId(), now).stream()
                .filter(grant -> Boolean.TRUE.equals(grant.getGrantOption()))
                .map(PermissionGrant::getPermissionId)
                .collect(Collectors.collectingAndThen(Collectors.toSet(), ids ->
                        permissionRepository.findAllById(ids).stream()
                                .map(Permission::getName)
                                .filter(PermissionCatalog::contains)
                                .collect(Collectors.toUnmodifiableSet())));
    }

    private List<PermissionGrant> currentGrants(UUID tenantId, UUID membershipId) {
        return grantRepository.findByTenantIdAndMembershipIdInAndStatusIn(
                        tenantId,
                        List.of(membershipId),
                        List.of(PermissionGrantStatus.ACTIVE, PermissionGrantStatus.SUSPENDED_UPSTREAM)).stream()
                .filter(grant -> grant.getMembershipId().equals(membershipId))
                .toList();
    }

    private Map<UUID, Permission> permissionsById(List<PermissionGrant> grants) {
        return permissionRepository.findAllById(
                        grants.stream().map(PermissionGrant::getPermissionId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Permission::getId, Function.identity()));
    }

    private Permission requirePermission(Map<UUID, Permission> permissionsById, PermissionGrant grant) {
        Permission permission = permissionsById.get(grant.getPermissionId());
        if (permission == null) {
            throw new IllegalStateException("Grant 引用了不存在的权限：" + grant.getId());
        }
        PermissionCatalog.require(permission.getName());
        return permission;
    }

    private Permission requirePermission(String permissionName) {
        if (!StringUtils.hasText(permissionName)) {
            throw new IllegalArgumentException("原子权限码不能为空。");
        }
        PermissionCatalog.require(permissionName.trim());
        return permissionRepository.findByName(permissionName.trim())
                .orElseThrow(() -> new IllegalStateException("数据库缺少权限目录项：" + permissionName));
    }

    private PermissionGrant requireCurrentGrant(
            Map<UUID, PermissionGrant> grantsById, UUID grantId, Set<UUID> removed) {
        PermissionGrant grant = grantsById.get(grantId);
        if (grant == null || removed.contains(grantId)) {
            throw new IllegalArgumentException("目标 Grant 不存在或已在本次计划中撤销。");
        }
        return grant;
    }

    private AuthorizationPlanResponseDTO.OperationPreview preview(
            AuthorizationOperationType type,
            String title,
            String detail,
            boolean increasesAccess,
            AuthorizationPlanResponseDTO.PermissionChangePreview change) {
        return new AuthorizationPlanResponseDTO.OperationPreview(
                type, title, detail, increasesAccess, true, List.of(change));
    }

    private AuthorizationPlanResponseDTO.GrantSnapshot snapshot(
            PermissionGrant grant, Permission permission, LocalDateTime now) {
        PermissionCatalog.Entry definition = PermissionCatalog.require(permission.getName());
        return new AuthorizationPlanResponseDTO.GrantSnapshot(
                grant.getId(), grant.getParentGrantId(), definition.name(), definition.displayName(),
                grant.getStatus(), Boolean.TRUE.equals(grant.getGrantOption()),
                grant.getValidFrom(), grant.getExpiresAt());
    }

    private AuthorizationPlanResponseDTO.GrantSnapshot requestedSnapshot(
            UUID grantId,
            UUID parentGrantId,
            Permission permission,
            PermissionGrantStatus status,
            boolean grantOption,
            LocalDateTime validFrom,
            LocalDateTime expiresAt) {
        PermissionCatalog.Entry definition = PermissionCatalog.require(permission.getName());
        return new AuthorizationPlanResponseDTO.GrantSnapshot(
                grantId, parentGrantId, definition.name(), definition.displayName(),
                status, grantOption, validFrom, expiresAt);
    }

    private UserAuthorizationViewDTO viewAfterMutation(UUID membershipId) {
        return view(membershipId);
    }

    private User requireTarget(UUID tenantId, UUID membershipId) {
        return userRepository.findByTenantIdAndId(tenantId, membershipId)
                .orElseThrow(() -> new IllegalArgumentException("目标用户不存在。"));
    }

    private TenantUserDetails currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantUserDetails principal)) {
            throw new AccessDeniedException("需要已认证的租户用户。");
        }
        return principal;
    }

    private void requireAuthority(String permission) {
        TenantUserDetails principal = currentPrincipal();
        if (principal.getAuthorities().stream().noneMatch(authority -> authority.getAuthority().equals(permission))) {
            throw new AccessDeniedException("缺少权限：" + permission);
        }
    }

    private void requireGrantOptionAuthority(Boolean grantOption) {
        if (Boolean.TRUE.equals(grantOption)) {
            requireAuthority(PermissionCatalog.USER_AUTHORIZATION_GRANT_OPTION_ISSUE);
        }
    }

    private void ensureReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("授权变更原因不能为空。");
        }
    }

    private LocalDateTime truncate(LocalDateTime value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MILLIS);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("授权变更计划序列化失败。", exception);
        }
    }

    private AuthorizationPlanRequestDTO readRequest(String json) {
        try {
            return objectMapper.readValue(json, AuthorizationPlanRequestDTO.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("授权变更计划内容无法读取。", exception);
        }
    }

    private record PlanAnalysis(
            List<AuthorizationPlanResponseDTO.OperationPreview> previews,
            int resultingActiveGrantCount) {
    }

    private record Manageability(boolean manageable, String reason) {
    }
}
