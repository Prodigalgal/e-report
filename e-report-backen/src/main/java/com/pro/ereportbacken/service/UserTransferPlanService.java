package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.dto.AuthorizationOperationRequestDTO;
import com.pro.ereportbacken.model.dto.AuthorizationPlanResponseDTO;
import com.pro.ereportbacken.model.dto.AuthorizationProfileDTO;
import com.pro.ereportbacken.model.dto.DepartmentDTO;
import com.pro.ereportbacken.model.dto.OrgUnitDTO;
import com.pro.ereportbacken.model.dto.UserTransferAuthorizationDiffDTO;
import com.pro.ereportbacken.model.dto.UserTransferFieldChangeDTO;
import com.pro.ereportbacken.model.dto.UserTransferPreviewSummaryDTO;
import com.pro.ereportbacken.model.pojo.AuthorizationOperationType;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserOrganizationMoveStrategy;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.SecurityHashingService;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.security.TenantUserDetails;
import com.pro.ereportbacken.security.UserStatusPermissionPolicy;
import com.pro.ereportbacken.time.DisplayTime;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

@Service
public class UserTransferPlanService {

    private static final String CLEAR_VALUE = "#CLEAR";

    private final UserTransferWorkbookService workbookService;
    private final UserRepository userRepository;
    private final OrgUnitService orgUnitService;
    private final DepartmentService departmentService;
    private final AuthorizationProfileService profileService;
    private final TenantContext tenantContext;
    private final OrganizationScopeAuthorizationService scopeAuthorizationService;
    private final AuthorizationAdministrationService authorizationService;
    private final UserOrganizationMoveAuthorizationPlanner organizationMoveAuthorizationPlanner;
    private final UserService userService;
    private final PasswordPolicy passwordPolicy;
    private final SecurityHashingService hashingService;
    private final ObjectMapper objectMapper;

    public UserTransferPlanService(
            UserTransferWorkbookService workbookService,
            UserRepository userRepository,
            OrgUnitService orgUnitService,
            DepartmentService departmentService,
            AuthorizationProfileService profileService,
            TenantContext tenantContext,
            OrganizationScopeAuthorizationService scopeAuthorizationService,
            AuthorizationAdministrationService authorizationService,
            UserOrganizationMoveAuthorizationPlanner organizationMoveAuthorizationPlanner,
            UserService userService,
            PasswordPolicy passwordPolicy,
            SecurityHashingService hashingService,
            ObjectMapper objectMapper) {
        this.workbookService = workbookService;
        this.userRepository = userRepository;
        this.orgUnitService = orgUnitService;
        this.departmentService = departmentService;
        this.profileService = profileService;
        this.tenantContext = tenantContext;
        this.scopeAuthorizationService = scopeAuthorizationService;
        this.authorizationService = authorizationService;
        this.organizationMoveAuthorizationPlanner = organizationMoveAuthorizationPlanner;
        this.userService = userService;
        this.passwordPolicy = passwordPolicy;
        this.hashingService = hashingService;
        this.objectMapper = objectMapper;
    }

    public TransferPlan build(FileTransferService.CompletedFile file, LocalDateTime referenceTime) {
        UUID tenantId = tenantContext.requireTenantId();
        UserTransferWorkbookService.ParsedWorkbook workbook = workbookService.parse(
                tenantId, file.fileName(), file.fileSize(), file.resource());
        List<OrgUnitDTO> organizations = orgUnitService.findAll();
        Map<String, OrgUnitDTO> organizationsByCode = organizations.stream().collect(Collectors.toMap(
                organization -> organization.code().toUpperCase(Locale.ROOT), Function.identity(),
                (first, ignored) -> first, LinkedHashMap::new));
        List<DepartmentDTO> departments = departmentService.findAccessible();
        Map<UUID, Map<String, DepartmentDTO>> departmentsByOrganization = departments.stream()
                .collect(Collectors.groupingBy(DepartmentDTO::orgUnitId, LinkedHashMap::new,
                        Collectors.toMap(department -> department.code().toUpperCase(Locale.ROOT),
                                Function.identity(), (first, ignored) -> first, LinkedHashMap::new)));
        List<AuthorizationProfileDTO> visibleProfiles = visibleProfiles();
        Map<String, AuthorizationProfileDTO> profilesByCode = visibleProfiles.stream()
                .collect(Collectors.toMap(profile -> profile.code().toUpperCase(Locale.ROOT), Function.identity()));
        Map<String, PermissionCatalog.Entry> permissionsByName = visiblePermissions().stream()
                .collect(Collectors.toMap(PermissionCatalog.Entry::name, Function.identity()));

        List<UserRow> userRows = workbook.userCommands().stream().map(UserRow::new).toList();
        List<AuthorizationRow> authorizationRows = new ArrayList<>();
        workbook.templateApplyCommands().forEach(raw -> authorizationRows.add(new TemplateRow(raw)));
        workbook.grantCommands().forEach(raw -> authorizationRows.add(new GrantRow(raw)));
        normalizePhones(userRows, authorizationRows);
        addDuplicateUserRowErrors(userRows);

        Set<String> phones = new LinkedHashSet<>();
        userRows.stream().map(row -> row.phone).filter(Objects::nonNull).forEach(phones::add);
        authorizationRows.stream().map(row -> row.phone).filter(Objects::nonNull).forEach(phones::add);
        Map<String, User> globalUsersByPhone = userRepository.findByPhoneIn(phones).stream()
                .collect(Collectors.toMap(User::getPhone, Function.identity()));
        Map<String, User> tenantUsersByPhone = globalUsersByPhone.values().stream()
                .filter(user -> user.getTenantId().equals(tenantId))
                .collect(Collectors.toMap(User::getPhone, Function.identity()));

        Map<String, UserMutation> userMutations = normalizeUsers(
                userRows, globalUsersByPhone, tenantUsersByPhone, organizationsByCode,
                departmentsByOrganization);
        normalizeAuthorizationRows(
                authorizationRows, userMutations, tenantUsersByPhone, organizationsByCode,
                profilesByCode, permissionsByName, referenceTime);
        applyOrganizationMoveStrategies(
                userRows, authorizationRows, userMutations, tenantUsersByPhone, organizationsByCode);
        validateAuthorizationPlans(authorizationRows, userMutations, tenantUsersByPhone);
        propagateTargetErrors(userRows, authorizationRows);

        List<PreviewRow> previewRows = new ArrayList<>();
        userRows.forEach(row -> previewRows.add(row.preview()));
        authorizationRows.forEach(row -> previewRows.add(row.preview()));
        previewRows.sort(Comparator
                .comparing(PreviewRow::phoneHash, Comparator.nullsLast(String::compareTo))
                .thenComparing(PreviewRow::sheetName)
                .thenComparingInt(PreviewRow::rowNumber));
        Set<String> blockedPhones = previewRows.stream()
                .filter(row -> !row.errors().isEmpty())
                .map(PreviewRow::phoneHash)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, List<AuthorizationOperationRequestDTO>> operationsByPhone = authorizationRows.stream()
                .filter(row -> row.errors.isEmpty() && row.operation != null)
                .collect(Collectors.groupingBy(
                        row -> row.phone, LinkedHashMap::new,
                        Collectors.mapping(row -> row.operation, Collectors.toList())));

        List<UserExecutionPlan> executions = phones.stream().sorted()
                .filter(phone -> !blockedPhones.contains(phoneHash(phone)))
                .map(phone -> {
                    User existing = tenantUsersByPhone.get(phone);
                    return new UserExecutionPlan(
                            phone,
                            userMutations.get(phone),
                            operationsByPhone.getOrDefault(phone, List.of()),
                            existing == null ? null : existing.getId(),
                            existing == null ? null : existing.getAuthorizationVersion());
                }).toList();
        String catalogDigest = catalogDigest(
                organizations, departments, visibleProfiles, permissionsByName.values());
        String userSnapshotDigest = userSnapshotDigest(phones, tenantUsersByPhone);
        String planDigest = planDigest(executions);
        UserTransferPreviewSummaryDTO summary = summarize(previewRows);
        return new TransferPlan(
                workbook.templateVersion(), executions, previewRows, catalogDigest,
                userSnapshotDigest, planDigest, summary,
                (int) previewRows.stream().filter(row -> row.errors().isEmpty()).count(),
                (int) previewRows.stream().filter(row -> !row.errors().isEmpty()).count());
    }

