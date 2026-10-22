package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.dto.WorkOrderFilterDTO;
import com.pro.ereportbacken.reporting.WorkOrderExportRow;
import com.pro.ereportbacken.reporting.WorkOrderSpreadsheetExporter;
import com.pro.ereportbacken.repository.WorkOrderQueryRepository;
import com.pro.ereportbacken.repository.WorkOrderReportRepository;
import com.pro.ereportbacken.security.WorkOrderAccessPolicy;
import com.pro.ereportbacken.time.DisplayTime;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Log4j2
public class WorkOrderExportService {

    public static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int QUERY_BATCH_SIZE = 500;
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final WorkOrderAccessPolicy accessPolicy;
    private final WorkOrderQueryRepository queryRepository;
    private final WorkOrderReportRepository reportRepository;
    private final OrganizationHierarchyService hierarchyService;
    private final WorkOrderSpreadsheetExporter spreadsheetExporter;
    private final int maxRows;

    public WorkOrderExportService(
            WorkOrderAccessPolicy accessPolicy,
            WorkOrderQueryRepository queryRepository,
            WorkOrderReportRepository reportRepository,
            OrganizationHierarchyService hierarchyService,
            WorkOrderSpreadsheetExporter spreadsheetExporter,
            @Value("${app.work-order-export.max-rows:50000}") int maxRows) {
        this.accessPolicy = accessPolicy;
        this.queryRepository = queryRepository;
        this.reportRepository = reportRepository;
        this.hierarchyService = hierarchyService;
        this.spreadsheetExporter = spreadsheetExporter;
        this.maxRows = Math.clamp(maxRows, 1, 1_000_000);
    }

    public PreparedWorkOrderExport prepare(WorkOrderFilterDTO filter, Sort sort) {
        WorkOrderFilterDTO scopedFilter = accessPolicy.applyExportScope(filter);
        Set<UUID> selectedWorkOrderIds = normalizeSelectedWorkOrderIds(scopedFilter.getSelectedWorkOrderIds());
        if (selectedWorkOrderIds != null) {
            if (selectedWorkOrderIds.size() > maxRows) {
                throw new IllegalArgumentException(
                        "本次选择了 " + selectedWorkOrderIds.size() + " 条工单，超过单次导出上限 "
                                + maxRows + " 条，请分批导出。");
            }
            scopedFilter.setSelectedWorkOrderIds(selectedWorkOrderIds);
        }
        UUID tenantId = accessPolicy.currentTenantId();
        Sort stableSort = sort == null || sort.isUnsorted()
                ? Sort.by(Sort.Order.desc("reportTime"))
                : sort;
        Page<UUID> firstPage = queryRepository.findIdsByCriteria(
                scopedFilter, PageRequest.of(0, QUERY_BATCH_SIZE, stableSort));
        if (firstPage.getTotalElements() > maxRows) {
            throw new IllegalArgumentException(
                    "当前筛选结果共 " + firstPage.getTotalElements() + " 条，超过单次导出上限 "
                            + maxRows + " 条，请缩小日期、类型或状态范围后重试。");
        }
        if (selectedWorkOrderIds != null && firstPage.getTotalElements() != selectedWorkOrderIds.size()) {
            throw new AccessDeniedException("所选工单中包含不在当前数据域内的记录，导出已拒绝。");
        }

        LocalDateTime generatedAt = UtcTime.now();
        Path targetFile = createTemporaryFile();
        int exportedRows = 0;
        try (WorkOrderSpreadsheetExporter.WorkbookSession workbook = spreadsheetExporter.open(
                targetFile, "工单导出 " + generatedAt, generatedAt)) {
            exportedRows += appendBatch(workbook, tenantId, firstPage.getContent());
            int pageNumber = 1;
            while (pageNumber < firstPage.getTotalPages()) {
                List<UUID> workOrderIds = queryRepository.findPageIdsByCriteria(
                        scopedFilter, PageRequest.of(pageNumber, QUERY_BATCH_SIZE, stableSort));
                if (workOrderIds.isEmpty()) break;
                exportedRows += appendBatch(workbook, tenantId, workOrderIds);
                pageNumber++;
            }
        } catch (RuntimeException exception) {
            deleteQuietly(targetFile);
            throw exception;
        }

        long fileSize;
        String sha256;
        try {
            fileSize = Files.size(targetFile);
            sha256 = sha256(targetFile);
        } catch (IOException | RuntimeException exception) {
            deleteQuietly(targetFile);
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("读取工单导出文件失败。", exception);
        }
        String fileName = "工单导出_" + DisplayTime.formatUtc(generatedAt, FILE_TIME_FORMATTER) + ".xlsx";
        log.info("工单导出文件已生成。tenantId={}, rows={}, bytes={}, sha256={}",
                tenantId, exportedRows, fileSize, sha256);
        return new PreparedWorkOrderExport(targetFile, fileName, fileSize, sha256, exportedRows);
    }

    private int appendBatch(WorkOrderSpreadsheetExporter.WorkbookSession workbook,
                            UUID tenantId,
                            List<UUID> workOrderIds) {
        if (workOrderIds.isEmpty()) return 0;
        Map<UUID, WorkOrderExportRow> rowsById = reportRepository.findExportRows(tenantId, workOrderIds).stream()
                .collect(Collectors.toMap(WorkOrderExportRow::id, Function.identity()));
        if (rowsById.size() != workOrderIds.size()) {
            throw new IllegalStateException("工单数据在导出过程中发生变化，请刷新列表后重试。");
        }
        List<WorkOrderExportRow> orderedRows = workOrderIds.stream()
                .map(rowsById::get)
                .filter(Objects::nonNull)
                .toList();
        Set<UUID> organizationIds = orderedRows.stream()
                .flatMap(row -> java.util.stream.Stream.of(row.reportingOrgId(), row.responsibleOrgId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        workbook.append(orderedRows, hierarchyService.namesById(tenantId, organizationIds));
        return orderedRows.size();
    }

    private Set<UUID> normalizeSelectedWorkOrderIds(Set<UUID> selectedWorkOrderIds) {
        if (selectedWorkOrderIds == null || selectedWorkOrderIds.isEmpty()) {
            return null;
        }
        Set<UUID> normalized = selectedWorkOrderIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return normalized.isEmpty() ? null : normalized;
    }

    private Path createTemporaryFile() {
        try {
            return Files.createTempFile("e-report-work-orders-", ".xlsx");
        } catch (IOException exception) {
            throw new IllegalStateException("创建工单导出临时文件失败。", exception);
        }
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256。", exception);
        }
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.warn("清理工单导出临时文件失败。file={}", file, exception);
        }
    }

    public record PreparedWorkOrderExport(
            Path path,
            String fileName,
            long fileSize,
            String sha256,
            int rowCount
    ) {
    }
}
