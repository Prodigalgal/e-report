package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.UserTransferJob;
import com.pro.ereportbacken.model.pojo.UserTransferJobRow;
import com.pro.ereportbacken.model.pojo.UserTransferValidationStatus;
import com.pro.ereportbacken.repository.UserTransferJobRepository;
import com.pro.ereportbacken.repository.UserTransferJobRowRepository;
import com.pro.ereportbacken.time.DisplayTime;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Log4j2
public class UserTransferErrorWorkbookService {

    private static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final Duration RETENTION = Duration.ofHours(24);
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String[] HEADERS = {
            "原工作表", "原行号", "用户", "命令", "失败阶段", "失败原因", "处理建议"
    };

    private final UserTransferJobRepository jobRepository;
    private final UserTransferJobRowRepository rowRepository;
    private final FileTransferService fileTransferService;
    private final ObjectMapper objectMapper;

    public UserTransferErrorWorkbookService(UserTransferJobRepository jobRepository,
                                            UserTransferJobRowRepository rowRepository,
                                            FileTransferService fileTransferService,
                                            ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.fileTransferService = fileTransferService;
        this.objectMapper = objectMapper;
    }

    public FileTransferService.GeneratedFile create(UUID jobId) {
        UserTransferJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("用户传输任务不存在。"));
        Path output;
        try {
            output = Files.createTempFile("e-report-user-import-errors-", ".xlsx");
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建导入失败结果临时文件。", exception);
        }
        try {
            writeWorkbook(job, output);
            return fileTransferService.registerGeneratedFile(
                    FileTransferPurpose.USER_IMPORT_ERROR,
                    "用户导入失败明细-" + DisplayTime.formatUtc(UtcTime.now(), FILE_TIME) + ".xlsx",
                    CONTENT_TYPE, output, RETENTION);
        } finally {
            try {
                Files.deleteIfExists(output);
            } catch (IOException exception) {
                log.warn("用户导入失败结果临时文件清理失败。path={}", output, exception);
            }
        }
    }

    private void writeWorkbook(UserTransferJob job, Path output) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            workbook.setCompressTempFiles(true);
            var sheet = workbook.createSheet("失败明细");
            sheet.setDisplayGridlines(false);
            CellStyle title = titleStyle(workbook);
            CellStyle header = headerStyle(workbook);
            CellStyle text = textStyle(workbook);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));
            writeCell(sheet.createRow(0), 0, "用户批量治理失败明细", title);
            Row headerRow = sheet.createRow(2);
            for (int column = 0; column < HEADERS.length; column++) {
                writeCell(headerRow, column, HEADERS[column], header);
            }
            int rowIndex = 3;
            int pageIndex = 0;
            Page<UserTransferJobRow> page;
            do {
                page = rowRepository.findFailures(job.getTenantId(), job.getId(),
                        PageRequest.of(pageIndex++, 500,
                                Sort.by("subjectKeyHash").ascending()
                                        .and(Sort.by("sheetName").ascending())
                                        .and(Sort.by("rowNumber").ascending())));
                for (UserTransferJobRow source : page.getContent()) {
                    boolean validationFailure = source.getValidationStatus() == UserTransferValidationStatus.INVALID;
                    String reason = validationFailure
                            ? String.join("；", validationErrors(source.getValidationErrorsJson()))
                            : source.getExecutionMessage();
                    writeValues(sheet.createRow(rowIndex++), text,
                            source.getSheetName(), source.getRowNumber(), source.getSubjectDisplay(),
                            source.getCommandType(), validationFailure ? "预览校验" : "执行",
                            reason, validationFailure
                                    ? "修正模板对应行后重新上传并预览。"
                                    : "核对当前用户、授权上限和数据版本后重新导入失败用户。");
                }
            } while (pageIndex < page.getTotalPages());
            int[] widths = {22, 10, 16, 18, 14, 60, 48};
            for (int column = 0; column < widths.length; column++) {
                sheet.setColumnWidth(column, widths[column] * 256);
            }
            sheet.createFreezePane(0, 3);
            sheet.setAutoFilter(new CellRangeAddress(2, Math.max(2, rowIndex - 1), 0, HEADERS.length - 1));
            workbook.getXSSFWorkbook().getProperties().getCoreProperties().setTitle("e-report 用户导入失败明细");
            workbook.getXSSFWorkbook().getProperties().getCoreProperties().setCreator("e-report");
            try (var stream = Files.newOutputStream(output)) {
                workbook.write(stream);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("生成用户导入失败明细失败。", exception);
        }
    }

    private List<String> validationErrors(String json) {
        if (json == null || json.isBlank()) return List.of("预览校验失败。");
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (IOException exception) {
            return List.of("预览错误信息无法解析，请查看任务详情。");
        }
    }

    private CellStyle titleStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        var font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle headerStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        var font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle textStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        style.setDataFormat(workbook.createDataFormat().getFormat("@"));
        return style;
    }

    private void writeValues(Row row, CellStyle style, Object... values) {
        for (int column = 0; column < values.length; column++) {
            writeCell(row, column, values[column] == null ? "" : String.valueOf(values[column]), style);
        }
    }

    private void writeCell(Row row, int column, String value, CellStyle style) {
        var cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }
}