    private UserTransferPreviewSummaryDTO summarize(List<PreviewRow> rows) {
        Set<String> users = rows.stream().map(PreviewRow::phoneHash)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> affectedUsers = rows.stream().filter(PreviewRow::hasChanges)
                .map(PreviewRow::phoneHash).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> blockedUsers = rows.stream().filter(row -> !row.errors().isEmpty())
                .map(PreviewRow::phoneHash).filter(Objects::nonNull).collect(Collectors.toSet());
        int fieldChanges = rows.stream().mapToInt(row -> row.fieldChanges().size()).sum();
        List<AuthorizationPlanResponseDTO.PermissionChangePreview> permissionChanges = rows.stream()
                .flatMap(row -> row.authorizationDiff().changes().stream()).toList();
        return new UserTransferPreviewSummaryDTO(
                users.size(), affectedUsers.size(), blockedUsers.size(),
                (int) rows.stream().filter(row -> row.errors().isEmpty()).count(),
                (int) rows.stream().filter(row -> !row.errors().isEmpty()).count(),
                (int) rows.stream().filter(row -> row.errors().isEmpty() && !row.hasChanges()).count(),
                fieldChanges,
                (int) permissionChanges.stream().filter(change ->
                        change.kind() == AuthorizationPlanResponseDTO.ChangeKind.ADD).count(),
                (int) permissionChanges.stream().filter(change ->
                        change.kind() == AuthorizationPlanResponseDTO.ChangeKind.UPDATE).count(),
                (int) permissionChanges.stream().filter(change ->
                        change.kind() == AuthorizationPlanResponseDTO.ChangeKind.REMOVE).count());
    }

    public List<UserTransferWorkbookService.ProfileCatalogEntry> visibleProfileCatalog() {
        return visibleProfiles().stream().map(profile -> {
            AuthorizationProfileDTO.Version version = profile.currentVersion();
            return new UserTransferWorkbookService.ProfileCatalogEntry(
                    profile.code(), profile.displayName(), version == null ? 0 : version.versionNo(),
                    version == null ? 0 : version.rules().size(), profile.description());
        }).toList();
    }

    public List<UserTransferWorkbookService.PermissionCatalogEntry> visiblePermissionCatalog() {
        return visiblePermissions().stream().map(permission ->
                new UserTransferWorkbookService.PermissionCatalogEntry(
                        permission.name(), permission.displayName(), permission.riskLevel(),
                        permission.description())).toList();
    }

    private List<AuthorizationProfileDTO> visibleProfiles() {
        return profileService.list().stream()
                .filter(AuthorizationProfileDTO::active)
                .filter(profile -> profile.currentVersion() != null)
                .filter(profile -> profile.currentVersion().rules().stream().allMatch(rule ->
                        scopeAuthorizationService.canGrantPermission(rule.permissionName())))
                .toList();
    }

    private List<PermissionCatalog.Entry> visiblePermissions() {
        return PermissionCatalog.entries().stream()
                .filter(permission -> permission.delegable() && !permission.systemScoped())
                .filter(permission -> scopeAuthorizationService.canGrantPermission(permission.name()))
                .toList();
    }

    private Map<String, UserMutation> normalizeUsers(
            List<UserRow> rows,
            Map<String, User> globalUsers,
            Map<String, User> tenantUsers,
            Map<String, OrgUnitDTO> organizations,
            Map<UUID, Map<String, DepartmentDTO>> departments) {
        Map<String, UserMutation> mutations = new LinkedHashMap<>();
        for (UserRow row : rows) {
            if (row.phone == null) continue;
            row.action = parseEnum(row.raw.action(), UserAction.class,
                    "用户动作仅支持 CREATE、UPDATE、ORG_MOVE、DEPARTMENT_MOVE。", row.errors);
            if (row.action == null) continue;
            User existing = tenantUsers.get(row.phone);
            User global = globalUsers.get(row.phone);
            if (global != null && existing == null) {
                row.errors.add("手机号已被其他租户账号使用。" );
                continue;
            }
            switch (row.action) {
                case CREATE -> normalizeCreate(row, existing, organizations, departments);
                case UPDATE -> normalizeUpdate(row, existing);
                case ORG_MOVE -> normalizeMove(row, existing, organizations);
                case DEPARTMENT_MOVE -> normalizeDepartmentMove(row, existing, departments);
            }
            if (row.errors.isEmpty()) {
                row.mutation = new UserMutation(
                        row.phone, row.action, row.username, row.raw.initialPassword(), row.name,
                        row.emailSpecified, row.email, row.status,
                        row.organization == null ? null : row.organization.id(),
                        row.department == null ? null : row.department.id(), row.moveStrategy,
                        existing == null ? null : existing.getAuthorizationVersion());
                if (mutations.putIfAbsent(row.phone, row.mutation) != null) {
                    row.errors.add("同一手机号只能填写一条用户资料命令。" );
                }
            }
        }
        return mutations;
    }

