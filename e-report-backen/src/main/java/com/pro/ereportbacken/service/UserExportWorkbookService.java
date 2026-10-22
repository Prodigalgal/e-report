package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.UserTransferExportPreviewDTO;
import com.pro.ereportbacken.time.DisplayTime;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class UserExportWorkbookService {

    private static final String[] USER_HEADERS = {
            "用户标识", "手机号", "姓名", "登录账号", "邮箱", "账号状态",
            "所属组织编码", "所属组织名称", "所属部门编码", "所属部门名称",
            "授权版本", "创建时间", "最后更新时间"
    };
    private static final int[] USER_WIDTHS = {38, 16, 16, 20, 30, 12, 18, 20, 18, 22, 14, 22, 22};
    private static final String[] GRANT_HEADERS = {
            "Grant 标识", "父 Grant 标识", "用户标识", "手机号", "姓名", "权限编码", "权限名称",
            "状态", "当前有效", "可转授权", "发行类型", "生效时间", "到期时间",
            "授予人标识", "授权原因", "授予时间"
    };
    private static final int[] GRANT_WIDTHS = {38, 38, 38, 16, 16, 42, 24, 18, 12, 12, 18, 22, 22, 38, 36, 22};
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<UserTransferExportPreviewDTO.SheetPreview> previewSheets() {
        return List.of(
                new UserTransferExportPreviewDTO.SheetPreview("用户清单", List.of(USER_HEADERS)),
                new UserTransferExportPreviewDTO.SheetPreview("原子权限 Grant", List.of(GRANT_HEADERS)),
                new UserTransferExportPreviewDTO.SheetPreview("导出说明", List.of("项目", "内容"))
        );
    }

    public StreamingWorkbook open(Path outputFile,
                                  UserExportQueryService.UserExportSnapshot snapshot,
                                  LocalDateTime generatedAt) {
        return new StreamingWorkbook(outputFile, snapshot, generatedAt);
    }

    private int writeUsers(Sheet sheet,
                           UserExportQueryService.UserExportDataset dataset,
                           int rowIndex,
                           CellStyle text) {
        for (UserExportQueryService.UserExportRow source : dataset.rows()) {
            writeValues(sheet.createRow(rowIndex++), text,
                    source.id(), source.phone(), source.name(), source.username(), source.email(), statusLabel(source.status()),
                    source.organizationCode(), source.organizationName(), source.departmentCode(), source.departmentName(),
                    source.authorizationVersion(),
                    format(source.createdAt()), format(source.updatedAt()));
        }
        return rowIndex;
    }

    private int writeGrants(Sheet sheet,
                            UserExportQueryService.UserExportDataset dataset,
                            int rowIndex,
                            CellStyle text) {
        for (UserExportQueryService.UserPermissionGrantExportRow source : dataset.grantRows()) {
            writeValues(sheet.createRow(rowIndex++), text,
                    source.grantId(), source.parentGrantId(), source.userId(), source.phone(), source.userName(),
                    source.permissionName(), source.permissionDisplayName(),
                    source.status(), source.effectivelyActive() ? "是" : "否",
                    source.grantOption() ? "是" : "否", source.issuerType(),
                    format(source.validFrom()), format(source.expiresAt()), source.grantedByMembershipId(),
                    source.reason(), format(source.grantedAt()));
        }
        return rowIndex;
    }

    private void writeInstructions(Sheet sheet,
                                   UserExportQueryService.UserExportSnapshot snapshot,
                                   LocalDateTime generatedAt,
                                   CellStyle title,
                                   CellStyle header,
                                   CellStyle text) {
        sheet.setDisplayGridlines(false);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
        writeCell(sheet.createRow(0), 0, "用户导出说明", title);
        writeValues(sheet.createRow(2), header, "项目", "内容");
        List<String[]> rows = List.of(
                new String[]{"导出时间", format(generatedAt)},
                new String[]{"导出数量", snapshot.userCount() + " 个用户"},
                new String[]{"原子授权", snapshot.grantCount() + " 条当前可管理 Grant"},
                new String[]{"筛选条件", filterSummary(snapshot.filters())},
                new String[]{"权限边界", "只导出当前操作者拥有有效转授权能力且有效期能够覆盖的 Grant。"},
                new String[]{"单一有效 Grant", "同一用户同一权限最多只有一个 ACTIVE Grant；岗位模板不会形成运行时来源。"},
                new String[]{"敏感信息", "文件不包含密码、验证码、令牌或其他认证凭据，请按内部资料妥善保管。"},
                new String[]{"导入关系", "本文件用于审计和核对；批量变更请使用 V6 用户与 Grant 批量治理模板。"}
        );
        int rowIndex = 3;
        for (String[] row : rows) writeValues(sheet.createRow(rowIndex++), text, (Object[]) row);
        sheet.setColumnWidth(0, 18 * 256);
        sheet.setColumnWidth(1, 90 * 256);
        sheet.createFreezePane(0, 3);
    }

    private void prepare(Sheet sheet,
                         String titleText,
                         String summaryText,
                         String[] headers,
                         int[] widths,
                         CellStyle title,
                         CellStyle header,
                         CellStyle text) {
        sheet.setDisplayGridlines(false);
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(30);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));
        writeCell(titleRow, 0, titleText, title);
        Row summary = sheet.createRow(1);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, headers.length - 1));
        writeCell(summary, 0, summaryText, text);
        Row headerRow = sheet.createRow(2);
        for (int column = 0; column < headers.length; column++) {
            writeCell(headerRow, column, headers[column], header);
            sheet.setColumnWidth(column, widths[column] * 256);
        }
    }

    private void finish(Sheet sheet, int nextRowIndex, int columnCount) {
        sheet.createFreezePane(0, 3);
        sheet.setAutoFilter(new CellRangeAddress(2, Math.max(2, nextRowIndex - 1), 0, columnCount - 1));
    }

    private String filterSummary(UserExportQueryService.UserExportFilters filters) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        if (filters.searchTerm() != null) values.add("关键词=" + filters.searchTerm());
        if (filters.organizationName() != null) values.add("组织=" + filters.organizationName());
        if (filters.departmentName() != null) values.add("部门=" + filters.departmentName());
        if (filters.status() != null) values.add("状态=" + statusLabel(filters.status()));
        if (filters.includeDeleted()) values.add("包含已删除账号");
        return values.isEmpty() ? "全部可见用户" : String.join("；", values);
    }

    private String statusLabel(com.pro.ereportbacken.model.pojo.UserStatus status) {
        return switch (status) {
            case ACTIVE -> "启用";
            case INACTIVE -> "停用";
            case DISABLED -> "禁用";
            case LOCKED -> "锁定";
            case DELETED -> "已删除";
        };
    }

    private String format(LocalDateTime value) {
        return DisplayTime.formatUtc(value, DATE_TIME);
    }

    private void writeValues(Row row, CellStyle style, Object... values) {
        for (int column = 0; column < values.length; column++) {
            writeCell(row, column, values[column] == null ? "" : String.valueOf(values[column]), style);
        }
    }

    private void writeCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.ROYAL_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyBorders(style);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle textStyle(Workbook workbook, boolean wrap) {
        CellStyle style = workbook.createCellStyle();
        applyBorders(style);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(wrap);
        style.setDataFormat(workbook.createDataFormat().getFormat("@"));
        return style;
    }

    private void applyBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
    }

    public final class StreamingWorkbook implements AutoCloseable {

        private final Path outputFile;
        private final UserExportQueryService.UserExportSnapshot snapshot;
        private final LocalDateTime generatedAt;
        private final SXSSFWorkbook workbook;
        private final Sheet users;
        private final Sheet grants;
        private final CellStyle text;
        private int userRowIndex = 3;
        private int grantRowIndex = 3;
        private boolean written;

        private StreamingWorkbook(Path outputFile,
                                  UserExportQueryService.UserExportSnapshot snapshot,
                                  LocalDateTime generatedAt) {
            this.outputFile = outputFile;
            this.snapshot = snapshot;
            this.generatedAt = generatedAt;
            this.workbook = new SXSSFWorkbook(200);
            workbook.setCompressTempFiles(true);
            CellStyle title = titleStyle(workbook);
            CellStyle header = headerStyle(workbook);
            this.text = textStyle(workbook, false);
            this.users = workbook.createSheet("用户清单");
            this.grants = workbook.createSheet("原子权限 Grant");
            prepare(users, "用户清单", "导出时间：" + format(generatedAt)
                            + "    用户数：" + snapshot.userCount()
                            + "    筛选：" + filterSummary(snapshot.filters()),
                    USER_HEADERS, USER_WIDTHS, title, header, text);
            prepare(grants, "原子权限 Grant", "导出时间：" + format(generatedAt)
                            + "    当前账号可管理的 Grant 数：" + snapshot.grantCount(),
                    GRANT_HEADERS, GRANT_WIDTHS, title, header, text);
        }

        public void append(UserExportQueryService.UserExportDataset batch) {
            if (written) {
                throw new IllegalStateException("导出工作簿已经完成，不能继续追加数据。");
            }
            userRowIndex = writeUsers(users, batch, userRowIndex, text);
            grantRowIndex = writeGrants(grants, batch, grantRowIndex, text);
        }

        public Path write() {
            if (written) {
                throw new IllegalStateException("导出工作簿已经生成。");
            }
            written = true;
            finish(users, userRowIndex, USER_HEADERS.length);
            finish(grants, grantRowIndex, GRANT_HEADERS.length);
            writeInstructions(workbook.createSheet("导出说明"), snapshot, generatedAt,
                    titleStyle(workbook), headerStyle(workbook), text);
            workbook.getXSSFWorkbook().getProperties().getCoreProperties()
                    .setTitle("e-report 用户与原子权限导出");
            workbook.getXSSFWorkbook().getProperties().getCoreProperties().setCreator("e-report");
            try (OutputStream output = Files.newOutputStream(outputFile)) {
                workbook.write(output);
                return outputFile;
            } catch (IOException exception) {
                throw new IllegalStateException("生成用户导出文件失败。", exception);
            }
        }

        @Override
        public void close() {
            try {
                workbook.close();
            } catch (IOException exception) {
                throw new IllegalStateException("关闭用户导出工作簿失败。", exception);
            } finally {
                workbook.dispose();
            }
        }
    }
}
