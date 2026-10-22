package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.FileTransferInitRequestDTO;
import com.pro.ereportbacken.model.dto.UserTransferExportRequestDTO;
import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.GrantIssuerType;
import com.pro.ereportbacken.model.pojo.PermissionGrantStatus;
import com.pro.ereportbacken.model.pojo.OrgUnit;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.model.pojo.UserTransferJobStatus;
import com.pro.ereportbacken.repository.FileTransferUploadSessionRepository;
import com.pro.ereportbacken.repository.PermissionGrantRepository;
import com.pro.ereportbacken.repository.PermissionRepository;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.repository.UserTransferJobRowRepository;
import com.pro.ereportbacken.security.BuiltInAuthorizationProfileCatalog;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantUserDetails;
import com.pro.ereportbacken.time.DisplayTime;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class UserTransferIntegrationTests {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private UserTransferService userTransferService;

    @Autowired
    private UserTransferPlanService userTransferPlanService;

    @Autowired
    private AuthorizationProfileService authorizationProfileService;

    @Autowired
    private FileTransferService fileTransferService;

    @Autowired
    private FileTransferUploadSessionRepository fileTransferUploadSessionRepository;

    @Autowired
    private DefaultTenantService defaultTenantService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrgUnitRepository orgUnitRepository;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private PermissionGrantRepository permissionGrantRepository;

    @Autowired
    private PermissionGrantChainService permissionGrantChainService;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private SystemGrantProvisioningService authorizationSystemProvisioningService;

    @Autowired
    private AuthorizationAdministrationService authorizationAdministrationService;

    @Autowired
    private UserTransferJobRowRepository jobRowRepository;

    @BeforeEach
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsTaskPreviewsAndExecutesUserWithProfileGrants() throws Exception {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        OrgUnit organization = authenticateTenantAdministrator(tenantId);
        FileTransferService.GeneratedFile templateArtifact = userTransferService.createTemplate();
        byte[] template = readTemplate(templateArtifact.fileId());
        Files.createDirectories(Path.of("build", "test-artifacts"));
        Files.write(Path.of("build", "test-artifacts", "用户批量治理模板-v3.1.xlsx"), template);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(9);
            assertThat(workbook.getSheet(UserTransferWorkbookService.INSTRUCTION_SHEET)).isNotNull();
            assertThat(workbook.getSheet(UserTransferWorkbookService.USER_SHEET).getLastRowNum()).isEqualTo(2);
            assertThat(workbook.getSheet(UserTransferWorkbookService.TEMPLATE_APPLY_SHEET).getLastRowNum()).isEqualTo(2);
            assertThat(workbook.getSheet(UserTransferWorkbookService.USER_SHEET).getDataValidations()).hasSize(5);
            assertThat(workbook.getSheet(UserTransferWorkbookService.TEMPLATE_APPLY_SHEET).getDataValidations()).hasSize(3);
            assertThat(workbook.getSheet(UserTransferWorkbookService.GRANT_COMMAND_SHEET).getDataValidations()).hasSize(3);
            assertThat(workbook.getSheet(UserTransferWorkbookService.GRANT_COMMAND_SHEET)
                    .getRow(2).getCell(3).getStringCellValue()).isEqualTo("权限编码");
            var instructions = workbook.getSheet(UserTransferWorkbookService.INSTRUCTION_SHEET);
            String authorizationExpiryInstruction = null;
            for (int rowIndex = instructions.getFirstRowNum(); rowIndex <= instructions.getLastRowNum(); rowIndex++) {
                Row instructionRow = instructions.getRow(rowIndex);
                if (instructionRow != null
                        && "有效时间".equals(instructionRow.getCell(0).getStringCellValue())) {
                    authorizationExpiryInstruction = instructionRow.getCell(1).getStringCellValue();
                    break;
                }
            }
            assertThat(authorizationExpiryInstruction).isNotNull().contains("北京时间（UTC+8）");
            List<String> revokeAllDescriptions = new java.util.ArrayList<>();
            for (int rowIndex = 3; rowIndex <= instructions.getLastRowNum(); rowIndex++) {
                Row instructionRow = instructions.getRow(rowIndex);
                if (instructionRow != null
                        && "组织迁移：REVOKE_ALL".equals(instructionRow.getCell(0).getStringCellValue())) {
                    revokeAllDescriptions.add(instructionRow.getCell(1).getStringCellValue());
                }
            }
            assertThat(revokeAllDescriptions).singleElement()
                    .asString()
                    .contains("全部可撤销授权", "系统根、系统基线和本人基线");
            assertThat(workbook.isSheetVeryHidden(workbook.getSheetIndex(
                    UserTransferWorkbookService.METADATA_SHEET))).isTrue();
            assertThat(workbook.getSheet(UserTransferWorkbookService.METADATA_SHEET)
                    .getRow(0).getCell(1).getStringCellValue()).isEqualTo("6.0");
            assertThat(workbook.getName("organization_codes")).isNotNull();
            assertThat(workbook.getName("department_codes")).isNotNull();
            assertThat(workbook.getName("department_codes").getRefersToFormula())
                    .endsWith("$A$" + (workbook.getSheet(UserTransferWorkbookService.DEPARTMENT_SHEET).getLastRowNum() + 1));
            assertThat(workbook.getSheet(UserTransferWorkbookService.DEPARTMENT_SHEET)
                    .getRow(workbook.getSheet(UserTransferWorkbookService.DEPARTMENT_SHEET).getLastRowNum())
                    .getCell(0).getStringCellValue()).isEqualTo("#CLEAR");
            assertThat(workbook.getName("profile_codes")).isNotNull();
            assertThat(workbook.getName("permission_codes")).isNotNull();
        }

        String phone = "139" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100_000_000);
        String password = "Transfer-Secure-Phrase-2026";
        byte[] content = fillTemplate(template, phone, password, organization.getCode());
        UUID uploadId = upload(content);
        var createdJob = userTransferService.createImport(uploadId, UUID.randomUUID().toString());
        var previewJob = awaitStatus(createdJob.id(), UserTransferJobStatus.PREVIEW_READY, Duration.ofSeconds(15));
        var previewRows = userTransferService.rows(createdJob.id(), null,
                org.springframework.data.domain.PageRequest.of(0, 20)).getContent();
        assertThat(previewJob.totalRows()).isEqualTo(2);
        assertThat(previewJob.validRows())
                .withFailMessage("导入预览应全部有效，实际错误=%s", previewRows.stream()
                        .flatMap(row -> row.validationErrors().stream()).toList())
                .isEqualTo(2);
        assertThat(previewJob.invalidRows()).isZero();
        assertThat(previewJob.previewToken()).isNotBlank();
        assertThat(previewJob.previewSummary()).isNotNull();
        assertThat(previewJob.previewSummary().userCount()).isEqualTo(1);
        assertThat(previewJob.previewSummary().affectedUserCount()).isEqualTo(1);
        assertThat(previewJob.previewSummary().fieldChangeCount()).isGreaterThanOrEqualTo(5);
        assertThat(previewJob.previewSummary().addedPermissionCount()).isPositive();

        assertThat(previewRows).hasSize(2);
        assertThat(previewRows).allSatisfy(row -> {
            assertThat(row.subjectKey()).hasSize(64);
            assertThat(row.subjectDisplay()).isEqualTo(phone.substring(0, 3) + "****" + phone.substring(7));
            assertThat(row.subjectDisplay()).doesNotContain(phone);
        });
        assertThat(previewRows).anySatisfy(row -> assertThat(row.fieldChanges())
                .anySatisfy(change -> {
                    assertThat(change.field()).isEqualTo("姓名");
                    assertThat(change.beforeValue()).isNull();
                    assertThat(change.afterValue()).isEqualTo("任务化导入用户");
                }));
        assertThat(previewRows).anySatisfy(row -> assertThat(row.authorizationDiff().changes())
                .allSatisfy(change -> {
                    assertThat(change.kind()).isEqualTo(
                            com.pro.ereportbacken.model.dto.AuthorizationPlanResponseDTO.ChangeKind.ADD);
                    assertThat(change.before()).isNull();
                    assertThat(change.after()).isNotNull();
                }));
        assertThat(jobRowRepository.findAll())
                .filteredOn(row -> row.getJobId().equals(createdJob.id()))
                .allSatisfy(row -> {
                    assertThat(row.getFieldDiffJson()).doesNotContain(password).doesNotContain(phone);
                    assertThat(row.getGrantDiffJson()).doesNotContain(password).doesNotContain(phone);
                    assertThat(row.getValidationErrorsJson()).doesNotContain(password).doesNotContain(phone);
                });

        userTransferService.execute(createdJob.id(), previewJob.previewToken());
        var completedJob = awaitStatus(createdJob.id(), UserTransferJobStatus.COMPLETED, Duration.ofSeconds(15));
        assertThat(completedJob.failedRows()).isZero();
        assertThat(completedJob.succeededRows()).isEqualTo(2);

        User created = userRepository.findByPhone(phone).orElseThrow();
        assertThat(created.getTenantId()).isEqualTo(tenantId);
        assertThat(created.getOrgUnitId()).isEqualTo(organization.getId());
        assertThat(created.getNamePinyin()).isNotBlank();
        assertThat(created.getNamePinyinInitials()).isNotBlank();
        var grants = permissionGrantRepository.findByTenantIdAndMembershipIdAndStatus(
                tenantId, created.getId(), PermissionGrantStatus.ACTIVE);
        assertThat(grants).isNotEmpty();
        assertThat(grants).allSatisfy(grant -> assertThat(grant.getPermissionId()).isNotNull());

        var withdrawGrant = authorizationAdministrationService.view(created.getId()).grants().stream()
                .filter(grant -> grant.permissionName().equals(
                        PermissionCatalog.requireWorkOrderEntry(
                                com.pro.ereportbacken.model.pojo.WorkOrderType.NETWORK_FAULT).withdrawPermission()))
                .findFirst()
                .orElseThrow();
        String claimPermission = PermissionCatalog.requireWorkOrderEntry(
                com.pro.ereportbacken.model.pojo.WorkOrderType.NETWORK_FAULT).claimPermission();
        FileTransferService.GeneratedFile adjustmentTemplateArtifact = userTransferService.createTemplate();
        byte[] adjustmentTemplate = readTemplate(adjustmentTemplateArtifact.fileId());
        byte[] adjustmentContent = fillPermissionAdjustmentTemplate(
                adjustmentTemplate, phone, withdrawGrant.id(), claimPermission, organization.getCode());
        UUID adjustmentUploadId = upload(adjustmentContent);
        var adjustmentJob = userTransferService.createImport(adjustmentUploadId, UUID.randomUUID().toString());
        var adjustmentPreview = awaitStatus(
                adjustmentJob.id(), UserTransferJobStatus.PREVIEW_READY, Duration.ofSeconds(15));
        assertThat(adjustmentPreview.totalRows()).isEqualTo(1);
        assertThat(adjustmentPreview.invalidRows()).isZero();
        userTransferService.execute(adjustmentJob.id(), adjustmentPreview.previewToken());
        awaitStatus(adjustmentJob.id(), UserTransferJobStatus.COMPLETED, Duration.ofSeconds(15));

        var adjustedPermissionNames = authorizationAdministrationService.view(created.getId()).grants().stream()
                .map(grant -> grant.permissionName())
                .collect(Collectors.toSet());
        assertThat(adjustedPermissionNames).contains(claimPermission)
                .doesNotContain(withdrawGrant.permissionName());

        FileTransferService.GeneratedFile updateTemplateArtifact = userTransferService.createTemplate();
        byte[] updateContent = fillUpdateTemplate(
                readTemplate(updateTemplateArtifact.fileId()), phone, "更新后的用户");
        var updateJob = userTransferService.createImport(upload(updateContent), UUID.randomUUID().toString());
        var updatePreview = awaitStatus(updateJob.id(), UserTransferJobStatus.PREVIEW_READY, Duration.ofSeconds(15));
        var updateRows = userTransferService.rows(updateJob.id(), null,
                org.springframework.data.domain.PageRequest.of(0, 20)).getContent();
        assertThat(updatePreview.previewSummary().fieldChangeCount()).isEqualTo(1);
        assertThat(updateRows).singleElement().satisfies(row ->
                assertThat(row.fieldChanges()).singleElement().satisfies(change -> {
                    assertThat(change.field()).isEqualTo("姓名");
                    assertThat(change.beforeValue()).isEqualTo("任务化导入用户");
                    assertThat(change.afterValue()).isEqualTo("更新后的用户");
                }));
        userTransferService.cancel(updateJob.id());
    }

    @Test
    void importsProfileUpdateForAnAdministratorPeerInTheSameOrganization() throws Exception {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        OrgUnit organization = authenticateTenantAdministrator(tenantId);
        User peerAdministrator = new User();
        peerAdministrator.setTenantId(tenantId);
        peerAdministrator.setOrgUnitId(organization.getId());
        peerAdministrator.setAuthorizationVersion(1L);
        peerAdministrator.setUsername("peer-admin-" + UUID.randomUUID().toString().substring(0, 8));
        peerAdministrator.setPassword("unused-integration-test-password");
        peerAdministrator.setName("同组织管理员旧姓名");
        peerAdministrator.setPhone("139" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100_000_000));
        peerAdministrator.setStatus(UserStatus.ACTIVE);
        peerAdministrator = userRepository.saveAndFlush(peerAdministrator);
        authorizationSystemProvisioningService.reconcileTemplateBaseline(
                peerAdministrator,
                BuiltInAuthorizationProfileCatalog.COUNTY_ADMINISTRATOR,
                "同组织管理员平等管理回归测试");

        byte[] template = readTemplate(userTransferService.createTemplate().fileId());
        var created = userTransferService.createImport(
                upload(fillUpdateTemplate(template, peerAdministrator.getPhone(), "同组织管理员新姓名")),
                UUID.randomUUID().toString());
        var preview = awaitStatus(created.id(), UserTransferJobStatus.PREVIEW_READY, Duration.ofSeconds(15));
        assertThat(preview.invalidRows()).isZero();

        userTransferService.execute(created.id(), preview.previewToken());
        awaitStatus(created.id(), UserTransferJobStatus.COMPLETED, Duration.ofSeconds(15));

        assertThat(userRepository.findById(peerAdministrator.getId()).orElseThrow().getName())
                .isEqualTo("同组织管理员新姓名");
    }

    @Test
    void createsAsynchronousExportTaskWithUsersAndGrantFacts() throws Exception {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        authenticateTenantAdministrator(tenantId);

        var exportRequest = new UserTransferExportRequestDTO(null, null, null, false);
        var exportPreview = userTransferService.previewExport(exportRequest);
        assertThat(exportPreview.userCount()).isPositive();
        assertThat(exportPreview.grantCount()).isNotNegative();
        assertThat(exportPreview.previewToken()).isNotBlank();
        assertThat(exportPreview.maximumUserCount()).isEqualTo(UserExportQueryService.MAX_EXPORT_USERS);
        assertThat(exportPreview.sheets()).extracting(
                        com.pro.ereportbacken.model.dto.UserTransferExportPreviewDTO.SheetPreview::name)
                .containsExactly("用户清单", "原子权限 Grant", "导出说明");

        var created = userTransferService.createExport(
                new UserTransferExportRequestDTO(
                        null, null, null, false, exportPreview.previewToken()),
                UUID.randomUUID().toString());
        assertThat(created.status()).isIn(UserTransferJobStatus.QUEUED, UserTransferJobStatus.EXECUTING,
                UserTransferJobStatus.COMPLETED);

        var completed = awaitStatus(created.id(), UserTransferJobStatus.COMPLETED, Duration.ofSeconds(15));
        assertThat(completed.resultFile()).isNotNull();
        assertThat(completed.resultFile().fileName()).startsWith("用户与原子权限授权-").endsWith(".xlsx");
        assertThat(completed.resultFile().sha256()).hasSize(64);

        FileTransferService.CompletedFile result = userTransferService.loadResult(created.id());
        byte[] content;
        try (var input = result.resource().getInputStream()) {
            content = input.readAllBytes();
        }
        Files.createDirectories(Path.of("build", "test-artifacts"));
        Files.write(Path.of("build", "test-artifacts", "用户与原子权限授权-v3.xlsx"), content);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheet("用户清单")).isNotNull();
            assertThat(workbook.getSheet("原子权限 Grant")).isNotNull();
            assertThat(workbook.getSheet("导出说明")).isNotNull();
            assertThat(workbook.getSheet("用户清单").getLastRowNum()).isGreaterThanOrEqualTo(3);
            assertThat(workbook.getSheet("原子权限 Grant").getLastRowNum()).isGreaterThanOrEqualTo(3);
            List<String> grantHeaders = new java.util.ArrayList<>();
            Row grantHeaderRow = workbook.getSheet("原子权限 Grant").getRow(2);
            for (int column = 0; column < grantHeaderRow.getLastCellNum(); column++) {
                grantHeaders.add(grantHeaderRow.getCell(column).getStringCellValue());
            }
            assertThat(grantHeaders).doesNotContain("主体敏感度");
        }
        TenantUserDetails taskViewer = (TenantUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        authenticateWithPermissions(taskViewer, PermissionCatalog.USER_EXPORT_TASK_VIEW);
        assertThat(userTransferService.find(completed.id()).resultFile()).isNull();
        assertThat(userTransferService.findAll(
                com.pro.ereportbacken.model.pojo.UserTransferJobType.EXPORT,
                PageRequest.of(0, 20)).getContent())
                .extracting(item -> item.id())
                .contains(completed.id());
        assertThatThrownBy(() -> userTransferService.loadResult(completed.id()))
                .isInstanceOf(AccessDeniedException.class);
        authenticateWithPermissions(taskViewer, PermissionCatalog.USER_EXPORT_DOWNLOAD);
        assertThat(userTransferService.loadResult(completed.id()).fileId()).isEqualTo(completed.resultFileId());
        authenticateWithPermissions(taskViewer, PermissionCatalog.USER_EXPORT_CREATE);
        assertThatThrownBy(() -> userTransferService.cancel(completed.id()))
                .isInstanceOf(AccessDeniedException.class);
        authenticateWithPermissions(taskViewer, PermissionCatalog.USER_EXPORT_CANCEL);
        assertThatThrownBy(() -> userTransferService.cancel(completed.id()))
                .isNotInstanceOf(AccessDeniedException.class);
        var expiredResult = fileTransferUploadSessionRepository.findById(completed.resultFileId()).orElseThrow();
        expiredResult.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        fileTransferUploadSessionRepository.saveAndFlush(expiredResult);
        authenticateWithPermissions(taskViewer, PermissionCatalog.USER_EXPORT_TASK_VIEW);
        assertThat(userTransferService.findAll(
                com.pro.ereportbacken.model.pojo.UserTransferJobType.EXPORT,
                PageRequest.of(0, 20)).getContent())
                .anySatisfy(item -> {
                    assertThat(item.id()).isEqualTo(completed.id());
                    assertThat(item.resultFile()).isNull();
                });
        TenantUserDetails currentPrincipal = (TenantUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                currentPrincipal, null, List.of(new SimpleGrantedAuthority(PermissionCatalog.USER_MANAGE_VIEW))));
        assertThatThrownBy(() -> userTransferService.find(completed.id()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("没有查看该类型用户批量任务的权限");
    }

    @Test
    void rejectsExportWhenPreviewSnapshotHasChanged() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        authenticateTenantAdministrator(tenantId);
        User administrator = userRepository.findByTenantIdAndUsername(tenantId, "xiangcheng").orElseThrow();
        String originalName = administrator.getName();
        UserTransferExportRequestDTO request = new UserTransferExportRequestDTO(
                administrator.getUsername(), null, null, false);
        var preview = userTransferService.previewExport(request);
        try {
            administrator.setName(originalName + "-快照变化");
            userRepository.saveAndFlush(administrator);
            assertThatThrownBy(() -> userTransferService.createExport(
                    new UserTransferExportRequestDTO(
                            request.searchTerm(), null, null, false, preview.previewToken()),
                    UUID.randomUUID().toString()))
                    .isInstanceOf(UserExportService.UserExportSnapshotChangedException.class)
                    .hasMessageContaining("重新预览");
        } finally {
            administrator.setName(originalName);
            userRepository.saveAndFlush(administrator);
        }
    }

    @Test
    void createsDownloadableFailureWorkbookForInvalidImport() throws Exception {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        authenticateTenantAdministrator(tenantId);
        byte[] template = readTemplate(userTransferService.createTemplate().fileId());
        String unknownPhone = "139" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100_000_000);
        var created = userTransferService.createImport(
                upload(fillUpdateTemplate(template, unknownPhone, "不存在的用户")), UUID.randomUUID().toString());
        var preview = awaitStatus(created.id(), UserTransferJobStatus.PREVIEW_READY, Duration.ofSeconds(15));
        assertThat(preview.invalidRows()).isPositive();
        var withErrorFile = awaitErrorFile(created.id(), Duration.ofSeconds(10));
        assertThat(withErrorFile.errorFile()).isNotNull();
        TenantUserDetails taskViewer = (TenantUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        authenticateWithPermissions(taskViewer,
                PermissionCatalog.USER_IMPORT_TASK_VIEW,
                PermissionCatalog.USER_IMPORT_ERROR_REPORT_DOWNLOAD);
        var visibleToTaskViewer = userTransferService.find(created.id());
        assertThat(visibleToTaskViewer.previewToken()).isNull();
        assertThat(visibleToTaskViewer.errorFile()).isNotNull();
        assertThat(userTransferService.rows(created.id(), null, PageRequest.of(0, 20)).getContent()).isNotEmpty();
        FileTransferService.CompletedFile errorFile = userTransferService.loadErrorResult(created.id());
        try (var input = errorFile.resource().getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            assertThat(workbook.getSheet("失败明细")).isNotNull();
            assertThat(workbook.getSheet("失败明细").getRow(2).getCell(0).getStringCellValue())
                    .isEqualTo("原工作表");
            assertThat(workbook.getSheet("失败明细").getLastRowNum()).isGreaterThanOrEqualTo(3);
        } finally {
            authenticateWithPermissions(taskViewer, PermissionCatalog.USER_IMPORT_TASK_VIEW);
            assertThat(userTransferService.find(created.id()).errorFile()).isNull();
            assertThatThrownBy(() -> userTransferService.loadErrorResult(created.id()))
                    .isInstanceOf(AccessDeniedException.class);
            authenticateWithPermissions(taskViewer, PermissionCatalog.USER_IMPORT_CANCEL);
            userTransferService.cancel(created.id());
        }
    }

    @Test
    void rejectsSpreadsheetGrantThatOutlivesDelegatorPermission() throws Exception {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        OrgUnit organization = orgUnitRepository.findByTenantId(tenantId).stream()
                .filter(candidate -> "350602".equals(candidate.getCode()))
                .findFirst()
                .orElseThrow();
        String reporterProfileCode = BuiltInAuthorizationProfileCatalog.workOrderProfileKey(
                com.pro.ereportbacken.model.pojo.WorkOrderType.NETWORK_FAULT,
                com.pro.ereportbacken.model.pojo.WorkOrderDuty.REPORTER);
        BuiltInAuthorizationProfileCatalog.Template reporterProfile =
                BuiltInAuthorizationProfileCatalog.templates(false).stream()
                        .filter(profile -> profile.code().equals(reporterProfileCode))
                        .findFirst().orElseThrow();
        LocalDateTime managementExpiry = LocalDateTime.now().plusDays(20).withNano(0);
        LocalDateTime permissionExpiry = LocalDateTime.now().plusDays(10).withNano(0);
        Authentication delegator = finiteDelegatorAuthentication(
                tenantId, organization.getId(), reporterProfile, managementExpiry, permissionExpiry);
        SecurityContextHolder.getContext().setAuthentication(delegator);

        assertThat(authorizationProfileService.list())
                .extracting(com.pro.ereportbacken.model.dto.AuthorizationProfileDTO::code)
                .contains(reporterProfile.code());

        FileTransferService.GeneratedFile templateArtifact = userTransferService.createTemplate();
        byte[] template = readTemplate(templateArtifact.fileId());
        String phone = "139" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100_000_000);
        byte[] content = fillDirectAuthorizationTemplate(
                template, phone, "Transfer-Secure-Phrase-2026", organization.getCode(),
                reporterProfile.code(), permissionExpiry.plusDays(1));
        UUID uploadId = upload(content);
        try {
            FileTransferService.CompletedFile file = fileTransferService.loadCompletedFile(
                    uploadId, FileTransferPurpose.USER_IMPORT);
            UserTransferPlanService.TransferPlan plan = userTransferPlanService.build(file, LocalDateTime.now());

            assertThat(plan.invalidRows()).isEqualTo(2);
            assertThat(plan.executions()).isEmpty();
            assertThat(plan.rows())
                    .flatExtracting(UserTransferPlanService.PreviewRow::errors)
                    .anyMatch(error -> error.contains("有效期") || error.contains("转授权"));
        } finally {
            fileTransferService.abort(uploadId);
            fileTransferService.abort(templateArtifact.fileId());
        }
    }

    @Test
    void importsSpreadsheetAuthorizationExpiryAsChinaWallTime() throws Exception {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        OrgUnit organization = authenticateTenantAdministrator(tenantId);
        FileTransferService.GeneratedFile templateArtifact = userTransferService.createTemplate();
        byte[] template = readTemplate(templateArtifact.fileId());
        LocalDateTime displayExpiry = LocalDate.now(DisplayTime.ZONE_ID)
                .plusDays(30).atTime(LocalTime.of(18, 30));
        String phone = "139" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100_000_000);
        String reporterProfileCode = BuiltInAuthorizationProfileCatalog.workOrderProfileKey(
                com.pro.ereportbacken.model.pojo.WorkOrderType.NETWORK_FAULT,
                com.pro.ereportbacken.model.pojo.WorkOrderDuty.REPORTER);
        UUID uploadId = upload(fillDirectAuthorizationTemplate(
                template, phone, "Transfer-Secure-Phrase-2026", organization.getCode(),
                reporterProfileCode, displayExpiry));
        try {
            FileTransferService.CompletedFile file = fileTransferService.loadCompletedFile(
                    uploadId, FileTransferPurpose.USER_IMPORT);
            UserTransferPlanService.TransferPlan plan = userTransferPlanService.build(file, LocalDateTime.now());

            assertThat(plan.invalidRows()).isZero();
            assertThat(plan.executions()).hasSize(1);
            assertThat(plan.executions().getFirst().authorizationOperations())
                    .isNotEmpty()
                    .allSatisfy(operation -> assertThat(operation.expiresAt())
                            .isEqualTo(DisplayTime.displayToUtc(displayExpiry)));
        } finally {
            fileTransferService.abort(uploadId);
            fileTransferService.abort(templateArtifact.fileId());
        }
    }

    @Test
    void importsDateOnlyAuthorizationBoundsAsChinaCalendarDays() throws Exception {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        OrgUnit organization = authenticateTenantAdministrator(tenantId);
        FileTransferService.GeneratedFile templateArtifact = userTransferService.createTemplate();
        byte[] template = readTemplate(templateArtifact.fileId());
        LocalDate displayStartDate = LocalDate.now(DisplayTime.ZONE_ID).plusDays(1);
        LocalDate displayExpiryDate = displayStartDate.plusDays(30);
        String phone = "139" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100_000_000);
        String reporterProfileCode = BuiltInAuthorizationProfileCatalog.workOrderProfileKey(
                com.pro.ereportbacken.model.pojo.WorkOrderType.NETWORK_FAULT,
                com.pro.ereportbacken.model.pojo.WorkOrderDuty.REPORTER);
        UUID uploadId = upload(fillDirectAuthorizationTemplate(
                template, phone, "Transfer-Secure-Phrase-2026", organization.getCode(),
                reporterProfileCode, displayStartDate.toString(), displayExpiryDate.toString()));
        try {
            FileTransferService.CompletedFile file = fileTransferService.loadCompletedFile(
                    uploadId, FileTransferPurpose.USER_IMPORT);
            UserTransferPlanService.TransferPlan plan = userTransferPlanService.build(file, LocalDateTime.now());

            assertThat(plan.invalidRows()).isZero();
            assertThat(plan.executions()).hasSize(1);
            assertThat(plan.executions().getFirst().authorizationOperations())
                    .isNotEmpty()
                    .allSatisfy(operation -> {
                        assertThat(operation.validFrom())
                                .isEqualTo(DisplayTime.startOfDisplayDateUtc(displayStartDate));
                        assertThat(operation.expiresAt())
                                .isEqualTo(DisplayTime.displayToUtc(displayExpiryDate.atTime(LocalTime.MAX)));
                    });
        } finally {
            fileTransferService.abort(uploadId);
            fileTransferService.abort(templateArtifact.fileId());
        }
    }

    private byte[] readTemplate(UUID fileId) throws Exception {
        FileTransferService.CompletedFile file = fileTransferService.loadCompletedFile(
                fileId, FileTransferPurpose.USER_IMPORT_TEMPLATE);
        try (var input = file.resource().getInputStream()) {
            return input.readAllBytes();
        }
    }

    private byte[] fillTemplate(byte[] template,
                                String phone,
                                String password,
                                String organizationCode) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Row user = workbook.getSheet(UserTransferWorkbookService.USER_SHEET).createRow(3);
            set(user, phone, "CREATE", "任务化导入用户", organizationCode, "", "", password, "", "启用");
            Row grant = workbook.getSheet(UserTransferWorkbookService.TEMPLATE_APPLY_SHEET).createRow(3);
            set(grant, phone, "ADD", BuiltInAuthorizationProfileCatalog.workOrderProfileKey(
                    com.pro.ereportbacken.model.pojo.WorkOrderType.NETWORK_FAULT,
                    com.pro.ereportbacken.model.pojo.WorkOrderDuty.REPORTER), "", "", "否",
                    "导入网络故障提单岗位");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] fillDirectAuthorizationTemplate(byte[] template,
                                          String phone,
                                          String password,
                                          String organizationCode,
                                          String roleName,
                                          LocalDateTime expiresAt) throws Exception {
        return fillDirectAuthorizationTemplate(template, phone, password, organizationCode,
                roleName, "", expiresAt.toString());
    }

    private byte[] fillDirectAuthorizationTemplate(byte[] template,
                                          String phone,
                                          String password,
                                          String organizationCode,
                                          String roleName,
                                          String validFrom,
                                          String expiresAt) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Row user = workbook.getSheet(UserTransferWorkbookService.USER_SHEET).createRow(3);
            set(user, phone, "CREATE", "限时授权导入用户", organizationCode, "", "", password, "", "启用");
            Row grant = workbook.getSheet(UserTransferWorkbookService.TEMPLATE_APPLY_SHEET).createRow(3);
            set(grant, phone, "ADD", roleName, validFrom, expiresAt, "否",
                    "验证授权有效期上限");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] fillPermissionAdjustmentTemplate(byte[] template,
                                                    String phone,
                                                    UUID grantId,
                                                    String permissionName,
                                                    String organizationCode) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Row adjustment = workbook.getSheet(UserTransferWorkbookService.GRANT_COMMAND_SHEET).createRow(3);
            set(adjustment, phone, "ADJUST", grantId.toString(), permissionName, "", "", "", "否",
                    "验证原子权限原地替换");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] fillUpdateTemplate(byte[] template,
                                      String phone,
                                      String name) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Row user = workbook.getSheet(UserTransferWorkbookService.USER_SHEET).createRow(3);
            set(user, phone, "UPDATE", name, "", "", "", "", "", "");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private Authentication finiteDelegatorAuthentication(UUID tenantId,
                                                          UUID organizationId,
                                                          BuiltInAuthorizationProfileCatalog.Template delegatedProfile,
                                                          LocalDateTime managementExpiresAt,
                                                          LocalDateTime delegatedExpiresAt) {
        User delegator = new User();
        delegator.setTenantId(tenantId);
        delegator.setOrgUnitId(organizationId);
        delegator.setAuthorizationVersion(1L);
        delegator.setUsername("finite-delegator-" + UUID.randomUUID().toString().substring(0, 8));
        delegator.setPassword("test-password");
        delegator.setName("限时转授权测试管理员");
        delegator.setPhone("138" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100_000_000));
        delegator.setStatus(UserStatus.ACTIVE);
        delegator = userRepository.saveAndFlush(delegator);
        Set<String> managementPermissions = Set.of(
                PermissionCatalog.USER_MANAGE_VIEW,
                PermissionCatalog.USER_MANAGE_CREATE,
                PermissionCatalog.USER_AUTHORIZATION_VIEW,
                PermissionCatalog.USER_AUTHORIZATION_GRANT,
                PermissionCatalog.USER_AUTHORIZATION_ADJUST,
                PermissionCatalog.USER_AUTHORIZATION_REVOKE,
                PermissionCatalog.AUTHORIZATION_TEMPLATE_VIEW,
                PermissionCatalog.AUTHORIZATION_TEMPLATE_APPLY,
                PermissionCatalog.USER_IMPORT_TEMPLATE_DOWNLOAD,
                PermissionCatalog.USER_IMPORT_PREVIEW,
                PermissionCatalog.USER_IMPORT_EXECUTE,
                PermissionCatalog.USER_IMPORT_CANCEL
        );
        for (String permission : managementPermissions) {
            permissionGrantChainService.issueRootGrant(
                    tenantId, delegator.getId(), permission, true, GrantIssuerType.SYSTEM_ROOT,
                    managementExpiresAt, "限时转授权集成测试管理权限");
        }
        for (BuiltInAuthorizationProfileCatalog.Rule rule : delegatedProfile.rules()) {
            permissionGrantChainService.issueRootGrant(
                    tenantId, delegator.getId(), rule.permissionName(), true, GrantIssuerType.SYSTEM_ROOT,
                    delegatedExpiresAt, "限时转授权集成测试业务权限");
        }
        delegator.setAuthorizationVersion(delegator.getAuthorizationVersion() + 1L);
        delegator = userRepository.saveAndFlush(delegator);
        TenantUserDetails principal = userDetailsService.loadForToken(
                tenantId, delegator.getId(), delegator.getUsername());
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private void set(Row row, String... values) {
        for (int column = 0; column < values.length; column++) {
            row.createCell(column).setCellValue(values[column]);
        }
    }

    private UUID upload(byte[] content) throws Exception {
        var session = fileTransferService.initiate(new FileTransferInitRequestDTO(
                FileTransferPurpose.USER_IMPORT,
                "用户批量治理测试.xlsx",
                XLSX_MEDIA_TYPE,
                (long) content.length,
                null,
                FileTransferService.DEFAULT_CHUNK_BYTES
        ));
        fileTransferService.uploadChunk(
                session.uploadId(), 0,
                "bytes 0-" + (content.length - 1) + "/" + content.length,
                content);
        fileTransferService.complete(session.uploadId());
        return session.uploadId();
    }

    private com.pro.ereportbacken.model.dto.UserTransferJobDTO awaitStatus(
            UUID jobId,
            UserTransferJobStatus status,
            Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        com.pro.ereportbacken.model.dto.UserTransferJobDTO job = userTransferService.find(jobId);
        while (Instant.now().isBefore(deadline) && job.status() != status
                && job.status() != UserTransferJobStatus.FAILED
                && job.status() != UserTransferJobStatus.STALE) {
            Thread.sleep(100);
            job = userTransferService.find(jobId);
        }
        assertThat(job.status()).withFailMessage("任务未达到期望状态，实际状态=%s，错误=%s",
                job.status(), job.failureMessage()).isEqualTo(status);
        return job;
    }

    private com.pro.ereportbacken.model.dto.UserTransferJobDTO awaitErrorFile(
            UUID jobId,
            Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        var job = userTransferService.find(jobId);
        while (Instant.now().isBefore(deadline) && job.errorFile() == null) {
            Thread.sleep(100);
            job = userTransferService.find(jobId);
        }
        assertThat(job.errorFile()).withFailMessage("失败结果文件未生成").isNotNull();
        return job;
    }

    private OrgUnit authenticateTenantAdministrator(UUID tenantId) {
        User administrator = userRepository.findByTenantIdAndUsername(tenantId, "xiangcheng").orElseThrow();
        OrgUnit organization = orgUnitRepository.findByTenantId(tenantId).stream()
                .filter(candidate -> "350602".equals(candidate.getCode()))
                .findFirst()
                .orElseThrow();
        ensureProfileAssignment(administrator, organization, BuiltInAuthorizationProfileCatalog.COUNTY_ADMINISTRATOR);
        var details = userDetailsService.loadForToken(tenantId, administrator.getId(), administrator.getUsername());
        assertThat(details.getAuthorities().stream().map(authority -> authority.getAuthority()).toList())
                .contains(
                        PermissionCatalog.USER_IMPORT_TEMPLATE_DOWNLOAD,
                        PermissionCatalog.USER_IMPORT_PREVIEW,
                        PermissionCatalog.USER_IMPORT_EXECUTE,
                        PermissionCatalog.USER_IMPORT_CANCEL,
                        PermissionCatalog.USER_IMPORT_TASK_VIEW,
                        PermissionCatalog.USER_IMPORT_ERROR_REPORT_DOWNLOAD,
                        PermissionCatalog.USER_EXPORT_CREATE,
                        PermissionCatalog.USER_EXPORT_TASK_VIEW,
                        PermissionCatalog.USER_EXPORT_CANCEL,
                        PermissionCatalog.USER_EXPORT_DOWNLOAD);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                details, null, details.getAuthorities()));
        return organization;
    }

    private void authenticateWithPermissions(TenantUserDetails principal, String... permissions) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(permissions)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private void ensureProfileAssignment(User user, OrgUnit organization, String profileSystemKey) {
        authorizationSystemProvisioningService.reconcileTemplateBaseline(
                user,
                profileSystemKey,
                "集成测试应用当前区县管理员标准岗位");
    }
}