    private void normalizeCreate(UserRow row,
                                 User existing,
                                 Map<String, OrgUnitDTO> organizations,
                                 Map<UUID, Map<String, DepartmentDTO>> departments) {
        if (existing != null) {
            row.errors.add("手机号已存在，不能执行 CREATE。" );
            return;
        }
        row.name = required(row.raw.name(), "创建用户必须填写姓名。", row.errors);
        row.organization = organization(row.raw.organizationCode(), organizations, true, row.errors);
        if (row.organization != null && StringUtils.hasText(row.raw.departmentCode())) {
            row.department = department(row.raw.departmentCode(), row.organization.id(), departments, false, row.errors);
        }
        row.username = StringUtils.hasText(row.raw.username()) ? row.raw.username().trim() : row.phone;
        if (userRepository.findByUsername(row.username).isPresent()) row.errors.add("登录账号已被使用。" );
        if (!StringUtils.hasText(row.raw.initialPassword())) row.errors.add("创建用户必须填写初始密码。" );
        row.emailSpecified = StringUtils.hasText(row.raw.email());
        row.email = row.emailSpecified ? normalizeEmail(row.raw.email(), row.errors) : null;
        requireEmailAvailable(row.email, null, row.errors);
        row.status = parseStatus(row.raw.status(), UserStatus.ACTIVE, row.errors);
        if (row.organization != null) requireOrganizationPermission(
                row.errors, PermissionCatalog.USER_MANAGE_CREATE, row.organization.id(), "无权在目标组织创建用户。" );
        if (row.errors.isEmpty()) {
            try {
                passwordPolicy.requireValid(row.raw.initialPassword(), new PasswordPolicy.PasswordContext(
                        row.username, row.name, row.phone, row.email),
                        passwordPolicy.settingsForScope(row.organization.id()));
            } catch (IllegalArgumentException exception) {
                row.errors.add(exception.getMessage());
            }
        }
        addFieldChange(row.fieldChanges, "登录账号", null,
                Objects.equals(row.username, row.phone) ? maskPhone(row.phone) : row.username);
        addFieldChange(row.fieldChanges, "姓名", null, row.name);
        addFieldChange(row.fieldChanges, "所属组织", null,
                row.organization == null ? null : row.organization.name());
        if (row.department != null) {
            addFieldChange(row.fieldChanges, "所属部门", null, row.department.name());
        }
        if (row.emailSpecified) {
            addFieldChange(row.fieldChanges, "邮箱", null, row.email);
        }
        addFieldChange(row.fieldChanges, "账号状态", null,
                row.status == null ? null : statusLabel(row.status));
        if (StringUtils.hasText(row.raw.initialPassword())) {
            addFieldChange(row.fieldChanges, "初始密码", null, "已设置（不回显）");
        }
    }

    private void normalizeUpdate(UserRow row, User existing) {
        if (existing == null) {
            row.errors.add("手机号不存在，不能执行 UPDATE。" );
            return;
        }
        requireTargetEditable(existing, row.errors);
        requireBlank(row.raw.organizationCode(), "UPDATE 不能修改组织，请使用 ORG_MOVE。", row.errors);
        requireBlank(row.raw.departmentCode(), "UPDATE 不能修改部门，请使用 DEPARTMENT_MOVE。", row.errors);
        requireBlank(row.raw.username(), "UPDATE 不能修改登录账号。", row.errors);
        requireBlank(row.raw.initialPassword(), "UPDATE 不能通过表格修改密码。", row.errors);
        row.name = StringUtils.hasText(row.raw.name()) ? row.raw.name().trim() : null;
        row.emailSpecified = StringUtils.hasText(row.raw.email());
        row.email = row.emailSpecified
                ? CLEAR_VALUE.equalsIgnoreCase(row.raw.email().trim()) ? null : normalizeEmail(row.raw.email(), row.errors)
                : null;
        requireEmailAvailable(row.email, existing.getId(), row.errors);
        row.status = StringUtils.hasText(row.raw.status())
                ? parseStatus(row.raw.status(), existing.getStatus(), row.errors) : null;
        if (row.name != null) requireOrganizationPermission(
                row.errors, PermissionCatalog.USER_MANAGE_UPDATE_DETAILS, existing.getOrgUnitId(), "无权修改该用户资料。" );
        if (row.emailSpecified) requireOrganizationPermission(
                row.errors, PermissionCatalog.USER_MANAGE_CONTACT_UPDATE, existing.getOrgUnitId(), "无权修改该用户联系方式。" );
        if (row.status != null && row.status != existing.getStatus()) requireOrganizationPermission(
                row.errors, UserStatusPermissionPolicy.permissionForTransition(existing.getStatus(), row.status),
                existing.getOrgUnitId(), "无权执行目标账号状态变更。" );
        if (row.name != null) {
            addFieldChange(row.fieldChanges, "姓名", existing.getName(), row.name);
        }
        if (row.emailSpecified) {
            addFieldChange(row.fieldChanges, "邮箱", existing.getEmail(), row.email);
        }
        if (row.status != null) {
            addFieldChange(row.fieldChanges, "账号状态",
                    statusLabel(existing.getStatus()), statusLabel(row.status));
        }
    }

