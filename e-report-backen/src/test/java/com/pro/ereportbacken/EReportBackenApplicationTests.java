package com.pro.ereportbacken;

import java.util.UUID;

import com.pro.ereportbacken.model.dto.WorkOrderFilterDTO;
import com.pro.ereportbacken.model.pojo.AppConfig;
import com.pro.ereportbacken.model.pojo.AppEdition;
import com.pro.ereportbacken.model.pojo.AppPlatform;
import com.pro.ereportbacken.model.pojo.AppRelease;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderStatus;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import com.pro.ereportbacken.model.pojo.WorkOrderDuty;
import com.pro.ereportbacken.model.pojo.MaterialCategory;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.WorkOrderMaterialUsage;
import com.pro.ereportbacken.repository.AppReleaseRepository;
import com.pro.ereportbacken.repository.WorkOrderRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.BuiltInAuthorizationProfileCatalog;
import com.pro.ereportbacken.service.AppConfigService;
import com.pro.ereportbacken.service.AppUpdateService;
import com.pro.ereportbacken.service.DefaultTenantService;
import com.pro.ereportbacken.service.WorkOrderService;
import com.pro.ereportbacken.service.SystemGrantProvisioningService;
import com.pro.ereportbacken.specification.WorkOrderSpecification;
import com.pro.ereportbacken.time.UtcTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@ActiveProfiles("test")
class EReportBackenApplicationTests {

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private AppConfigService appConfigService;

    @Autowired
    private AppReleaseRepository appReleaseRepository;

    @Autowired
    private AppUpdateService appUpdateService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DefaultTenantService defaultTenantService;

    @Autowired
    private SystemGrantProvisioningService authorizationProvisioningService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void generatedEntityTimestampsUseUtc() {
        assertThat(System.getProperty("user.timezone")).isEqualTo("UTC");
        assertThat(jdbcTemplate.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class).getOffset())
                .isEqualTo(ZoneOffset.UTC);
        LocalDateTime before = UtcTime.now().minusSeconds(1);

        User created = saveDefaultTenantUser("utc-created-at-" + System.nanoTime(), "UTC 时间测试人员");

