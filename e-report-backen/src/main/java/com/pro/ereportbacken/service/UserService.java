package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import java.util.UUID;

import com.pro.ereportbacken.model.dto.CurrentUserUpdateRequestDTO;
import com.pro.ereportbacken.model.dto.AuthorizationOperationRequestDTO;
import com.pro.ereportbacken.model.dto.AuthorizationPlanRequestDTO;
import com.pro.ereportbacken.model.dto.UserCreateRequestDTO;
import com.pro.ereportbacken.model.dto.UserResponseDTO;
import com.pro.ereportbacken.model.dto.UserUpdateRequestDTO;
import com.pro.ereportbacken.model.pojo.AuthorizationOperationType;
import com.pro.ereportbacken.model.pojo.AuthorizationProfile;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.model.pojo.UserSystemKey;
import com.pro.ereportbacken.model.pojo.UserNameSearchKeys;
import com.pro.ereportbacken.model.pojo.UserOrganizationMoveStrategy;
import com.pro.ereportbacken.repository.AuthorizationProfileRepository;
import com.pro.ereportbacken.repository.AuthorizationProfileVersionRepository;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.repository.UserWorkOrderPerformanceRepository;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.UserStatusPermissionPolicy;
import com.pro.ereportbacken.security.AdministrativeControlAction;
import com.pro.ereportbacken.security.AdministrativeControlService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.pro.ereportbacken.model.dto.UserWorkOrderPerformanceDTO;
import com.pro.ereportbacken.model.dto.UserAdministrationUpdateRequestDTO;
import com.pro.ereportbacken.model.dto.UserManagementCapabilitiesDTO;
import com.pro.ereportbacken.model.dto.UserOrganizationMovePreviewRequestDTO;
import com.pro.ereportbacken.model.dto.UserDepartmentMoveRequestDTO;

