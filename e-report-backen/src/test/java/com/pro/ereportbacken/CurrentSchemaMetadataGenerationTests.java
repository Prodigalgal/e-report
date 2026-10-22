package com.pro.ereportbacken;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:current_schema_metadata;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.data-initializer.enabled=false",
        "app.system-authorization-baseline.enabled=false",
        "app.auth-session.retention-enabled=false",
        "app.user-transfer.recovery-initial-delay-ms=3600000",
        "app.user-transfer.retention-initial-delay-ms=3600000",
        "app.user-search-key-backfill.initial-delay-ms=3600000",
        "spring.cache.type=simple"
})
@ActiveProfiles("test")
class CurrentSchemaMetadataGenerationTests {

    private final DataSource dataSource;

    @Autowired
    CurrentSchemaMetadataGenerationTests(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Test
    void exportsCurrentLiquibaseForeignKeysForPostgreSqlBaselineComposition() throws Exception {
        List<ForeignKeyRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet tables = metadata.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String table = tables.getString("TABLE_NAME");
                    if (table.startsWith("databasechangelog")) continue;
                    try (ResultSet keys = metadata.getImportedKeys(null, null, table)) {
                        while (keys.next()) {
                            rows.add(new ForeignKeyRow(
                                    table,
                                    keys.getString("FK_NAME"),
                                    keys.getString("FKCOLUMN_NAME"),
                                    keys.getString("PKTABLE_NAME"),
                                    keys.getString("PKCOLUMN_NAME"),
                                    keys.getShort("KEY_SEQ"),
                                    keys.getShort("DELETE_RULE")));
                        }
                    }
                }
            }
        }
        rows = new ArrayList<>(new java.util.LinkedHashSet<>(rows));
        rows.sort(Comparator.comparing(ForeignKeyRow::table)
                .thenComparing(ForeignKeyRow::name)
                .thenComparingInt(ForeignKeyRow::sequence));
        Path output = Path.of("build/generated/current-liquibase-schema-metadata.txt");
        Files.createDirectories(output.getParent());
        Files.write(output, rows.stream().map(ForeignKeyRow::serialize).toList(), StandardCharsets.UTF_8);
        assertThat(output).exists().isNotEmptyFile();
        assertThat(rows).anyMatch(row -> row.table().equals("departments"));
        assertThat(rows).anyMatch(row -> row.table().equals("work_order_organization_transfers"));
        assertThat(rows).anyMatch(row -> row.table().equals("user_transfer_job_rows")
                && row.deleteRule() == DatabaseMetaData.importedKeyCascade);
        assertThat(rows).noneMatch(row -> row.table().equals("authorization_assignments"));
    }

    private record ForeignKeyRow(
            String table,
            String name,
            String column,
            String referencedTable,
            String referencedColumn,
            short sequence,
            short deleteRule) {
        String serialize() {
            return String.join("|", "FOREIGN_KEY", table, name, column, referencedTable, referencedColumn,
                    Short.toString(sequence), deleteAction(deleteRule));
        }

        private String deleteAction(short rule) {
            return switch (rule) {
                case DatabaseMetaData.importedKeyCascade -> "CASCADE";
                case DatabaseMetaData.importedKeySetNull -> "SET NULL";
                case DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT";
                case DatabaseMetaData.importedKeyRestrict -> "RESTRICT";
                default -> "NO ACTION";
            };
        }
    }
}
