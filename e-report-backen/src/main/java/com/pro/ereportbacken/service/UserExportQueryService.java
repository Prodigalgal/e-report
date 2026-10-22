package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.dto.UserTransferExportRequestDTO;
import com.pro.ereportbacken.model.pojo.GrantIssuerType;
import com.pro.ereportbacken.model.pojo.Department;
import com.pro.ereportbacken.model.pojo.OrgUnit;
import com.pro.ereportbacken.model.pojo.Permission;
import com.pro.ereportbacken.model.pojo.PermissionGrant;
import com.pro.ereportbacken.model.pojo.PermissionGrantStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.DepartmentRepository;
import com.pro.ereportbacken.repository.PermissionGrantRepository;
import com.pro.ereportbacken.repository.PermissionRepository;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantContext;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Log4j2
public class UserExportQueryService {

    static final int MAX_EXPORT_USERS = 10_000;
    static final long MAX_EXPORT_DETAIL_ROWS = 150_000;
    static final long MAX_ESTIMATED_FILE_BYTES = 45L * 1024L * 1024L;
    private static final int PAGE_SIZE = 250;
    private static final long BASE_FILE_BYTES = 64L * 1024L;
    private static final long ESTIMATED_USER_BYTES = 320L;
    private static final long ESTIMATED_GRANT_BYTES = 360L;

    private final UserService userService;
    private final TenantContext tenantContext;
    private final OrgUnitRepository orgUnitRepository;
    private final DepartmentRepository departmentRepository;
    private final PermissionGrantRepository grantRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionGrantChainService grantChainService;
    private final OrganizationScopeAuthorizationService scopeAuthorizationService;

    public UserExportQueryService(UserService userService,
                                  TenantContext tenantContext,
                                  OrgUnitRepository orgUnitRepository,
                                  DepartmentRepository departmentRepository,
                                  PermissionGrantRepository grantRepository,
                                  PermissionRepository permissionRepository,
                                  PermissionGrantChainService grantChainService,
                                  OrganizationScopeAuthorizationService scopeAuthorizationService) {
        this.userService = userService;
        this.tenantContext = tenantContext;
        this.orgUnitRepository = orgUnitRepository;
        this.departmentRepository = departmentRepository;
        this.grantRepository = grantRepository;
        this.permissionRepository = permissionRepository;
        this.grantChainService = grantChainService;
        this.scopeAuthorizationService = scopeAuthorizationService;
    }

    public UserExportSnapshot snapshot(UserTransferExportRequestDTO request) {
        return scan(request, batch -> { });
    }

