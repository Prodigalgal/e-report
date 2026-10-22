package com.pro.ereportbacken.config;

import com.pro.ereportbacken.model.pojo.GrantIssuerType;
import com.pro.ereportbacken.model.pojo.PermissionGrant;
import com.pro.ereportbacken.model.pojo.PermissionGrantStatus;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.repository.PermissionGrantRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.service.DefaultTenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:e_report_existing_default_admin_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "app.bootstrap-admin.username=existing-bootstrap-admin",
        "app.bootstrap-admin.password=Bootstrap!Admin_2026",
        "app.bootstrap-admin.phone=13800000045"
})
@ActiveProfiles("test")
class DataInitializerExistingDefaultAdminAuthorizationTests {

    @Autowired
    private DefaultTenantService defaultTenantService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PermissionGrantRepository permissionGrantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataInitializer dataInitializer;

    @Test
    void startupDoesNotRecreateRevokedAdministratorGrantsOrOverwriteDatabaseOwnedAccountState() {
        User administrator = userRepository.findByTenantIdAndUsername(
                defaultTenantService.requireDefaultTenant().getId(), "nanjing").orElseThrow();
        String databasePassword = "数据库维护-默认管理员-2026-安全口令";
        administrator.setPassword(passwordEncoder.encode(databasePassword));
        administrator.setName("数据库维护的管理员名称");
        userRepository.saveAndFlush(administrator);

        LocalDateTime revokedAt = LocalDateTime.now();
        activeSystemBaselineGrants(administrator)
                .forEach(grant -> {
                    grant.setStatus(PermissionGrantStatus.REVOKED);
                    grant.setRevokedAt(revokedAt);
                    grant.setRevokeReason("模拟数据库系统授权缺失");
                    permissionGrantRepository.save(grant);
                });
        permissionGrantRepository.flush();
        Long authorizationVersionBeforeRepair = administrator.getAuthorizationVersion();

        dataInitializer.run();

        User afterRestart = userRepository.findByTenantIdAndUsername(
                defaultTenantService.requireDefaultTenant().getId(), "nanjing").orElseThrow();
        assertThat(passwordEncoder.matches(databasePassword, afterRestart.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("Bootstrap!Admin_2026", afterRestart.getPassword())).isFalse();
        assertThat(afterRestart.getName()).isEqualTo("数据库维护的管理员名称");
        assertThat(afterRestart.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(afterRestart.getAuthorizationVersion()).isEqualTo(authorizationVersionBeforeRepair);
        assertThat(activeSystemBaselineGrants(afterRestart)).isEmpty();
    }

    private java.util.List<PermissionGrant> activeSystemBaselineGrants(User administrator) {
        return permissionGrantRepository.findByTenantIdAndMembershipIdAndStatus(
                        administrator.getTenantId(), administrator.getId(), PermissionGrantStatus.ACTIVE)
                .stream()
                .filter(grant -> grant.getIssuerType() == GrantIssuerType.SYSTEM_BASELINE)
                .toList();
    }
}