@Service
@Log4j2
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationProfileRepository profileRepository;
    private final AuthorizationProfileVersionRepository profileVersionRepository;
    private final TenantContext tenantContext;
    private final DefaultTenantService defaultTenantService;
    private final OrgUnitRepository orgUnitRepository;
    private final OrganizationScopeAuthorizationService scopeAuthorizationService;
    private final UserAuthorizationCache userAuthorizationCache;
    private final UserWorkOrderPerformanceRepository userPerformanceRepository;
    private final PasswordPolicy passwordPolicy;
    private final CredentialRevocationService credentialRevocationService;
    private final AuthorizationAdministrationService authorizationAdministrationService;
    private final OrganizationHierarchyService hierarchyService;
    private final AdministrativeControlService administrativeControlService;
    private final SystemGrantProvisioningService systemGrantProvisioningService;
    private final UserOrganizationMoveAuthorizationPlanner organizationMoveAuthorizationPlanner;
    private final DepartmentService departmentService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthorizationProfileRepository profileRepository,
                       AuthorizationProfileVersionRepository profileVersionRepository,
                       TenantContext tenantContext,
                       DefaultTenantService defaultTenantService,
                       OrgUnitRepository orgUnitRepository,
                       OrganizationScopeAuthorizationService scopeAuthorizationService,
                       UserAuthorizationCache userAuthorizationCache,
                       UserWorkOrderPerformanceRepository userPerformanceRepository,
                       PasswordPolicy passwordPolicy,
                       CredentialRevocationService credentialRevocationService,
                       AuthorizationAdministrationService authorizationAdministrationService,
                       OrganizationHierarchyService hierarchyService,
                       AdministrativeControlService administrativeControlService,
                       SystemGrantProvisioningService systemGrantProvisioningService,
                       UserOrganizationMoveAuthorizationPlanner organizationMoveAuthorizationPlanner,
                       DepartmentService departmentService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.profileRepository = profileRepository;
        this.profileVersionRepository = profileVersionRepository;
        this.tenantContext = tenantContext;
        this.defaultTenantService = defaultTenantService;
        this.orgUnitRepository = orgUnitRepository;
        this.scopeAuthorizationService = scopeAuthorizationService;
        this.userAuthorizationCache = userAuthorizationCache;
        this.userPerformanceRepository = userPerformanceRepository;
        this.passwordPolicy = passwordPolicy;
        this.credentialRevocationService = credentialRevocationService;
        this.authorizationAdministrationService = authorizationAdministrationService;
        this.hierarchyService = hierarchyService;
        this.administrativeControlService = administrativeControlService;
        this.systemGrantProvisioningService = systemGrantProvisioningService;
        this.organizationMoveAuthorizationPlanner = organizationMoveAuthorizationPlanner;
        this.departmentService = departmentService;
    }

    @Transactional
    public User createUser(UserCreateRequestDTO userDTO) {
        UUID tenantId = tenantContext.currentTenantIdOrDefault();
        String username = userDTO.getUsername().trim();
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("用户名 '" + username + "' 已在系统中使用，请更换后重试。");
        }

        User user = new User();
        user.setTenantId(tenantId);
        UUID orgUnitId = resolveOrgUnitId(tenantId, userDTO.getOrgUnitId());
        scopeAuthorizationService.requireOrganizationAccessIfAuthenticated(PermissionCatalog.USER_MANAGE_CREATE, orgUnitId);
        user.setOrgUnitId(orgUnitId);
        if (userDTO.getDepartmentId() != null) {
            departmentService.requireAssignable(tenantId, orgUnitId, userDTO.getDepartmentId());
            user.setDepartmentId(userDTO.getDepartmentId());
        }
        user.setUsername(username);
        passwordPolicy.requireValid(userDTO.getPassword(), new PasswordPolicy.PasswordContext(
                username, userDTO.getName(), userDTO.getPhone(), userDTO.getEmail()),
                passwordPolicy.settingsForScope(orgUnitId));
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setPasswordChangeRequired(true);
        user.setName(userDTO.getName());
        String phone = UserContactPolicy.normalizeRequiredPhone(userDTO.getPhone());
        String email = UserContactPolicy.normalizeOptionalEmail(userDTO.getEmail());
        requireUniqueContacts(null, phone, email);
        user.setPhone(phone);
        user.setEmail(email);
        user.setStatus(userDTO.getStatus() != null ? userDTO.getStatus() : UserStatus.ACTIVE);

        User saved = userRepository.saveAndFlush(user);
        systemGrantProvisioningService.reconcileSelfBaseline(saved);
        return saved;
    }

    public User findUserById(UUID userId) {
        User user = findTenantUserById(userId);
        scopeAuthorizationService.requireOrganizationAccessIfAuthenticated(PermissionCatalog.USER_MANAGE_VIEW, user.getOrgUnitId());
        return user;
    }

    @Transactional(readOnly = true)
    public Page<UserResponseDTO> findAllUserResponses(String searchTerm,
                                                      UUID orgUnitId,
                                                      UserStatus status,
                                                      Pageable pageable,
                                                      boolean includeDeleted) {
        return findAllUserResponses(searchTerm, orgUnitId, null, status, pageable, includeDeleted);
    }

    @Transactional(readOnly = true)
    public Page<UserResponseDTO> findAllUserResponses(String searchTerm,
                                                      UUID orgUnitId,
                                                      UUID departmentId,
                                                      UserStatus status,
                                                      Pageable pageable,
                                                      boolean includeDeleted) {
        Page<User> userPage = findAllUsers(
                searchTerm, orgUnitId, departmentId, status, pageable, includeDeleted,
                PermissionCatalog.USER_MANAGE_VIEW);
        if (userPage.isEmpty()) {
            return userPage.map(this::toUserResponseDTO);
        }
        List<User> users = userPage.getContent();
        UUID tenantId = users.get(0).getTenantId();
        Map<UUID, String> orgUnitNames = hierarchyService.namesById(
                tenantId,
                users.stream().map(User::getOrgUnitId).filter(Objects::nonNull).collect(Collectors.toSet())
        );
        Map<UUID, String> departmentNames = departmentService.namesByIds(
                tenantId,
                users.stream().map(User::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet())
        );
        return userPage.map(user -> toUserResponseDTO(
                user,
                orgUnitNames.get(user.getOrgUnitId()),
                user.getDepartmentId() == null ? null : departmentNames.get(user.getDepartmentId())
        ));
    }

    public UserResponseDTO toUserResponseDTO(User user) {
        if (user == null) {
            return null;
        }
        Map<UUID, String> organizationNames = hierarchyService.namesById(
                user.getTenantId(),
                user.getOrgUnitId() == null ? Set.of() : Set.of(user.getOrgUnitId())
        );
        String orgUnitName = user.getOrgUnitId() == null ? null : organizationNames.get(user.getOrgUnitId());
        Map<UUID, String> departmentNames = departmentService.namesByIds(
                user.getTenantId(), user.getDepartmentId() == null ? Set.of() : Set.of(user.getDepartmentId())
        );
        String departmentName = user.getDepartmentId() == null
                ? null : departmentNames.get(user.getDepartmentId());
        return toUserResponseDTO(user, orgUnitName, departmentName);
    }

    private UserResponseDTO toUserResponseDTO(User user,
                                              String orgUnitName,
                                              String departmentName) {
        UserResponseDTO response = new UserResponseDTO(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getPhone(),
                user.getEmail(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getTenantId(),
                user.getOrgUnitId(),
                orgUnitName,
                user.getDepartmentId(),
                departmentName,
                user.getAuthorizationVersion(),
                isSystemProtected(user)
        );
        return response;
    }


    @Transactional
    public User updateUserDetails(UUID userId, UserUpdateRequestDTO dto) {
        User user = findTenantUserById(userId);
        boolean nameChanging = StringUtils.hasText(dto.getName())
                && !Objects.equals(dto.getName().trim(), user.getName());
        boolean contactDetailsChanging = contactDetailsChanged(user, dto);
        boolean profileDetailsChanging = nameChanging || contactDetailsChanging;
        boolean organizationChanging = dto.getOrgUnitId() != null
                && !Objects.equals(dto.getOrgUnitId(), user.getOrgUnitId());
        if (nameChanging) {
            scopeAuthorizationService.requireOrganizationAccessIfAuthenticated(
                    PermissionCatalog.USER_MANAGE_UPDATE_DETAILS, user.getOrgUnitId());
        }
        if (profileDetailsChanging) {
            administrativeControlService.require(user,
                    identityDetailsChanged(user, dto)
                            ? AdministrativeControlAction.UPDATE_IDENTITY
                            : AdministrativeControlAction.UPDATE_DETAILS);
        }
        if (contactDetailsChanging) {
            scopeAuthorizationService.requireOrganizationAccessIfAuthenticated(
                    PermissionCatalog.USER_MANAGE_CONTACT_UPDATE, user.getOrgUnitId());
        }
        if (organizationChanging) {
            if (dto.getExpectedAuthorizationVersion() == null) {
                throw new IllegalArgumentException("调整所属组织前必须先预览变化。");
            }
            if (!Objects.equals(dto.getExpectedAuthorizationVersion(), user.getAuthorizationVersion())) {
                throw new IllegalStateException("用户资料或授权已发生变化，请刷新详情后重新预览。");
            }
            scopeAuthorizationService.requireOrganizationAccessIfAuthenticated(
                    PermissionCatalog.USER_MANAGE_ORGANIZATION_MOVE, user.getOrgUnitId());
            administrativeControlService.require(user, AdministrativeControlAction.MOVE_ORGANIZATION);
        }

        if (StringUtils.hasText(dto.getName())) {
            user.setName(dto.getName());
        }
        String phone = UserContactPolicy.normalizeRequiredPhone(dto.getPhone());
        String email = UserContactPolicy.normalizeOptionalEmail(dto.getEmail());
        requireUniqueContacts(user, phone, email);
        user.setPhone(phone);
        user.setEmail(email);
        if (organizationChanging) {
            UUID targetOrgUnitId = resolveOrgUnitId(user.getTenantId(), dto.getOrgUnitId());
            if (isSystemProtected(user)
                    && !targetOrgUnitId.equals(defaultTenantService.requireRootOrgUnit(user.getTenantId()).getId())) {
                throw new IllegalArgumentException("系统管理员必须归属租户根组织。");
            }
            scopeAuthorizationService.requireOrganizationAccessIfAuthenticated(
                    PermissionCatalog.USER_MANAGE_ORGANIZATION_MOVE, targetOrgUnitId);
            applyOrganizationMoveAuthorizationStrategy(user, targetOrgUnitId, dto.getOrganizationMoveStrategy());
            user.setOrgUnitId(targetOrgUnitId);
            user.setDepartmentId(null);
            incrementAuthorizationVersion(user);
        }
        User saved = userRepository.save(user);
        if (organizationChanging) {
            userAuthorizationCache.clearAfterCommit();
        } else {
            evictAuthorizationAfterCommit(saved);
        }
        return saved;
    }

    @Transactional
    public User updateAdministration(UUID userId, UserAdministrationUpdateRequestDTO request) {
        User user = findTenantUserById(userId);
        User result = user;
        if (request.details() != null && detailsChanged(user, request.details())) {
            result = updateUserDetails(userId, request.details());
        }
        if (request.status() != null && request.status() != result.getStatus()) {
            result = updateUserStatus(userId, request.status().name());
        }
        return result;
    }

    @Transactional
    public User moveOrganization(UUID userId, UserOrganizationMovePreviewRequestDTO request) {
        User user = findTenantUserById(userId);
        UserUpdateRequestDTO details = new UserUpdateRequestDTO();
        details.setName(user.getName());
        details.setPhone(user.getPhone());
        details.setEmail(user.getEmail());
        details.setOrgUnitId(request.targetOrgUnitId());
        details.setOrganizationMoveStrategy(request.strategy());
        details.setExpectedAuthorizationVersion(request.expectedAuthorizationVersion());
        return updateUserDetails(userId, details);
    }

    @Transactional
    public User moveDepartment(UUID userId, UserDepartmentMoveRequestDTO request) {
        User user = findTenantUserById(userId);
        if (!Objects.equals(request.expectedAuthorizationVersion(), user.getAuthorizationVersion())) {
            throw new IllegalStateException("用户资料或授权已发生变化，请刷新详情后重试。");
        }
        scopeAuthorizationService.requireOrganizationAccessIfAuthenticated(
                PermissionCatalog.USER_MANAGE_DEPARTMENT_MOVE, user.getOrgUnitId());
        administrativeControlService.require(user, AdministrativeControlAction.MOVE_DEPARTMENT);
        if (request.targetDepartmentId() != null) {
            departmentService.requireAssignable(
                    user.getTenantId(), user.getOrgUnitId(), request.targetDepartmentId());
        }
        if (Objects.equals(user.getDepartmentId(), request.targetDepartmentId())) {
            return user;
        }
        user.setDepartmentId(request.targetDepartmentId());
        incrementAuthorizationVersion(user);
        User saved = userRepository.save(user);
        evictAuthorizationAfterCommit(saved);
        return saved;
    }

    private boolean detailsChanged(User user, UserUpdateRequestDTO details) {
        return profileDetailsChanged(user, details)
                || (details.getOrgUnitId() != null && !Objects.equals(details.getOrgUnitId(), user.getOrgUnitId()));
    }

    private boolean profileDetailsChanged(User user, UserUpdateRequestDTO details) {
        String normalizedName = StringUtils.hasText(details.getName()) ? details.getName().trim() : user.getName();
        return !Objects.equals(normalizedName, user.getName()) || contactDetailsChanged(user, details);
    }

    private boolean contactDetailsChanged(User user, UserUpdateRequestDTO details) {
        String normalizedPhone = details.getPhone() == null ? null : details.getPhone().trim();
        String normalizedEmail = StringUtils.hasText(details.getEmail()) ? details.getEmail().trim() : null;
        return !Objects.equals(normalizedPhone, user.getPhone())
                || !Objects.equals(normalizedEmail, user.getEmail());
    }

    private boolean identityDetailsChanged(User user, UserUpdateRequestDTO details) {
        String normalizedPhone = details.getPhone() == null ? null : details.getPhone().trim();
        String normalizedEmail = StringUtils.hasText(details.getEmail()) ? details.getEmail().trim() : null;
        return !Objects.equals(normalizedPhone, user.getPhone())
                || !Objects.equals(normalizedEmail, user.getEmail());
    }

    private void applyOrganizationMoveAuthorizationStrategy(User user,
                                                             UUID targetOrgUnitId,
                                                             UserOrganizationMoveStrategy strategy) {
        List<AuthorizationOperationRequestDTO> operations = organizationMoveAuthorizationPlanner
                .prepare(user, targetOrgUnitId, strategy).operations();
        if (operations.isEmpty()) {
            return;
        }

        var plan = authorizationAdministrationService.createPlan(
                user.getId(), new AuthorizationPlanRequestDTO(user.getAuthorizationVersion(), operations));
        var applied = authorizationAdministrationService.applyPlan(
                user.getId(), plan.planToken(),
                "user-org-move-" + user.getId() + "-" + user.getAuthorizationVersion()
                        + "-" + targetOrgUnitId + "-" + strategy.name());
        user.setAuthorizationVersion(applied.authorizationVersion());
    }

    @Transactional
    public User updateCurrentUserProfile(UUID tenantId, String username, CurrentUserUpdateRequestDTO dto) {
        User user = userRepository.findByTenantIdAndUsername(tenantId, username)
                .orElseThrow(() -> new EntityNotFoundException("User not found with username: " + username));

        user.setName(dto.getName().trim());
        String phone = UserContactPolicy.normalizeRequiredPhone(dto.getPhone());
        String email = UserContactPolicy.normalizeOptionalEmail(dto.getEmail());
        requireUniqueContacts(user, phone, email);
        user.setPhone(phone);
        user.setEmail(email);
        return userRepository.save(user);
    }

    @Transactional
    public void upgradeAuthenticatedPasswordEncodingIfNeeded(UUID tenantId,
                                                             UUID membershipId,
                                                             String authenticatedPassword,
                                                             String rawPassword) {
        if (!passwordEncoder.upgradeEncoding(authenticatedPassword)) {
            return;
        }
        userRepository.upgradePasswordIfCurrent(
                tenantId,
                membershipId,
                authenticatedPassword,
                passwordEncoder.encode(rawPassword)
        );
    }

    @Transactional
    public void changeCurrentPassword(UUID tenantId,
                                      UUID membershipId,
                                      UUID currentSessionId,
                                      String currentPassword,
                                      String newPassword) {
        User user = userRepository.findByTenantIdAndId(tenantId, membershipId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + membershipId));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("当前密码不正确。");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同。");
        }
        passwordPolicy.requireValid(newPassword, new PasswordPolicy.PasswordContext(
                user.getUsername(), user.getName(), user.getPhone(), user.getEmail()),
                passwordPolicy.settingsForScope(user.getOrgUnitId()));
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(false);
        user.setPasswordChangedAt(UtcTime.now());
        incrementAuthorizationVersion(user);
        userRepository.saveAndFlush(user);
        credentialRevocationService.afterSelfPasswordChange(
                currentSessionId,
                tenantId,
                membershipId
        );
        evictAuthorizationAfterCommit(user);
    }

    @Transactional
    public User updateUserStatus(UUID userId, String statusStr) {
        User user = findTenantUserById(userId);
        UserStatus newStatus;
        try {
            newStatus = UserStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status value: '" + statusStr + "'. Valid statuses are: " +
                    java.util.Arrays.stream(UserStatus.values()).map(Enum::name).collect(Collectors.joining(", ")));
        }
        scopeAuthorizationService.requireOrganizationAccessIfAuthenticated(
                UserStatusPermissionPolicy.permissionForTransition(user.getStatus(), newStatus), user.getOrgUnitId());
        administrativeControlService.require(user, AdministrativeControlAction.UPDATE_STATUS);
        if (isSystemProtected(user) && newStatus != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("系统管理员账号不能停用或锁定。");
        }
        user.setStatus(newStatus);
        incrementAuthorizationVersion(user);
        User saved = userRepository.save(user);
        evictAuthorizationAfterCommit(saved);
        return saved;
    }

    @Transactional
    public void adminResetPassword(UUID userId, String newPassword) {
        User user = findTenantUserById(userId);
        if (isSystemProtected(user) && scopeAuthorizationService.hasTenantPrincipal()) {
            throw new IllegalArgumentException("系统管理员密码只能通过受控的系统配置更新。");
        }
        scopeAuthorizationService.requireOrganizationAccessIfAuthenticated(
                PermissionCatalog.USER_MANAGE_RESET_PASSWORD, user.getOrgUnitId());
        administrativeControlService.require(user, AdministrativeControlAction.RESET_PASSWORD);
        passwordPolicy.requireValid(newPassword, new PasswordPolicy.PasswordContext(
                user.getUsername(), user.getName(), user.getPhone(), user.getEmail()),
                passwordPolicy.settingsForScope(user.getOrgUnitId()));
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(true);
        user.setPasswordChangedAt(UtcTime.now());
        incrementAuthorizationVersion(user);
        userRepository.save(user);
        credentialRevocationService.afterAdminPasswordReset(user.getTenantId(), user.getId());
        evictAuthorizationAfterCommit(user);
    }

    @Transactional
    public void deleteUser(UUID userId) {
        User user = findTenantUserById(userId);
        scopeAuthorizationService.requireOrganizationAccessIfAuthenticated(
                PermissionCatalog.USER_MANAGE_DELETE, user.getOrgUnitId());
        administrativeControlService.require(user, AdministrativeControlAction.DELETE_OR_RESTORE);

        // 系统身份账号必须通过受控运维流程管理，不能从普通用户管理入口删除。
        if (isSystemProtected(user)) {
            throw new IllegalArgumentException("系统管理员账号不能删除。");
        }

        // 检查用户是否有关联的关键数据（例如，未完成的故障单等）
        // 如果有，根据业务规则决定是否允许删除或需要其他处理
        // 软删除：将状态设置为 DELETED
        user.setStatus(UserStatus.DELETED);
        incrementAuthorizationVersion(user);
        // 可选：清除敏感信息，例如将用户名和邮箱加上一个后缀，使其不再可用
        // user.setUsername(user.getUsername() + "_deleted_" + System.currentTimeMillis());
        // user.setEmail(user.getEmail() + "_deleted_" + System.currentTimeMillis()); // 如果有email字段
        userRepository.save(user);
        evictAuthorizationAfterCommit(user);
        log.info("User with ID {} soft deleted (status set to DELETED).", userId);

        // 如果是硬删除 (不推荐):
        // userRepository.delete(user);
        // log.info("User with ID {} hard deleted.", userId);
    }

    @Transactional
    public User restoreUser(UUID userId) {
        User user = findTenantUserById(userId);
        scopeAuthorizationService.requireOrganizationAccessIfAuthenticated(
                PermissionCatalog.USER_MANAGE_RESTORE, user.getOrgUnitId());
        administrativeControlService.require(user, AdministrativeControlAction.DELETE_OR_RESTORE);

        if (user.getStatus() != UserStatus.DELETED) {
            throw new IllegalStateException("User " + user.getUsername() + " is not in DELETED status, cannot restore.");
        }

        // 恢复用户时，通常将其设置回 ACTIVE 状态
        // 或者也可以设置为 INACTIVE，让管理员之后再手动激活
        user.setStatus(UserStatus.ACTIVE); // 或者 UserStatus.INACTIVE
        incrementAuthorizationVersion(user);

        // 如果在软删除时修改了用户名/邮箱等以防止冲突，这里可能需要考虑是否恢复它们
        // 例如，如果软删除时 username 变为 "username_deleted_timestamp"
        // 恢复时可能需要检查原始用户名是否已被占用，或者提供界面让管理员输入新的用户名
        // 为了简单起见，我们目前不处理用户名/邮箱的恢复修改

        log.info("User {} (ID: {}) restored to {} status.", user.getUsername(), userId, user.getStatus());
        User saved = userRepository.save(user);
        evictAuthorizationAfterCommit(saved);
        return saved;
    }

    public Page<User> findAllUsers(String searchTerm,
                                   UUID orgUnitId,
                                   UserStatus status,
                                   Pageable pageable,
                                   boolean includeDeleted) {
        return findAllUsers(
                searchTerm, orgUnitId, null, status, pageable, includeDeleted,
                PermissionCatalog.USER_MANAGE_VIEW);
    }

    public Page<User> findAllUsersForExport(String searchTerm,
                                            UUID orgUnitId,
                                            UUID departmentId,
                                            UserStatus status,
                                            Pageable pageable,
                                            boolean includeDeleted) {
        return findAllUsers(
                searchTerm, orgUnitId, departmentId, status, pageable, includeDeleted,
                PermissionCatalog.USER_EXPORT_CREATE);
    }

    private Page<User> findAllUsers(String searchTerm,
                                    UUID orgUnitId,
                                    UUID departmentId,
                                    UserStatus status,
                                    Pageable pageable,
                                    boolean includeDeleted,
                                    String requiredPermission) {
        UUID tenantId = tenantContext.requireTenantId();
        Specification<User> spec = (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("tenantId"), tenantId);
        if (scopeAuthorizationService.hasTenantPrincipal()) {
            var organizationAccess = scopeAuthorizationService.accessFor(requiredPermission);
            if (!organizationAccess.tenantWide()) {
                spec = spec.and((root, query, criteriaBuilder) ->
                        organizationAccess.orgUnitIds().isEmpty()
                                ? criteriaBuilder.disjunction()
                                : root.get("orgUnitId").in(organizationAccess.orgUnitIds()));
            }
        }

        if (orgUnitId != null) {
            scopeAuthorizationService.requireOrganizationAccessIfAuthenticated(
                    requiredPermission, orgUnitId);
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("orgUnitId"), orgUnitId));
        }

        if (departmentId != null) {
            UUID departmentOrgUnitId = departmentService.requireOrganizationId(tenantId, departmentId);
            if (orgUnitId != null && !orgUnitId.equals(departmentOrgUnitId)) {
                throw new IllegalArgumentException("所选部门不属于筛选组织。");
            }
            scopeAuthorizationService.requireOrganizationAccessIfAuthenticated(
                    requiredPermission, departmentOrgUnitId);
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("departmentId"), departmentId));
        }

        if (status != null) {
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), status));
        }

        if (!includeDeleted && status == null) {
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.notEqual(root.get("status"), UserStatus.DELETED)
            );
        }

        if (StringUtils.hasText(searchTerm)) {
            Specification<User> searchSpec = (root, query, criteriaBuilder) -> {
                String escapedSearchTerm = UserNameSearchKeys.escapeLikePattern(
                        searchTerm.trim().toLowerCase(java.util.Locale.ROOT), '!');
                String lowerSearchTerm = "%" + escapedSearchTerm + "%";
                List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.<String>get("username")), lowerSearchTerm, '!'));
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.<String>get("name")), lowerSearchTerm, '!'));
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(criteriaBuilder.coalesce(
                                root.<String>get("phone"), criteriaBuilder.literal(""))), lowerSearchTerm, '!'));
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(criteriaBuilder.coalesce(
                                root.<String>get("email"), criteriaBuilder.literal(""))), lowerSearchTerm, '!'));
                String normalizedPinyin = UserNameSearchKeys.normalizeSearchTerm(searchTerm);
                if (!normalizedPinyin.isEmpty()) {
                    String phoneticSearchTerm = normalizedPinyin + "%";
                    predicates.add(criteriaBuilder.like(
                            criteriaBuilder.lower(criteriaBuilder.coalesce(
                                    root.<String>get("namePinyin"), criteriaBuilder.literal(""))), phoneticSearchTerm));
                    predicates.add(criteriaBuilder.like(
                            criteriaBuilder.lower(criteriaBuilder.coalesce(
                                    root.<String>get("namePinyinInitials"), criteriaBuilder.literal(""))), phoneticSearchTerm));
                }
                return criteriaBuilder.or(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
            };
            spec = spec.and(searchSpec);
        }
        return userRepository.findAll(spec, pageable);
    }

    public Page<User> findAllUsers(String searchTerm, Pageable pageable, boolean includeDeleted) {
        return findAllUsers(searchTerm, null, null, pageable, includeDeleted);
    }

    // 在 findAllUsers 和其他查询用户的地方，可能需要过滤掉 DELETED 状态的用户
    // 除非是特定的管理视图需要显示所有用户包括已删除的。
    public Page<User> findAllUsers(String searchTerm, Pageable pageable) {
        return findAllUsers(searchTerm, pageable, false);
    }

    public String orgUnitName(User user) {
        if (user == null || user.getOrgUnitId() == null) {
            return null;
        }
        return orgUnitRepository.findByTenantIdAndId(user.getTenantId(), user.getOrgUnitId())
                .map(com.pro.ereportbacken.model.pojo.OrgUnit::getName)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public UserWorkOrderPerformanceDTO getWorkOrderPerformance(User user) {
        UserWorkOrderPerformanceRepository.UserWorkOrderPerformanceProjection summary =
                userPerformanceRepository.summarize(
                        user.getTenantId(),
                        user.getId(),
                        UtcTime.now().minusDays(30)
                );
        return new UserWorkOrderPerformanceDTO(
                summary.getReportedTotal(),
                summary.getReportedLast30Days(),
                summary.getReportedOpen(),
                summary.getReportedClosed(),
                summary.getAssignedTotal(),
                summary.getActiveAssignments(),
                summary.getResponsesSubmitted(),
                summary.getResponsesLast30Days(),
                summary.getResponsesWithin24Hours(),
                summary.getVerificationsCompleted(),
                summary.getVerificationsApproved(),
                oneDecimal(summary.getAverageResponseMinutes()),
                oneDecimal(summary.getAverageVerificationMinutes()),
                oneDecimal(summary.getAverageReportedClosureHours()),
                percentage(summary.getResponsesWithin24Hours(), summary.getResponsesSubmitted()),
                percentage(summary.getVerificationsApproved(), summary.getVerificationsCompleted())
        );
    }

    private double percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return oneDecimal(numerator * 100.0 / denominator);
    }

    private double oneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private UUID resolveOrgUnitId(UUID tenantId, UUID requestedOrgUnitId) {
        if (requestedOrgUnitId == null) {
            return defaultTenantService.requireRootOrgUnit(tenantId).getId();
        }
        return orgUnitRepository.findByTenantIdAndId(tenantId, requestedOrgUnitId)
                .orElseThrow(() -> new IllegalArgumentException("选择的组织不属于当前租户。"))
                .getId();
    }

    private void incrementAuthorizationVersion(User user) {
        long currentVersion = user.getAuthorizationVersion() == null ? 0L : user.getAuthorizationVersion();
        user.setAuthorizationVersion(currentVersion + 1L);
    }

    private void evictAuthorizationAfterCommit(User user) {
        userAuthorizationCache.evictAfterCommit(user.getTenantId(), user.getId());
    }

    @Transactional(readOnly = true)
    public TargetUserAccess targetUserAccess(User user) {
        var decision = administrativeControlService.decide(user, AdministrativeControlAction.UPDATE_DETAILS);
        return new TargetUserAccess(decision.allowed(), decision.allowed() ? null : decision.message());
    }

    @Transactional(readOnly = true)
    public UserManagementCapabilitiesDTO managementCapabilities(User user) {
        String organizationName = hierarchyService.namesById(
                user.getTenantId(), Set.of(user.getOrgUnitId())).get(user.getOrgUnitId());
        Map<String, UserManagementCapabilitiesDTO.Decision> actions = new java.util.LinkedHashMap<>();
        for (AdministrativeControlAction action : AdministrativeControlAction.values()) {
            var decision = administrativeControlService.decide(user, action);
            actions.put(action.name(), new UserManagementCapabilitiesDTO.Decision(
                    decision.allowed(), decision.reasonCode(), decision.message()));
        }
        var contactControl = administrativeControlService.decide(
                user, AdministrativeControlAction.UPDATE_IDENTITY);
        boolean hasContactPermission = scopeAuthorizationService.accessFor(
                PermissionCatalog.USER_MANAGE_CONTACT_UPDATE).orgUnitIds().contains(user.getOrgUnitId());
        boolean canUpdateContact = contactControl.allowed() && hasContactPermission;
        String contactReasonCode = canUpdateContact ? null
                : contactControl.allowed() ? "MISSING_CONTACT_PERMISSION" : contactControl.reasonCode();
        String contactMessage = canUpdateContact ? null
                : contactControl.allowed() ? "缺少联系方式修改权限或目标组织超出管理范围。" : contactControl.message();
        actions.put("UPDATE_CONTACT", new UserManagementCapabilitiesDTO.Decision(
                canUpdateContact,
                contactReasonCode,
                contactMessage));
        return new UserManagementCapabilitiesDTO(
                organizationName, actions);
    }

    public record TargetUserAccess(boolean editable, String protectionReason) {
    }

    public boolean isSystemProtected(User user) {
        return user != null && user.getSystemKey() != null;
    }

    private void requireUniqueContacts(User currentUser, String phone, String email) {
        userRepository.findByPhone(phone)
                .filter(existing -> currentUser == null || !existing.getId().equals(currentUser.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("手机号已被其他用户使用。");
                });
        if (email == null) {
            return;
        }
        userRepository.findByEmailIgnoreCase(email)
                .filter(existing -> currentUser == null || !existing.getId().equals(currentUser.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("邮箱已被其他用户使用。");
                });
    }

    private User findTenantUserById(UUID userId) {
        return userRepository.findByTenantIdAndId(tenantContext.currentTenantIdOrDefault(), userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
    }
}
