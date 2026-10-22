package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.pojo.AuthorizationProfile;
import com.pro.ereportbacken.model.pojo.AuthorizationProfilePermissionRule;
import com.pro.ereportbacken.model.pojo.AuthorizationProfileVersion;
import com.pro.ereportbacken.model.pojo.AuthorizationProfileVersionStatus;
import com.pro.ereportbacken.model.pojo.Permission;
import com.pro.ereportbacken.repository.AuthorizationProfilePermissionRuleRepository;
import com.pro.ereportbacken.repository.AuthorizationProfileRepository;
import com.pro.ereportbacken.repository.AuthorizationProfileVersionRepository;
import com.pro.ereportbacken.security.BuiltInAuthorizationProfileCatalog;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthorizationProfileCatalogSynchronizer {

    private final AuthorizationProfileRepository profileRepository;
    private final AuthorizationProfileVersionRepository versionRepository;
    private final AuthorizationProfilePermissionRuleRepository ruleRepository;

    public AuthorizationProfileCatalogSynchronizer(
            AuthorizationProfileRepository profileRepository,
            AuthorizationProfileVersionRepository versionRepository,
            AuthorizationProfilePermissionRuleRepository ruleRepository) {
        this.profileRepository = profileRepository;
        this.versionRepository = versionRepository;
        this.ruleRepository = ruleRepository;
    }

    public void synchronize(UUID tenantId,
                            boolean platformTenant,
                            Map<String, Permission> permissionsByName) {
        int sortOrder = 10;
        for (BuiltInAuthorizationProfileCatalog.Template template
                : BuiltInAuthorizationProfileCatalog.templates(platformTenant)) {
            AuthorizationProfile profile = profileRepository.findByTenantIdAndSystemKey(tenantId, template.systemKey())
                    .orElseGet(() -> profileRepository.findByTenantIdAndCode(tenantId, template.code())
                            .orElseGet(AuthorizationProfile::new));
            boolean newProfile = profile.getId() == null;
            if (newProfile) {
                profile.setTenantId(tenantId);
                profile.setCode(template.code());
                profile.setSystemKey(template.systemKey());
            }
            profile.setDisplayName(template.displayName());
            profile.setDescription(template.description());
            profile.setBuiltIn(template.builtIn());
            profile.setActive(true);
            profile.setSortOrder(template.sortOrder());
            profile = profileRepository.saveAndFlush(profile);

            AuthorizationProfileVersion current = profile.getCurrentVersionId() == null
                    ? null
                    : versionRepository.findByTenantIdAndId(tenantId, profile.getCurrentVersionId()).orElse(null);
            if (current == null || !matches(tenantId, current, template, permissionsByName)) {
                int nextVersion = versionRepository.findByTenantIdAndProfileIdOrderByVersionNoDesc(
                                tenantId, profile.getId()).stream()
                        .mapToInt(AuthorizationProfileVersion::getVersionNo)
                        .max().orElse(0) + 1;
                current = publish(tenantId, profile, template, permissionsByName, nextVersion);
                profile.setCurrentVersionId(current.getId());
                profileRepository.saveAndFlush(profile);
            }
        }
    }

    private boolean matches(UUID tenantId,
                            AuthorizationProfileVersion version,
                            BuiltInAuthorizationProfileCatalog.Template template,
                            Map<String, Permission> permissionsByName) {
        Set<UUID> existing = ruleRepository.findByTenantIdAndProfileVersionIdOrderBySortOrderAsc(
                        tenantId, version.getId()).stream()
                .map(AuthorizationProfilePermissionRule::getPermissionId)
                .collect(Collectors.toSet());
        Set<UUID> expected = template.rules().stream()
                .map(rule -> requirePermission(permissionsByName, rule.permissionName()).getId())
                .collect(Collectors.toSet());
        return existing.equals(expected);
    }

    private AuthorizationProfileVersion publish(UUID tenantId,
                                                 AuthorizationProfile profile,
                                                 BuiltInAuthorizationProfileCatalog.Template template,
                                                 Map<String, Permission> permissionsByName,
                                                 int versionNo) {
        AuthorizationProfileVersion version = new AuthorizationProfileVersion();
        version.setTenantId(tenantId);
        version.setProfileId(profile.getId());
        version.setVersionNo(versionNo);
        version.setStatus(AuthorizationProfileVersionStatus.PUBLISHED);
        version.setChangeSummary("同步内置岗位原子权限目录");
        version.setPublishedAt(UtcTime.now());
        version = versionRepository.saveAndFlush(version);

        int sortOrder = 10;
        for (BuiltInAuthorizationProfileCatalog.Rule templateRule : template.rules()) {
            AuthorizationProfilePermissionRule rule = new AuthorizationProfilePermissionRule();
            rule.setTenantId(tenantId);
            rule.setProfileVersionId(version.getId());
            rule.setPermissionId(requirePermission(permissionsByName, templateRule.permissionName()).getId());
            rule.setSortOrder(sortOrder);
            sortOrder += 10;
            ruleRepository.save(rule);
        }
        ruleRepository.flush();
        return version;
    }

    private Permission requirePermission(Map<String, Permission> permissionsByName, String permissionName) {
        Permission permission = permissionsByName.get(permissionName);
        if (permission == null) {
            throw new IllegalStateException("内置岗位引用了权限目录之外的权限：" + permissionName);
        }
        return permission;
    }

}