        assertThat(created.getCreatedAt()).isBetween(before, UtcTime.now().plusSeconds(1));
        assertThat(userRepository.findById(created.getId()).orElseThrow().getCreatedAt())
                .isEqualTo(created.getCreatedAt());
    }

    @Test
    void workOrderReportTimeRemainsImmutableWhileLastUpdatedTimeAdvances() throws InterruptedException {
        User reporter = saveDefaultTenantUser("audit-time-" + System.nanoTime(), "审计时间测试人员");
        WorkOrder workOrder = testWorkOrder("审计时间分离-" + System.nanoTime(), WorkOrderType.NETWORK_FAULT, reporter);

        WorkOrder created = workOrderRepository.saveAndFlush(workOrder);
        LocalDateTime reportTime = created.getReportTime();
        LocalDateTime initialUpdatedTime = created.getLastUpdatedTime();

        assertThat(reportTime).isNotNull();
        assertThat(initialUpdatedTime).isEqualTo(reportTime);

        Thread.sleep(10L);
        created.setReason("更新工单内容但不改变报单时间");
        WorkOrder updated = workOrderRepository.saveAndFlush(created);
        WorkOrder reloaded = workOrderRepository.findById(updated.getId()).orElseThrow();

        assertThat(reloaded.getReportTime()).isCloseTo(reportTime, within(1, ChronoUnit.MICROS));
        assertThat(reloaded.getLastUpdatedTime()).isAfter(initialUpdatedTime);
    }

    @Test
    void authorizationV2ProfileRelationsAreCollapsedWithAuditEvidence() {
        Integer duplicateRules = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT profile_version_id, permission_id
                    FROM authorization_profile_permission_rules
                    GROUP BY profile_version_id, permission_id
                    HAVING COUNT(*) > 1
                ) duplicate_rules
                """, Integer.class);
        Integer collapsedRules = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM authorization_v2_profile_rule_collapse_audit_20260823
                """, Integer.class);
        Integer retiredV2Tables = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE LOWER(table_name) IN (
                    'authorization_assignments',
                    'authorization_data_scopes',
                    'authorization_data_scope_nodes'
                )
                """, Integer.class);
        Integer retirementAuditRows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM authorization_v2_retirement_audit_20260823
                """, Integer.class);

        assertThat(duplicateRules).isZero();
        assertThat(collapsedRules).isNotNull().isNotNegative();
        assertThat(retiredV2Tables).isZero();
        assertThat(retirementAuditRows).isEqualTo(3);
    }

    @Test
    void fuzzySearchSupportsTextColumns() {
        User reporter = saveDefaultTenantUser("search-reporter", "鼓楼装维人员");

        WorkOrder workOrder = new WorkOrder();
        assignDefaultOwnership(workOrder);
        workOrder.setLocation("鼓楼区测试点");
        workOrder.setReason("光缆外皮破损，需要现场处理");
        workOrder.setResolutionDetails("回单现场处理完成，光缆已恢复");
        workOrder.setClosureReason("验收通过，故障已闭环");
        workOrder.setReplacementResult("无需更换箱体");
        workOrder.setContactPerson("测试联系人");
        workOrder.setPhone("15900000000");
        workOrder.setReportedBy(reporter);
        workOrder = workOrderRepository.saveAndFlush(workOrder);

        WorkOrderFilterDTO filterDTO = new WorkOrderFilterDTO();
        filterDTO.setSearchTerm("外皮破损");
        filterDTO.setTenantId(workOrder.getTenantId());
        filterDTO.setAccessibleReportTypes(Set.of(WorkOrderType.values()));

        assertThat(workOrderRepository.findAll(
                WorkOrderSpecification.getWorkOrdersByCriteria(filterDTO),
                PageRequest.of(0, 10)
        ).getTotalElements()).isGreaterThanOrEqualTo(1);

        filterDTO.setSearchTerm("鼓楼装维");
        assertThat(workOrderRepository.findAll(
                WorkOrderSpecification.getWorkOrdersByCriteria(filterDTO),
                PageRequest.of(0, 10)
        ).getContent()).extracting(WorkOrder::getId).contains(workOrder.getId());

        for (String textColumnTerm : List.of("现场处理完成", "故障已闭环", "无需更换箱体")) {
            filterDTO.setSearchTerm(textColumnTerm);
            assertThat(workOrderRepository.findAll(
                    WorkOrderSpecification.getWorkOrdersByCriteria(filterDTO),
                    PageRequest.of(0, 10)
            ).getContent()).extracting(WorkOrder::getId).contains(workOrder.getId());
        }

        filterDTO.setSearchTerm("工单 #" + workOrder.getId());
        assertThat(workOrderRepository.findAll(
                WorkOrderSpecification.getWorkOrdersByCriteria(filterDTO),
                PageRequest.of(0, 10)
        ).getContent()).extracting(WorkOrder::getId).containsExactly(workOrder.getId());

        appConfigService.saveOrUpdateConfig("test.search.webhook", "webhook-secret-abc", "测试搜索配置");
        assertThat(appConfigService.searchConfigs("secret", PageRequest.of(0, 10)).getTotalElements())
                .isZero();
        assertThat(appConfigService.searchConfigs("test.search.webhook", PageRequest.of(0, 10)).getTotalElements())
                .isGreaterThanOrEqualTo(1);
        assertThat(appConfigService.saveOrUpdateScopedConfig(
                "test.search.webhook", null, "保留敏感值", null, true).config().getConfigValue())
                .isEqualTo("webhook-secret-abc");

        AppRelease appRelease = new AppRelease();
        appRelease.setPlatform(AppPlatform.ANDROID);
        appRelease.setChannel("stable");
        appRelease.setVersionCode(99901);
        appRelease.setVersionName("1.0.99-test");
        appRelease.setRuntimeVersion("android");
        appRelease.setMinSupportedVersionCode(1);
        appRelease.setForceUpdate(false);
        appRelease.setReleaseNotes("测试发布说明，验证模糊搜索");
        appRelease.setOriginalFileName("test-release.apk");
        appRelease.setStoredFileName("test-release-stored.apk");
        appRelease.setFileSize(1024L);
        appRelease.setSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        appRelease.setContentType("application/vnd.android.package-archive");
        appRelease.setPublished(true);
        appRelease.setPublishedAt(LocalDateTime.now());
        appReleaseRepository.saveAndFlush(appRelease);

        assertThat(appUpdateService.list(AppEdition.NATIVE, AppPlatform.ANDROID, "stable", "发布说明", PageRequest.of(0, 10)).getTotalElements())
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void workOrderDetailKeepsMaterialUsagesAvailableAfterReadTransaction() {
        String reporterUsername = "detail-reporter-" + System.nanoTime();
        UUID workOrderId = new TransactionTemplate(transactionManager).execute(status -> {
            User reporter = saveDefaultTenantUser(reporterUsername, "详情测试提单人");
            grantProfile(reporter, BuiltInAuthorizationProfileCatalog.workOrderProfileKey(
                    WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));

            WorkOrder workOrder = new WorkOrder();
            assignDefaultOwnership(workOrder);
            workOrder.setLocation("详情查询测试点");
            workOrder.setReason("验证关闭 Open EntityManager in View 后的详情读取");
            workOrder.setContactPerson("详情测试联系人");
            workOrder.setPhone("15900000001");
            workOrder.setReportedBy(reporter);

            WorkOrderMaterialUsage materialUsage = new WorkOrderMaterialUsage();
            materialUsage.setCategory(MaterialCategory.OPTICAL_CABLE);
            materialUsage.setSpecification("24芯");
            materialUsage.setQuantity(2);
            workOrder.addMaterialUsage(materialUsage);
            return workOrderRepository.saveAndFlush(workOrder).getId();
        });

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(reporterUsername, "test-password", PermissionCatalog.NETWORK_FAULT_VIEW)
        );
        try {
            var detail = workOrderService.getWorkOrderById(workOrderId);

            assertThat(detail.getMaterialUsages())
                    .singleElement()
                    .satisfies(material -> {
                        assertThat(material.getCategory()).isEqualTo(MaterialCategory.OPTICAL_CABLE);
                        assertThat(material.getSpecification()).isEqualTo("24芯");
                        assertThat(material.getQuantity()).isEqualTo(2);
                    });
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void unifiedSearchReturnsOwnAndManagedWorkOrdersWithoutLeakingOtherTypes() {
        String marker = "统一查询-" + System.nanoTime();
        User currentUser = saveDefaultTenantUser(
                "unified-search-user-" + System.nanoTime(),
                "统一查询测试人员"
        );
        User otherReporter = saveDefaultTenantUser(
                "unified-search-other-" + System.nanoTime(),
                "其他提单人员"
        );
        grantProfile(currentUser, BuiltInAuthorizationProfileCatalog.workOrderProfileKey(
                WorkOrderType.NETWORK_FAULT, WorkOrderDuty.REPORTER));
        grantProfile(currentUser, BuiltInAuthorizationProfileCatalog.workOrderProfileKey(
                WorkOrderType.BOX_DOOR_OPEN, WorkOrderDuty.AUDITOR));

        WorkOrder ownFault = testWorkOrder(marker, WorkOrderType.NETWORK_FAULT, currentUser);
        WorkOrder managedBoxDoorOpen = testWorkOrder(marker, WorkOrderType.BOX_DOOR_OPEN, otherReporter);
        WorkOrder inaccessibleDamagedBox = testWorkOrder(marker, WorkOrderType.DAMAGED_BOX_REPLACEMENT, otherReporter);
        workOrderRepository.saveAllAndFlush(Set.of(ownFault, managedBoxDoorOpen, inaccessibleDamagedBox));

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                currentUser.getUsername(),
                "test-password",
                PermissionCatalog.NETWORK_FAULT_VIEW,
                PermissionCatalog.BOX_DOOR_OPEN_VIEW
        ));
        try {
            WorkOrderFilterDTO filter = new WorkOrderFilterDTO();
            filter.setSearchTerm(marker);

            var result = workOrderService.searchVisibleWorkOrders(filter, PageRequest.of(0, 10));

            assertThat(result.getContent())
                    .extracting(item -> item.getReportType())
                    .containsExactlyInAnyOrder(WorkOrderType.NETWORK_FAULT.name(), WorkOrderType.BOX_DOOR_OPEN.name());
            assertThat(result.getContent())
                    .extracting(item -> item.getId())
                    .doesNotContain(inaccessibleDamagedBox.getId());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void workOrderSearchCombinesMultiValueDimensionsWithOrAndAcrossDimensions() {
        String marker = "多选查询-" + System.nanoTime();
        User currentUser = saveDefaultTenantUser(
                "multi-search-user-" + System.nanoTime(),
                "多选查询当前用户"
        );
        User otherReporter = saveDefaultTenantUser(
                "multi-search-other-" + System.nanoTime(),
                "多选查询其他用户"
        );

        WorkOrder ownClosedFault = testWorkOrder(marker, WorkOrderType.NETWORK_FAULT, currentUser);
        ownClosedFault.transitionTo(WorkOrderStatus.PENDING_VERIFICATION);
        ownClosedFault.transitionTo(WorkOrderStatus.CLOSED);
        WorkOrder claimableBoxDoorOpen = testWorkOrder(marker, WorkOrderType.BOX_DOOR_OPEN, otherReporter);
        WorkOrder filteredDamagedBox = testWorkOrder(marker, WorkOrderType.DAMAGED_BOX_REPLACEMENT, otherReporter);
        filteredDamagedBox.transitionTo(WorkOrderStatus.IN_PROGRESS);
        ownClosedFault = workOrderRepository.saveAndFlush(ownClosedFault);
        claimableBoxDoorOpen = workOrderRepository.saveAndFlush(claimableBoxDoorOpen);
        filteredDamagedBox = workOrderRepository.saveAndFlush(filteredDamagedBox);

        WorkOrderFilterDTO filter = new WorkOrderFilterDTO();
        filter.setSearchTerm(marker);
        filter.setStatuses(Set.of(WorkOrderStatus.SUBMITTED.name(), WorkOrderStatus.CLOSED.name()));
        filter.setReportTypes(Set.of(WorkOrderType.NETWORK_FAULT.name(), WorkOrderType.BOX_DOOR_OPEN.name()));
        filter.setQueues(Set.of("REPORTED_BY_ME", "CLAIMABLE"));
        filter.setAccessMembershipId(currentUser.getId());
        filter.setTenantWideReporterTypes(Set.of(WorkOrderType.NETWORK_FAULT));
        filter.setTenantWideClaimableTypes(Set.of(WorkOrderType.BOX_DOOR_OPEN));

        var result = workOrderRepository.findAll(
                WorkOrderSpecification.getWorkOrdersByCriteria(filter),
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(WorkOrder::getId)
                .containsExactlyInAnyOrder(ownClosedFault.getId(), claimableBoxDoorOpen.getId())
                .doesNotContain(filteredDamagedBox.getId());
    }

    private WorkOrder testWorkOrder(String marker, WorkOrderType type, User reporter) {
        WorkOrder workOrder = new WorkOrder();
        assignDefaultOwnership(workOrder);
        workOrder.setLocation(marker + "-" + type.name());
        workOrder.setReason("验证统一工单查询权限并集");
        workOrder.setContactPerson("统一查询联系人");
        workOrder.setPhone("15900000002");
        workOrder.setReportType(type);
        workOrder.setReportedBy(reporter);
        return workOrder;
    }

    private User saveDefaultTenantUser(String username, String name) {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        User user = new User(username, "test-password", name);
        user.setTenantId(tenantId);
        user.setOrgUnitId(defaultTenantService.requireRootOrgUnit(tenantId).getId());
        return userRepository.saveAndFlush(user);
    }

    private void grantProfile(User user, String profileKey) {
        authorizationProvisioningService.applyTemplateBaseline(
                user, profileKey, "测试用内置岗位权限基线");
    }

    private void assignDefaultOwnership(WorkOrder workOrder) {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        UUID rootOrgId = defaultTenantService.requireRootOrgUnit(tenantId).getId();
        workOrder.setTenantId(tenantId);
        workOrder.setReportingOrgId(rootOrgId);
        workOrder.setResponsibleOrgId(rootOrgId);
    }

}

