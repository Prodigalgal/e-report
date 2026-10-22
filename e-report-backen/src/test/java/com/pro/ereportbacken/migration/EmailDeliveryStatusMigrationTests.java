package com.pro.ereportbacken.migration;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.core.H2Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EmailDeliveryStatusMigrationTests {
    private static final String CHANGELOG = "db/changelog/changes/2026-10-22-078-email-delivery-status.sql";

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void normalizesUnsuccessfulAttemptsAndRejectsConcurrentWritesOfRemovedStatus(String mode) throws Exception {
        String url = "jdbc:h2:mem:email_status_" + UUID.randomUUID() + ";MODE=" + mode;
        var database = new H2Database();
        try (var connection = DriverManager.getConnection(url);
             var resources = new ClassLoaderResourceAccessor()) {
            database.setConnection(new JdbcConnection(connection));
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE email_deliveries (id INT PRIMARY KEY, status VARCHAR(24) NOT NULL, "
                        + "failure_code VARCHAR(40), started_at TIMESTAMP, completed_at TIMESTAMP, duration_ms BIGINT)");
                statement.execute("INSERT INTO email_deliveries VALUES "
                        + "(1, 'UNKNOWN', 'TIMEOUT', TIMESTAMP '2026-10-22 01:00:00', TIMESTAMP '2026-10-22 01:00:10', 10000),"
                        + "(2, 'UNKNOWN', NULL, TIMESTAMP '2026-10-22 01:00:00', NULL, NULL),"
                        + "(3, 'SMTP_ACCEPTED', NULL, TIMESTAMP '2026-10-22 01:00:00', TIMESTAMP '2026-10-22 01:00:01', 1000),"
                        + "(4, 'FAILED', 'AUTHENTICATION', TIMESTAMP '2026-10-22 01:00:00', NULL, NULL),"
                        + "(5, 'SENDING', NULL, TIMESTAMP '2026-10-22 01:00:00', NULL, NULL)");
            }
            try (var liquibase = new Liquibase(CHANGELOG, resources, database)) {
                liquibase.update(new Contexts(), new LabelExpression());
                liquibase.update(new Contexts(), new LabelExpression());
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("SELECT * FROM email_deliveries ORDER BY id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("status")).isEqualTo("FAILED");
                    assertThat(rows.getString("failure_code")).isEqualTo("TIMEOUT");
                    assertThat(rows.getTimestamp("started_at")).isEqualTo(Timestamp.valueOf("2026-10-22 01:00:00"));
                    assertThat(rows.getTimestamp("completed_at")).isEqualTo(Timestamp.valueOf("2026-10-22 01:00:10"));
                    assertThat(rows.getLong("duration_ms")).isEqualTo(10000);
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("status")).isEqualTo("FAILED");
                    assertThat(rows.getString("failure_code")).isEqualTo("INCOMPLETE");
                    assertThat(rows.getTimestamp("completed_at")).isNull();
                    assertThat(rows.getObject("duration_ms")).isNull();
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("status")).isEqualTo("SMTP_ACCEPTED");
                    assertThat(rows.getString("failure_code")).isNull();
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("status")).isEqualTo("FAILED");
                    assertThat(rows.getString("failure_code")).isEqualTo("AUTHENTICATION");
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("status")).isEqualTo("SENDING");
                    assertThat(rows.next()).isFalse();
                }
                try (var statement = connection.createStatement()) {
                    assertThatThrownBy(() -> statement.executeUpdate("UPDATE email_deliveries SET status = 'UNKNOWN' WHERE id = 5"))
                            .isInstanceOf(SQLException.class);
                    assertThat(statement.executeUpdate("UPDATE email_deliveries SET status = 'SMTP_ACCEPTED' WHERE id = 5")).isEqualTo(1);
                }
                liquibase.rollback(1, new Contexts(), new LabelExpression());
                try (var statement = connection.createStatement()) {
                    assertThat(statement.executeUpdate("UPDATE email_deliveries SET status = 'UNKNOWN' WHERE id = 5")).isEqualTo(1);
                    try (var rows = statement.executeQuery("SELECT COUNT(*) FROM email_deliveries WHERE status = 'FAILED'")) {
                        rows.next();
                        assertThat(rows.getInt(1)).isEqualTo(3);
                    }
                }
            }
        }
    }
}
