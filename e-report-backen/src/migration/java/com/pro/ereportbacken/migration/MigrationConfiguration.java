package com.pro.ereportbacken.migration;

import java.util.Map;
import java.util.UUID;

record MigrationConfiguration(
        String sourceUrl,
        String sourceUsername,
        String sourcePassword,
        String targetUrl,
        String targetUsername,
        String targetPassword,
        int batchSize,
        UUID resumeRunId) {

    static MigrationConfiguration fromEnvironment(Map<String, String> environment) {
        String sourceUrl = require(environment, "MIGRATION_SOURCE_URL");
        String targetUrl = require(environment, "MIGRATION_TARGET_URL");
        if (!sourceUrl.startsWith("jdbc:mysql:")) {
            throw new IllegalArgumentException("MIGRATION_SOURCE_URL 必须使用 jdbc:mysql:。 ");
        }
        if (!sourceUrl.contains("serverTimezone=UTC") && !sourceUrl.contains("connectionTimeZone=UTC")) {
            throw new IllegalArgumentException("MIGRATION_SOURCE_URL 必须显式使用 UTC 时区。 ");
        }
        if (!targetUrl.startsWith("jdbc:postgresql:")) {
            throw new IllegalArgumentException("MIGRATION_TARGET_URL 必须使用 jdbc:postgresql:。 ");
        }
        int batchSize = parseBatchSize(environment.getOrDefault("MIGRATION_BATCH_SIZE", "500"));
        String resumeRunId = environment.getOrDefault("MIGRATION_RESUME_RUN_ID", "").trim();
        return new MigrationConfiguration(
                sourceUrl,
                require(environment, "MIGRATION_SOURCE_USERNAME"),
                require(environment, "MIGRATION_SOURCE_PASSWORD"),
                targetUrl,
                require(environment, "MIGRATION_TARGET_USERNAME"),
                require(environment, "MIGRATION_TARGET_PASSWORD"),
                batchSize,
                resumeRunId.isEmpty() ? null : UUID.fromString(resumeRunId));
    }

    private static String require(Map<String, String> environment, String name) {
        String value = environment.getOrDefault(name, "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " 不允许为空。 ");
        }
        return value;
    }

    private static int parseBatchSize(String value) {
        int parsed = Integer.parseInt(value);
        if (parsed < 1 || parsed > 5_000) {
            throw new IllegalArgumentException("MIGRATION_BATCH_SIZE 必须在 1 到 5000 之间。 ");
        }
        return parsed;
    }
}
