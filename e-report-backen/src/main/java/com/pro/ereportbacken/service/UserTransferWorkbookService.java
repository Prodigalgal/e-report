package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.OrgUnitDTO;
import com.pro.ereportbacken.model.dto.DepartmentDTO;
import com.pro.ereportbacken.security.PermissionRiskLevel;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class UserTransferWorkbookService {

    public static final String TEMPLATE_VERSION = "6.0";
    public static final int MAX_COMMAND_ROWS = 20_000;
    public static final long MAX_FILE_SIZE = 20L * 1024L * 1024L;
    public static final String INSTRUCTION_SHEET = "填写说明";
    public static final String USER_SHEET = "用户资料";
    public static final String TEMPLATE_APPLY_SHEET = "岗位模板追加";
    public static final String GRANT_COMMAND_SHEET = "原子权限变更";
    public static final String PROFILE_CATALOG_SHEET = "岗位模板目录";
    public static final String PERMISSION_CATALOG_SHEET = "权限目录";
    public static final String ORGANIZATION_SHEET = "组织目录";
    public static final String DEPARTMENT_SHEET = "部门目录";
    public static final String METADATA_SHEET = "模板元数据";

    private static final int HEADER_ROW_INDEX = 2;
    private static final int FIRST_DATA_ROW_INDEX = 3;
    private static final String[] USER_HEADERS = {
            "手机号", "动作", "姓名", "组织编码", "部门编码", "登录账号", "初始密码", "邮箱", "账号状态", "授权处理策略"
    };
    private static final String[] TEMPLATE_APPLY_HEADERS = {
            "手机号", "动作", "岗位模板编码", "生效时间", "到期时间", "允许转授权", "原因"
    };
    private static final String[] GRANT_COMMAND_HEADERS = {
            "手机号", "动作", "Grant 标识", "权限编码", "新父 Grant 标识",
            "生效时间", "到期时间", "允许转授权", "原因"
    };
    private static final Set<String> REQUIRED_SHEETS = Set.of(
            INSTRUCTION_SHEET, USER_SHEET, TEMPLATE_APPLY_SHEET, GRANT_COMMAND_SHEET,
            PROFILE_CATALOG_SHEET, PERMISSION_CATALOG_SHEET, ORGANIZATION_SHEET, DEPARTMENT_SHEET, METADATA_SHEET);

    static {
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(64L * 1024L * 1024L);
        ZipSecureFile.setMaxTextSize(8L * 1024L * 1024L);
    }

    public byte[] createTemplate(UUID tenantId,
                                 List<OrgUnitDTO> organizations,
                                 List<DepartmentDTO> departments,
                                 List<ProfileCatalogEntry> profiles,
                                 List<PermissionCatalogEntry> permissions) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle title = titleStyle(workbook);
            CellStyle note = noteStyle(workbook);
            CellStyle header = headerStyle(workbook, false);
            CellStyle requiredHeader = headerStyle(workbook, true);
            CellStyle text = textStyle(workbook);

            writeInstructions(workbook.createSheet(INSTRUCTION_SHEET), title, header, text);
            Sheet users = workbook.createSheet(USER_SHEET);
            writeCommandSheet(users, "用户批量创建与资料调整", USER_HEADERS, Set.of(0, 1),
                    "手机号是全局唯一键；邮箱填写 #CLEAR 表示清空。", title, note, header, requiredHeader);
            Sheet templates = workbook.createSheet(TEMPLATE_APPLY_SHEET);
            writeCommandSheet(templates, "岗位模板批量追加", TEMPLATE_APPLY_HEADERS, Set.of(0, 1, 2, 6),
                    "模板只批量添加独立 Grant，不记录模板来源；动作仅支持 ADD。", title, note, header, requiredHeader);
            Sheet grants = workbook.createSheet(GRANT_COMMAND_SHEET);
            writeCommandSheet(grants, "原子权限 Grant 增量变更", GRANT_COMMAND_HEADERS, Set.of(0, 1, 8),
                    "支持 ADD、ADJUST、REVOKE、REATTACH；留空到期时间表示长期。", title, note, header, requiredHeader);
            writeProfileCatalog(workbook.createSheet(PROFILE_CATALOG_SHEET), profiles, header, text);
            writePermissionCatalog(workbook.createSheet(PERMISSION_CATALOG_SHEET), permissions, header, text);
            writeOrganizations(workbook.createSheet(ORGANIZATION_SHEET), organizations, header, text);
            writeDepartments(workbook.createSheet(DEPARTMENT_SHEET), departments, organizations, header, text);
            writeMetadata(workbook.createSheet(METADATA_SHEET), tenantId, text);
            addValidations(workbook, users, templates, grants,
                    organizations.size(), departments.size() + 1, profiles.size(), permissions.size());
            protectReferenceSheet(workbook.getSheet(PROFILE_CATALOG_SHEET));
            protectReferenceSheet(workbook.getSheet(PERMISSION_CATALOG_SHEET));
            protectReferenceSheet(workbook.getSheet(ORGANIZATION_SHEET));
            protectReferenceSheet(workbook.getSheet(DEPARTMENT_SHEET));
            workbook.setSheetVisibility(workbook.getSheetIndex(METADATA_SHEET), SheetVisibility.VERY_HIDDEN);
            workbook.setActiveSheet(workbook.getSheetIndex(USER_SHEET));
            workbook.getProperties().getCoreProperties().setTitle("e-report 用户与 Grant 批量治理 V6 模板");
            workbook.getProperties().getCoreProperties().setCreator("e-report");
            workbook.getProperties().getCustomProperties().addProperty(
                    "e-report-template-version", TEMPLATE_VERSION);
            workbook.getProperties().getCustomProperties().addProperty(
                    "e-report-tenant-id", tenantId.toString());
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成用户批量治理模板失败。", exception);
        }
    }

    public ParsedWorkbook parse(UUID expectedTenantId,
                                String fileName,
                                long fileSize,
                                Resource resource) {
        validateFile(fileName, fileSize, resource);
        try (InputStream input = resource.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            validateWorkbook(workbook, expectedTenantId);
            List<RawUserCommand> users = parseUsers(workbook.getSheet(USER_SHEET));
            List<RawTemplateApplyCommand> templates = parseTemplates(workbook.getSheet(TEMPLATE_APPLY_SHEET));
            List<RawGrantCommand> grants = parseGrants(workbook.getSheet(GRANT_COMMAND_SHEET));
            int commandCount = users.size() + templates.size() + grants.size();
            if (commandCount == 0) {
                throw new IllegalArgumentException("命令工作表中没有可处理的数据行。" );
            }
            if (commandCount > MAX_COMMAND_ROWS) {
                throw new IllegalArgumentException("单个文件最多包含 " + MAX_COMMAND_ROWS + " 条命令。" );
            }
            return new ParsedWorkbook(TEMPLATE_VERSION, users, templates, grants);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Excel 文件无法读取，请使用系统下载的最新 .xlsx 模板。", exception);
        }
    }

    private void validateWorkbook(XSSFWorkbook workbook, UUID expectedTenantId) {
        Set<String> names = new LinkedHashSet<>();
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            names.add(workbook.getSheetName(index));
        }
        if (!names.equals(REQUIRED_SHEETS)) {
            Set<String> missing = new LinkedHashSet<>(REQUIRED_SHEETS);
            missing.removeAll(names);
            Set<String> unknown = new LinkedHashSet<>(names);
            unknown.removeAll(REQUIRED_SHEETS);
            throw new IllegalArgumentException("模板工作表结构不正确。缺少：" + missing + "；未知：" + unknown + "。" );
        }
        if (!workbook.getExternalLinksTable().isEmpty()) {
            throw new IllegalArgumentException("模板不能包含外部工作簿链接。" );
        }
        validateHeaders(workbook.getSheet(USER_SHEET), USER_HEADERS);
        validateHeaders(workbook.getSheet(TEMPLATE_APPLY_SHEET), TEMPLATE_APPLY_HEADERS);
        validateHeaders(workbook.getSheet(GRANT_COMMAND_SHEET), GRANT_COMMAND_HEADERS);
        requireVisible(workbook, USER_SHEET, USER_HEADERS.length);
        requireVisible(workbook, TEMPLATE_APPLY_SHEET, TEMPLATE_APPLY_HEADERS.length);
        requireVisible(workbook, GRANT_COMMAND_SHEET, GRANT_COMMAND_HEADERS.length);
        Sheet metadata = workbook.getSheet(METADATA_SHEET);
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        String version = cellText(metadata.getRow(0), 1, formatter);
        String tenantId = cellText(metadata.getRow(1), 1, formatter);
        if (!TEMPLATE_VERSION.equals(version)) {
            throw new IllegalArgumentException("模板版本不受支持，请重新下载最新模板。" );
        }
        if (!expectedTenantId.toString().equalsIgnoreCase(tenantId)) {
            throw new IllegalArgumentException("模板属于其他租户，请重新下载当前市公司的模板。" );
        }
    }

    private List<RawUserCommand> parseUsers(Sheet sheet) {
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        List<RawUserCommand> result = new ArrayList<>();
        forEachCommandRow(sheet, USER_HEADERS.length, formatter, row -> result.add(new RawUserCommand(
                row.getRowNum() + 1, cellText(row, 0, formatter), cellText(row, 1, formatter),
                cellText(row, 2, formatter), cellText(row, 3, formatter), cellText(row, 4, formatter),
                cellText(row, 5, formatter), cellText(row, 6, formatter), cellText(row, 7, formatter),
                cellText(row, 8, formatter), cellText(row, 9, formatter))));
        return List.copyOf(result);
    }

    private List<RawTemplateApplyCommand> parseTemplates(Sheet sheet) {
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        List<RawTemplateApplyCommand> result = new ArrayList<>();
        forEachCommandRow(sheet, TEMPLATE_APPLY_HEADERS.length, formatter, row -> result.add(new RawTemplateApplyCommand(
                row.getRowNum() + 1, cellText(row, 0, formatter), cellText(row, 1, formatter),
                cellText(row, 2, formatter), cellText(row, 3, formatter), cellText(row, 4, formatter),
                cellText(row, 5, formatter), cellText(row, 6, formatter))));
        return List.copyOf(result);
    }

    private List<RawGrantCommand> parseGrants(Sheet sheet) {
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        List<RawGrantCommand> result = new ArrayList<>();
        forEachCommandRow(sheet, GRANT_COMMAND_HEADERS.length, formatter, row -> result.add(new RawGrantCommand(
                row.getRowNum() + 1, cellText(row, 0, formatter), cellText(row, 1, formatter),
                cellText(row, 2, formatter), cellText(row, 3, formatter), cellText(row, 4, formatter),
                cellText(row, 5, formatter), cellText(row, 6, formatter), cellText(row, 7, formatter),
                cellText(row, 8, formatter))));
        return List.copyOf(result);
    }

    private void forEachCommandRow(Sheet sheet,
                                   int columnCount,
                                   DataFormatter formatter,
                                   java.util.function.Consumer<Row> consumer) {
        for (int rowIndex = FIRST_DATA_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isBlank(row, columnCount, formatter)) {
                continue;
            }
            if (row.getZeroHeight()) {
                throw new IllegalArgumentException("第 " + (rowIndex + 1) + " 行不能隐藏。" );
            }
            consumer.accept(row);
        }
    }

    private void writeInstructions(Sheet sheet, CellStyle title, CellStyle header, CellStyle text) {
        sheet.setDisplayGridlines(false);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
        writeCell(sheet.createRow(0), 0, "用户与 Grant 批量治理 V6 填写说明", title);
        writeValues(sheet.createRow(2), header, "主题", "规则");
        String[][] rows = {
                {"执行顺序", "先处理用户资料，再处理岗位模板追加和原子权限变更；同一用户任一行失败，该用户全部不写入。"},
                {"用户资料", "CREATE 新建；UPDATE 修改资料；ORG_MOVE 移动组织；DEPARTMENT_MOVE 调整组织内部门。手机号是全局唯一键。"},
                {"部门归属", "CREATE 可填写部门编码；DEPARTMENT_MOVE 填写目标部门编码，填写 #CLEAR 表示调整为组织直属。部门不改变组织数据面。"},
                {"组织迁移：KEEP", "保留现有 Grant，只改变所属组织；数据可见范围随新所属组织自然变化。"},
                {"组织迁移：REVOKE_ALL", "移动组织并撤销全部可撤销授权；系统根、系统基线和本人基线授权由系统继续维护。"},
                {"岗位模板追加", "只支持 ADD；模板展开为独立 Grant，不记录模板来源，也不能按模板整体撤销。"},
                {"原子权限变更", "ADD 新增；ADJUST 覆盖；REVOKE 撤销；REATTACH 将暂停授权挂到新的父 Grant。"},
                  {"有效时间", "按北京时间（UTC+8）填写 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅填写日期时，生效为当天 00:00，到期为当天结束；留空到期时间表示长期授权。"},
                {"允许转授权", "填写是/否；下游权限和到期时间都不能超过操作者对应父 Grant。"},
                {"增量原则", "工作簿只提交明确命令，不删除未出现在文件中的 Grant。"},
                {"预览门禁", "上传后必须分页查看结构化 Diff；目录、用户版本或操作者授权变化后，旧预览自动失效。"}
        };
        for (int index = 0; index < rows.length; index++) {
            writeValues(sheet.createRow(index + 3), text, (Object[]) rows[index]);
        }
        sheet.setColumnWidth(0, 24 * 256);
        sheet.setColumnWidth(1, 100 * 256);
        sheet.createFreezePane(0, 3);
    }

    private void writeCommandSheet(Sheet sheet,
                                   String titleText,
                                   String[] headers,
                                   Set<Integer> requiredColumns,
                                   String noteText,
                                   CellStyle title,
                                   CellStyle note,
                                   CellStyle header,
                                   CellStyle requiredHeader) {
        sheet.setDisplayGridlines(false);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));
        writeCell(sheet.createRow(0), 0, titleText, title);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, headers.length - 1));
        writeCell(sheet.createRow(1), 0, noteText, note);
        Row headerRow = sheet.createRow(HEADER_ROW_INDEX);
        for (int column = 0; column < headers.length; column++) {
            writeCell(headerRow, column, headers[column],
                    requiredColumns.contains(column) ? requiredHeader : header);
            sheet.setColumnWidth(column, Math.min(45, Math.max(14, headers[column].length() * 3)) * 256);
        }
        sheet.createFreezePane(0, FIRST_DATA_ROW_INDEX);
        sheet.setAutoFilter(new CellRangeAddress(HEADER_ROW_INDEX, HEADER_ROW_INDEX, 0, headers.length - 1));
    }

    private void writeProfileCatalog(Sheet sheet,
                                     List<ProfileCatalogEntry> profiles,
                                     CellStyle header,
                                     CellStyle text) {
        writeValues(sheet.createRow(0), header, "岗位模板编码", "岗位名称", "版本", "原子权限数", "职责说明");
        int rowIndex = 1;
        for (ProfileCatalogEntry profile : profiles) {
            writeValues(sheet.createRow(rowIndex++), text, profile.code(), profile.displayName(),
                    profile.versionNo(), profile.permissionCount(), profile.description());
        }
        setCatalogWidths(sheet, 28, 28, 12, 14, 60);
        finishCatalog(sheet, rowIndex, 5);
    }

    private void writePermissionCatalog(Sheet sheet,
                                        List<PermissionCatalogEntry> permissions,
                                        CellStyle header,
                                        CellStyle text) {
        writeValues(sheet.createRow(0), header, "权限编码", "权限名称", "风险等级", "说明");
        int rowIndex = 1;
        for (PermissionCatalogEntry permission : permissions) {
            writeValues(sheet.createRow(rowIndex++), text, permission.name(), permission.displayName(),
                    permission.riskLevel(), permission.description());
        }
        setCatalogWidths(sheet, 46, 28, 16, 70);
        finishCatalog(sheet, rowIndex, 4);
    }

    private void writeOrganizations(Sheet sheet,
                                    List<OrgUnitDTO> organizations,
                                    CellStyle header,
                                    CellStyle text) {
        writeValues(sheet.createRow(0), header, "组织编码", "组织名称", "组织类型", "状态", "组织标识");
        int rowIndex = 1;
        for (OrgUnitDTO organization : organizations) {
            writeValues(sheet.createRow(rowIndex++), text, organization.code(), organization.name(),
                    organization.unitType(), organization.status(), organization.id());
        }
        setCatalogWidths(sheet, 22, 28, 18, 14, 38);
        finishCatalog(sheet, rowIndex, 5);
    }

    private void writeDepartments(Sheet sheet,
                                  List<DepartmentDTO> departments,
                                  List<OrgUnitDTO> organizations,
                                  CellStyle header,
                                  CellStyle text) {
        writeValues(sheet.createRow(0), header,
                "部门编码", "部门名称", "所属组织编码", "所属组织名称", "状态", "部门标识");
        int rowIndex = 1;
        java.util.Map<UUID, String> organizationCodes = organizations.stream()
                .collect(java.util.stream.Collectors.toMap(OrgUnitDTO::id, OrgUnitDTO::code));
        for (DepartmentDTO department : departments) {
            writeValues(sheet.createRow(rowIndex++), text,
                    department.code(), department.name(), organizationCodes.get(department.orgUnitId()), department.orgUnitName(),
                    department.status(), department.id());
        }
        writeValues(sheet.createRow(rowIndex++), text,
                "#CLEAR", "调整为组织直属", "-", "-", "可选操作", "-");
        setCatalogWidths(sheet, 22, 28, 22, 28, 14, 38);
        finishCatalog(sheet, rowIndex, 6);
    }

    private void writeMetadata(Sheet sheet, UUID tenantId, CellStyle text) {
        writeValues(sheet.createRow(0), text, "templateVersion", TEMPLATE_VERSION);
        writeValues(sheet.createRow(1), text, "tenantId", tenantId);
        writeValues(sheet.createRow(2), text, "authorizationModel", "ATOMIC_PERMISSION_GRANTS_V4");
    }

    private void addValidations(XSSFWorkbook workbook,
                                Sheet users,
                                Sheet templates,
                                Sheet grants,
                                int organizationCount,
                                int departmentCount,
                                int profileCount,
                                int permissionCount) {
        namedRange(workbook, "profile_codes", PROFILE_CATALOG_SHEET, 1, Math.max(1, profileCount), 0);
        namedRange(workbook, "permission_codes", PERMISSION_CATALOG_SHEET, 1, Math.max(1, permissionCount), 0);
        namedRange(workbook, "organization_codes", ORGANIZATION_SHEET, 1, Math.max(1, organizationCount), 0);
        namedRange(workbook, "department_codes", DEPARTMENT_SHEET, 1, Math.max(1, departmentCount), 0);
        listValidation(users, 1, "CREATE,UPDATE,ORG_MOVE,DEPARTMENT_MOVE");
        listValidation(users, 3, "organization_codes");
        listValidation(users, 4, "department_codes");
        listValidation(users, 8, "ACTIVE,INACTIVE,DISABLED,LOCKED");
        listValidation(users, 9, "KEEP,REVOKE_ALL");
        listValidation(templates, 1, "ADD");
        listValidation(templates, 2, "profile_codes");
        listValidation(templates, 5, "是,否");
        listValidation(grants, 1, "ADD,ADJUST,REVOKE,REATTACH");
        listValidation(grants, 3, "permission_codes");
        listValidation(grants, 7, "是,否");
    }

    private void namedRange(XSSFWorkbook workbook,
                            String rangeName,
                            String sheetName,
                            int firstRow,
                            int lastRow,
                            int column) {
        Name name = workbook.createName();
        name.setNameName(rangeName);
        String escaped = sheetName.replace("'", "''");
        name.setRefersToFormula("'" + escaped + "'!$" + columnLetter(column) + "$" + (firstRow + 1)
                + ":$" + columnLetter(column) + "$" + (lastRow + 1));
    }

    private void listValidation(Sheet sheet, int column, String valuesOrName) {
        var helper = sheet.getDataValidationHelper();
        var constraint = valuesOrName.contains(",")
                ? helper.createExplicitListConstraint(valuesOrName.split(","))
                : helper.createFormulaListConstraint(valuesOrName);
        DataValidation validation = helper.createValidation(constraint,
                new CellRangeAddressList(FIRST_DATA_ROW_INDEX,
                        MAX_COMMAND_ROWS + FIRST_DATA_ROW_INDEX, column, column));
        validation.setShowErrorBox(true);
        validation.createErrorBox("输入不受支持", "请从系统模板提供的选项中选择。" );
        sheet.addValidationData(validation);
    }

    private void validateFile(String fileName, long fileSize, Resource resource) {
        if (!StringUtils.hasText(fileName) || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("仅支持 .xlsx 文件。" );
        }
        if (fileSize <= 0 || fileSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小必须在 1 字节到 20MB 之间。" );
        }
        if (resource == null || !resource.exists()) {
            throw new IllegalArgumentException("上传文件不存在或已过期。" );
        }
    }

    private void validateHeaders(Sheet sheet, String[] expected) {
        Row row = sheet.getRow(HEADER_ROW_INDEX);
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        for (int column = 0; column < expected.length; column++) {
            if (!expected[column].equals(cellText(row, column, formatter))) {
                throw new IllegalArgumentException(sheet.getSheetName() + " 的表头已被修改，请重新下载模板。" );
            }
        }
    }

    private void requireVisible(XSSFWorkbook workbook, String sheetName, int columnCount) {
        int index = workbook.getSheetIndex(sheetName);
        if (workbook.getSheetVisibility(index) != SheetVisibility.VISIBLE) {
            throw new IllegalArgumentException("命令工作表不能隐藏：" + sheetName);
        }
        Sheet sheet = workbook.getSheet(sheetName);
        for (int column = 0; column < columnCount; column++) {
            if (sheet.isColumnHidden(column)) {
                throw new IllegalArgumentException("命令工作表不能隐藏列：" + sheetName);
            }
        }
    }

    private boolean isBlank(Row row, int columnCount, DataFormatter formatter) {
        for (int column = 0; column < columnCount; column++) {
            if (StringUtils.hasText(cellText(row, column, formatter))) {
                return false;
            }
        }
        return true;
    }

    private String cellText(Row row, int column, DataFormatter formatter) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(column);
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA) {
            throw new IllegalArgumentException("命令工作表不允许使用公式。" );
        }
        return formatter.formatCellValue(cell).trim();
    }

    private void finishCatalog(Sheet sheet, int rowIndex, int columns) {
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, columns - 1));
    }

    private void protectReferenceSheet(Sheet sheet) {
        sheet.protectSheet("e-report-reference-v5");
    }

    private void setCatalogWidths(Sheet sheet, int... widths) {
        for (int column = 0; column < widths.length; column++) {
            sheet.setColumnWidth(column, widths[column] * 256);
        }
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

    private CellStyle titleStyle(XSSFWorkbook workbook) {
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

    private CellStyle noteStyle(XSSFWorkbook workbook) {
        CellStyle style = textStyle(workbook);
        style.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setWrapText(true);
        return style;
    }

    private CellStyle headerStyle(XSSFWorkbook workbook, boolean required) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor((required
                ? org.apache.poi.ss.usermodel.IndexedColors.LIGHT_ORANGE
                : org.apache.poi.ss.usermodel.IndexedColors.LIGHT_CORNFLOWER_BLUE).getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(style);
        return style;
    }

    private CellStyle textStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        style.setDataFormat(workbook.createDataFormat().getFormat("@"));
        applyBorders(style);
        return style;
    }

    private void applyBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
    }

    private String columnLetter(int column) {
        int current = column + 1;
        StringBuilder value = new StringBuilder();
        while (current > 0) {
            int remainder = (current - 1) % 26;
            value.insert(0, (char) ('A' + remainder));
            current = (current - 1) / 26;
        }
        return value.toString();
    }

    public record ParsedWorkbook(String templateVersion,
                                 List<RawUserCommand> userCommands,
                                 List<RawTemplateApplyCommand> templateApplyCommands,
                                 List<RawGrantCommand> grantCommands) {
        public ParsedWorkbook {
            userCommands = List.copyOf(userCommands);
            templateApplyCommands = List.copyOf(templateApplyCommands);
            grantCommands = List.copyOf(grantCommands);
        }
    }

    public record RawUserCommand(int rowNumber,
                                 String phone,
                                 String action,
                                 String name,
                                 String organizationCode,
                                 String departmentCode,
                                 String username,
                                 String initialPassword,
                                 String email,
                                 String status,
                                 String authorizationStrategy) {
    }

    public record RawTemplateApplyCommand(int rowNumber,
                                          String phone,
                                          String action,
                                          String profileCode,
                                          String validFrom,
                                          String expiresAt,
                                          String grantOption,
                                          String reason) {
    }

    public record RawGrantCommand(int rowNumber,
                                  String phone,
                                  String action,
                                  String grantId,
                                  String permissionName,
                                  String parentGrantId,
                                  String validFrom,
                                  String expiresAt,
                                  String grantOption,
                                  String reason) {
    }

    public record ProfileCatalogEntry(String code,
                                      String displayName,
                                      int versionNo,
                                      int permissionCount,
                                      String description) {
    }

    public record PermissionCatalogEntry(String name,
                                         String displayName,
                                         PermissionRiskLevel riskLevel,
                                         String description) {
    }
}
