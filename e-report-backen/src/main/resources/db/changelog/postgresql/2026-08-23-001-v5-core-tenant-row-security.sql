--liquibase formatted sql

--changeset e-report:2026-08-23-001-v5-core-tenant-row-security dbms:postgresql splitStatements:false stripComments:false
--comment Enable fail-closed tenant row security for work-order, organization participation and department collaboration tables.
DO $rls$
DECLARE
    protected_table text;
BEGIN
    FOREACH protected_table IN ARRAY ARRAY[
        'attachments',
        'daily_work_order_report_dispatches',
        'department_change_events',
        'departments',
        'fault_reports',
        'work_order_assignments',
        'work_order_department_participations',
        'work_order_events',
        'work_order_material_usages',
        'work_order_organization_participations',
        'work_order_organization_transfers',
        'work_order_responses',
        'work_order_sla_policies',
        'work_order_verifications'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', protected_table);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', protected_table);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', protected_table);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (' ||
            'tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid' ||
            ') WITH CHECK (' ||
            'tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid' ||
            ')',
            protected_table
        );
    END LOOP;
END
$rls$;

--rollback ALTER TABLE attachments DISABLE ROW LEVEL SECURITY;
--rollback ALTER TABLE daily_work_order_report_dispatches DISABLE ROW LEVEL SECURITY;
--rollback ALTER TABLE department_change_events DISABLE ROW LEVEL SECURITY;
--rollback ALTER TABLE departments DISABLE ROW LEVEL SECURITY;
--rollback ALTER TABLE fault_reports DISABLE ROW LEVEL SECURITY;
--rollback ALTER TABLE work_order_assignments DISABLE ROW LEVEL SECURITY;
--rollback ALTER TABLE work_order_department_participations DISABLE ROW LEVEL SECURITY;
--rollback ALTER TABLE work_order_events DISABLE ROW LEVEL SECURITY;
--rollback ALTER TABLE work_order_material_usages DISABLE ROW LEVEL SECURITY;
--rollback ALTER TABLE work_order_organization_participations DISABLE ROW LEVEL SECURITY;
--rollback ALTER TABLE work_order_organization_transfers DISABLE ROW LEVEL SECURITY;
--rollback ALTER TABLE work_order_responses DISABLE ROW LEVEL SECURITY;
--rollback ALTER TABLE work_order_sla_policies DISABLE ROW LEVEL SECURITY;
--rollback ALTER TABLE work_order_verifications DISABLE ROW LEVEL SECURITY;
