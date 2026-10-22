package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.dto.AuthorizationProfileCopyRequestDTO;
import com.pro.ereportbacken.model.dto.AuthorizationProfileDTO;
import com.pro.ereportbacken.model.dto.AuthorizationProfileRequestDTO;
import com.pro.ereportbacken.model.dto.AuthorizationProfileSummaryDTO;
import com.pro.ereportbacken.model.pojo.AuthorizationProfile;
import com.pro.ereportbacken.model.pojo.AuthorizationProfilePermissionRule;
import com.pro.ereportbacken.model.pojo.AuthorizationProfileVersion;
import com.pro.ereportbacken.model.pojo.AuthorizationProfileVersionStatus;
import com.pro.ereportbacken.model.pojo.Permission;
import com.pro.ereportbacken.repository.AuthorizationProfilePermissionRuleRepository;
import com.pro.ereportbacken.repository.AuthorizationProfileRepository;
import com.pro.ereportbacken.repository.AuthorizationProfileVersionRepository;
import com.pro.ereportbacken.repository.PermissionRepository;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.security.TenantUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AuthorizationProfileService {

    private final TenantContext tenantContext;
    private final AuthorizationProfileRepository profileRepository;
    private final AuthorizationProfileVersionRepository versionRepository;
    private final AuthorizationProfilePermissionRuleRepository ruleRepository;
    private final PermissionRepository permissionRepository;
    private final OrganizationScopeAuthorizationService scopeAuthorizationService;

    public AuthorizationProfileService(TenantContext tenantContext,
                                       AuthorizationProfileRepository profileRepository,
                                       AuthorizationProfileVersionRepository versionRepository,
                                       AuthorizationProfilePermissionRuleRepository ruleRepository,
                                       PermissionRepository permissionRepository,
                                       OrganizationScopeAuthorizationService scopeAuthorizationService) {
        this.tenantContext = tenantContext;
        this.profileRepository = profileRepository;
        this.versionRepository = versionRepository;
        this.ruleRepository = ruleRepository;
        this.permissionRepository = permissionRepository;
        this.scopeAuthorizationService = scopeAuthorizationService;
    }

    @Transactional(readOnly = true)
    public List<AuthorizationProfileDTO> list() {
        UUID tenantId = tenantContext.requireTenantId();
        ProfileCatalog catalog = visibleCatalog(tenantId);
        return catalog.profiles().stream()
                .map(profile -> toDto(
                        profile,
                        catalog.versionsByProfileId().getOrDefault(profile.getId(), List.of()),
                        catalog.rulesByVersionId(),
                        catalog.permissionsById()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuthorizationProfileSummaryDTO> listSummaries() {
        UUID tenantId = tenantContext.requireTenantId();
        ProfileCatalog catalog = visibleCatalog(tenantId);
        return catalog.profiles().stream().map(profile -> {
            AuthorizationProfileVersion current = catalog.versionsByProfileId()
                    .getOrDefault(profile.getId(), List.of()).stream()
                    .filter(version -> version.getId().equals(profile.getCurrentVersionId()))
                    .findFirst().orElse(null);
            return new AuthorizationProfileSummaryDTO(
                    profile.getId(), profile.getCode(), profile.getDisplayName(), profile.getDescription(),
                    Boolean.TRUE.equals(profile.getBuiltIn()), Boolean.TRUE.equals(profile.getActive()),
                    current == null ? null : current.getId(), current == null ? null : current.getVersionNo());
        }).toList();
    }

    @Transactional
    public AuthorizationProfileDTO createDraft(AuthorizationProfileRequestDTO request) {
        UUID tenantId = tenantContext.requireTenantId();
        String code = request.code().trim().toUpperCase();
        if (profileRepository.findByTenantIdAndCode(tenantId, code).isPresent()) {
            throw new IllegalArgumentException("岗位权限模板编码已存在。" );
        }
        AuthorizationProfile profile = new AuthorizationProfile();
        profile.setTenantId(tenantId);
        profile.setCode(code);
        profile.setDisplayName(request.displayName().trim());
        profile.setDescription(trimToNull(request.description()));
        profile.setBuiltIn(false);
        profile.setActive(true);
        profile = profileRepository.saveAndFlush(profile);
        AuthorizationProfileVersion draft = newVersion(
                tenantId, profile.getId(), 1, AuthorizationProfileVersionStatus.DRAFT,
                trimToNull(request.changeSummary()), currentMembershipId());
        replaceRules(tenantId, draft.getId(), request.rules());
        return toDto(tenantId, profile);
    }

    @Transactional
    public AuthorizationProfileDTO updateDraft(UUID profileId, AuthorizationProfileRequestDTO request) {
        UUID tenantId = tenantContext.requireTenantId();
        AuthorizationProfile profile = requireVisibleProfile(tenantId, profileId);
        requireCustom(profile);
        String requestedCode = request.code().trim().toUpperCase();
        profileRepository.findByTenantIdAndCode(tenantId, requestedCode)
                .filter(existing -> !existing.getId().equals(profileId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("岗位权限模板编码已存在。" );
                });
        profile.setCode(requestedCode);
        profile.setDisplayName(request.displayName().trim());
        profile.setDescription(trimToNull(request.description()));
        profileRepository.saveAndFlush(profile);

        AuthorizationProfileVersion draft = draftVersion(tenantId, profile).orElseGet(() -> {
            int nextVersion = versionRepository.findByTenantIdAndProfileIdOrderByVersionNoDesc(tenantId, profileId)
                    .stream().mapToInt(AuthorizationProfileVersion::getVersionNo).max().orElse(0) + 1;
            return newVersion(tenantId, profileId, nextVersion, AuthorizationProfileVersionStatus.DRAFT,
                    trimToNull(request.changeSummary()), currentMembershipId());
        });
        draft.setChangeSummary(trimToNull(request.changeSummary()));
        versionRepository.saveAndFlush(draft);
        replaceRules(tenantId, draft.getId(), request.rules());
        return toDto(tenantId, profile);
    }

    @Transactional
    public AuthorizationProfileDTO copy(UUID sourceProfileId, AuthorizationProfileCopyRequestDTO request) {
        UUID tenantId = tenantContext.requireTenantId();
        AuthorizationProfile source = requireVisibleProfile(tenantId, sourceProfileId);
        AuthorizationProfileVersion sourceVersion = currentOrDraftVersion(tenantId, source);
        List<AuthorizationProfilePermissionRule> sourceRules = ruleRepository
                .findByTenantIdAndProfileVersionIdOrderBySortOrderAsc(tenantId, sourceVersion.getId());
        Map<UUID, Permission> sourcePermissions = permissionRepository.findAllById(
                        sourceRules.stream().map(AuthorizationProfilePermissionRule::getPermissionId)
                                .collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Permission::getId, Function.identity()));
        List<AuthorizationProfileRequestDTO.Rule> rules = sourceRules.stream()
                .map(rule -> new AuthorizationProfileRequestDTO.Rule(
                        requirePermission(sourcePermissions, rule).getName()))
                .toList();
        return createDraft(new AuthorizationProfileRequestDTO(
                request.code(), request.displayName(), source.getDescription(),
                rules, "复制自 " + source.getDisplayName()));
    }

    @Transactional
    public AuthorizationProfileDTO publish(UUID profileId) {
        UUID tenantId = tenantContext.requireTenantId();
        AuthorizationProfile profile = requireVisibleProfile(tenantId, profileId);
        requireCustom(profile);
        AuthorizationProfileVersion draft = draftVersion(tenantId, profile)
                .orElseThrow(() -> new IllegalArgumentException("岗位权限模板没有可发布的草稿版本。"));
        draft.setStatus(AuthorizationProfileVersionStatus.PUBLISHED);
        draft.setPublishedByMembershipId(currentMembershipId());
        draft.setPublishedAt(UtcTime.now());
        versionRepository.saveAndFlush(draft);
        profile.setCurrentVersionId(draft.getId());
        profile.setActive(true);
        profileRepository.saveAndFlush(profile);
        return toDto(tenantId, profile);
    }

    @Transactional
    public AuthorizationProfileDTO retire(UUID profileId) {
        UUID tenantId = tenantContext.requireTenantId();
        AuthorizationProfile profile = requireVisibleProfile(tenantId, profileId);
        requireCustom(profile);
        profile.setActive(false);
        profileRepository.saveAndFlush(profile);
        return toDto(tenantId, profile);
    }

    @Transactional(readOnly = true)
    public AuthorizationProfileDTO get(UUID profileId) {
        UUID tenantId = tenantContext.requireTenantId();
        return toDto(tenantId, requireVisibleProfile(tenantId, profileId));
    }

    private AuthorizationProfileDTO toDto(UUID tenantId, AuthorizationProfile profile) {
        List<AuthorizationProfileVersion> versions = versionRepository
                .findByTenantIdAndProfileIdOrderByVersionNoDesc(tenantId, profile.getId());
        AuthorizationProfileVersion current = versions.stream()
                .filter(version -> version.getId().equals(profile.getCurrentVersionId()))
                .findFirst().orElse(null);
        AuthorizationProfileVersion draft = versions.stream()
                .filter(version -> version.getStatus() == AuthorizationProfileVersionStatus.DRAFT)
                .findFirst().orElse(null);
        Set<UUID> versionIds = java.util.stream.Stream.of(current, draft)
                .filter(java.util.Objects::nonNull)
                .map(AuthorizationProfileVersion::getId)
                .collect(Collectors.toSet());
        Map<UUID, List<AuthorizationProfilePermissionRule>> rules = versionIds.isEmpty()
                ? Map.of()
                : ruleRepository.findByTenantIdAndProfileVersionIdInOrderBySortOrderAsc(tenantId, versionIds)
                .stream().collect(Collectors.groupingBy(AuthorizationProfilePermissionRule::getProfileVersionId));
        Map<UUID, Permission> permissions = permissionRepository.findAllById(
                        rules.values().stream().flatMap(List::stream)
                                .map(AuthorizationProfilePermissionRule::getPermissionId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Permission::getId, Function.identity()));
        return toDto(profile, versions, rules, permissions);
    }

    private AuthorizationProfileDTO toDto(
            AuthorizationProfile profile,
            List<AuthorizationProfileVersion> versions,
            Map<UUID, List<AuthorizationProfilePermissionRule>> rules,
            Map<UUID, Permission> permissions) {
        AuthorizationProfileVersion current = versions.stream()
                .filter(version -> version.getId().equals(profile.getCurrentVersionId()))
                .findFirst().orElse(null);
        AuthorizationProfileVersion draft = versions.stream()
                .filter(version -> version.getStatus() == AuthorizationProfileVersionStatus.DRAFT)
                .findFirst().orElse(null);
        return new AuthorizationProfileDTO(
                profile.getId(), profile.getCode(), profile.getDisplayName(), profile.getDescription(),
                Boolean.TRUE.equals(profile.getBuiltIn()), Boolean.TRUE.equals(profile.getActive()),
                toVersion(current, rules, permissions),
                toVersion(draft, rules, permissions));
    }

    private ProfileCatalog visibleCatalog(UUID tenantId) {
        List<AuthorizationProfile> profiles = profileRepository
                .findByTenantId(tenantId, org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream()
                .sorted(Comparator.comparing(AuthorizationProfile::getSortOrder)
                        .thenComparing(AuthorizationProfile::getDisplayName))
                .toList();
        if (profiles.isEmpty()) {
            return new ProfileCatalog(List.of(), Map.of(), Map.of(), Map.of());
        }
        Map<UUID, List<AuthorizationProfileVersion>> versionsByProfileId = versionRepository
                .findByTenantIdAndProfileIdInOrderByProfileIdAscVersionNoDesc(
                        tenantId,
                        profiles.stream().map(AuthorizationProfile::getId).collect(Collectors.toSet()))
                .stream().collect(Collectors.groupingBy(AuthorizationProfileVersion::getProfileId));
        Set<UUID> visibleVersionIds = profiles.stream().flatMap(profile -> versionsByProfileId
                        .getOrDefault(profile.getId(), List.of()).stream()
                        .filter(version -> version.getId().equals(profile.getCurrentVersionId())
                                || version.getStatus() == AuthorizationProfileVersionStatus.DRAFT))
                .map(AuthorizationProfileVersion::getId)
                .collect(Collectors.toSet());
        Map<UUID, List<AuthorizationProfilePermissionRule>> rulesByVersionId = visibleVersionIds.isEmpty()
                ? Map.of()
                : ruleRepository.findByTenantIdAndProfileVersionIdInOrderBySortOrderAsc(
                        tenantId, visibleVersionIds).stream()
                .collect(Collectors.groupingBy(AuthorizationProfilePermissionRule::getProfileVersionId));
        Map<UUID, Permission> permissionsById = permissionRepository.findAllById(
                        rulesByVersionId.values().stream().flatMap(List::stream)
                                .map(AuthorizationProfilePermissionRule::getPermissionId)
                                .collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Permission::getId, Function.identity()));
        List<AuthorizationProfile> visibleProfiles = profiles.stream()
                .filter(profile -> canDelegateAnyVisibleVersion(
                        profile,
                        versionsByProfileId.getOrDefault(profile.getId(), List.of()),
                        rulesByVersionId,
                        permissionsById))
                .toList();
        return new ProfileCatalog(
                visibleProfiles,
                Map.copyOf(versionsByProfileId),
                Map.copyOf(rulesByVersionId),
                Map.copyOf(permissionsById));
    }

    private AuthorizationProfileDTO.Version toVersion(
            AuthorizationProfileVersion version,
            Map<UUID, List<AuthorizationProfilePermissionRule>> rules,
            Map<UUID, Permission> permissions) {
        if (version == null) {
            return null;
        }
        List<AuthorizationProfileDTO.Rule> ruleDtos = rules.getOrDefault(version.getId(), List.of()).stream()
                .map(rule -> {
                    Permission permission = permissions.get(rule.getPermissionId());
                    if (permission == null) {
                        throw new IllegalStateException("岗位模板引用了不存在的权限。" );
                    }
                    return new AuthorizationProfileDTO.Rule(
                            rule.getId(), permission.getName(), permission.getDisplayName(),
                            rule.getSortOrder());
                }).toList();
        return new AuthorizationProfileDTO.Version(
                version.getId(), version.getVersionNo(), version.getStatus(), version.getChangeSummary(),
                version.getCreatedAt(), version.getPublishedAt(), ruleDtos);
    }

    private void replaceRules(UUID tenantId,
                              UUID versionId,
                              List<AuthorizationProfileRequestDTO.Rule> requestedRules) {
        List<AuthorizationProfileRequestDTO.Rule> rules = requestedRules == null ? List.of() : requestedRules;
        Map<String, AuthorizationProfileRequestDTO.Rule> unique = new LinkedHashMap<>();
        for (AuthorizationProfileRequestDTO.Rule rule : rules) {
            PermissionCatalog.Entry definition = PermissionCatalog.require(rule.permissionName());
            if (!definition.delegable() || definition.systemScoped()) {
                throw new IllegalArgumentException("权限不能加入当前租户的岗位模板：" + rule.permissionName());
            }
            if (!scopeAuthorizationService.canGrantPermission(rule.permissionName())) {
                throw new AccessDeniedException("不能把超出当前账号委派边界的权限写入岗位模板："
                        + rule.permissionName());
            }
            unique.put(rule.permissionName(), rule);
        }
        if (unique.size() != rules.size()) {
            throw new IllegalArgumentException("岗位模板不能包含重复的原子权限。" );
        }
        List<AuthorizationProfilePermissionRule> existing = ruleRepository
                .findByTenantIdAndProfileVersionIdOrderBySortOrderAsc(tenantId, versionId);
        ruleRepository.deleteAll(existing);
        ruleRepository.flush();
        int sortOrder = 10;
        for (AuthorizationProfileRequestDTO.Rule requested : unique.values()) {
            Permission permission = permissionRepository.findByName(requested.permissionName())
                    .orElseThrow(() -> new IllegalArgumentException("权限目录缺少：" + requested.permissionName()));
            AuthorizationProfilePermissionRule rule = new AuthorizationProfilePermissionRule();
            rule.setTenantId(tenantId);
            rule.setProfileVersionId(versionId);
            rule.setPermissionId(permission.getId());
            rule.setSortOrder(sortOrder);
            sortOrder += 10;
            ruleRepository.save(rule);
        }
        ruleRepository.flush();
    }

    private AuthorizationProfileVersion newVersion(UUID tenantId,
                                                    UUID profileId,
                                                    int versionNo,
                                                    AuthorizationProfileVersionStatus status,
                                                    String summary,
                                                    UUID creator) {
        AuthorizationProfileVersion version = new AuthorizationProfileVersion();
        version.setTenantId(tenantId);
        version.setProfileId(profileId);
        version.setVersionNo(versionNo);
        version.setStatus(status);
        version.setChangeSummary(summary);
        version.setCreatedByMembershipId(creator);
        return versionRepository.saveAndFlush(version);
    }

    private java.util.Optional<AuthorizationProfileVersion> draftVersion(UUID tenantId, AuthorizationProfile profile) {
        return versionRepository.findByTenantIdAndProfileIdOrderByVersionNoDesc(tenantId, profile.getId()).stream()
                .filter(version -> version.getStatus() == AuthorizationProfileVersionStatus.DRAFT)
                .findFirst();
    }

    private AuthorizationProfileVersion currentOrDraftVersion(UUID tenantId, AuthorizationProfile profile) {
        return draftVersion(tenantId, profile).orElseGet(() -> {
            if (profile.getCurrentVersionId() == null) {
                throw new IllegalArgumentException("来源岗位模板没有可复制版本。" );
            }
            return versionRepository.findByTenantIdAndId(tenantId, profile.getCurrentVersionId())
                    .orElseThrow(() -> new IllegalStateException("岗位模板当前版本不存在。"));
        });
    }

    private AuthorizationProfile requireProfile(UUID tenantId, UUID profileId) {
        return profileRepository.findByTenantIdAndId(tenantId, profileId)
                .orElseThrow(() -> new IllegalArgumentException("岗位权限模板不存在。"));
    }

    private AuthorizationProfile requireVisibleProfile(UUID tenantId, UUID profileId) {
        AuthorizationProfile profile = requireProfile(tenantId, profileId);
        if (!canDelegateAnyVisibleVersion(tenantId, profile)) {
            throw new AccessDeniedException("当前账号无权查看或管理该岗位权限模板。" );
        }
        return profile;
    }

    private boolean canDelegateAnyVisibleVersion(UUID tenantId, AuthorizationProfile profile) {
        return java.util.stream.Stream.of(
                        profile.getCurrentVersionId() == null ? null
                                : versionRepository.findByTenantIdAndId(tenantId, profile.getCurrentVersionId()).orElse(null),
                        draftVersion(tenantId, profile).orElse(null))
                .filter(java.util.Objects::nonNull)
                .anyMatch(version -> canDelegateVersion(tenantId, version));
    }

    private boolean canDelegateVersion(UUID tenantId, AuthorizationProfileVersion version) {
        List<AuthorizationProfilePermissionRule> rules = ruleRepository
                .findByTenantIdAndProfileVersionIdOrderBySortOrderAsc(tenantId, version.getId());
        Map<UUID, Permission> permissions = permissionRepository.findAllById(
                        rules.stream().map(AuthorizationProfilePermissionRule::getPermissionId)
                                .collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Permission::getId, Function.identity()));
        return rules.stream().allMatch(rule -> {
            Permission permission = permissions.get(rule.getPermissionId());
            return permission != null && scopeAuthorizationService.canGrantPermission(permission.getName());
        });
    }

    private boolean canDelegateAnyVisibleVersion(
            AuthorizationProfile profile,
            List<AuthorizationProfileVersion> versions,
            Map<UUID, List<AuthorizationProfilePermissionRule>> rulesByVersionId,
            Map<UUID, Permission> permissionsById) {
        return versions.stream()
                .filter(version -> version.getId().equals(profile.getCurrentVersionId())
                        || version.getStatus() == AuthorizationProfileVersionStatus.DRAFT)
                .anyMatch(version -> rulesByVersionId.getOrDefault(version.getId(), List.of()).stream()
                        .allMatch(rule -> {
                            Permission permission = permissionsById.get(rule.getPermissionId());
                            return permission != null && scopeAuthorizationService.canGrantPermission(
                                    permission.getName());
                        }));
    }

    private Permission requirePermission(
            Map<UUID, Permission> permissions,
            AuthorizationProfilePermissionRule rule) {
        Permission permission = permissions.get(rule.getPermissionId());
        if (permission == null) {
            throw new IllegalStateException("岗位模板引用了不存在的权限。" );
        }
        return permission;
    }

    private void requireCustom(AuthorizationProfile profile) {
        if (Boolean.TRUE.equals(profile.getBuiltIn())) {
            throw new AccessDeniedException("内置岗位权限模板不可直接修改，可以复制后定制。" );
        }
    }

    private UUID currentMembershipId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof TenantUserDetails principal
                ? principal.membershipId() : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record ProfileCatalog(
            List<AuthorizationProfile> profiles,
            Map<UUID, List<AuthorizationProfileVersion>> versionsByProfileId,
            Map<UUID, List<AuthorizationProfilePermissionRule>> rulesByVersionId,
            Map<UUID, Permission> permissionsById) {
    }
}
