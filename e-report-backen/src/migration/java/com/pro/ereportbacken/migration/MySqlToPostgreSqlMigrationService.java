package com.pro.ereportbacken.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class MySqlToPostgreSqlMigrationService {

    private static final String COPY_PHASE = "COPY";
    private static final String DEFERRED_PHASE = "DEFERRED";
    private static final String VALIDATE_PHASE = "VALIDATE";
    private static final String ARCHIVE_PHASE = "ARCHIVE";

    private final Connection source;
    private final Connection target;
    private final MigrationConfiguration configuration;
    private final MigrationCheckpointRepository checkpoints;
    private final ObjectMapper objectMapper;

    MySqlToPostgreSqlMigrationService(
            Connection source,
            Connection target,
            MigrationConfiguration configuration) {
        this.source = source;
        this.target = target;
        this.configuration = configuration;
        this.checkpoints = new MigrationCheckpointRepository(target);
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    UUID migrate() throws Exception {
        configureConnections();
        preflight();
        requireEmptyTargetForNewRun();
        UUID runId = checkpoints.startOrResume(configuration);
        try {
            for (MigrationTablePlan table : MigrationTablePlan.orderedTables()) {
                copyTable(runId, table);
            }
            for (MigrationTablePlan table : MigrationTablePlan.orderedTables()) {
                applyDeferredColumns(runId, table);
            }
            archiveLegacyAuditTables(runId);
            for (MigrationTablePlan table : MigrationTablePlan.orderedTables()) {
                validateTable(runId, table);
            }
            checkpoints.markRunCompleted(runId);
            target.commit();
            return runId;
        } catch (Exception exception) {
            checkpoints.markRunFailed(runId, exception);
            throw exception;
        }
    }

    private void configureConnections() throws SQLException {
        source.setReadOnly(true);
        source.setAutoCommit(false);
        source.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        target.setAutoCommit(false);
        try (Statement statement = target.createStatement()) {
            statement.execute("SET TIME ZONE 'UTC'");
            statement.execute("SET lock_timeout = '10s'");
            statement.execute("SET statement_timeout = '30min'");
        }
        target.commit();
    }

    private void preflight() throws SQLException {
        requireProduct(source, "MySQL");
        requireProduct(target, "PostgreSQL");

        Set<String> sourceTables = JdbcSchemaInspector.tableNames(source);
        Set<String> missingSourceTables = difference(MigrationTablePlan.expectedSourceTables(), sourceTables);
        if (!missingSourceTables.isEmpty()) {
            throw new IllegalStateException("源 MySQL 缺少 V5 迁移所需表：" + missingSourceTables);
        }

        LinkedHashSet<String> expectedTargetTables = MigrationTablePlan.orderedTables().stream()
                .map(MigrationTablePlan::tableName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        expectedTargetTables.add("database_migration_runs");
        expectedTargetTables.add("database_migration_table_results");
        expectedTargetTables.add("legacy_migration_audit_records");
        Set<String> targetTables = JdbcSchemaInspector.tableNames(target);
        Set<String> missingTargetTables = difference(expectedTargetTables, targetTables);
        if (!missingTargetTables.isEmpty()) {
            throw new IllegalStateException("目标 PostgreSQL baseline 不完整，缺少表：" + missingTargetTables);
        }

        Set<String> ignoredSourceTables = difference(sourceTables, MigrationTablePlan.expectedSourceTables()).stream()
                .filter(table -> !table.startsWith("databasechangelog"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!ignoredSourceTables.isEmpty()) {
            throw new IllegalStateException("源 MySQL 存在未纳入迁移计划的业务表，已拒绝静默遗漏：" + ignoredSourceTables);
        }
    }

    private void requireEmptyTargetForNewRun() throws SQLException {
        if (configuration.resumeRunId() != null) {
            return;
        }
        List<String> nonEmptyTables = new ArrayList<>();
        for (MigrationTablePlan table : MigrationTablePlan.orderedTables()) {
            if (countRows(target, table.tableName()) != 0) {
                nonEmptyTables.add(table.tableName());
            }
        }
        if (countRows(target, "legacy_migration_audit_records") != 0) {
            nonEmptyTables.add("legacy_migration_audit_records");
        }
        if (!nonEmptyTables.isEmpty()) {
            throw new IllegalStateException("目标 PostgreSQL 不是空 baseline，已拒绝覆盖：" + nonEmptyTables);
        }
    }

    private void copyTable(UUID runId, MigrationTablePlan table) throws SQLException {
        Optional<MigrationCheckpointRepository.TableCheckpoint> completed =
                checkpoints.completed(runId, table.tableName(), COPY_PHASE);
        if (completed.isPresent()) {
            printSkipped(table.tableName(), COPY_PHASE, completed.orElseThrow());
            return;
        }

        List<JdbcSchemaInspector.Column> targetColumns = JdbcSchemaInspector.columns(target, table.tableName());
        Set<String> sourceColumnNames = JdbcSchemaInspector.columns(source, table.tableName()).stream()
                .map(JdbcSchemaInspector.Column::name)
                .collect(Collectors.toSet());
        List<JdbcSchemaInspector.Column> copiedColumns = targetColumns.stream()
                .filter(column -> !table.deferredColumns().contains(column.name()))
                .toList();
        requireSourceColumns(table.tableName(), sourceColumnNames, copiedColumns);
        requireTargetTableEmpty(table.tableName());

        List<String> primaryKeys = JdbcSchemaInspector.primaryKeyColumns(source, table.tableName());
        String selectSql = "SELECT " + columnList(copiedColumns) + " FROM " + table.tableName()
                + orderBy(primaryKeys);
        String insertSql = "INSERT INTO " + table.tableName() + " (" + columnList(copiedColumns) + ") VALUES ("
                + placeholders(copiedColumns.size()) + ")";

        MessageDigest sourceDigest = JdbcValueCodec.sha256();
        long sourceRows = 0;
        try (PreparedStatement select = source.prepareStatement(
                selectSql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             PreparedStatement insert = target.prepareStatement(insertSql)) {
            select.setFetchSize(configuration.batchSize());
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    sourceRows++;
                    for (int index = 0; index < copiedColumns.size(); index++) {
                        JdbcSchemaInspector.Column column = copiedColumns.get(index);
                        Object value = JdbcValueCodec.convertForTarget(result.getObject(index + 1), column);
                        JdbcValueCodec.updateDigest(sourceDigest, column.name(), value);
                        JdbcValueCodec.bind(insert, index + 1, value, column);
                    }
                    insert.addBatch();
                    if (sourceRows % configuration.batchSize() == 0) {
                        requireSuccessfulBatch(insert.executeBatch(), table.tableName());
                    }
                }
            }
            if (sourceRows % configuration.batchSize() != 0) {
                requireSuccessfulBatch(insert.executeBatch(), table.tableName());
            }
        } catch (SQLException exception) {
            target.rollback();
            throw exception;
        }

        DigestResult targetResult = digestTable(target, table.tableName(), copiedColumns, primaryKeys);
        String sourceHash = JdbcValueCodec.finishDigest(sourceDigest);
        requireEquivalent(table.tableName(), COPY_PHASE, sourceRows, targetResult.rowCount(), sourceHash, targetResult.digest());
        checkpoints.completePhase(
                runId, table.tableName(), COPY_PHASE,
                sourceRows, targetResult.rowCount(), sourceHash, targetResult.digest());
        target.commit();
        printCompleted(table.tableName(), COPY_PHASE, sourceRows, sourceHash);
    }

    private void applyDeferredColumns(UUID runId, MigrationTablePlan table) throws SQLException {
        if (table.deferredColumns().isEmpty()) {
            return;
        }
        Optional<MigrationCheckpointRepository.TableCheckpoint> completed =
                checkpoints.completed(runId, table.tableName(), DEFERRED_PHASE);
        if (completed.isPresent()) {
            printSkipped(table.tableName(), DEFERRED_PHASE, completed.orElseThrow());
            return;
        }

        Map<String, JdbcSchemaInspector.Column> targetColumns = JdbcSchemaInspector.columns(target, table.tableName()).stream()
                .collect(Collectors.toMap(JdbcSchemaInspector.Column::name, column -> column));
        List<String> primaryKeys = JdbcSchemaInspector.primaryKeyColumns(source, table.tableName());
        if (primaryKeys.isEmpty()) {
            throw new IllegalStateException("延迟更新表缺少主键：" + table.tableName());
        }
        List<String> selectedNames = new ArrayList<>(primaryKeys);
        selectedNames.addAll(table.deferredColumns());
        String selectSql = "SELECT " + String.join(", ", selectedNames) + " FROM " + table.tableName()
                + orderBy(primaryKeys);
        String assignments = table.deferredColumns().stream()
                .map(column -> column + " = ?")
                .collect(Collectors.joining(", "));
        String predicates = primaryKeys.stream()
                .map(column -> column + " = ?")
                .collect(Collectors.joining(" AND "));
        String updateSql = "UPDATE " + table.tableName() + " SET " + assignments + " WHERE " + predicates;

        long sourceRows = 0;
        MessageDigest digest = JdbcValueCodec.sha256();
        try (PreparedStatement select = source.prepareStatement(
                selectSql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             PreparedStatement update = target.prepareStatement(updateSql)) {
            select.setFetchSize(configuration.batchSize());
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    sourceRows++;
                    int parameterIndex = 1;
                    int resultIndex = primaryKeys.size() + 1;
                    for (String columnName : table.deferredColumns()) {
                        JdbcSchemaInspector.Column column = requireColumn(targetColumns, columnName, table.tableName());
                        Object value = JdbcValueCodec.convertForTarget(result.getObject(resultIndex++), column);
                        JdbcValueCodec.updateDigest(digest, columnName, value);
                        JdbcValueCodec.bind(update, parameterIndex++, value, column);
                    }
                    resultIndex = 1;
                    for (String primaryKey : primaryKeys) {
                        JdbcSchemaInspector.Column column = requireColumn(targetColumns, primaryKey, table.tableName());
                        Object value = JdbcValueCodec.convertForTarget(result.getObject(resultIndex++), column);
                        JdbcValueCodec.bind(update, parameterIndex++, value, column);
                    }
                    update.addBatch();
                    if (sourceRows % configuration.batchSize() == 0) {
                        requireSuccessfulBatch(update.executeBatch(), table.tableName());
                    }
                }
            }
            if (sourceRows % configuration.batchSize() != 0) {
                requireSuccessfulBatch(update.executeBatch(), table.tableName());
            }
        } catch (SQLException exception) {
            target.rollback();
            throw exception;
        }
        String hash = JdbcValueCodec.finishDigest(digest);
        checkpoints.completePhase(runId, table.tableName(), DEFERRED_PHASE, sourceRows, sourceRows, hash, hash);
        target.commit();
        printCompleted(table.tableName(), DEFERRED_PHASE, sourceRows, hash);
    }

    private void validateTable(UUID runId, MigrationTablePlan table) throws SQLException {
        Optional<MigrationCheckpointRepository.TableCheckpoint> completed =
                checkpoints.completed(runId, table.tableName(), VALIDATE_PHASE);
        if (completed.isPresent()) {
            printSkipped(table.tableName(), VALIDATE_PHASE, completed.orElseThrow());
            return;
        }
        List<JdbcSchemaInspector.Column> targetColumns = JdbcSchemaInspector.columns(target, table.tableName());
        List<String> primaryKeys = JdbcSchemaInspector.primaryKeyColumns(source, table.tableName());
        DigestResult sourceResult = digestSourceTable(table.tableName(), targetColumns, primaryKeys);
        DigestResult targetResult = digestTable(target, table.tableName(), targetColumns, primaryKeys);
        requireEquivalent(
                table.tableName(), VALIDATE_PHASE,
                sourceResult.rowCount(), targetResult.rowCount(), sourceResult.digest(), targetResult.digest());
        checkpoints.completePhase(
                runId, table.tableName(), VALIDATE_PHASE,
                sourceResult.rowCount(), targetResult.rowCount(), sourceResult.digest(), targetResult.digest());
        target.commit();
        printCompleted(table.tableName(), VALIDATE_PHASE, sourceResult.rowCount(), sourceResult.digest());
    }

    private void archiveLegacyAuditTables(UUID runId) throws SQLException, JsonProcessingException {
        for (String tableName : MigrationTablePlan.auditSourceTables()) {
            Optional<MigrationCheckpointRepository.TableCheckpoint> completed =
                    checkpoints.completed(runId, tableName, ARCHIVE_PHASE);
            if (completed.isPresent()) {
                printSkipped(tableName, ARCHIVE_PHASE, completed.orElseThrow());
                continue;
            }
            archiveLegacyAuditTable(runId, tableName);
        }
    }

    private void archiveLegacyAuditTable(UUID runId, String tableName) throws SQLException, JsonProcessingException {
        JdbcSchemaInspector.requireSafeIdentifier(tableName);
        List<JdbcSchemaInspector.Column> columns = JdbcSchemaInspector.columns(source, tableName);
        List<String> primaryKeys = JdbcSchemaInspector.primaryKeyColumns(source, tableName);
        String query = "SELECT " + columnList(columns) + " FROM " + tableName + orderBy(primaryKeys);
        String insertSql = """
                INSERT INTO legacy_migration_audit_records (
                    id, tenant_id, source_table, source_key, payload_json, archived_at
                ) VALUES (uuidv7(), ?, ?, ?, ?::jsonb, ?)
                """;
        long rowCount = 0;
        MessageDigest digest = JdbcValueCodec.sha256();
        try (PreparedStatement select = source.prepareStatement(
                query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             PreparedStatement insert = target.prepareStatement(insertSql)) {
            select.setFetchSize(configuration.batchSize());
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    rowCount++;
                    LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
                    for (int index = 0; index < columns.size(); index++) {
                        JdbcSchemaInspector.Column column = columns.get(index);
                        Object value = normalizeAuditValue(result.getObject(index + 1));
                        payload.put(column.name(), value);
                        JdbcValueCodec.updateDigest(digest, column.name(), value);
                    }
                    String sourceKey = sourceKey(payload, primaryKeys);
                    insert.setObject(1, nullableTenantId(payload.get("tenant_id")));
                    insert.setString(2, tableName);
                    insert.setString(3, sourceKey);
                    insert.setString(4, objectMapper.writeValueAsString(payload));
                    insert.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)));
                    insert.addBatch();
                    if (rowCount % configuration.batchSize() == 0) {
                        requireSuccessfulBatch(insert.executeBatch(), tableName);
                    }
                }
            }
            if (rowCount % configuration.batchSize() != 0) {
                requireSuccessfulBatch(insert.executeBatch(), tableName);
            }
        } catch (SQLException | JsonProcessingException exception) {
            target.rollback();
            throw exception;
        }
        String hash = JdbcValueCodec.finishDigest(digest);
        long targetRows = countRows(target, "legacy_migration_audit_records", "source_table", tableName);
        if (rowCount != targetRows) {
            target.rollback();
            throw new IllegalStateException("历史迁移审计归档数量不一致：" + tableName
                    + " source=" + rowCount + " target=" + targetRows);
        }
        checkpoints.completePhase(runId, tableName, ARCHIVE_PHASE, rowCount, targetRows, hash, hash);
        target.commit();
        printCompleted(tableName, ARCHIVE_PHASE, rowCount, hash);
    }

    private DigestResult digestSourceTable(
            String tableName,
            List<JdbcSchemaInspector.Column> targetColumns,
            List<String> primaryKeys) throws SQLException {
        String query = "SELECT " + columnList(targetColumns) + " FROM " + tableName + orderBy(primaryKeys);
        MessageDigest digest = JdbcValueCodec.sha256();
        long rows = 0;
        try (PreparedStatement statement = source.prepareStatement(
                query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            statement.setFetchSize(configuration.batchSize());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows++;
                    for (int index = 0; index < targetColumns.size(); index++) {
                        JdbcSchemaInspector.Column column = targetColumns.get(index);
                        Object value = JdbcValueCodec.convertForTarget(result.getObject(index + 1), column);
                        JdbcValueCodec.updateDigest(digest, column.name(), value);
                    }
                }
            }
        }
        return new DigestResult(rows, JdbcValueCodec.finishDigest(digest));
    }

    private DigestResult digestTable(
            Connection connection,
            String tableName,
            List<JdbcSchemaInspector.Column> columns,
            List<String> primaryKeys) throws SQLException {
        String query = "SELECT " + columnList(columns) + " FROM " + tableName + orderBy(primaryKeys);
        MessageDigest digest = JdbcValueCodec.sha256();
        long rows = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            statement.setFetchSize(configuration.batchSize());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows++;
                    for (int index = 0; index < columns.size(); index++) {
                        JdbcSchemaInspector.Column column = columns.get(index);
                        JdbcValueCodec.updateDigest(digest, column.name(), result.getObject(index + 1));
                    }
                }
            }
        }
        return new DigestResult(rows, JdbcValueCodec.finishDigest(digest));
    }

    private void requireTargetTableEmpty(String tableName) throws SQLException {
        long existingRows = countRows(target, tableName);
        if (existingRows != 0) {
            throw new IllegalStateException("目标表存在数据但没有完成检查点，已拒绝覆盖："
                    + tableName + " rows=" + existingRows);
        }
    }

    private long countRows(Connection connection, String tableName) throws SQLException {
        JdbcSchemaInspector.requireSafeIdentifier(tableName);
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            result.next();
            return result.getLong(1);
        }
    }

    private long countRows(Connection connection, String tableName, String columnName, String value) throws SQLException {
        JdbcSchemaInspector.requireSafeIdentifier(tableName);
        JdbcSchemaInspector.requireSafeIdentifier(columnName);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + tableName + " WHERE " + columnName + " = ?")) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private String columnList(List<JdbcSchemaInspector.Column> columns) {
        return columns.stream().map(JdbcSchemaInspector.Column::name).collect(Collectors.joining(", "));
    }

    private String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private String orderBy(List<String> primaryKeys) {
        return primaryKeys.isEmpty() ? "" : " ORDER BY " + String.join(", ", primaryKeys);
    }

    private void requireSourceColumns(
            String tableName,
            Set<String> sourceColumns,
            List<JdbcSchemaInspector.Column> targetColumns) {
        Set<String> missing = targetColumns.stream()
                .map(JdbcSchemaInspector.Column::name)
                .filter(column -> !sourceColumns.contains(column))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!missing.isEmpty()) {
            throw new IllegalStateException("源表缺少目标列：" + tableName + " " + missing);
        }
    }

    private void requireSuccessfulBatch(int[] results, String tableName) {
        if (Arrays.stream(results).anyMatch(result -> result == Statement.EXECUTE_FAILED)) {
            throw new IllegalStateException("批量写入失败：" + tableName);
        }
    }

    private JdbcSchemaInspector.Column requireColumn(
            Map<String, JdbcSchemaInspector.Column> columns,
            String name,
            String tableName) {
        JdbcSchemaInspector.Column column = columns.get(name);
        if (column == null) {
            throw new IllegalStateException("目标表缺少列：" + tableName + "." + name);
        }
        return column;
    }

    private void requireEquivalent(
            String tableName,
            String phase,
            long sourceRows,
            long targetRows,
            String sourceDigest,
            String targetDigest) {
        if (sourceRows != targetRows || !sourceDigest.equals(targetDigest)) {
            targetRollbackQuietly();
            throw new IllegalStateException("迁移校验失败：" + tableName + " phase=" + phase
                    + " sourceRows=" + sourceRows + " targetRows=" + targetRows
                    + " sourceDigest=" + sourceDigest + " targetDigest=" + targetDigest);
        }
    }

    private void requireProduct(Connection connection, String expectedProduct) throws SQLException {
        String actual = connection.getMetaData().getDatabaseProductName();
        if (!actual.toLowerCase(Locale.ROOT).contains(expectedProduct.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("数据库类型不匹配，预期 " + expectedProduct + "，实际 " + actual + "。 ");
        }
    }

    private Set<String> difference(Set<String> expected, Set<String> actual) {
        LinkedHashSet<String> result = new LinkedHashSet<>(expected);
        result.removeAll(actual);
        return result;
    }

    private Object normalizeAuditValue(Object value) {
        if (value instanceof byte[] bytes) {
            return bytes.length == 16
                    ? JdbcValueCodec.toUuid(bytes).toString()
                    : java.util.HexFormat.of().formatHex(bytes);
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        return value;
    }

    private String sourceKey(Map<String, Object> payload, List<String> primaryKeys) {
        MessageDigest digest = JdbcValueCodec.sha256();
        List<String> keyColumns = primaryKeys.isEmpty() ? new ArrayList<>(payload.keySet()) : primaryKeys;
        keyColumns.forEach(column -> JdbcValueCodec.updateDigest(digest, column, payload.get(column)));
        return JdbcValueCodec.finishDigest(digest);
    }

    private UUID nullableTenantId(Object value) {
        return value == null ? null : JdbcValueCodec.toUuid(value);
    }

    private void printCompleted(String tableName, String phase, long rows, String digest) {
        System.out.println("MIGRATION_PHASE status=COMPLETED table=" + tableName
                + " phase=" + phase + " rows=" + rows + " digest=" + digest);
    }

    private void printSkipped(
            String tableName,
            String phase,
            MigrationCheckpointRepository.TableCheckpoint checkpoint) {
        System.out.println("MIGRATION_PHASE status=SKIPPED table=" + tableName
                + " phase=" + phase + " rows=" + checkpoint.sourceRowCount()
                + " digest=" + checkpoint.sourceDigest());
    }

    private void targetRollbackQuietly() {
        try {
            target.rollback();
        } catch (SQLException ignored) {
            // The original validation failure remains the primary error.
        }
    }

    private record DigestResult(long rowCount, String digest) {
    }
}
