--liquibase formatted sql

--changeset e-report:2026-08-24-003-v5-delegation-baseline-repair dbms:postgresql splitStatements:false stripComments:false
--comment Restore delegable system baselines lost by the legacy grant_option default and archive the before state.
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT CASE WHEN COUNT(*) <= 1 THEN 0 ELSE 1 END FROM users WHERE system_key = 'SUPER_ADMINISTRATOR'
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM permissions WHERE name = 'user:authorization:grant'
DO $repair$
BEGIN
    INSERT INTO legacy_migration_audit_records (
        id, tenant_id, source_table, source_key, payload_json, archived_at)
    SELECT uuidv7(), affected.tenant_id, 'permission_grants',
           'v5.2.0-delegation-baseline:' || affected.membership_id::text,
           jsonb_build_object(
               'reason', 'V5 migration initialized grant_option=false for legacy system baselines',
               'activeGrantCount', affected.active_grant_count,
               'grantOptionCountBefore', affected.grant_option_count,
               'systemKey', affected.system_key),
           clock_timestamp()
    FROM (
        SELECT users.id AS membership_id,
               users.tenant_id,
               users.system_key,
               COUNT(grants.id) FILTER (WHERE grants.status = 'ACTIVE') AS active_grant_count,
               COUNT(grants.id) FILTER (
                   WHERE grants.status = 'ACTIVE' AND grants.grant_option) AS grant_option_count
        FROM users
        LEFT JOIN permission_grants grants
               ON grants.membership_id = users.id
              AND grants.tenant_id = users.tenant_id
        WHERE users.system_key = 'SUPER_ADMINISTRATOR'
           OR EXISTS (
               SELECT 1
               FROM permission_grants marker_grant
               JOIN permissions marker_permission ON marker_permission.id = marker_grant.permission_id
               WHERE marker_grant.membership_id = users.id
                 AND marker_grant.tenant_id = users.tenant_id
                 AND marker_grant.status = 'ACTIVE'
                 AND marker_grant.issuer_type = 'SYSTEM_BASELINE'
                 AND marker_permission.name = 'user:authorization:grant')
        GROUP BY users.id, users.tenant_id, users.system_key
    ) affected
    ON CONFLICT (source_table, source_key) DO NOTHING;

    WITH affected_memberships AS (
        SELECT users.id
        FROM users
        WHERE users.system_key = 'SUPER_ADMINISTRATOR'
           OR EXISTS (
               SELECT 1
               FROM permission_grants marker_grant
               JOIN permissions marker_permission ON marker_permission.id = marker_grant.permission_id
               WHERE marker_grant.membership_id = users.id
                 AND marker_grant.tenant_id = users.tenant_id
                 AND marker_grant.status = 'ACTIVE'
                 AND marker_grant.issuer_type = 'SYSTEM_BASELINE'
                 AND marker_permission.name = 'user:authorization:grant')
    ), changed_grants AS (
        UPDATE permission_grants grants
           SET grant_option = TRUE,
               status_changed_at = clock_timestamp(),
               reason = CASE
                   WHEN grants.reason IS NULL OR grants.reason = ''
                       THEN '5.2.0 系统委派基线校准'
                   WHEN grants.reason LIKE '%5.2.0 系统委派基线校准%'
                       THEN grants.reason
                   ELSE grants.reason || '；5.2.0 系统委派基线校准'
               END
         WHERE grants.status = 'ACTIVE'
           AND grants.grant_option = FALSE
           AND grants.membership_id IN (SELECT id FROM affected_memberships)
        RETURNING grants.membership_id
    )
    UPDATE users
       SET authorization_version = authorization_version + 1,
           updated_at = clock_timestamp()
     WHERE id IN (SELECT DISTINCT membership_id FROM changed_grants);

    IF EXISTS (
        SELECT 1
        FROM permission_grants grants
        JOIN users ON users.id = grants.membership_id
        WHERE users.system_key = 'SUPER_ADMINISTRATOR'
          AND grants.status = 'ACTIVE'
          AND grants.grant_option = FALSE
    ) THEN
        RAISE EXCEPTION 'superAdmin still has active non-delegable grants after baseline repair';
    END IF;
END
$repair$;
--rollback SELECT 1;
