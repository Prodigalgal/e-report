package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.PermissionCatalogDriftDTO;
import com.pro.ereportbacken.model.pojo.Permission;
import com.pro.ereportbacken.repository.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:e_report_permission_drift_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class PermissionCatalogDriftIntegrationTests {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PermissionCatalogDriftService driftService;

    @Test
    @Transactional
    void reportsDatabaseOnlyPermissionWithoutBreakingCurrentCatalog() {
        assertThat(driftService.scan().status()).isEqualTo(PermissionCatalogDriftDTO.Status.HEALTHY);
        Permission retired = permissionRepository.saveAndFlush(
                new Permission("retired:permission", "已退役权限", "目录漂移样本"));

        PermissionCatalogDriftDTO drift = driftService.scan();

        assertThat(drift.status()).isEqualTo(PermissionCatalogDriftDTO.Status.WARNING);
        assertThat(drift.permissionCatalogDriftCount()).isEqualTo(1);
        assertThat(drift.databaseOnlyPermissions()).singleElement().satisfies(permission -> {
            assertThat(permission.permissionName()).isEqualTo(retired.getName());
            assertThat(permission.totalReferences()).isZero();
        });
    }
}
