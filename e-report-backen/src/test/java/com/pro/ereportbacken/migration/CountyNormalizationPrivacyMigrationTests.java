package com.pro.ereportbacken.migration;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.change.CheckSum;
import liquibase.database.core.H2Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CountyNormalizationPrivacyMigrationTests {

    private static final String CHANGELOG =
            "db/changelog/changes/2026-08-13-032-county-administrator-normalization.sql";
    private static final String H2_CHANGESET = "2026-08-13-032-county-administrator-normalization-h2";
    private static final String H2_CHECKSUM = "9:3929d7bce18d66bf6e28c868a56d3e40";
    private static final Map<String, String> IDENTITIES = Map.of(
            "countyAdministratorUsernameA", "county_admin_a", "countyAdministratorNameA", "示例管理员甲",
            "countyAdministratorUsernameB", "county_admin_b", "countyAdministratorNameB", "示例管理员乙",
            "countyAdministratorUsernameC", "county_admin_c", "countyAdministratorNameC", "示例管理员丙"
    );

    @Test
    void acceptsOnlyTheExactPreviouslyExecutedChecksums() throws Exception {
        try (var resources = new ClassLoaderResourceAccessor();
             var liquibase = new Liquibase(CHANGELOG, resources, new H2Database())) {
            IDENTITIES.forEach(liquibase::setChangeLogParameter);
            var changes = liquibase.getDatabaseChangeLog();
            for (var change : changes.getChangeSets()) {
                if (change.getId().endsWith("-assertions")) continue;
                String expected = change.getId().endsWith("-mysql")
                        ? "9:ed3f7c311907043606c0f430f58735b5" : H2_CHECKSUM;
                assertThat(change.isCheckSumValid(CheckSum.parse(expected))).isTrue();
                assertThat(change.isCheckSumValid(CheckSum.parse("9:00000000000000000000000000000000")))
                        .isFalse();
                assertThat(change.getValidCheckSums()).hasSize(2);
            }
        }
    }

    @Test
    void normalizesOnlyExplicitlyConfiguredAccountsAndDoesNotRerunExecutedChanges() throws Exception {
        String url = databaseUrl();
        try (Connection connection = DriverManager.getConnection(url)) {
            createFixture(connection);
        }
        try (var liquibase = open(url)) {
            IDENTITIES.forEach(liquibase::setChangeLogParameter);
            liquibase.update(new Contexts(), new LabelExpression());
        }
        String executedAt;
        try (Connection connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            assertThat(count(connection, "SELECT COUNT(*) FROM county_administrator_user_snapshot_20260813"))
                    .isEqualTo(3);
            assertThat(count(connection, "SELECT COUNT(*) FROM membership_role_grants")).isEqualTo(6);
            assertThat(count(connection, "SELECT COUNT(*) FROM user_roles")).isEqualTo(6);
            assertThat(count(connection, "SELECT COUNT(*) FROM users WHERE authorization_version = 1"))
                    .isEqualTo(3);
            assertThat(count(connection, "SELECT COUNT(*) FROM users WHERE username = 'unrelated_user' "
                    + "AND authorization_version = 0 AND org_unit_id IS NULL")).isEqualTo(1);
            statement.executeUpdate("UPDATE DATABASECHANGELOG SET MD5SUM = '" + H2_CHECKSUM
                    + "' WHERE ID = '" + H2_CHANGESET + "'");
            try (var rows = statement.executeQuery("SELECT DATEEXECUTED FROM DATABASECHANGELOG WHERE ID = '"
                    + H2_CHANGESET + "'")) {
                rows.next();
                executedAt = rows.getString(1);
            }
        }
        try (var liquibase = open(url)) {
            IDENTITIES.forEach(liquibase::setChangeLogParameter);
            liquibase.validate();
            liquibase.update(new Contexts(), new LabelExpression());
        }
        try (Connection connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            assertThat(count(connection, "SELECT COUNT(*) FROM users WHERE authorization_version = 1"))
                    .isEqualTo(3);
            try (var rows = statement.executeQuery("SELECT DATEEXECUTED FROM DATABASECHANGELOG WHERE ID = '"
                    + H2_CHANGESET + "'")) {
                rows.next();
                assertThat(rows.getString(1)).isEqualTo(executedAt);
            }
        }
    }

    @Test
    void acceptsTheExecutedPasswordSeedChecksumWithoutPublishingItsCredentialHash() throws Exception {
        String changelog = "db/changelog/changes/2026-08-11-023-user-identity-import-contract.sql";
        try (var resources = new ClassLoaderResourceAccessor();
             var liquibase = new Liquibase(changelog, resources, new H2Database())) {
            liquibase.setChangeLogParameter("defaultTenantCode", "DEFAULT_CITY");
            var change = liquibase.getDatabaseChangeLog().getChangeSets().stream()
                    .filter(candidate -> candidate.getId().equals("2026-08-11-023-lowercase-county-default-passwords"))
                    .findFirst().orElseThrow();

            assertThat(change.isCheckSumValid(CheckSum.parse("9:96ad16f6ce78d71e164c9285f58057e2")))
                    .isTrue();
            assertThat(change.isCheckSumValid(CheckSum.parse("9:00000000000000000000000000000000")))
                    .isFalse();
            assertThat(change.getValidCheckSums()).hasSize(2);
        }
    }

    @Test
    void unconfiguredMigrationDoesNotSelectUnrelatedAccounts() throws Exception {
        String url = databaseUrl();
        try (Connection connection = DriverManager.getConnection(url)) {
            createFixture(connection);
        }
        try (var liquibase = open(url)) {
            IDENTITIES.keySet().forEach(name -> liquibase.setChangeLogParameter(name, "__unconfigured__"));
            liquibase.update(new Contexts(), new LabelExpression());
        }
        try (Connection connection = DriverManager.getConnection(url)) {
            assertThat(count(connection, "SELECT COUNT(*) FROM county_administrator_user_snapshot_20260813"))
                    .isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM users WHERE authorization_version <> 0"))
                    .isZero();
        }
    }

    private static Liquibase open(String url) throws Exception {
        var database = new H2Database();
        database.setConnection(new JdbcConnection(DriverManager.getConnection(url)));
        return new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
    }

    private static String databaseUrl() {
        return "jdbc:h2:mem:county_privacy_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    }

    private static int count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static void createFixture(Connection connection) throws Exception {
        String[] schema = {
                "CREATE TABLE tenants (id UUID PRIMARY KEY, system_key VARCHAR(50))",
                "CREATE TABLE users (id UUID PRIMARY KEY, tenant_id UUID, username VARCHAR(50), name VARCHAR(100), "
                        + "org_unit_id UUID, authorization_version BIGINT DEFAULT 0, updated_at TIMESTAMP)",
                "CREATE TABLE org_units (id UUID PRIMARY KEY, tenant_id UUID, code VARCHAR(50), "
                        + "unit_type VARCHAR(30), status VARCHAR(30))",
                "CREATE TABLE user_roles (user_id UUID, role_id UUID)",
                "CREATE TABLE role_bundles (id UUID PRIMARY KEY, tenant_id UUID, system_key VARCHAR(50))",
                "CREATE TABLE role_bundle_grant_rules (id UUID PRIMARY KEY, role_bundle_id UUID, role_id UUID, "
                        + "scope_strategy VARCHAR(30))",
                "CREATE TABLE membership_role_grants (id UUID PRIMARY KEY, tenant_id UUID, membership_id UUID, "
                        + "role_id UUID, data_scope VARCHAR(30), scope_org_id UUID, granted_at TIMESTAMP, expires_at TIMESTAMP)"
        };
        UUID tenantId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        try (var statement = connection.createStatement()) {
            for (String sql : schema) statement.execute(sql);
            statement.execute("INSERT INTO tenants VALUES ('" + tenantId + "', 'DEFAULT_TENANT')");
            statement.execute("INSERT INTO role_bundles VALUES ('" + bundleId + "', '" + tenantId
                    + "', 'COUNTY_ADMINISTRATOR')");
            for (String code : new String[] {"350623", "350627"}) {
                statement.execute("INSERT INTO org_units VALUES ('" + UUID.randomUUID() + "', '" + tenantId
                        + "', '" + code + "', 'COUNTY', 'ACTIVE')");
            }
            for (String scope : new String[] {"ACCOUNT_ORG", "ACCOUNT_ORG_TREE"}) {
                statement.execute("INSERT INTO role_bundle_grant_rules VALUES ('" + UUID.randomUUID() + "', '"
                        + bundleId + "', '" + UUID.randomUUID() + "', '" + scope + "')");
            }
        }
        try (var insert = connection.prepareStatement("INSERT INTO users (id, tenant_id, username, name) VALUES (?, ?, ?, ?)")) {
            for (String suffix : new String[] {"A", "B", "C", "unrelated"}) {
                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, tenantId);
                insert.setString(3, IDENTITIES.getOrDefault("countyAdministratorUsername" + suffix, "unrelated_user"));
                insert.setString(4, IDENTITIES.getOrDefault("countyAdministratorName" + suffix, "无关用户"));
                insert.executeUpdate();
            }
        }
    }
}
