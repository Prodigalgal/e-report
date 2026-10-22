package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.CurrentUserUpdateRequestDTO;
import com.pro.ereportbacken.model.dto.FileTransferInitRequestDTO;
import com.pro.ereportbacken.model.dto.UserTransferExportRequestDTO;
import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.OrgUnit;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.BuiltInAuthorizationProfileCatalog;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class UserImportIntegrationTests {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private UserExportService userExportService;

    @Autowired
    private FileTransferService fileTransferService;

    @Autowired
    private DefaultTenantService defaultTenantService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrgUnitRepository orgUnitRepository;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private SystemGrantProvisioningService authorizationSystemProvisioningService;

    @BeforeEach
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Transactional
    void exportsOnlyAuthorizedOrganizationUsersWithoutCredentials() throws Exception {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        OrgUnit organization = authenticateCountyAdministrator(tenantId);
        OrgUnit outsideOrganization = orgUnitRepository.findByTenantId(tenantId).stream()
                .filter(candidate -> !candidate.getId().equals(organization.getId()))
                .filter(candidate -> candidate.getParentId() != null)
                .findFirst()
                .orElseThrow();
        User visible = saveUser(
                tenantId, organization.getId(), "export-visible", "13990000120", "export.visible@example.com");
        User hidden = saveUser(
                tenantId, outsideOrganization.getId(), "export-hidden", "13990000121", "export.hidden@example.com");

        UserTransferExportRequestDTO request = new UserTransferExportRequestDTO(
                null, null, null, false);
        var preview = userExportService.preview(request);
        UserExportService.UserExportArtifact export = userExportService.export(
                userExportService.prepare(new UserTransferExportRequestDTO(
                        null, null, null, false, preview.previewToken())));
        FileTransferService.CompletedFile exportedFile = fileTransferService.loadCompletedFile(
                export.file().fileId(),
                FileTransferPurpose.USER_EXPORT
        );
        byte[] exportedContent;
        try (var input = exportedFile.resource().getInputStream()) {
            exportedContent = input.readAllBytes();
        }
        Path exportArtifact = Path.of("build", "test-artifacts", "用户数据导出.xlsx");
        Files.createDirectories(exportArtifact.getParent());
        Files.write(exportArtifact, exportedContent);

        assertThat(export.file().fileName()).startsWith("用户与原子权限授权-").endsWith(".xlsx");
        assertThat(exportedContent).hasSize(Math.toIntExact(export.file().fileSize()));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exportedContent))) {
            var dataSheet = workbook.getSheet("用户清单");
            assertThat(dataSheet).isNotNull();
            assertThat(workbook.getSheet("原子权限 Grant")).isNotNull();
            assertThat(workbook.getSheet("导出说明")).isNotNull();
            assertThat(dataSheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("用户标识");
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            List<String> exportedUsernames = new java.util.ArrayList<>();
            List<String> allValues = new java.util.ArrayList<>();
            for (int rowIndex = 3; rowIndex <= dataSheet.getLastRowNum(); rowIndex++) {
                var row = dataSheet.getRow(rowIndex);
                if (row == null) continue;
                exportedUsernames.add(formatter.formatCellValue(row.getCell(3)));
                for (int column = 0; column < 11; column++) {
                    allValues.add(formatter.formatCellValue(row.getCell(column)));
                }
            }
            assertThat(exportedUsernames).contains(visible.getUsername()).doesNotContain(hidden.getUsername());
            assertThat(allValues).noneMatch(value -> value.contains("Password@123"));
            assertThat(java.util.Arrays.asList(
                    dataSheet.getRow(2).getCell(0).getStringCellValue(),
                    dataSheet.getRow(2).getCell(1).getStringCellValue()))
                    .doesNotContain("密码", "初始密码");
        }
        fileTransferService.abort(export.file().fileId());
    }

    @Test
    @Transactional
    void rejectsGloballyDuplicatedEmail() {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        OrgUnit organization = authenticateCountyAdministrator(tenantId);
        User emailOwner = saveUser(
                tenantId, organization.getId(), "email-owner", "13990000110", "global.owner@example.com");
        User editable = saveUser(
                tenantId, organization.getId(), "email-editable", "13990000113", "editable@example.com");

        CurrentUserUpdateRequestDTO request = new CurrentUserUpdateRequestDTO();
        request.setName(editable.getName());
        request.setPhone(editable.getPhone());
        request.setEmail("GLOBAL.OWNER@EXAMPLE.COM");
        assertThatThrownBy(() -> userService.updateCurrentUserProfile(
                tenantId, editable.getUsername(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("邮箱已被其他用户使用。");
        assertThat(userRepository.findById(emailOwner.getId())).isPresent();
    }

    @Test
    @Transactional
    void resumesMultiChunkUserImportAndPreventsConcurrentProcessing() throws Exception {
        UUID tenantId = defaultTenantService.requireDefaultTenant().getId();
        authenticateCountyAdministrator(tenantId);
        int chunkSize = 1024 * 1024;
        byte[] content = new byte[chunkSize * 2 + 137];
        Arrays.fill(content, (byte) 0x5a);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));

        var session = fileTransferService.initiate(new FileTransferInitRequestDTO(
                FileTransferPurpose.USER_IMPORT,
                "分片导入测试.xlsx",
                XLSX_MEDIA_TYPE,
                (long) content.length,
                sha256,
                (long) chunkSize
        ));
        byte[] firstChunk = Arrays.copyOfRange(content, 0, chunkSize);
        var firstResult = fileTransferService.uploadChunk(
                session.uploadId(),
                0,
                "bytes 0-" + (chunkSize - 1) + "/" + content.length,
                firstChunk
        );
        var retriedResult = fileTransferService.uploadChunk(
                session.uploadId(),
                0,
                "bytes 0-" + (chunkSize - 1) + "/" + content.length,
                firstChunk
        );
        assertThat(firstResult.receivedBytes()).isEqualTo((long) chunkSize);
        assertThat(retriedResult.receivedBytes()).isEqualTo((long) chunkSize);

        for (int chunkIndex = 1; chunkIndex <= 2; chunkIndex++) {
            int start = chunkIndex * chunkSize;
            int endExclusive = Math.min(start + chunkSize, content.length);
            fileTransferService.uploadChunk(
                    session.uploadId(),
                    chunkIndex,
                    "bytes " + start + "-" + (endExclusive - 1) + "/" + content.length,
                    Arrays.copyOfRange(content, start, endExclusive)
            );
        }

        var completed = fileTransferService.complete(session.uploadId());
        var repeatedCompletion = fileTransferService.complete(session.uploadId());
        assertThat(completed.sha256()).isEqualTo(sha256);
        assertThat(repeatedCompletion.status()).isEqualTo(completed.status());
        assertThat(fileTransferService.loadCompletedFile(session.uploadId(), FileTransferPurpose.USER_IMPORT)
                .resource().contentLength()).isEqualTo(content.length);

        fileTransferService.beginProcessing(session.uploadId(), FileTransferPurpose.USER_IMPORT);
        assertThatThrownBy(() -> fileTransferService.beginProcessing(
                session.uploadId(),
                FileTransferPurpose.USER_IMPORT
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("正在处理");
        fileTransferService.releaseProcessing(session.uploadId(), FileTransferPurpose.USER_IMPORT);
        fileTransferService.abort(session.uploadId());
    }

    private OrgUnit authenticateCountyAdministrator(UUID tenantId) {
        User administrator = userRepository.findByTenantIdAndUsername(tenantId, "xiangcheng").orElseThrow();
        OrgUnit organization = orgUnitRepository.findByTenantId(tenantId).stream()
                .filter(candidate -> "350602".equals(candidate.getCode()))
                .findFirst()
                .orElseThrow();
        authorizationSystemProvisioningService.reconcileTemplateBaseline(
                administrator,
                BuiltInAuthorizationProfileCatalog.COUNTY_ADMINISTRATOR,
                "用户导入导出集成测试区县管理员岗位");
        var details = userDetailsService.loadForToken(tenantId, administrator.getId(), administrator.getUsername());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                details,
                null,
                details.getAuthorities()
        ));
        return organization;
    }

    private User saveUser(UUID tenantId, UUID orgUnitId, String username, String phone, String email) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setOrgUnitId(orgUnitId);
        user.setAuthorizationVersion(1L);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("Password@123"));
        user.setName(username);
        user.setPhone(phone);
        user.setEmail(email);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

}
