package com.pro.ereportbacken.reporting;

import com.pro.ereportbacken.time.DisplayTime;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutstandingWorkOrderSpreadsheetExporter {

    private static final int EXCEL_TEXT_LIMIT = 32_767;
    private static final List<String> HEADERS = List.of(
            "序号", "工单编号", "工单标识", "工单类型", "处理状态", "优先级",
            "故障地点", "问题描述", "联系人", "联系电话", "报单人", "当前处理人",
            "报单时间", "当前历时", "SLA截止时间", "当前是否超时"
    );
    private static final int[] COLUMN_WIDTHS = {
            8, 22, 38, 16, 14, 10, 32, 48, 16, 16, 18, 18, 20, 16, 20, 14
    };

    public byte[] export(OutstandingWorkOrderReport report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.getProperties().getCoreProperties().setTitle(
                    report.countyName() + report.reportDate() + "未处理工单汇总");
            Sheet sheet = workbook.createSheet(safeSheetName(report.countyName() + "未处理工单"));
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle textStyle = textStyle(workbook);
            CellStyle dateTimeStyle = dateTimeStyle(workbook);
            writeHeader(sheet, headerStyle);

            for (int index = 0; index < report.rows().size(); index++) {
                writeRow(sheet, index + 1, report.rows().get(index), report.generatedAt(), textStyle, dateTimeStyle);
            }

            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, report.rows().size()), 0, HEADERS.size() - 1));
            for (int index = 0; index < COLUMN_WIDTHS.length; index++) {
                sheet.setColumnWidth(index, COLUMN_WIDTHS[index] * 256);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成未处理工单电子表格失败。", exception);
        }
    }

    private void writeHeader(Sheet sheet, CellStyle style) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(24);
        for (int index = 0; index < HEADERS.size(); index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(HEADERS.get(index));
            cell.setCellStyle(style);
        }
    }

    private void writeRow(Sheet sheet,
                          int rowNumber,
                          OutstandingWorkOrderReportRow reportRow,
                          LocalDateTime generatedAt,
                          CellStyle textStyle,
                          CellStyle dateTimeStyle) {
        Row row = sheet.createRow(rowNumber);
        int column = 0;
        setNumber(row.createCell(column++), rowNumber, textStyle);
        setText(row.createCell(column++), reportRow.workOrderNo(), textStyle);
        setText(row.createCell(column++), String.valueOf(reportRow.id()), textStyle);
        setText(row.createCell(column++), reportRow.typeLabel(), textStyle);
        setText(row.createCell(column++), reportRow.statusLabel(), textStyle);
        setText(row.createCell(column++), reportRow.priorityLabel(), textStyle);
        setText(row.createCell(column++), reportRow.location(), textStyle);
        setText(row.createCell(column++), reportRow.reason(), textStyle);
        setText(row.createCell(column++), reportRow.contactPerson(), textStyle);
        setText(row.createCell(column++), reportRow.phone(), textStyle);
        setText(row.createCell(column++), reportRow.reporterDisplayName(), textStyle);
        setText(row.createCell(column++), reportRow.assigneeDisplayName(), textStyle);
        setDateTime(row.createCell(column++), reportRow.submittedAt(), dateTimeStyle);
        setText(row.createCell(column++), formatElapsed(reportRow.submittedAt(), generatedAt), textStyle);
        setDateTime(row.createCell(column++), reportRow.slaDueAt(), dateTimeStyle);
        setText(row.createCell(column), reportRow.currentlyOverdue() ? "是" : "否", textStyle);
    }

    private void setNumber(Cell cell, int value, CellStyle style) {
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setText(Cell cell, String value, CellStyle style) {
        cell.setCellValue(safeText(value));
        cell.setCellStyle(style);
    }

    private void setDateTime(Cell cell, LocalDateTime value, CellStyle style) {
        LocalDateTime displayValue = DisplayTime.utcLocalDateTime(value);
        if (displayValue != null) {
            cell.setCellValue(displayValue);
        }
        cell.setCellStyle(style);
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = borderedStyle(workbook);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle textStyle(XSSFWorkbook workbook) {
        CellStyle style = borderedStyle(workbook);
        style.setWrapText(true);
        return style;
    }

    private CellStyle dateTimeStyle(XSSFWorkbook workbook) {
        CellStyle style = borderedStyle(workbook);
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
        return style;
    }

    private CellStyle borderedStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    static String formatElapsed(LocalDateTime startedAt, LocalDateTime now) {
        if (startedAt == null || now == null) {
            return "";
        }
        Duration elapsed = Duration.between(startedAt, now);
        if (elapsed.isNegative()) {
            elapsed = Duration.ZERO;
        }
        long days = elapsed.toDays();
        long hours = elapsed.minusDays(days).toHours();
        long minutes = elapsed.minusDays(days).minusHours(hours).toMinutes();
        return days > 0 ? days + "天" + hours + "小时" : hours + "小时" + minutes + "分钟";
    }

    private String safeText(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "");
        return sanitized.length() <= EXCEL_TEXT_LIMIT
                ? sanitized
                : sanitized.substring(0, EXCEL_TEXT_LIMIT - 3) + "...";
    }

    private String safeSheetName(String value) {
        String safe = value.replaceAll("[\\\\/?*\\[\\]:]", "_");
        return safe.length() <= 31 ? safe : safe.substring(0, 31);
    }
}
