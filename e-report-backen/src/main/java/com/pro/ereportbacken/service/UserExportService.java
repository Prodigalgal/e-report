package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.dto.UserTransferExportPreviewDTO;
import com.pro.ereportbacken.model.dto.UserTransferExportRequestDTO;
import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.security.SecurityHashingService;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.security.WorkOrderAccessPolicy;
import com.pro.ereportbacken.time.DisplayTime;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Log4j2
public class UserExportService {

    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Duration EXPORT_RETENTION = Duration.ofHours(2);
    private static final Duration PREVIEW_RETENTION = Duration.ofMinutes(10);
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final UserExportQueryService queryService;
    private final UserExportWorkbookService workbookService;
    private final FileTransferService fileTransferService;
    private final UserExportPreviewTokenService tokenService;
    private final TenantContext tenantContext;
    private final WorkOrderAccessPolicy workOrderAccessPolicy;
    private final SecurityHashingService hashingService;

    public UserExportService(UserExportQueryService queryService,
                             UserExportWorkbookService workbookService,
                             FileTransferService fileTransferService,
                             UserExportPreviewTokenService tokenService,
                             TenantContext tenantContext,
                             WorkOrderAccessPolicy workOrderAccessPolicy,
                             SecurityHashingService hashingService) {
        this.queryService = queryService;
        this.workbookService = workbookService;
        this.fileTransferService = fileTransferService;
        this.tokenService = tokenService;
        this.tenantContext = tenantContext;
        this.workOrderAccessPolicy = workOrderAccessPolicy;
        this.hashingService = hashingService;
    }

    public PreparedExport prepare(UserTransferExportRequestDTO request) {
        UserTransferExportRequestDTO normalized = request.withoutPreviewToken();
        String requestHash = requestHash(normalized);
        UserExportPreviewTokenService.Claims claims = tokenService.verify(
                request.previewToken(), tenantContext.requireTenantId(),
                workOrderAccessPolicy.currentMembershipId(), requestHash);
        UserExportQueryService.UserExportSnapshot current = queryService.snapshot(normalized);
        if (!matches(current, claims)) {
            throw new UserExportSnapshotChangedException(
                    "预览后用户或授权数据已变化，请重新预览导出范围。");
        }
        return new PreparedExport(normalized, current, claims.expiresAt());
    }

    public PreparedExport prepareJob(UserTransferExportRequestDTO request,
                                     String expectedDigest,
                                     LocalDateTime previewExpiresAt) {
        if (previewExpiresAt == null || !previewExpiresAt.isAfter(UtcTime.now())) {
            throw new UserExportSnapshotChangedException("导出预检已过期，请重新预览导出范围。");
        }
        UserTransferExportRequestDTO normalized = request.withoutPreviewToken();
        UserExportQueryService.UserExportSnapshot current = queryService.snapshot(normalized);
        if (!current.digest().equals(expectedDigest)) {
            throw new UserExportSnapshotChangedException("预览后用户或授权数据已变化，请重新预览导出范围。");
        }
        return new PreparedExport(normalized, current, previewExpiresAt);
    }

    public UserExportArtifact export(PreparedExport prepared) {
        LocalDateTime generatedAt = UtcTime.now();
        Path outputFile;
        try {
            outputFile = Files.createTempFile("e-report-user-export-", ".xlsx");
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建用户导出临时文件。", exception);
        }
        try {
            UserExportQueryService.UserExportSnapshot generatedSnapshot;
            try (UserExportWorkbookService.StreamingWorkbook workbook =
                         workbookService.open(outputFile, prepared.snapshot(), generatedAt)) {
                generatedSnapshot = queryService.scan(prepared.request(), workbook::append);
                requireSameSnapshot(prepared.snapshot(), generatedSnapshot);
                workbook.write();
            }
            UserExportQueryService.UserExportSnapshot finalSnapshot = queryService.snapshot(prepared.request());
            requireSameSnapshot(prepared.snapshot(), finalSnapshot);

            String fileName = "用户与原子权限授权-" + DisplayTime.formatUtc(generatedAt, FILE_TIME_FORMATTER) + ".xlsx";
            FileTransferService.GeneratedFile generatedFile = fileTransferService.registerGeneratedFile(
                    FileTransferPurpose.USER_EXPORT, fileName, XLSX_CONTENT_TYPE, outputFile, EXPORT_RETENTION);
            log.info("用户与原子权限 Grant 导出完成。userCount={}, grantCount={}, organizationFilter={}",
                    finalSnapshot.userCount(), finalSnapshot.grantCount(),
                    prepared.request().orgUnitId());
            return new UserExportArtifact(generatedFile, finalSnapshot);
        } finally {
            try {
                Files.deleteIfExists(outputFile);
            } catch (IOException exception) {
                log.warn("用户导出临时文件清理失败。path={}", outputFile, exception);
            }
        }
    }

