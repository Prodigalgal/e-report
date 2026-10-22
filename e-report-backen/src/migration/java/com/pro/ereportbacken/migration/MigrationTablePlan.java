package com.pro.ereportbacken.migration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

record MigrationTablePlan(String tableName, Set<String> deferredColumns) {

    MigrationTablePlan(String tableName, String... deferredColumns) {
        this(tableName, Set.of(deferredColumns));
    }

    static List<MigrationTablePlan> orderedTables() {
        return List.of(
                table("tenant_groups"),
                table("tenants", "root_org_unit_id"),
                table("org_units", "parent_id"),
                table("departments", "parent_id", "merged_into_department_id"),
                table("users"),
                table("permissions"),
                table("app_config"),
                table("app_releases"),
                table("tenant_app_config"),
                table("org_app_config"),
                table("authorization_profiles", "current_version_id"),
                table("authorization_profile_versions"),
                table("authorization_profile_permission_rules"),
                table("permission_grants"),
                table("authorization_change_plans"),
                table("trusted_devices"),
                table("auth_sessions"),
                table("api_tokens"),
                table("api_token_permissions"),
                table("work_order_sla_policies"),
                table("fault_reports", "current_assignment_id", "pending_assignment_id", "current_response_id"),
                table("work_order_assignments"),
                table("work_order_responses"),
                table("work_order_verifications"),
                table("work_order_events"),
                table("work_order_organization_participations"),
                table("work_order_organization_transfers"),
                table("department_change_events"),
                table("work_order_department_participations"),
                table("attachments"),
                table("work_order_material_usages"),
                table("organization_move_events"),
                table("daily_work_order_report_dispatches"),
                table("command_idempotency_records"),
                table("outbox_events"),
                table("client_observation_events"),
                table("file_transfer_upload_sessions"),
                table("user_transfer_jobs"),
                table("user_transfer_job_rows"));
    }

    static List<String> auditSourceTables() {
        return List.of(
                "api_resource_permissions",
                "authorization_scope_repair_20260812",
                "authorization_scope_restore_20260813",
                "authorization_v2_profile_rule_collapse_audit_20260823",
                "authorization_v2_retirement_audit_20260823",
                "authorization_v2_permission_code_map_20260814",
                "county_administrator_grant_snapshot_20260813",
                "county_administrator_role_snapshot_20260813",
                "county_administrator_user_snapshot_20260813",
                "default_admin_grant_snapshot_20260812",
                "default_admin_role_snapshot_20260812",
                "default_admin_state_snapshot_20260812",
                "external_command_records",
                "external_user_bindings",
                "permission_catalog_migration_audit_20260820",
                "permission_catalog_release_20260820",
                "permission_catalog_additions_20260823",
                "permission_catalog_v4_code_map_20260823",
                "permission_catalog_v4_migration_audit_20260823",
                "work_order_transfer_permission_manifest_20260823",
                "organization_default_administrator_retirement_audit_20260823",
                "wecom_group_chat_configs",
                "wecom_pending_materials");
    }

    static Set<String> expectedSourceTables() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        orderedTables().forEach(table -> result.add(table.tableName()));
        result.addAll(auditSourceTables());
        return Set.copyOf(result);
    }

    private static MigrationTablePlan table(String name, String... deferredColumns) {
        return new MigrationTablePlan(name, deferredColumns);
    }
}
