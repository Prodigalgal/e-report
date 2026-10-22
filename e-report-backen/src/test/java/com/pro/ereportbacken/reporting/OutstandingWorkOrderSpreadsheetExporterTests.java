package com.pro.ereportbacken.reporting;

import com.pro.ereportbacken.model.dto.OrgUnitDTO;
import com.pro.ereportbacken.model.pojo.OrgUnitType;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.model.pojo.WorkOrderPriority;
import com.pro.ereportbacken.model.pojo.WorkOrderStatus;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutstandingWorkOrderSpreadsheetExporterTests {

    @Test
    void exportsReadableChineseWorkbookAndKeepsFormulaLikeTextAsString() throws Exception {
        UUID tenantId = uuid(1);
        OrgUnitDTO county = new OrgUnitDTO(
                uuid(2), tenantId, null, "NANJING", "南靖县",
              OrgUnitType.COUNTY, "/" + uuid(2) + "/", TenantStatus.ACTIVE, false
        );
        LocalDateTime generatedAt = LocalDateTime.of(2026, 8, 7, 18, 0);
        OutstandingWorkOrderReportRow row = new OutstandingWorkOrderReportRow(
                uuid(3), "WO-20260807-001", WorkOrderType.NETWORK_FAULT,
                WorkOrderStatus.SUBMITTED.getCode(), WorkOrderPriority.HIGH,
                "测试地点", "=HYPERLINK(\"https://example.com\")", "张三", "13900000000",
                "报单人", "reporter", null, null,
                generatedAt.minusHours(26), generatedAt.minusHours(1), generatedAt.minusHours(2), generatedAt.minusMinutes(30)
        );
        OutstandingWorkOrderReport report = OutstandingWorkOrderReport.create(
                tenantId, county, generatedAt.toLocalDate(), generatedAt, List.of(row));

        byte[] workbookBytes = new OutstandingWorkOrderSpreadsheetExporter().export(report);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            var sheet = workbook.getSheet("南靖县未处理工单");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("工单编号");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("WO-20260807-001");
            assertThat(sheet.getRow(1).getCell(7).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).startsWith("=HYPERLINK");
            assertThat(sheet.getRow(1).getCell(12).getLocalDateTimeCellValue())
                    .isEqualTo(LocalDateTime.of(2026, 8, 7, 0, 0));
            assertThat(sheet.getRow(1).getCell(13).getStringCellValue()).isEqualTo("1天2小时");
            assertThat(sheet.getRow(1).getCell(15).getStringCellValue()).isEqualTo("是");
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
        }
    }

    private UUID uuid(long seed) {
        return com.pro.ereportbacken.TestIds.uuid(seed);
    }
}
