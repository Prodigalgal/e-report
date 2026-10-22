package com.pro.ereportbacken.migration;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationTablePlanTests {

    @Test
    void coversEveryRuntimeEntityTableExactlyOnce() {
        Set<String> tableNames = MigrationTablePlan.orderedTables().stream()
                .map(MigrationTablePlan::tableName)
                .collect(Collectors.toSet());

        assertThat(tableNames).hasSameSizeAs(MigrationTablePlan.orderedTables());
        assertThat(tableNames).contains(
                "users",
                "permission_grants",
                "authorization_change_plans",
                "departments",
                "fault_reports",
                "work_order_department_participations",
                "attachments",
                "user_transfer_jobs");
        assertThat(MigrationTablePlan.expectedSourceTables())
                .containsAll(MigrationTablePlan.auditSourceTables());
        assertThat(MigrationTablePlan.auditSourceTables()).contains(
                "authorization_v2_profile_rule_collapse_audit_20260823",
                "authorization_v2_retirement_audit_20260823");
    }

    @Test
    void defersEveryKnownCircularReference() {
        assertThat(deferredColumns("tenants")).containsExactly("root_org_unit_id");
        assertThat(deferredColumns("org_units")).containsExactly("parent_id");
        assertThat(deferredColumns("authorization_profiles")).containsExactly("current_version_id");
        assertThat(deferredColumns("fault_reports"))
                .containsExactlyInAnyOrder("current_assignment_id", "pending_assignment_id", "current_response_id");
    }

    @Test
    void postgreSqlV5BaselineMatchesRuntimeMigrationPlanWithoutRetiredAuthorizationTables() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/changelog/postgresql/2026-08-23-000-v5-baseline.sql"));
        var matcher = Pattern.compile("(?im)^create table ([a-z0-9_]+)").matcher(sql);
        java.util.LinkedHashSet<String> baselineTables = new java.util.LinkedHashSet<>();
        while (matcher.find()) baselineTables.add(matcher.group(1));

        Set<String> expected = MigrationTablePlan.orderedTables().stream()
                .map(MigrationTablePlan::tableName)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        expected.add("database_migration_runs");
        expected.add("database_migration_table_results");
        expected.add("legacy_migration_audit_records");

        assertThat(baselineTables).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(sql).doesNotContain(
                "authorization_assignments",
                "authorization_data_scopes",
                "management_domains",
                "default_administrator_user_id");
        assertThat(sql).contains(
                "fk_user_transfer_job_rows_job FOREIGN KEY (job_id) REFERENCES user_transfer_jobs (id) ON DELETE CASCADE");
    }

    private Set<String> deferredColumns(String tableName) {
        return MigrationTablePlan.orderedTables().stream()
                .filter(table -> table.tableName().equals(tableName))
                .findFirst()
                .orElseThrow()
                .deferredColumns();
    }
}
