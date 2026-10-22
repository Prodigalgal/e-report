package com.pro.ereportbacken.migration;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LiquibaseMigrationMain {

    private static final String DEFAULT_CHANGELOG = "db/changelog/db.changelog-master.yaml";
    private static final Map<String, String> CHANGELOG_PARAMETERS = Map.of(
            "defaultTenantGroupCode", "DEFAULT_TENANT_GROUP_CODE",
            "defaultTenantGroupName", "DEFAULT_TENANT_GROUP_NAME",
            "defaultTenantCode", "DEFAULT_TENANT_CODE",
            "defaultTenantName", "DEFAULT_TENANT_NAME",
            "defaultTenantRootOrgName", "DEFAULT_TENANT_ROOT_ORG_NAME",
            "defaultAdminUsername", "DEFAULT_ADMIN_USERNAME"
    );
    private static final Map<String, String> COUNTY_NORMALIZATION_PARAMETERS = Map.of(
            "countyAdministratorUsernameA", "COUNTY_ADMINISTRATOR_USERNAME_A",
            "countyAdministratorNameA", "COUNTY_ADMINISTRATOR_NAME_A",
            "countyAdministratorUsernameB", "COUNTY_ADMINISTRATOR_USERNAME_B",
            "countyAdministratorNameB", "COUNTY_ADMINISTRATOR_NAME_B",
            "countyAdministratorUsernameC", "COUNTY_ADMINISTRATOR_USERNAME_C",
            "countyAdministratorNameC", "COUNTY_ADMINISTRATOR_NAME_C"
    );

    private LiquibaseMigrationMain() {
    }

    public static void main(String[] args) throws Exception {
        MigrationConfiguration configuration = MigrationConfiguration.fromEnvironment(System.getenv());
        loadSupportedJdbcDriver(configuration.url());
        System.out.printf(
                "Starting Liquibase migration: changelog=%s, tenantCode=%s%n",
                configuration.changeLog(),
                configuration.parameters().get("defaultTenantCode")
        );

        try (Connection connection = DriverManager.getConnection(
                configuration.url(),
                configuration.username(),
                configuration.password()
        )) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor(
                    LiquibaseMigrationMain.class.getClassLoader());
                 Liquibase liquibase = new Liquibase(configuration.changeLog(), resourceAccessor, database)) {
                configuration.parameters().forEach(liquibase::setChangeLogParameter);
                liquibase.validate();
                liquibase.update(new Contexts(), new LabelExpression());
                if (hasText(configuration.tag())) {
                    liquibase.tag(configuration.tag());
                }
            }
        }

        System.out.printf("Liquibase migration completed: tag=%s%n", configuration.tag());
    }

    static void loadSupportedJdbcDriver(String url) throws ClassNotFoundException {
        if (url.startsWith("jdbc:mysql:")) {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return;
        }
        if (url.startsWith("jdbc:postgresql:")) {
            Class.forName("org.postgresql.Driver");
            return;
        }
        throw new IllegalArgumentException("Unsupported database migration JDBC URL: " + url);
    }

    record MigrationConfiguration(String url,
                                  String username,
                                  String password,
                                  String changeLog,
                                  String tag,
                                  Map<String, String> parameters) {

        static MigrationConfiguration fromEnvironment(Map<String, String> environment) {
            String changeLog = optional(environment, "E_REPORT_MIGRATION_CHANGELOG", DEFAULT_CHANGELOG);
            Map<String, String> parameters = new LinkedHashMap<>();
            CHANGELOG_PARAMETERS.forEach((parameter, environmentName) -> {
                String value = environment.get(environmentName);
                if (DEFAULT_CHANGELOG.equals(changeLog)) {
                    parameters.put(parameter, require(environment, environmentName));
                } else if (hasText(value)) {
                    parameters.put(parameter, value.trim());
                }
            });
            if (DEFAULT_CHANGELOG.equals(changeLog)) {
                long supplied = COUNTY_NORMALIZATION_PARAMETERS.values().stream()
                        .filter(name -> hasText(environment.get(name))).count();
                if (supplied != 0 && supplied != COUNTY_NORMALIZATION_PARAMETERS.size()) {
                    throw new IllegalStateException("County administrator migration requires all six identity parameters.");
                }
                if (supplied != 0) {
                    COUNTY_NORMALIZATION_PARAMETERS.forEach((parameter, environmentName) -> {
                        String value = environment.get(environmentName).trim();
                        if (value.length() > 100 || value.chars().anyMatch(character ->
                                character == '\'' || character == '\\' || Character.isISOControl(character))) {
                            throw new IllegalArgumentException(environmentName + " contains an invalid migration identity.");
                        }
                        parameters.put(parameter, value);
                    });
                }
            }
            return new MigrationConfiguration(
                    require(environment, "E_REPORT_MIGRATION_URL"),
                    require(environment, "E_REPORT_MIGRATION_USERNAME"),
                    requireRaw(environment, "E_REPORT_MIGRATION_PASSWORD"),
                    changeLog,
                    optional(environment, "E_REPORT_MIGRATION_TAG", ""),
                    Map.copyOf(parameters)
            );
        }

        private static String require(Map<String, String> environment, String name) {
            String value = environment.get(name);
            if (!hasText(value)) {
                throw new IllegalStateException(name + " must be configured for database migration.");
            }
            return value.trim();
        }

        private static String requireRaw(Map<String, String> environment, String name) {
            String value = environment.get(name);
            if (!hasText(value)) {
                throw new IllegalStateException(name + " must be configured for database migration.");
            }
            return value;
        }

        private static String optional(Map<String, String> environment, String name, String defaultValue) {
            String value = environment.get(name);
            return hasText(value) ? value.trim() : defaultValue;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
