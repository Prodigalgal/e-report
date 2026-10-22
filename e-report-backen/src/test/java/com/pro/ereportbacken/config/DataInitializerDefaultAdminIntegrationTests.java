package com.pro.ereportbacken.config;

import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.GrantIssuerType;
import com.pro.ereportbacken.model.pojo.PermissionGrantStatus;
import com.pro.ereportbacken.model.pojo.TenantSystemKey;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.PermissionGrantRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.service.DefaultTenantService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:e_report_default_admin_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "app.bootstrap-admin.username=bootstrap-admin",
        "app.bootstrap-admin.password=Bootstrap!Admin_2026",
        "app.bootstrap-admin.phone=13800000044"
})
@ActiveProfiles("test")
class DataInitializerDefaultAdminIntegrationTests {

    @Autowired
    private DefaultTenantService defaultTenantService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrgUnitRepository orgUnitRepository;

    @Autowired
    private PermissionGrantRepository permissionGrantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataInitializer dataInitializer;

    @Test
    void retiredBootstrapAdministratorPropertiesCannotCreateRootAdministrator() {
        assertThat(userRepository.findByUsername("bootstrap-admin")).isEmpty();
        assertThat(defaultTenantService.requireDefaultTenant().getSystemKey()).isEqualTo(TenantSystemKey.DEFAULT_TENANT);
        assertThat(defaultTenantService.requireRootOrgUnit(
                defaultTenantService.requireDefaultTenant().getId()).getCode()).isEqualTo("3506");
    }

    @Test
    void countyAdministratorSeedAccountsKeepExplicitGrantsWithoutAnOrganizationOwnerPointer() {
        List<String> countyAdministratorUsernames = List.of(
                "xiangcheng", "longwen", "longhai", "changtai", "yunxiao", "zhangpu",
                "zhaoan", "dongshan", "nanjing", "pinghe", "huaan");
        var tenant = defaultTenantService.requireDefaultTenant();

        assertThat(countyAdministratorUsernames).hasSize(11);
        countyAdministratorUsernames.forEach(username -> {
            User administrator = userRepository.findByTenantIdAndUsername(tenant.getId(), username).orElseThrow();
            var organization = orgUnitRepository.findByTenantIdAndId(
                    tenant.getId(), administrator.getOrgUnitId()).orElseThrow();
            assertThat(administrator.getOrgUnitId()).isEqualTo(organization.getId());
            assertThat(organization.getParentId()).isNotNull();
            assertThat(administrator.getSystemKey()).isNull();
            assertThat(permissionGrantRepository.findByTenantIdAndMembershipIdAndStatus(
                            administrator.getTenantId(), administrator.getId(), PermissionGrantStatus.ACTIVE)
                    .stream()
                    .filter(grant -> grant.getIssuerType() == GrantIssuerType.SYSTEM_BASELINE))
                    .isNotEmpty();
        });
    }

    @Test
    void repeatedInitializationPreservesDatabaseOwnedAdministratorState() {
        User before = userRepository.findByTenantIdAndUsername(
                defaultTenantService.requireDefaultTenant().getId(), "nanjing").orElseThrow();
        String administratorManagedPassword = "清风-月明-远山-2026-管理员";
        before.setPassword(passwordEncoder.encode(administratorManagedPassword));
        before.setPasswordChangeRequired(false);
        before.setPasswordChangedAt(java.time.LocalDateTime.now());
        before.setName("数据库管理员自定义名称");
        userRepository.saveAndFlush(before);
        var grantsBefore = permissionGrantRepository.findByTenantIdAndMembershipIdAndStatus(
                before.getTenantId(), before.getId(), PermissionGrantStatus.ACTIVE);
        assertThat(grantsBefore).isNotEmpty();
        var retainedGrantIds = grantsBefore.stream()
                .map(com.pro.ereportbacken.model.pojo.PermissionGrant::getId)
                .collect(java.util.stream.Collectors.toSet());
        String passwordHash = before.getPassword();
        Long authorizationVersion = before.getAuthorizationVersion();

        dataInitializer.run();

        User after = userRepository.findByTenantIdAndUsername(
                defaultTenantService.requireDefaultTenant().getId(), "nanjing").orElseThrow();
        assertThat(passwordEncoder.matches(administratorManagedPassword, after.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("Bootstrap!Admin_2026", after.getPassword())).isFalse();
        assertThat(after.getPassword()).isEqualTo(passwordHash);
        assertThat(after.getAuthorizationVersion()).isEqualTo(authorizationVersion);
        assertThat(after.getName()).isEqualTo("数据库管理员自定义名称");
        assertThat(after.getStatus()).isEqualTo(com.pro.ereportbacken.model.pojo.UserStatus.ACTIVE);
        assertThat(permissionGrantRepository.findByTenantIdAndMembershipIdAndStatus(
                after.getTenantId(), after.getId(), PermissionGrantStatus.ACTIVE))
                .extracting(com.pro.ereportbacken.model.pojo.PermissionGrant::getId)
                .containsExactlyInAnyOrderElementsOf(retainedGrantIds);
    }
}
