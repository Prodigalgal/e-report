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
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class WorkOrderSpreadsheetExporter {

    private static final int EXCEL_TEXT_LIMIT = 32_767;
    private static final List<String> HEADERS = List.of(
            "序号", "工单编号", "工单标识", "工单类型", "处理状态", "优先级",
            "提单组织", "责任组织", "故障地点", "问题描述", "联系人", "联系电话",
            "报单人", "当前处理人", "待接单人", "报单时间", "最后更新时间", "故障历时",
            "SLA环节", "SLA截止时间", "当前是否超时", "是否曾超时"
    );
    private static final int[] COLUMN_WIDTHS = {
            8, 22, 38, 16, 14, 10, 20, 20, 32, 48, 16, 16,
            18, 18, 18, 20, 20, 16, 18, 20, 14, 14
    };

    public WorkbookSession open(Path targetFile, String title, LocalDateTime generatedAt) {
        return new WorkbookSession(targetFile, title, generatedAt);
    }

    public final class WorkbookSession implements AutoCloseable {
        private final Path targetFile;
        private final LocalDateTime generatedAt;
        private final SXSSFWorkbook workbook;
        private final Sheet sheet;
        private final CellStyle textStyle;
        private final CellStyle dateTimeStyle;
        private int rowNumber;
        private boolean closed;

        private WorkbookSession(Path targetFile, String title, LocalDateTime generatedAt) {
            this.targetFile = targetFile;
            this.generatedAt = generatedAt;
            XSSFWorkbook template = new XSSFWorkbook();
            template.getProperties().getCoreProperties().setTitle(title);
            workbook = new SXSSFWorkbook(template, 100, true, false);
            workbook.setCompressTempFiles(true);
            sheet = workbook.createSheet("工单明细");
            textStyle = textStyle(workbook);
            dateTimeStyle = dateTimeStyle(workbook);
            writeHeader(sheet, headerStyle(workbook));
        }

        public void append(List<WorkOrderExportRow> rows, Map<java.util.UUID, String> organizationNames) {
            ensureOpen();
            for (WorkOrderExportRow row : rows) {
                writeRow(sheet, ++rowNumber, row, organizationNames, generatedAt, textStyle, dateTimeStyle);
            }
        }

        public int rowCount() {
            return rowNumber;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowNumber), 0, HEADERS.size() - 1));
            for (int index = 0; index < COLUMN_WIDTHS.length; index++) {
                sheet.setColumnWidth(index, COLUMN_WIDTHS[index] * 256);
            }
            RuntimeException failure = null;
            try (OutputStream output = Files.newOutputStream(targetFile)) {
                workbook.write(output);
            } catch (IOException exception) {
                failure = new IllegalStateException("生成工单电子表格失败。", exception);
            } finally {
                try {
                    workbook.close();
                } catch (IOException exception) {
                    failure = appendFailure(
                            failure, new IllegalStateException("关闭工单电子表格失败。", exception));
                }
            }
            if (failure != null) throw failure;
        }

        private void ensureOpen() {
            if (closed) throw new IllegalStateException("工单电子表格写入器已经关闭。");
        }

        private RuntimeException appendFailure(RuntimeException current, RuntimeException next) {
            if (current == null) return next;
            current.addSuppressed(next);
            return current;
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
                          WorkOrderExportRow rowData,
                          Map<java.util.UUID, String> organizationNames,
                          LocalDateTime generatedAt,
                          CellStyle textStyle,
                          CellStyle dateTimeStyle) {
        Row row = sheet.createRow(rowNumber);
        int column = 0;
        setNumber(row.createCell(column++), rowNumber, textStyle);
        setText(row.createCell(column++), rowData.workOrderNo(), textStyle);
        setText(row.createCell(column++), String.valueOf(rowData.id()), textStyle);
        setText(row.createCell(column++), rowData.typeLabel(), textStyle);
        setText(row.createCell(column++), rowData.statusLabel(), textStyle);
        setText(row.createCell(column++), rowData.priorityLabel(), textStyle);
        setText(row.createCell(column++), organizationNames.get(rowData.reportingOrgId()), textStyle);
        setText(row.createCell(column++), organizationNames.get(rowData.responsibleOrgId()), textStyle);
        setText(row.createCell(column++), rowData.location(), textStyle);
        setText(row.createCell(column++), rowData.reason(), textStyle);
        setText(row.createCell(column++), rowData.contactPerson(), textStyle);
        setText(row.createCell(column++), rowData.phone(), textStyle);
        setText(row.createCell(column++), rowData.reporterDisplayName(), textStyle);
        setText(row.createCell(column++), rowData.currentAssigneeDisplayName(), textStyle);
        setText(row.createCell(column++), rowData.pendingAssigneeDisplayName(), textStyle);
        setDateTime(row.createCell(column++), rowData.submittedAt(), dateTimeStyle);
        setDateTime(row.createCell(column++), rowData.lastUpdatedAt(), dateTimeStyle);
        setText(row.createCell(column++), formatElapsed(rowData.submittedAt(), rowData.elapsedUntil(generatedAt)), textStyle);
        setText(row.createCell(column++), rowData.slaStageLabel(), textStyle);
        setDateTime(row.createCell(column++), rowData.slaDueAt(), dateTimeStyle);
        setText(row.createCell(column++), rowData.currentlyOverdue(generatedAt) ? "是" : "否", textStyle);
        setText(row.createCell(column), Boolean.TRUE.equals(rowData.slaEverBreached()) ? "是" : "否", textStyle);
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
        if (displayValue != null) cell.setCellValue(displayValue);
        cell.setCellStyle(style);
    }

    private CellStyle headerStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle textStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        style.setBorderBottom(BorderStyle.HAIR);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        return style;
    }

    private CellStyle dateTimeStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        style.setBorderBottom(BorderStyle.HAIR);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
        return style;
    }

    static String formatElapsed(LocalDateTime startedAt, LocalDateTime endedAt) {
        if (startedAt == null || endedAt == null) return "";
        Duration elapsed = Duration.between(startedAt, endedAt);
        if (elapsed.isNegative()) elapsed = Duration.ZERO;
        long days = elapsed.toDays();
        long hours = elapsed.minusDays(days).toHours();
        long minutes = elapsed.minusDays(days).minusHours(hours).toMinutes();
        return days > 0 ? days + "天" + hours + "小时" : hours + "小时" + minutes + "分钟";
    }

    private String safeText(String value) {
        if (value == null) return "";
        String sanitized = value.replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "");
        return sanitized.length() <= EXCEL_TEXT_LIMIT
                ? sanitized
                : sanitized.substring(0, EXCEL_TEXT_LIMIT - 3) + "...";
    }
}