    public UserTransferExportPreviewDTO preview(UserTransferExportRequestDTO request) {
        UserTransferExportRequestDTO normalized = request.withoutPreviewToken();
        UserExportQueryService.UserExportSnapshot snapshot = queryService.snapshot(normalized);
        LocalDateTime expiresAt = UtcTime.now().plus(PREVIEW_RETENTION);
        String token = tokenService.issue(
                tenantContext.requireTenantId(), workOrderAccessPolicy.currentMembershipId(),
                requestHash(normalized), snapshot, expiresAt);
        UserExportQueryService.UserExportFilters filters = snapshot.filters();
        return new UserTransferExportPreviewDTO(
                snapshot.userCount(), snapshot.grantCount(),
                snapshot.estimatedFileSize(), UserExportQueryService.MAX_EXPORT_USERS,
                UserExportQueryService.MAX_EXPORT_DETAIL_ROWS,
                UserExportQueryService.MAX_ESTIMATED_FILE_BYTES, token, expiresAt,
                new UserTransferExportPreviewDTO.FilterPreview(
                        filters.searchTerm(), filters.organizationName(),
                        filters.departmentName(),
                        filters.status() == null ? null : statusLabel(filters.status()),
                        filters.includeDeleted()),
                workbookService.previewSheets());
    }

    public String requestHash(UserTransferExportRequestDTO request) {
        UserTransferExportRequestDTO normalized = request.withoutPreviewToken();
        return hashingService.sha256(String.join("|",
                value(normalized.searchTerm()), value(normalized.orgUnitId()), value(normalized.departmentId()),
                value(normalized.status()),
                String.valueOf(normalized.shouldIncludeDeleted())));
    }

    private boolean matches(UserExportQueryService.UserExportSnapshot snapshot,
                            UserExportPreviewTokenService.Claims claims) {
        return snapshot.digest().equals(claims.snapshotDigest())
                && snapshot.userCount() == claims.userCount()
                && snapshot.grantCount() == claims.grantCount()
                && snapshot.estimatedFileSize() == claims.estimatedFileSize();
    }

    private void requireSameSnapshot(UserExportQueryService.UserExportSnapshot expected,
                                     UserExportQueryService.UserExportSnapshot actual) {
        if (!expected.digest().equals(actual.digest())
                || expected.userCount() != actual.userCount()
                || expected.grantCount() != actual.grantCount()) {
            throw new UserExportSnapshotChangedException(
                    "导出生成期间用户或授权数据发生变化，文件未发布，请重新预览。");
        }
    }

    private String statusLabel(UserStatus status) {
        return switch (status) {
            case ACTIVE -> "启用";
            case INACTIVE -> "停用";
            case DISABLED -> "禁用";
            case LOCKED -> "锁定";
            case DELETED -> "已删除";
        };
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record PreparedExport(
            UserTransferExportRequestDTO request,
            UserExportQueryService.UserExportSnapshot snapshot,
            LocalDateTime previewExpiresAt) {
    }

    public record UserExportArtifact(
            FileTransferService.GeneratedFile file,
            UserExportQueryService.UserExportSnapshot snapshot) {
    }

    public static class UserExportSnapshotChangedException extends IllegalStateException {
        public UserExportSnapshotChangedException(String message) {
            super(message);
        }
    }
}
