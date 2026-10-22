package com.pro.ereportbacken.service;

import java.util.UUID;

import com.pro.ereportbacken.config.DataInitializer;
import com.pro.ereportbacken.model.dto.OrgUnitRequestDTO;
import com.pro.ereportbacken.model.dto.FileTransferInitRequestDTO;
import com.pro.ereportbacken.model.pojo.AppConfig;
import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.OrgUnit;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.repository.AppConfigRepository;
import com.pro.ereportbacken.repository.PermissionGrantRepository;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.TenantGroupRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.ConfigurationScopeContext;
import com.pro.ereportbacken.security.BuiltInAuthorizationProfileCatalog;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantUserDetails;
import com.pro.ereportbacken.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ZhangzhouOrganizationIsolationIntegrationTests {
    private static final List<CountySeedExpectation> COUNTY_SEEDS = List.of(
            new CountySeedExpectation("350602", "xiangcheng", "xc@363000"),
            new CountySeedExpectation("350603", "longwen", "lw@363005"),
            new CountySeedExpectation("350604", "longhai", "lh@363100"),
            new CountySeedExpectation("350605", "changtai", "ct@363900"),
            new CountySeedExpectation("350622", "yunxiao", "yx@363300"),
            new CountySeedExpectation("350623", "zhangpu", "zp@363200"),
            new CountySeedExpectation("350624", "zhaoan", "za@363500"),
            new CountySeedExpectation("350626", "dongshan", "ds@363400"),
            new CountySeedExpectation("350627", "nanjing", "ExistingCounty@Test2026!"),
            new CountySeedExpectation("350628", "pinghe", "ph@363700"),
            new CountySeedExpectation("350629", "huaan", "ha@363800")
    );

    @Autowired
    private DefaultTenantService defaultTenantService;

    @Autowired
    private OrgUnitRepository orgUnitRepository;

    @Autowired
    private TenantGroupRepository tenantGroupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PermissionGrantRepository permissionGrantRepository;

    @Autowired
    private UserAuthorizationCache userAuthorizationCache;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataInitializer dataInitializer;

    @Autowired
    private AppConfigRepository appConfigRepository;

    @Autowired
    private AppConfigService appConfigService;

    @Autowired
    private ConfigurationScopeContext configurationScopeContext;

    @Autowired
    private OrganizationScopeAuthorizationService scopeAuthorizationService;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private UserService userService;

    @Autowired
    private OrgUnitService orgUnitService;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private AuthorizationAdministrationService authorizationAdministrationService;

    @Autowired
    private FileTransferService fileTransferService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Transactional
    void seedsAllCountyOrganizationsAndAdministratorsWithoutResettingPasswords() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        List<OrgUnit> organizations = orgUnitRepository.findByTenantId(tenantId);

        assertThat(defaultTenantService.requireDefaultTenant().getName()).isEqualTo("漳州市公司");
        assertThat(defaultTenantService.requireRootOrgUnit(tenantId))
                .satisfies(root -> {
                    assertThat(root.getCode()).isEqualTo("3506");
                    assertThat(root.getName()).isEqualTo("漳州市公司本部");
                });
        assertThat(tenantGroupRepository.findByCode("FUJIAN_PROVINCE").orElseThrow().getName()).isEqualTo("福建省公司");

        assertThat(organizations)
                .extracting(OrgUnit::getCode)
                .containsAll(COUNTY_SEEDS.stream()
                        .map(CountySeedExpectation::administrativeCode)
                        .toList());

        for (CountySeedExpectation county : COUNTY_SEEDS) {
            OrgUnit orgUnit = organizations.stream()
                    .filter(org -> county.administrativeCode().equals(org.getCode()))
                    .findFirst()
                    .orElseThrow();
            User administrator = userRepository.findByTenantIdAndUsername(tenantId, county.username()).orElseThrow();

            assertThat(administrator.getOrgUnitId()).isEqualTo(orgUnit.getId());
            assertThat(passwordEncoder.matches(county.initialPassword(), administrator.getPassword())).isTrue();
            var snapshot = userAuthorizationCache.snapshot(administrator);
            assertThat(snapshot.activeGrants(PermissionCatalog.NOTIFICATION_RECIPIENT_COUNTY,
                    java.time.LocalDateTime.now())).isNotEmpty();
        }

        User xiangcheng = userRepository.findByTenantIdAndUsername(tenantId, "xiangcheng").orElseThrow();
        String rotatedPassword = "Rotated@" + System.nanoTime();
        String rotatedHash = passwordEncoder.encode(rotatedPassword);
        xiangcheng.setPassword(rotatedHash);
        userRepository.saveAndFlush(xiangcheng);

        dataInitializer.run();

        User afterRestart = userRepository.findByTenantIdAndUsername(tenantId, "xiangcheng").orElseThrow();
        assertThat(afterRestart.getPassword()).isEqualTo(rotatedHash);
        assertThat(passwordEncoder.matches(rotatedPassword, afterRestart.getPassword())).isTrue();
    }

    @Test
    void tenantRootCannotBeDisabledOrChangedToCountyType() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        OrgUnit root = defaultTenantService.requireRootOrgUnit(tenantId);
        OrgUnitRequestDTO request = new OrgUnitRequestDTO();
        request.setCode(root.getCode());
        request.setName(root.getName());
        request.setParentId(null);
        request.setUnitType(com.pro.ereportbacken.model.pojo.OrgUnitType.COUNTY);
        request.setStatus(com.pro.ereportbacken.model.pojo.TenantStatus.ACTIVE);

        assertThatThrownBy(() -> tenantContext.runInTenant(
                tenantId, () -> orgUnitService.update(root.getId(), request)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("租户根组织必须保持市公司类型和启用状态");

        request.setUnitType(com.pro.ereportbacken.model.pojo.OrgUnitType.CITY);
        request.setStatus(com.pro.ereportbacken.model.pojo.TenantStatus.SUSPENDED);
        assertThatThrownBy(() -> tenantContext.runInTenant(
                tenantId, () -> orgUnitService.update(root.getId(), request)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("租户根组织必须保持市公司类型和启用状态");
    }

    @Test
    @Transactional
    void resolvesPlatformTenantAndOrganizationOverridesWithoutSiblingLeakage() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        OrgUnit root = defaultTenantService.requireRootOrgUnit(tenantId);
        OrgUnit xiangcheng = requireOrganization(tenantId, "350602");
        OrgUnit longwen = requireOrganization(tenantId, "350603");
        String configKey = "test.scope." + System.nanoTime();

        appConfigRepository.saveAndFlush(new AppConfig(configKey, "platform", "scope test"));
        assertEffective(configKey, xiangcheng.getId(), "platform", ResolvedAppConfig.SourceScope.PLATFORM_DEFAULT, true);

        appConfigService.saveOrUpdateScopedConfig(configKey, "city", "scope test", null);
        assertEffective(configKey, xiangcheng.getId(), "city", ResolvedAppConfig.SourceScope.TENANT_OVERRIDE, true);
        assertEffective(configKey, longwen.getId(), "city", ResolvedAppConfig.SourceScope.TENANT_OVERRIDE, true);

        appConfigService.saveOrUpdateScopedConfig(configKey, "root", "scope test", root.getId());
        assertEffective(configKey, xiangcheng.getId(), "root", ResolvedAppConfig.SourceScope.ORGANIZATION_OVERRIDE, true);
        assertEffective(configKey, longwen.getId(), "root", ResolvedAppConfig.SourceScope.ORGANIZATION_OVERRIDE, true);

        appConfigService.saveOrUpdateScopedConfig(configKey, "xiangcheng", "scope test", xiangcheng.getId());
        assertEffective(configKey, xiangcheng.getId(), "xiangcheng", ResolvedAppConfig.SourceScope.ORGANIZATION_OVERRIDE, false);
        assertEffective(configKey, longwen.getId(), "root", ResolvedAppConfig.SourceScope.ORGANIZATION_OVERRIDE, true);

        assertThat(configurationScopeContext.runInOrgUnit(
                xiangcheng.getId(),
                () -> appConfigService.getConfigValue(configKey)
        )).isEqualTo("xiangcheng");
        assertThat(configurationScopeContext.runInOrgUnit(
                longwen.getId(),
                () -> appConfigService.getConfigValue(configKey)
        )).isEqualTo("root");

        assertThatThrownBy(() -> appConfigService.deleteScopedConfig(configKey, longwen.getId()))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
                .hasMessageContaining("Organization config override");
    }

    @Test
    @Transactional
    void runtimeInitializerDoesNotMutateSeededCountyAccountBusinessData() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        OrgUnit longwenOrg = requireOrganization(tenantId, "350603");
        User longwen = userRepository.findByTenantIdAndUsername(tenantId, "longwen").orElseThrow();
        longwen.setPassword(passwordEncoder.encode("existing-password"));
        userRepository.saveAndFlush(longwen);
        var grantIds = permissionGrantRepository.findByTenantIdAndMembershipIdAndStatus(
                        tenantId, longwen.getId(), com.pro.ereportbacken.model.pojo.PermissionGrantStatus.ACTIVE)
                .stream().map(com.pro.ereportbacken.model.pojo.PermissionGrant::getId).toList();

        dataInitializer.run();

        User unchanged = userRepository.findByTenantIdAndUsername(tenantId, "longwen").orElseThrow();
        assertThat(unchanged.getOrgUnitId()).isEqualTo(longwenOrg.getId());
        assertThat(passwordEncoder.matches("existing-password", unchanged.getPassword())).isTrue();
        assertThat(permissionGrantRepository.findByTenantIdAndMembershipIdAndStatus(
                        tenantId, unchanged.getId(), com.pro.ereportbacken.model.pojo.PermissionGrantStatus.ACTIVE))
                .extracting(com.pro.ereportbacken.model.pojo.PermissionGrant::getId)
                .containsExactlyInAnyOrderElementsOf(grantIds);
    }

    @Test
    void countyAdministratorCannotEscapeItsOrganizationTreeOrGrantTenantScope() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        OrgUnit xiangcheng = requireOrganization(tenantId, "350602");
        OrgUnit longwen = requireOrganization(tenantId, "350603");
        User administrator = userRepository.findByTenantIdAndUsername(tenantId, "xiangcheng").orElseThrow();
        authenticate(administrator);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .contains(PermissionCatalog.USER_AUTHORIZATION_ADJUST, PermissionCatalog.SYSTEM_CONFIG_UPDATE);

        var access = scopeAuthorizationService.accessFor(PermissionCatalog.SYSTEM_CONFIG_VIEW);
        assertThat(access.tenantWide()).isFalse();
        assertThat(access.orgUnitIds()).contains(xiangcheng.getId()).doesNotContain(longwen.getId());
        assertThatThrownBy(() -> scopeAuthorizationService.requireOrganizationAccess(
                PermissionCatalog.SYSTEM_CONFIG_VIEW, longwen.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> scopeAuthorizationService.requireTenantWideAccess(
                PermissionCatalog.SYSTEM_CONFIG_VIEW))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(userService.findAllUsers(null, PageRequest.of(0, 100), false).getContent())
                .extracting(User::getUsername)
                .contains("xiangcheng")
                .doesNotContain("longwen");
        assertThat(userService.findAllUsers(
                null,
                xiangcheng.getId(),
                UserStatus.ACTIVE,
                PageRequest.of(0, 100),
                false
        ).getContent())
                .allSatisfy(user -> assertThat(user.getOrgUnitId()).isEqualTo(xiangcheng.getId()))
                .extracting(User::getUsername)
                .contains("xiangcheng")
                .doesNotContain("longwen");
        assertThatThrownBy(() -> userService.findAllUsers(
                null,
                longwen.getId(),
                UserStatus.ACTIVE,
                PageRequest.of(0, 100),
                false
        )).isInstanceOf(AccessDeniedException.class);

        assertThat(authorizationAdministrationService.options(administrator.getId()).templates())
                .extracting(option -> option.code())
                .contains(BuiltInAuthorizationProfileCatalog.COUNTY_NOTIFICATION_RECIPIENT)
                .doesNotContain(
                        BuiltInAuthorizationProfileCatalog.TENANT_NOTIFICATION_RECIPIENT,
                        BuiltInAuthorizationProfileCatalog.SLA_EDITOR,
                        BuiltInAuthorizationProfileCatalog.TENANT_PROVISIONER,
                        BuiltInAuthorizationProfileCatalog.TENANT_ADMINISTRATOR,
                        BuiltInAuthorizationProfileCatalog.PLATFORM_ADMINISTRATOR
                );
        assertThatThrownBy(() -> scopeAuthorizationService.requireOrganizationAccess(
                PermissionCatalog.USER_AUTHORIZATION_GRANT,
                defaultTenantService.requireRootOrgUnit(tenantId).getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @Transactional
    void pinyinSearchKeepsCountyOrganizationIsolation() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        User xiangcheng = userRepository.findByTenantIdAndUsername(tenantId, "xiangcheng").orElseThrow();
        User longwen = userRepository.findByTenantIdAndUsername(tenantId, "longwen").orElseThrow();
        xiangcheng.setName("林智博");
        longwen.setName("林智博");
        userRepository.saveAllAndFlush(List.of(xiangcheng, longwen));

        authenticate(xiangcheng);

        assertThat(userRepository.searchActiveUserIds(
                tenantId, UserStatus.ACTIVE, "lzb", "lzb", PageRequest.of(0, 20)))
                .contains(xiangcheng.getId(), longwen.getId());
        assertThat(userService.findAllUsers("lzb", PageRequest.of(0, 100), false).getContent())
                .extracting(User::getUsername)
                .containsExactly("xiangcheng");
        assertThat(userService.findAllUsers("LIN ZHI BO", PageRequest.of(0, 100), false).getContent())
                .extracting(User::getUsername)
                .containsExactly("xiangcheng");
        assertThat(xiangcheng.getNamePinyin()).isEqualTo("linzhibo");
        assertThat(xiangcheng.getNamePinyinInitials()).isEqualTo("lzb");
    }

    @Test
    @Transactional
    void countyAdministratorCannotMoveSiblingUserAcrossOrganizationBoundary() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        OrgUnit xiangcheng = requireOrganization(tenantId, "350602");
        User longwen = userRepository.findByTenantIdAndUsername(tenantId, "longwen").orElseThrow();
        longwen.setPhone("13990000200");
        userRepository.saveAndFlush(longwen);

        User administrator = userRepository.findByTenantIdAndUsername(tenantId, "xiangcheng").orElseThrow();
        authenticate(administrator);
        assertThatThrownBy(() -> scopeAuthorizationService.requireOrganizationAccess(
                PermissionCatalog.USER_MANAGE_UPDATE_DETAILS, longwen.getOrgUnitId()))
                .isInstanceOf(AccessDeniedException.class);
        scopeAuthorizationService.requireOrganizationAccess(
                PermissionCatalog.USER_MANAGE_UPDATE_DETAILS, xiangcheng.getId());
        assertThat(userRepository.findById(longwen.getId()).orElseThrow().getOrgUnitId())
                .isNotEqualTo(xiangcheng.getId());
    }

    private OrgUnit requireOrganization(UUID tenantId, String code) {
        return orgUnitRepository.findByTenantId(tenantId).stream()
                .filter(org -> code.equals(org.getCode()))
                .findFirst()
                .orElseThrow();
    }

    private void assertEffective(String configKey,
                                 UUID orgUnitId,
                                 String expectedValue,
                                 ResolvedAppConfig.SourceScope expectedSource,
                                 boolean inherited) {
        ResolvedAppConfig resolved = appConfigService.findScopedConfigByKey(configKey, orgUnitId).orElseThrow();
        assertThat(resolved.config().getConfigValue()).isEqualTo(expectedValue);
        assertThat(resolved.sourceScope()).isEqualTo(expectedSource);
        assertThat(resolved.inherited()).isEqualTo(inherited);
    }

    private void authenticate(User user) {
        TenantUserDetails details = userDetailsService.loadForToken(user.getTenantId(), user.getId(), user.getUsername());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                details,
                null,
                details.getAuthorities()
        ));
    }

    private record CountySeedExpectation(String administrativeCode, String username, String initialPassword) {
    }
}