    private void normalizeMove(UserRow row,
                               User existing,
                               Map<String, OrgUnitDTO> organizations) {
        if (existing == null) {
            row.errors.add("手机号不存在，不能执行 ORG_MOVE。" );
            return;
        }
        requireTargetEditable(existing, row.errors);
        row.organization = organization(row.raw.organizationCode(), organizations, true, row.errors);
        row.moveStrategy = parseEnum(row.raw.authorizationStrategy(), UserOrganizationMoveStrategy.class,
                "组织移动的授权处理策略必须填写：KEEP（保留全部授权）或 REVOKE_ALL（撤销全部可撤销授权），请重新下载模板。", row.errors);
        requireBlank(row.raw.name(), "ORG_MOVE 只能填写手机号、目标组织和授权处理策略。", row.errors);
        requireBlank(row.raw.departmentCode(), "ORG_MOVE 会自动清空原部门，不能同时填写目标部门。", row.errors);
        requireBlank(row.raw.username(), "ORG_MOVE 不能修改登录账号。", row.errors);
        requireBlank(row.raw.initialPassword(), "ORG_MOVE 不能修改密码。", row.errors);
        requireBlank(row.raw.email(), "ORG_MOVE 不能修改邮箱。", row.errors);
        requireBlank(row.raw.status(), "ORG_MOVE 不能修改状态。", row.errors);
        requireOrganizationPermission(row.errors, PermissionCatalog.USER_MANAGE_ORGANIZATION_MOVE,
                existing.getOrgUnitId(), "无权从用户当前组织移出。" );
        if (row.organization != null) requireOrganizationPermission(row.errors,
                PermissionCatalog.USER_MANAGE_ORGANIZATION_MOVE, row.organization.id(), "无权将用户移动到目标组织。" );
        String currentOrganizationName = organizations.values().stream()
                .filter(organization -> Objects.equals(organization.id(), existing.getOrgUnitId()))
                .map(OrgUnitDTO::name)
                .findFirst()
                .orElse("未知组织");
        addFieldChange(row.fieldChanges, "所属组织", currentOrganizationName,
                row.organization == null ? null : row.organization.name());
        if (existing.getDepartmentId() != null) {
            addFieldChange(row.fieldChanges, "所属部门", "原部门", "组织直属");
        }
    }

    private void normalizeDepartmentMove(
            UserRow row,
            User existing,
            Map<UUID, Map<String, DepartmentDTO>> departments) {
        if (existing == null) {
            row.errors.add("手机号不存在，不能执行 DEPARTMENT_MOVE。");
            return;
        }
        requireTargetEditable(existing, row.errors);
        requireBlank(row.raw.organizationCode(), "DEPARTMENT_MOVE 不改变所属组织，组织编码必须留空。", row.errors);
        requireBlank(row.raw.name(), "DEPARTMENT_MOVE 只能填写手机号和目标部门编码。", row.errors);
        requireBlank(row.raw.username(), "DEPARTMENT_MOVE 不能修改登录账号。", row.errors);
        requireBlank(row.raw.initialPassword(), "DEPARTMENT_MOVE 不能修改密码。", row.errors);
        requireBlank(row.raw.email(), "DEPARTMENT_MOVE 不能修改邮箱。", row.errors);
        requireBlank(row.raw.status(), "DEPARTMENT_MOVE 不能修改状态。", row.errors);
        requireBlank(row.raw.authorizationStrategy(), "DEPARTMENT_MOVE 不处理授权，授权处理策略必须留空。", row.errors);
        String rawDepartmentCode = required(
                row.raw.departmentCode(), "DEPARTMENT_MOVE 必须填写部门编码或 #CLEAR。", row.errors);
        if (StringUtils.hasText(rawDepartmentCode) && !CLEAR_VALUE.equalsIgnoreCase(rawDepartmentCode)) {
            row.department = department(rawDepartmentCode, existing.getOrgUnitId(), departments, true, row.errors);
        }
        requireOrganizationPermission(row.errors, PermissionCatalog.USER_MANAGE_DEPARTMENT_MOVE,
                existing.getOrgUnitId(), "无权调整该用户所属部门。");
        String currentDepartmentName = departmentName(existing.getDepartmentId(), departments);
        String targetDepartmentName = row.department == null ? "组织直属" : row.department.name();
        addFieldChange(row.fieldChanges, "所属部门", currentDepartmentName, targetDepartmentName);
    }

    private void addFieldChange(List<UserTransferFieldChangeDTO> changes,
                                String field,
                                String beforeValue,
                                String afterValue) {
        if (!Objects.equals(beforeValue, afterValue)) {
            changes.add(new UserTransferFieldChangeDTO(field, beforeValue, afterValue));
        }
    }

    private void normalizeAuthorizationRows(
            List<AuthorizationRow> rows,
            Map<String, UserMutation> userMutations,
            Map<String, User> existingUsers,
            Map<String, OrgUnitDTO> organizations,
            Map<String, AuthorizationProfileDTO> profiles,
            Map<String, PermissionCatalog.Entry> permissions,
            LocalDateTime referenceTime) {
        for (AuthorizationRow row : rows) {
            if (row.phone == null) continue;
            User existing = existingUsers.get(row.phone);
            UserMutation mutation = userMutations.get(row.phone);
            if (existing == null && (mutation == null || mutation.action() != UserAction.CREATE)) {
                row.errors.add("用户不存在，必须在用户资料中提供 CREATE 命令。" );
                continue;
            }
            try {
                row.normalize(existing, organizations, profiles, permissions, referenceTime);
            } catch (IllegalArgumentException exception) {
                row.errors.add(exception.getMessage());
            }
        }
    }

