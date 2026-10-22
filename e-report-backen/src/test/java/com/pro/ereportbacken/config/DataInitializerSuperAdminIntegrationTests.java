package com.pro.ereportbacken.config;

import com.pro.ereportbacken.model.pojo.GrantIssuerType;
import com.pro.ereportbacken.model.pojo.PermissionGrant;
import com.pro.ereportbacken.model.pojo.PermissionGrantStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserSystemKey;
import com.pro.ereportbacken.repository.PermissionGrantRepository;
import com.pro.ereportbacken.repository.PermissionRepository;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.service.DefaultTenantService;
import com.pro.ereportbacken.service.SystemGrantProvisioningService;
import com.pro.ereportbacken.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:e_report_super_admin_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "app.bootstrap-admin.username=city-admin",
        "app.bootstrap-admin.password=Bootstrap!City_2026",
        "app.bootstrap-admin.phone=13800000046",
        "app.bootstrap-super-admin.username=superAdmin",
        "app.bootstrap-super-admin.password=Cedar!Quartz_2026-Root",
        "app.bootstrap-super-admin.phone=13900000046"
})
@ActiveProfiles("test")
class DataInitializerSuperAdminIntegrationTests {

    @Autowired
    private DefaultTenantService defaultTenantService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PermissionGrantRepository permissionGrantRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    @Autowired
    private DataInitializer dataInitializer;

    @Autowired
    private SystemAuthorizationBaselineInitializer systemAuthorizationBaselineInitializer;

    @Autowired
    private SystemGrantProvisioningService systemGrantProvisioningService;

    @Test
    void createsProtectedSuperAdminAndSeparatesPlatformAuthorityFromCityAdmin() {
        User countyAdministrator = user("nanjing");
        User superAdministrator = user("superAdmin");

        assertThat(countyAdministrator.getSystemKey()).isNull();
        assertThat(superAdministrator.getSystemKey()).isEqualTo(UserSystemKey.SUPER_ADMINISTRATOR);
        assertThat(userService.isSystemProtected(countyAdministrator)).isFalse();
        assertThat(userService.isSystemProtected(superAdministrator)).isTrue();
        assertThat(activeGrants(countyAdministrator, GrantIssuerType.SYSTEM_BASELINE)).isNotEmpty();
        assertThat(activeGrants(superAdministrator, GrantIssuerType.SYSTEM_ROOT)).isNotEmpty();
    }

    @Test
    void systemRootAndCurrentAdministratorTemplateExposeDelegableCatalog() {
        User countyAdministrator = user("nanjing");
        User superAdministrator = user("superAdmin");

        List<PermissionGrant> superAdministratorGrants = activeGrants(
                superAdministrator, GrantIssuerType.SYSTEM_ROOT);
        assertThat(superAdministratorGrants).hasSize(PermissionCatalog.names().size());
        assertThat(superAdministratorGrants).allSatisfy(grant ->
                assertThat(grant.getGrantOption()).isTrue());
        assertThat(permissionNames(superAdministratorGrants))
                .containsExactlyInAnyOrderElementsOf(PermissionCatalog.names());

        systemGrantProvisioningService.reconcileTemplateBaseline(
                countyAdministrator,
                com.pro.ereportbacken.security.BuiltInAuthorizationProfileCatalog.COUNTY_ADMINISTRATOR,
                "测试校准：区县管理员标准权限基线");
        List<PermissionGrant> countyBaseline = activeGrants(
                countyAdministrator, GrantIssuerType.SYSTEM_BASELINE);
        assertThat(countyBaseline).isNotEmpty().allSatisfy(grant ->
                assertThat(grant.getGrantOption()).isTrue());
        assertThat(permissionNames(countyBaseline))
                .contains(PermissionCatalog.USER_AUTHORIZATION_GRANT_OPTION_ISSUE);
    }

    @Test
    void everyOrdinaryUserReceivesNonDelegableSelfServiceApiTokenPermissions() {
        User countyAdministrator = user("nanjing");
        List<PermissionGrant> selfBaseline = activeGrants(
                countyAdministrator, GrantIssuerType.SYSTEM_SELF_BASELINE);

        assertThat(permissionNames(selfBaseline)).contains(
                PermissionCatalog.API_TOKEN_VIEW,
                PermissionCatalog.API_TOKEN_CREATE,
                PermissionCatalog.API_TOKEN_REVOKE);
        assertThat(selfBaseline).filteredOn(grant -> permissionNames(List.of(grant))
                        .stream().anyMatch(Set.of(
                                PermissionCatalog.API_TOKEN_VIEW,
                                PermissionCatalog.API_TOKEN_CREATE,
                                PermissionCatalog.API_TOKEN_REVOKE)::contains))
                .allSatisfy(grant -> assertThat(grant.getGrantOption()).isFalse());
    }

    @Test
    void repeatedSystemBaselineReconciliationIsIdempotent() throws Exception {
        User before = user("superAdmin");
        List<java.util.UUID> grantIds = activeGrants(before, GrantIssuerType.SYSTEM_ROOT).stream()
                .map(PermissionGrant::getId)
                .toList();

        systemAuthorizationBaselineInitializer.run();

        assertThat(activeGrants(user("superAdmin"), GrantIssuerType.SYSTEM_ROOT))
                .extracting(PermissionGrant::getId)
                .containsExactlyInAnyOrderElementsOf(grantIds);
    }

    @Test
    void repeatedInitializationKeepsDatabaseOwnedSuperAdminCredentials() {
        User before = user("superAdmin");
        String databasePassword = "数据库维护-超级管理员-2026-唯一口令";
        before.setPassword(passwordEncoder.encode(databasePassword));
        before.setName("数据库维护的超级管理员");
        userRepository.saveAndFlush(before);
        List<java.util.UUID> grantIds = activeGrants(before, GrantIssuerType.SYSTEM_ROOT).stream()
                .map(PermissionGrant::getId)
                .toList();

        dataInitializer.run();

        User after = user("superAdmin");
        assertThat(passwordEncoder.matches(databasePassword, after.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("Cedar!Quartz_2026-Root", after.getPassword())).isFalse();
        assertThat(after.getName()).isEqualTo("数据库维护的超级管理员");
        assertThat(activeGrants(after, GrantIssuerType.SYSTEM_ROOT))
                .extracting(PermissionGrant::getId)
                .containsExactlyInAnyOrderElementsOf(grantIds);
    }

    private User user(String username) {
        return userRepository.findByTenantIdAndUsername(
                defaultTenantService.requireDefaultTenant().getId(), username).orElseThrow();
    }

    private List<PermissionGrant> activeGrants(User user, GrantIssuerType issuerType) {
        return permissionGrantRepository.findByTenantIdAndMembershipIdAndStatus(
                        user.getTenantId(), user.getId(), PermissionGrantStatus.ACTIVE)
                .stream()
                .filter(grant -> grant.getIssuerType() == issuerType)
                .toList();
    }

    private java.util.Set<String> permissionNames(List<PermissionGrant> grants) {
        java.util.Map<java.util.UUID, String> namesById = permissionRepository.findAllById(
                        grants.stream().map(PermissionGrant::getPermissionId).collect(java.util.stream.Collectors.toSet()))
                .stream().collect(java.util.stream.Collectors.toMap(
                        com.pro.ereportbacken.model.pojo.Permission::getId,
                        com.pro.ereportbacken.model.pojo.Permission::getName));
        return grants.stream().map(grant -> namesById.get(grant.getPermissionId()))
                .collect(java.util.stream.Collectors.toSet());
    }
}
