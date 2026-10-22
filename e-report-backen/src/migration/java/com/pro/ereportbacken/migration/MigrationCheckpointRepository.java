package com.pro.ereportbacken.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

final class MigrationCheckpointRepository {

    private final Connection target;

    MigrationCheckpointRepository(Connection target) {
        this.target = target;
    }

    UUID startOrResume(MigrationConfiguration configuration) throws SQLException {
        if (configuration.resumeRunId() != null) {
            requireResumable(configuration.resumeRunId());
            return configuration.resumeRunId();
        }
        UUID runId;
        try (PreparedStatement statement = target.prepareStatement("SELECT uuidv7()")) {
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                runId = result.getObject(1, UUID.class);
            }
        }
        try (PreparedStatement statement = target.prepareStatement("""
                INSERT INTO database_migration_runs (
                    id, status, source_database, target_database, started_at
                ) VALUES (?, 'RUNNING', ?, ?, ?)
                """)) {
            statement.setObject(1, runId);
            statement.setString(2, sanitizeJdbcUrl(configuration.sourceUrl()));
            statement.setString(3, sanitizeJdbcUrl(configuration.targetUrl()));
            statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)));
            statement.executeUpdate();
        }
        target.commit();
        return runId;
    }

    Optional<TableCheckpoint> completed(UUID runId, String tableName, String phase) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement("""
                SELECT source_row_count, target_row_count, source_digest, target_digest
                FROM database_migration_table_results
                WHERE run_id = ? AND table_name = ? AND phase = ? AND status = 'COMPLETED'
                """)) {
            statement.setObject(1, runId);
            statement.setString(2, tableName);
            statement.setString(3, phase);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new TableCheckpoint(
                        result.getLong("source_row_count"),
                        result.getLong("target_row_count"),
                        result.getString("source_digest"),
                        result.getString("target_digest")));
            }
        }
    }

    void completePhase(
            UUID runId,
            String tableName,
            String phase,
            long sourceRowCount,
            long targetRowCount,
            String sourceDigest,
            String targetDigest) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement("""
                INSERT INTO database_migration_table_results (
                    id, run_id, table_name, phase, status,
                    source_row_count, target_row_count, source_digest, target_digest, completed_at
                ) VALUES (uuidv7(), ?, ?, ?, 'COMPLETED', ?, ?, ?, ?, ?)
                ON CONFLICT (run_id, table_name, phase) DO UPDATE SET
                    status = EXCLUDED.status,
                    source_row_count = EXCLUDED.source_row_count,
                    target_row_count = EXCLUDED.target_row_count,
                    source_digest = EXCLUDED.source_digest,
                    target_digest = EXCLUDED.target_digest,
                    completed_at = EXCLUDED.completed_at,
                    error_summary = NULL
                """)) {
            statement.setObject(1, runId);
            statement.setString(2, tableName);
            statement.setString(3, phase);
            statement.setLong(4, sourceRowCount);
            statement.setLong(5, targetRowCount);
            statement.setString(6, sourceDigest);
            statement.setString(7, targetDigest);
            statement.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)));
            statement.executeUpdate();
        }
    }

    void markRunCompleted(UUID runId) throws SQLException {
        updateRun(runId, "COMPLETED", null);
    }

    void markRunFailed(UUID runId, Exception exception) {
        try {
            target.rollback();
            String message = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            updateRun(runId, "FAILED", truncate(message, 2_000));
            target.commit();
        } catch (SQLException checkpointException) {
            exception.addSuppressed(checkpointException);
        }
    }

    private void requireResumable(UUID runId) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement(
                "SELECT status FROM database_migration_runs WHERE id = ?")) {
            statement.setObject(1, runId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("指定的迁移运行记录不存在：" + runId);
                }
                String status = result.getString(1);
                if (!"RUNNING".equals(status) && !"FAILED".equals(status)) {
                    throw new IllegalStateException("只有 RUNNING 或 FAILED 的迁移可以继续，当前状态为 " + status + "。 ");
                }
            }
        }
        try (PreparedStatement statement = target.prepareStatement(
                "UPDATE database_migration_runs SET status = 'RUNNING', error_summary = NULL WHERE id = ?")) {
            statement.setObject(1, runId);
            statement.executeUpdate();
        }
        target.commit();
    }

    private void updateRun(UUID runId, String status, String errorSummary) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement("""
                UPDATE database_migration_runs
                SET status = ?, completed_at = ?, error_summary = ?
                WHERE id = ?
                """)) {
            statement.setString(1, status);
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)));
            statement.setString(3, errorSummary);
            statement.setObject(4, runId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("迁移运行记录不存在：" + runId);
            }
        }
    }

    private String sanitizeJdbcUrl(String jdbcUrl) {
        int queryIndex = jdbcUrl.indexOf('?');
        return queryIndex < 0 ? jdbcUrl : jdbcUrl.substring(0, queryIndex);
    }

    private String truncate(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    record TableCheckpoint(
            long sourceRowCount,
            long targetRowCount,
            String sourceDigest,
            String targetDigest) {
    }
}