    private void applyOrganizationMoveStrategies(
            List<UserRow> userRows,
            List<AuthorizationRow> authorizationRows,
            Map<String, UserMutation> mutations,
            Map<String, User> users,
            Map<String, OrgUnitDTO> organizationsByCode) {
        for (UserRow row : userRows) {
            if (row.errors.size() > 0 || row.mutation == null || row.mutation.action() != UserAction.ORG_MOVE) continue;
            if (row.mutation.moveStrategy() == UserOrganizationMoveStrategy.KEEP) continue;
            User user = users.get(row.phone);
            try {
                UserOrganizationMoveAuthorizationPlanner.Plan plan = organizationMoveAuthorizationPlanner.prepare(
                        user, row.mutation.orgUnitId(), row.mutation.moveStrategy());
                Map<UUID, String> permissionNamesByGrantId = plan.currentAuthorization().grants().stream()
                        .collect(Collectors.toMap(
                                grant -> grant.id(),
                                grant -> grant.permissionDisplayName(),
                                (first, ignored) -> first));
                for (AuthorizationOperationRequestDTO operation : plan.operations()) {
                    String permissionDisplayName = permissionNamesByGrantId.getOrDefault(
                            operation.grantId(), "未知权限");
                    authorizationRows.add(new SyntheticAuthorizationRow(
                            row.phone,
                            row.raw.rowNumber(),
                            operation,
                            "组织迁移授权处理：" + permissionDisplayName));
                }
            } catch (RuntimeException exception) {
                row.errors.add(exception.getMessage());
            }
        }
    }

    private void validateAuthorizationPlans(
            List<AuthorizationRow> rows,
            Map<String, UserMutation> mutations,
            Map<String, User> existingUsers) {
        Map<String, List<AuthorizationRow>> byPhone = rows.stream()
                .filter(row -> row.phone != null)
                .collect(Collectors.groupingBy(row -> row.phone, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<AuthorizationRow>> entry : byPhone.entrySet()) {
            List<AuthorizationRow> userRows = entry.getValue();
            if (userRows.stream().anyMatch(row -> !row.errors.isEmpty())) continue;
            List<AuthorizationOperationRequestDTO> operations = userRows.stream()
                    .map(row -> row.operation).filter(Objects::nonNull).toList();
            if (operations.isEmpty()) continue;
            try {
                User existing = existingUsers.get(entry.getKey());
                AuthorizationPlanResponseDTO preview;
                if (existing == null) {
                    UserMutation mutation = mutations.get(entry.getKey());
                    preview = authorizationService.previewNewUserImportPlan(mutation.orgUnitId(), operations);
                } else {
                    preview = authorizationService.previewImportPlan(existing.getId(),
                            new com.pro.ereportbacken.model.dto.AuthorizationPlanRequestDTO(
                                    existing.getAuthorizationVersion(), operations));
                }
                List<AuthorizationPlanResponseDTO.OperationPreview> operationPreviews = preview.operations();
                int previewIndex = 0;
                for (AuthorizationRow row : userRows) {
                    if (row.operation == null) continue;
                    int operationCount = row.previewOperationCount();
                    if (previewIndex + operationCount > operationPreviews.size()) {
                        throw new IllegalStateException("授权预览结果与导入命令数量不一致。");
                    }
                    row.applyPreviews(operationPreviews.subList(previewIndex, previewIndex + operationCount));
                    previewIndex += operationCount;
                }
                if (previewIndex != operationPreviews.size()) {
                    throw new IllegalStateException("授权预览结果包含无法对应的命令。");
                }
            } catch (RuntimeException exception) {
                userRows.forEach(row -> row.errors.add(exception.getMessage()));
            }
        }
    }

    private void normalizePhones(List<UserRow> users, List<AuthorizationRow> authorizationRows) {
        users.forEach(row -> row.phone = normalizePhone(row.raw.phone(), row.errors));
        authorizationRows.forEach(row -> row.phone = normalizePhone(row.rawPhone(), row.errors));
    }

    private void addDuplicateUserRowErrors(List<UserRow> rows) {
        Map<String, Long> counts = rows.stream().filter(row -> row.phone != null)
                .collect(Collectors.groupingBy(row -> row.phone, Collectors.counting()));
        rows.stream().filter(row -> row.phone != null && counts.getOrDefault(row.phone, 0L) > 1)
                .forEach(row -> row.errors.add("同一手机号只能填写一条用户资料命令。" ));
    }

    private void propagateTargetErrors(List<UserRow> users, List<AuthorizationRow> authorizationRows) {
        Set<String> invalidPhones = new LinkedHashSet<>();
        users.stream().filter(row -> row.phone != null && !row.errors.isEmpty()).map(row -> row.phone).forEach(invalidPhones::add);
        authorizationRows.stream().filter(row -> row.phone != null && !row.errors.isEmpty()).map(row -> row.phone).forEach(invalidPhones::add);
        users.stream().filter(row -> invalidPhones.contains(row.phone) && row.errors.isEmpty())
                .forEach(row -> row.errors.add("同一用户存在其他无效命令，本用户全部命令必须一起修正。" ));
        authorizationRows.stream().filter(row -> invalidPhones.contains(row.phone) && row.errors.isEmpty())
                .forEach(row -> row.errors.add("同一用户存在其他无效命令，本用户全部命令必须一起修正。" ));
    }

    private Boolean parseGrantOption(String value, boolean nullable) {
        if (!StringUtils.hasText(value)) return nullable ? null : false;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "是", "true", "1", "yes" -> true;
            case "否", "false", "0", "no" -> false;
            default -> throw new IllegalArgumentException("允许转授权仅支持是或否。" );
        };
    }

    private LocalDateTime parseDateTime(String value, LocalDateTime defaultValue) {
        if (!StringUtils.hasText(value)) return defaultValue;
        try {
            String normalized = value.trim();
            LocalDateTime displayValue = normalized.length() <= 10
                    ? LocalDate.parse(normalized).atStartOfDay()
                    : LocalDateTime.parse(normalized.replace(' ', 'T'));
            return DisplayTime.displayToUtc(displayValue);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("时间必须使用 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss 格式。" );
        }
    }

