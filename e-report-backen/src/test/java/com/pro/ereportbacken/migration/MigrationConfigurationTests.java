package com.pro.ereportbacken.migration;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MigrationConfigurationTests {

    @Test
    void requiresExplicitDatabaseTypesAndUtcSourceTimeZone() {
        Map<String, String> environment = validEnvironment();
        environment.put("MIGRATION_SOURCE_URL", "jdbc:mysql://source/fault_reporter_db");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> MigrationConfiguration.fromEnvironment(environment))
                .withMessageContaining("UTC");
    }

    @Test
    void parsesResumeRunAndBoundedBatchSize() {
        UUID runId = UUID.fromString("019d0000-0000-7000-8000-000000000001");
        Map<String, String> environment = validEnvironment();
        environment.put("MIGRATION_BATCH_SIZE", "800");
        environment.put("MIGRATION_RESUME_RUN_ID", runId.toString());

        MigrationConfiguration configuration = MigrationConfiguration.fromEnvironment(environment);

        assertThat(configuration.batchSize()).isEqualTo(800);
        assertThat(configuration.resumeRunId()).isEqualTo(runId);
    }

    private Map<String, String> validEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("MIGRATION_SOURCE_URL", "jdbc:mysql://source/fault_reporter_db?serverTimezone=UTC");
        environment.put("MIGRATION_SOURCE_USERNAME", "source");
        environment.put("MIGRATION_SOURCE_PASSWORD", "source-password");
        environment.put("MIGRATION_TARGET_URL", "jdbc:postgresql://target/fault_reporter_db");
        environment.put("MIGRATION_TARGET_USERNAME", "target");
        environment.put("MIGRATION_TARGET_PASSWORD", "target-password");
        return environment;
    }
}
