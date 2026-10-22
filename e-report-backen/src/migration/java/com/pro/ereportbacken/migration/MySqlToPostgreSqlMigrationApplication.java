package com.pro.ereportbacken.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

public final class MySqlToPostgreSqlMigrationApplication {

    private MySqlToPostgreSqlMigrationApplication() {
    }

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        Class.forName("org.postgresql.Driver");
        MigrationConfiguration configuration = MigrationConfiguration.fromEnvironment(System.getenv());
        try (Connection source = DriverManager.getConnection(
                configuration.sourceUrl(), configuration.sourceUsername(), configuration.sourcePassword());
             Connection target = DriverManager.getConnection(
                     configuration.targetUrl(), configuration.targetUsername(), configuration.targetPassword())) {
            UUID runId = new MySqlToPostgreSqlMigrationService(source, target, configuration).migrate();
            System.out.println("MIGRATION_RUN status=COMPLETED runId=" + runId);
        }
    }
}
