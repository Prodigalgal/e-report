package com.pro.ereportbacken;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:postgresql_schema_generation;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=create",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=build/generated/postgresql-entity-schema.sql",
        "spring.jpa.properties.jakarta.persistence.schema-generation.create-source=metadata",
        "app.data-initializer.enabled=false",
        "app.system-authorization-baseline.enabled=false",
        "app.auth-session.retention-enabled=false",
        "app.user-transfer.recovery-initial-delay-ms=3600000",
        "app.user-transfer.retention-initial-delay-ms=3600000",
        "app.user-search-key-backfill.initial-delay-ms=3600000",
        "spring.cache.type=simple"
})
class PostgreSqlSchemaGenerationTests {

    static {
        try {
            java.nio.file.Files.deleteIfExists(
                    java.nio.file.Path.of("build/generated/postgresql-entity-schema.sql"));
        } catch (java.io.IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Test
    void exportsPostgreSqlEntitySchema() {
        assertThat(java.nio.file.Path.of("build/generated/postgresql-entity-schema.sql"))
                .exists()
                .isNotEmptyFile();
    }
}