    public UserExportSnapshot scan(UserTransferExportRequestDTO request, BatchConsumer consumer) {
        UserTransferExportRequestDTO normalizedRequest = request.withoutPreviewToken();
        UUID tenantId = tenantContext.requireTenantId();
        Map<UUID, OrgUnit> organizationsById = orgUnitRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(OrgUnit::getId, Function.identity()));
        Map<UUID, Department> departmentsById = departmentRepository.findByTenantIdOrderByPathAsc(tenantId).stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));
        UserExportFilters filters = resolveFilters(normalizedRequest, organizationsById, departmentsById);
        MessageDigest digest = sha256Digest();
        updateDigest(digest, filters.searchTerm(), filters.organizationName(), filters.departmentName(), filters.status(),
                filters.includeDeleted());

        long userCount = 0;
        long grantCount = 0;
        int pageIndex = 0;
        Page<User> page;
        Sort sort = Sort.by(Sort.Order.asc("username"), Sort.Order.asc("id"));
        do {
            page = userService.findAllUsersForExport(
                    normalizedRequest.searchTerm(), normalizedRequest.orgUnitId(), normalizedRequest.departmentId(), normalizedRequest.status(),
                    PageRequest.of(pageIndex, PAGE_SIZE, sort),
                    normalizedRequest.shouldIncludeDeleted());
            if (page.getTotalElements() > MAX_EXPORT_USERS) {
                throw new IllegalArgumentException(
                        "当前筛选结果超过 " + MAX_EXPORT_USERS + " 个用户，请缩小筛选范围后再导出。");
            }
            UserExportDataset batch = loadBatch(tenantId, page.getContent(), organizationsById, departmentsById, filters);
            userCount += batch.rows().size();
            grantCount += batch.grantRows().size();
            enforceDetailLimits(grantCount, estimatedFileSize(userCount, grantCount));
            updateDigest(digest, batch);
            consumer.accept(batch);
            pageIndex++;
        } while (pageIndex < page.getTotalPages());

        long estimatedFileSize = estimatedFileSize(userCount, grantCount);
        return new UserExportSnapshot(userCount, grantCount, estimatedFileSize,
                HexFormat.of().formatHex(digest.digest()), filters);
    }

    private UserExportDataset loadBatch(UUID tenantId,
                                        List<User> users,
                                        Map<UUID, OrgUnit> organizationsById,
                                        Map<UUID, Department> departmentsById,
                                        UserExportFilters filters) {
        List<UUID> userIds = users.stream().map(User::getId).toList();
        List<PermissionGrant> grants = userIds.isEmpty() ? List.of()
                : grantRepository.findByTenantIdAndMembershipIdInAndStatusIn(
                tenantId, userIds,
                List.of(PermissionGrantStatus.ACTIVE, PermissionGrantStatus.SUSPENDED_UPSTREAM));
        Map<UUID, User> usersById = users.stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, Permission> permissionsById = permissionRepository.findAllById(
                        grants.stream().map(PermissionGrant::getPermissionId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Permission::getId, Function.identity()));
        LocalDateTime now = UtcTime.now();
        List<UserPermissionGrantExportRow> grantRows = grants.stream()
                .map(grant -> toGrantRow(grant, usersById.get(grant.getMembershipId()),
                        permissionsById.get(grant.getPermissionId()), now))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(UserPermissionGrantExportRow::phone,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(UserPermissionGrantExportRow::permissionName)
                        .thenComparing(UserPermissionGrantExportRow::grantId))
                .toList();
        List<UserExportRow> rows = users.stream()
                .map(user -> toRow(user, organizationsById.get(user.getOrgUnitId()),
                        departmentsById.get(user.getDepartmentId())))
                .toList();
        return new UserExportDataset(rows, grantRows, filters);
    }

    private UserPermissionGrantExportRow toGrantRow(PermissionGrant grant,
                                                     User user,
                                                     Permission permission,
                                                     LocalDateTime now) {
        if (user == null || permission == null) {
            throw new IllegalStateException("导出 Grant 引用了不存在的用户或权限。");
        }
        if (!PermissionCatalog.contains(permission.getName())) {
            log.debug("导出时忽略已退役权限 Grant。tenantId={}, membershipId={}, grantId={}, permission={}",
                    grant.getTenantId(), grant.getMembershipId(), grant.getId(), permission.getName());
            return null;
        }
        if (!scopeAuthorizationService.canGrantPermission(permission.getName(), grant.getExpiresAt())) {
            return null;
        }
        PermissionCatalog.Entry definition = PermissionCatalog.require(permission.getName());
        return new UserPermissionGrantExportRow(
                grant.getId(), grant.getParentGrantId(), user.getId(), user.getPhone(), user.getName(),
                permission.getName(), definition.displayName(),
                grant.getStatus(), grantChainService.isEffectivelyActive(grant, now),
                Boolean.TRUE.equals(grant.getGrantOption()), grant.getIssuerType(),
                grant.getValidFrom(), grant.getExpiresAt(), grant.getGrantedByMembershipId(),
                grant.getReason(), grant.getGrantedAt());
    }

    private UserExportFilters resolveFilters(UserTransferExportRequestDTO request,
                                             Map<UUID, OrgUnit> organizationsById,
                                             Map<UUID, Department> departmentsById) {
        String organizationName = null;
        if (request.orgUnitId() != null) {
            organizationName = java.util.Optional.ofNullable(organizationsById.get(request.orgUnitId()))
                    .map(OrgUnit::getName)
                    .orElseThrow(() -> new IllegalArgumentException("导出筛选组织不存在或不可见。"));
        }
        String departmentName = null;
        if (request.departmentId() != null) {
            Department department = departmentsById.get(request.departmentId());
            if (department == null || (request.orgUnitId() != null
                    && !request.orgUnitId().equals(department.getOrgUnitId()))) {
                throw new IllegalArgumentException("导出筛选部门不存在、不属于所选组织或不可见。");
            }
            departmentName = department.getName();
        }
        return new UserExportFilters(normalizeFilter(request.searchTerm()), organizationName, departmentName,
                request.status(), request.shouldIncludeDeleted());
    }

    private void enforceDetailLimits(long grantCount, long estimatedFileSize) {
        if (grantCount > MAX_EXPORT_DETAIL_ROWS) {
            throw new IllegalArgumentException(
                    "当前筛选结果包含超过 " + MAX_EXPORT_DETAIL_ROWS + " 条 Grant，请缩小导出范围。");
        }
        if (estimatedFileSize > MAX_ESTIMATED_FILE_BYTES) {
            throw new IllegalArgumentException("预计导出文件超过45兆字节，请缩小导出范围。");
        }
    }

    private long estimatedFileSize(long userCount, long grantCount) {
        return BASE_FILE_BYTES + userCount * ESTIMATED_USER_BYTES + grantCount * ESTIMATED_GRANT_BYTES;
    }

    private void updateDigest(MessageDigest digest, UserExportDataset batch) {
        batch.rows().forEach(row -> updateDigest(digest,
                row.id(), row.phone(), row.name(), row.username(), row.email(), row.status(),
                row.organizationCode(), row.organizationName(), row.authorizationVersion(),
                row.departmentCode(), row.departmentName(),
                row.createdAt(), row.updatedAt()));
        batch.grantRows().forEach(row -> updateDigest(digest,
                row.grantId(), row.parentGrantId(), row.userId(), row.phone(), row.userName(),
                row.permissionName(), row.permissionDisplayName(), row.status(),
                row.effectivelyActive(), row.grantOption(), row.issuerType(), row.validFrom(), row.expiresAt(),
                row.grantedByMembershipId(), row.reason(), row.grantedAt()));
    }

    private void updateDigest(MessageDigest digest, Object... values) {
        for (Object value : values) {
            digest.update(String.valueOf(value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        digest.update((byte) '\n');
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256。", exception);
        }
    }

    private UserExportRow toRow(User user, OrgUnit organization, Department department) {
        return new UserExportRow(
                user.getId(), user.getPhone(), user.getName(), user.getUsername(), user.getEmail(),
                user.getStatus(), organization == null ? null : organization.getCode(),
                organization == null ? null : organization.getName(),
                department == null ? null : department.getCode(),
                department == null ? null : department.getName(), user.getAuthorizationVersion(),
                user.getCreatedAt(), user.getUpdatedAt());
    }

    private String normalizeFilter(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @FunctionalInterface
    interface BatchConsumer {
        void accept(UserExportDataset batch);
    }

    record UserExportDataset(List<UserExportRow> rows,
                             List<UserPermissionGrantExportRow> grantRows,
                             UserExportFilters filters) {
        UserExportDataset {
            rows = List.copyOf(rows);
            grantRows = List.copyOf(grantRows);
        }
    }

    record UserExportSnapshot(long userCount,
                              long grantCount,
                              long estimatedFileSize,
                              String digest,
                              UserExportFilters filters) {
    }

    record UserExportFilters(String searchTerm,
                             String organizationName,
                             String departmentName,
                             UserStatus status,
                             boolean includeDeleted) {
    }

    record UserExportRow(UUID id,
                         String phone,
                         String name,
                         String username,
                         String email,
                         UserStatus status,
                         String organizationCode,
                          String organizationName,
                          String departmentCode,
                          String departmentName,
                          Long authorizationVersion,
                         LocalDateTime createdAt,
                         LocalDateTime updatedAt) {
    }

    record UserPermissionGrantExportRow(UUID grantId,
                                        UUID parentGrantId,
                                        UUID userId,
                                        String phone,
                                        String userName,
                                        String permissionName,
                                        String permissionDisplayName,
                                        PermissionGrantStatus status,
                                        boolean effectivelyActive,
                                        boolean grantOption,
                                        GrantIssuerType issuerType,
                                        LocalDateTime validFrom,
                                        LocalDateTime expiresAt,
                                        UUID grantedByMembershipId,
                                        String reason,
                                        LocalDateTime grantedAt) {
    }
}