    private LocalDateTime parseExpiry(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            String normalized = value.trim();
            LocalDateTime displayExpiry = normalized.length() <= 10
                    ? LocalDate.parse(normalized).atTime(LocalTime.MAX)
                    : LocalDateTime.parse(normalized.replace(' ', 'T'));
            LocalDateTime expiry = DisplayTime.displayToUtc(displayExpiry);
            if (!expiry.isAfter(UtcTime.now())) throw new IllegalArgumentException("授权到期时间必须晚于当前时间。" );
            return expiry;
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("到期时间必须使用 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss 格式。" );
        }
    }

    private UUID parseId(String value, String message) {
        try {
            return UUID.fromString(value.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireTargetEditable(User user, List<String> errors) {
        UserService.TargetUserAccess access = userService.targetUserAccess(user);
        if (!access.editable()) errors.add(access.protectionReason());
    }

    private String normalizePhone(String value, List<String> errors) {
        try {
            return UserContactPolicy.normalizeRequiredPhone(value);
        } catch (IllegalArgumentException exception) {
            errors.add(exception.getMessage());
            return null;
        }
    }

    private String normalizeEmail(String value, List<String> errors) {
        try {
            return UserContactPolicy.normalizeOptionalEmail(value);
        } catch (IllegalArgumentException exception) {
            errors.add(exception.getMessage());
            return null;
        }
    }

    private void requireEmailAvailable(String email, UUID currentUserId, List<String> errors) {
        if (email == null) return;
        userRepository.findByEmailIgnoreCase(email)
                .filter(user -> !Objects.equals(user.getId(), currentUserId))
                .ifPresent(user -> errors.add("邮箱已被其他用户使用。" ));
    }

    private OrgUnitDTO organization(String code,
                                    Map<String, OrgUnitDTO> organizations,
                                    boolean required,
                                    List<String> errors) {
        if (!StringUtils.hasText(code)) {
            if (required) errors.add("组织编码不能为空。" );
            return null;
        }
        OrgUnitDTO organization = organizations.get(code.trim().toUpperCase(Locale.ROOT));
        if (organization == null) errors.add("组织编码不存在或超出当前账号数据范围。" );
        return organization;
    }

    private DepartmentDTO department(
            String code,
            UUID orgUnitId,
            Map<UUID, Map<String, DepartmentDTO>> departments,
            boolean required,
            List<String> errors) {
        if (!StringUtils.hasText(code)) {
            if (required) errors.add("部门编码不能为空。");
            return null;
        }
        DepartmentDTO department = departments.getOrDefault(orgUnitId, Map.of())
                .get(code.trim().toUpperCase(Locale.ROOT));
        if (department == null || department.status() != com.pro.ereportbacken.model.pojo.DepartmentStatus.ACTIVE) {
            errors.add("部门编码不存在、已停用或不属于用户所在组织。");
            return null;
        }
        return department;
    }

    private String departmentName(UUID departmentId,
                                  Map<UUID, Map<String, DepartmentDTO>> departments) {
        if (departmentId == null) return "组织直属";
        return departments.values().stream().flatMap(map -> map.values().stream())
                .filter(department -> department.id().equals(departmentId))
                .map(DepartmentDTO::name).findFirst().orElse("未知部门");
    }

    private UserStatus parseStatus(String value, UserStatus defaultValue, List<String> errors) {
        if (!StringUtils.hasText(value)) return defaultValue;
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "启用", "ACTIVE" -> UserStatus.ACTIVE;
            case "停用", "INACTIVE" -> UserStatus.INACTIVE;
            case "禁用", "DISABLED" -> UserStatus.DISABLED;
            case "锁定", "LOCKED" -> UserStatus.LOCKED;
            default -> {
                errors.add("账号状态仅支持启用、停用、禁用、锁定。" );
                yield defaultValue;
            }
        };
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> type, String message, List<String> errors) {
        if (!StringUtils.hasText(value)) {
            errors.add(message);
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            errors.add(message);
            return null;
        }
    }

    private void requireOrganizationPermission(List<String> errors, String permission, UUID orgUnitId, String message) {
        try {
            scopeAuthorizationService.requireOrganizationAccessIfAuthenticated(permission, orgUnitId);
        } catch (AccessDeniedException exception) {
            if (!errors.contains(message)) errors.add(message);
        }
    }

    private String required(String value, String message, List<String> errors) {
        if (!StringUtils.hasText(value)) {
            errors.add(message);
            return null;
        }
        return value.trim();
    }

    private void requireBlank(String value, String message, List<String> errors) {
        if (StringUtils.hasText(value)) errors.add(message);
    }

    private String phoneHash(String phone) {
        return hashingService.protectedHash("user-transfer-phone", phone);
    }

    private String catalogDigest(
            List<OrgUnitDTO> organizations,
            List<DepartmentDTO> departments,
            List<AuthorizationProfileDTO> profiles,
            java.util.Collection<PermissionCatalog.Entry> permissions) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long operatorVersion = authentication != null && authentication.getPrincipal() instanceof TenantUserDetails principal
                ? principal.authorizationVersion() : 0L;
        List<String> facts = new ArrayList<>();
        facts.add("operatorAuthorizationVersion=" + operatorVersion);
        organizations.stream().sorted(Comparator.comparing(OrgUnitDTO::code)).forEach(org ->
                facts.add("org|" + org.id() + "|" + org.code() + "|" + org.path() + "|" + org.status()));
        departments.stream().sorted(Comparator.comparing(DepartmentDTO::code)).forEach(department ->
                facts.add("department|" + department.id() + "|" + department.orgUnitId() + "|"
                        + department.code() + "|" + department.path() + "|" + department.status()));
        profiles.stream().sorted(Comparator.comparing(AuthorizationProfileDTO::code)).forEach(profile -> {
            facts.add("profile|" + profile.id() + "|" + profile.code() + "|" + profile.currentVersion().id());
            profile.currentVersion().rules().forEach(rule -> facts.add(
                    "profile-rule|" + profile.id() + "|" + rule.permissionName()));
        });
        permissions.stream().sorted(Comparator.comparing(PermissionCatalog.Entry::name)).forEach(permission ->
                facts.add("permission|" + permission.name()));
        return hashingService.sha256(String.join("\n", facts));
    }

    private String userSnapshotDigest(Set<String> phones, Map<String, User> users) {
        List<String> facts = new ArrayList<>();
        phones.stream().sorted().forEach(phone -> {
            User user = users.get(phone);
            facts.add(user == null ? phoneHash(phone) + "|MISSING"
                    : phoneHash(phone) + "|" + user.getId() + "|" + user.getAuthorizationVersion()
                    + "|" + user.getStatus() + "|" + user.getOrgUnitId() + "|" + user.getDepartmentId());
        });
        return hashingService.sha256(String.join("\n", facts));
    }

    private String planDigest(List<UserExecutionPlan> executions) {
        try {
            return hashingService.sha256(objectMapper.writeValueAsString(executions));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("用户批量治理计划无法序列化。", exception);
        }
    }

    private String statusLabel(UserStatus status) {
        return switch (status) {
            case ACTIVE -> "启用";
            case INACTIVE -> "停用";
            case DISABLED -> "禁用";
            case LOCKED -> "锁定";
            case DELETED -> "已删除";
        };
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return "未知用户";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public record TransferPlan(
            String templateVersion,
            List<UserExecutionPlan> executions,
            List<PreviewRow> rows,
            String catalogDigest,
            String userSnapshotDigest,
            String planDigest,
            UserTransferPreviewSummaryDTO summary,
            int validRows,
            int invalidRows) {
        public TransferPlan {
            executions = List.copyOf(executions);
            rows = List.copyOf(rows);
        }
    }

    public record UserExecutionPlan(
            String phone,
            UserMutation userMutation,
            List<AuthorizationOperationRequestDTO> authorizationOperations,
            UUID existingUserId,
            Long beforeAuthorizationVersion) {
        public UserExecutionPlan {
            authorizationOperations = List.copyOf(authorizationOperations);
        }
    }

    public record UserMutation(
            String phone,
            UserAction action,
            String username,
            String initialPassword,
            String name,
            boolean emailSpecified,
            String email,
            UserStatus status,
            UUID orgUnitId,
            UUID departmentId,
            UserOrganizationMoveStrategy moveStrategy,
            Long beforeAuthorizationVersion) {
    }

    public record PreviewRow(
            String sheetName,
            int rowNumber,
            String phoneHash,
            String subjectDisplay,
            String commandType,
            List<UserTransferFieldChangeDTO> fieldChanges,
            UserTransferAuthorizationDiffDTO authorizationDiff,
            List<String> errors,
            Long beforeVersion) {
        public PreviewRow {
            fieldChanges = List.copyOf(fieldChanges);
            authorizationDiff = authorizationDiff == null
                    ? new UserTransferAuthorizationDiffDTO(null, List.of())
                    : authorizationDiff;
            errors = List.copyOf(errors);
        }

        boolean hasChanges() {
            return !fieldChanges.isEmpty() || !authorizationDiff.changes().isEmpty();
        }
    }

    public enum UserAction { CREATE, UPDATE, ORG_MOVE, DEPARTMENT_MOVE }
    private final class UserRow {
        private final UserTransferWorkbookService.RawUserCommand raw;
        private final List<String> errors = new ArrayList<>();
        private final List<UserTransferFieldChangeDTO> fieldChanges = new ArrayList<>();
        private String phone;
        private UserAction action;
        private String name;
        private String username;
        private boolean emailSpecified;
        private String email;
        private UserStatus status;
        private OrgUnitDTO organization;
        private DepartmentDTO department;
        private UserOrganizationMoveStrategy moveStrategy;
        private UserMutation mutation;

        private UserRow(UserTransferWorkbookService.RawUserCommand raw) {
            this.raw = raw;
        }

        private PreviewRow preview() {
            return new PreviewRow(UserTransferWorkbookService.USER_SHEET, raw.rowNumber(),
                    phone == null ? null : phoneHash(phone), maskPhone(phone),
                    action == null ? raw.action() : action.name(), fieldChanges,
                    new UserTransferAuthorizationDiffDTO(null, List.of()), errors,
                    mutation == null ? null : mutation.beforeAuthorizationVersion());
        }
    }

    private abstract class AuthorizationRow {
        protected final List<String> errors = new ArrayList<>();
        protected String phone;
        protected AuthorizationOperationRequestDTO operation;
        protected String authorizationSummary;
        protected List<AuthorizationPlanResponseDTO.PermissionChangePreview> authorizationChanges = List.of();

        abstract int rowNumber();
        abstract String rawPhone();
        abstract String actionText();
        abstract String sheetName();
        abstract void normalize(
                User existing,
                Map<String, OrgUnitDTO> organizations,
                Map<String, AuthorizationProfileDTO> profiles,
                Map<String, PermissionCatalog.Entry> permissions,
                LocalDateTime referenceTime);

        int previewOperationCount() {
            return 1;
        }

        PreviewRow preview() {
            return new PreviewRow(sheetName(), rowNumber(), phone == null ? null : phoneHash(phone), maskPhone(phone),
                    operation == null ? actionText() : operation.type().name(), List.of(),
                    new UserTransferAuthorizationDiffDTO(authorizationSummary, authorizationChanges), errors,
                    null);
        }

        void applyPreviews(List<AuthorizationPlanResponseDTO.OperationPreview> previews) {
            authorizationChanges = previews.stream()
                    .flatMap(preview -> preview.permissionChanges().stream())
                    .toList();
            if (previews.size() == 1) {
                authorizationSummary = previews.getFirst().title();
            } else if (StringUtils.hasText(authorizationSummary)) {
                authorizationSummary = authorizationSummary + "（" + previews.size() + "项原子权限）";
            }
        }
    }

    private final class TemplateRow extends AuthorizationRow {
        private final UserTransferWorkbookService.RawTemplateApplyCommand raw;
        private int expandedOperationCount = 1;

        private TemplateRow(UserTransferWorkbookService.RawTemplateApplyCommand raw) { this.raw = raw; }
        int rowNumber() { return raw.rowNumber(); }
        String rawPhone() { return raw.phone(); }
        String actionText() { return raw.action(); }
        String sheetName() { return UserTransferWorkbookService.TEMPLATE_APPLY_SHEET; }

        void normalize(User existing,
                       Map<String, OrgUnitDTO> organizations,
                       Map<String, AuthorizationProfileDTO> profiles,
                       Map<String, PermissionCatalog.Entry> permissions,
                       LocalDateTime referenceTime) {
            String action = raw.action().trim().toUpperCase(Locale.ROOT);
            if (!action.equals("ADD")) {
                throw new IllegalArgumentException("岗位模板追加动作仅支持 ADD。" );
            }
            AuthorizationProfileDTO profile = profiles.get(required(
                    raw.profileCode(), "岗位模板编码不能为空。", errors).toUpperCase(Locale.ROOT));
            if (profile == null) {
                throw new IllegalArgumentException("岗位模板不存在或超出当前账号转授权边界。" );
            }
            if (profile.currentVersion() == null || profile.currentVersion().rules().isEmpty()) {
                throw new IllegalArgumentException("岗位模板没有可追加的原子权限。" );
            }
            Set<String> activePermissionNames = existing == null
                    ? Set.of()
                    : authorizationService.view(existing.getId()).grants().stream()
                    .filter(grant -> grant.status()
                            == com.pro.ereportbacken.model.pojo.PermissionGrantStatus.ACTIVE)
                    .map(com.pro.ereportbacken.model.dto.PermissionGrantViewDTO::permissionName)
                    .collect(Collectors.toSet());
            expandedOperationCount = (int) profile.currentVersion().rules().stream()
                    .map(AuthorizationProfileDTO.Rule::permissionName)
                    .filter(permissionName -> !activePermissionNames.contains(permissionName))
                    .count();
            String reason = required(raw.reason(), "岗位模板追加必须填写原因。", errors);
            if (expandedOperationCount == 0) {
                operation = null;
                authorizationSummary = "岗位模板权限已全部具备，无需变更：" + profile.displayName();
                return;
            }
            operation = new AuthorizationOperationRequestDTO(
                    AuthorizationOperationType.APPLY_TEMPLATE, null, null, profile.id(), null,
                    parseDateTime(raw.validFrom(), null), parseExpiry(raw.expiresAt()),
                    parseGrantOption(raw.grantOption(), false), reason);
            authorizationSummary = "追加岗位模板权限：" + profile.displayName();
        }

        @Override
        int previewOperationCount() {
            return expandedOperationCount;
        }
    }

    private final class GrantRow extends AuthorizationRow {
        private final UserTransferWorkbookService.RawGrantCommand raw;

        private GrantRow(UserTransferWorkbookService.RawGrantCommand raw) { this.raw = raw; }
        int rowNumber() { return raw.rowNumber(); }
        String rawPhone() { return raw.phone(); }
        String actionText() { return raw.action(); }
        String sheetName() { return UserTransferWorkbookService.GRANT_COMMAND_SHEET; }

        void normalize(User existing,
                       Map<String, OrgUnitDTO> organizations,
                       Map<String, AuthorizationProfileDTO> profiles,
                       Map<String, PermissionCatalog.Entry> permissions,
                       LocalDateTime referenceTime) {
            String reason = required(raw.reason(), "原子权限变更必须填写原因。", errors);
            String action = raw.action().trim().toUpperCase(Locale.ROOT);
            switch (action) {
                case "ADD" -> {
                    PermissionCatalog.Entry permission = requireVisiblePermission(
                            raw.permissionName(), "ADD 必须填写权限编码。", permissions);
                    operation = new AuthorizationOperationRequestDTO(
                            AuthorizationOperationType.GRANT_PERMISSION, null, null, null, permission.name(),
                            parseDateTime(raw.validFrom(), null), parseExpiry(raw.expiresAt()),
                            parseGrantOption(raw.grantOption(), false), reason);
                    authorizationSummary = "新增权限：" + permission.displayName();
                }
                case "ADJUST" -> {
                    UUID grantId = parseId(raw.grantId(), "ADJUST 必须填写有效的 Grant 标识。" );
                    PermissionCatalog.Entry permission = StringUtils.hasText(raw.permissionName())
                            ? requireVisiblePermission(raw.permissionName(), "目标权限编码无效。", permissions)
                            : null;
                    operation = new AuthorizationOperationRequestDTO(
                            AuthorizationOperationType.ADJUST_PERMISSION, grantId, null, null,
                            permission == null ? null : permission.name(),
                            parseDateTime(raw.validFrom(), null), parseExpiry(raw.expiresAt()),
                            parseGrantOption(raw.grantOption(), true), reason);
                    authorizationSummary = permission == null
                            ? "调整 Grant：" + grantId
                            : "调整权限：" + permission.displayName();
                }
                case "REVOKE" -> {
                    UUID grantId = parseId(raw.grantId(), "REVOKE 必须填写有效的 Grant 标识。" );
                    operation = new AuthorizationOperationRequestDTO(
                            AuthorizationOperationType.REVOKE_PERMISSION, grantId, null, null, null,
                            null, null, null, reason);
                    authorizationSummary = "撤销 Grant：" + grantId;
                }
                case "REATTACH" -> {
                    UUID grantId = parseId(raw.grantId(), "REATTACH 必须填写有效的 Grant 标识。" );
                    UUID parentGrantId = parseId(raw.parentGrantId(), "REATTACH 必须填写有效的新父 Grant 标识。" );
                    operation = new AuthorizationOperationRequestDTO(
                            AuthorizationOperationType.REATTACH_PERMISSION, grantId, parentGrantId,
                            null, null, null, null, null, reason);
                    authorizationSummary = "重新挂接 Grant：" + grantId;
                }
                default -> throw new IllegalArgumentException(
                        "原子权限变更动作仅支持 ADD、ADJUST、REVOKE、REATTACH。" );
            }
        }

        private PermissionCatalog.Entry requireVisiblePermission(
                String rawPermissionName,
                String missingMessage,
                Map<String, PermissionCatalog.Entry> permissions) {
            String permissionName = required(rawPermissionName, missingMessage, errors);
            PermissionCatalog.Entry permission = permissions.get(permissionName);
            if (permission == null) {
                throw new IllegalArgumentException("权限不存在或超出当前账号转授权边界。" );
            }
            return permission;
        }
    }

    private final class SyntheticAuthorizationRow extends AuthorizationRow {
        private final String rawPhone;
        private final int sourceRow;
        private final String description;

        private SyntheticAuthorizationRow(String phone, int sourceRow,
                                          AuthorizationOperationRequestDTO operation, String description) {
            this.phone = phone;
            this.rawPhone = phone;
            this.sourceRow = sourceRow;
            this.operation = operation;
            this.description = description;
            this.authorizationSummary = description;
        }
        int rowNumber() { return sourceRow; }
        String rawPhone() { return rawPhone; }
        String actionText() { return operation.type().name(); }
        String sheetName() { return UserTransferWorkbookService.USER_SHEET; }
        void normalize(User existing, Map<String, OrgUnitDTO> organizations,
                       Map<String, AuthorizationProfileDTO> profiles,
                       Map<String, PermissionCatalog.Entry> permissions,
                       LocalDateTime referenceTime) {
        }
    }
}
