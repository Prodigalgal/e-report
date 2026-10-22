package com.pro.ereportbacken.reporting;

import com.pro.ereportbacken.model.pojo.WorkOrderPriority;
import com.pro.ereportbacken.model.pojo.WorkOrderSlaStage;
import com.pro.ereportbacken.model.pojo.WorkOrderStatus;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderSpreadsheetExporterTests {

    @TempDir
    Path tempDir;

    @Test
    void createsReadableWorkbookWithTypedDatesAndLiteralUntrustedText() throws Exception {
        Path target = tempDir.resolve("工单导出.xlsx");
        LocalDateTime generatedAt = LocalDateTime.of(2026, 8, 18, 18, 0);
        UUID organizationId = uuid(2);
        WorkOrderExportRow exportRow = new WorkOrderExportRow(
                uuid(1), "WO-20260818-001", WorkOrderType.NETWORK_FAULT,
                WorkOrderStatus.IN_PROGRESS.getCode(), WorkOrderPriority.URGENT,
                organizationId, organizationId, "=1+1", "光缆中断", "张三", "13000000095",
                "报单人", "reporter", "处理人", "worker", null, null,
                generatedAt.minusHours(3), generatedAt.minusMinutes(5), WorkOrderSlaStage.RESPONSE,
                generatedAt.minusHours(1), generatedAt.minusHours(1), true, null, null);

        WorkOrderSpreadsheetExporter exporter = new WorkOrderSpreadsheetExporter();
        try (var workbook = exporter.open(target, "工单导出", generatedAt)) {
            workbook.append(List.of(exportRow), Map.of(organizationId, "南靖县"));
        }

        assertThat(Files.size(target)).isGreaterThan(0);
        try (var input = Files.newInputStream(target); var workbook = new XSSFWorkbook(input)) {
            var sheet = workbook.getSheet("工单明细");
            assertThat(sheet.getCTWorksheet().isSetAutoFilter()).isTrue();
            assertThat(sheet.getPaneInformation()).isNotNull();
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("WO-20260818-001");
            assertThat(sheet.getRow(1).getCell(8).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(1).getCell(8).getStringCellValue()).isEqualTo("=1+1");
            assertThat(sheet.getRow(1).getCell(15).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(15).getLocalDateTimeCellValue())
                    .isEqualTo(LocalDateTime.of(2026, 8, 18, 23, 0));
            assertThat(sheet.getRow(1).getCell(20).getStringCellValue()).isEqualTo("是");
        }
    }

    private UUID uuid(long seed) {
        return com.pro.ereportbacken.TestIds.uuid(seed);
    }
}
