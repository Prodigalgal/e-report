--liquibase formatted sql

--changeset e-report:2026-08-13-031-city-administrator-scope-restoration-mysql dbms:mysql
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT CASE WHEN EXISTS (SELECT 1 FROM authorization_scope_repair_20260812 repair JOIN users member ON member.id = repair.membership_id JOIN tenants tenant ON tenant.id = member.tenant_id WHERE tenant.system_key = 'DEFAULT_TENANT' AND member.username = '吕春珉') AND (SELECT COUNT(*) FROM authorization_scope_repair_20260812 repair JOIN membership_role_grants grant_row ON grant_row.id = repair.grant_id JOIN users member ON member.id = repair.membership_id JOIN tenants tenant ON tenant.id = member.tenant_id WHERE tenant.system_key = 'DEFAULT_TENANT' AND member.username = '吕春珉' AND repair.previous_data_scope = 'TENANT_ALL' AND grant_row.data_scope = 'ORG_TREE' AND grant_row.scope_org_id = member.org_unit_id) <> 34 THEN 1 ELSE 0 END
--comment Restore the explicitly confirmed city administrator grants that change 027 incorrectly constrained by membership organization alone.
CREATE TABLE authorization_scope_restore_20260813 (
    grant_id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    membership_id BINARY(16) NOT NULL,
    role_id BINARY(16) NOT NULL,
    previous_data_scope VARCHAR(30) NOT NULL,
    previous_scope_org_id BINARY(16) NULL,
    restored_data_scope VARCHAR(30) NOT NULL,
    restored_scope_org_id BINARY(16) NULL,
    restored_at DATETIME(6) NOT NULL,
    restore_reason VARCHAR(255) NOT NULL,
    PRIMARY KEY (grant_id)
);

INSERT INTO authorization_scope_restore_20260813 (
    grant_id, tenant_id, membership_id, role_id,
    previous_data_scope, previous_scope_org_id,
    restored_data_scope, restored_scope_org_id,
    restored_at, restore_reason
)
SELECT grant_row.id, grant_row.tenant_id, grant_row.membership_id, grant_row.role_id,
       grant_row.data_scope, grant_row.scope_org_id,
       repair.previous_data_scope, repair.previous_scope_org_id,
       CURRENT_TIMESTAMP,
       'Confirmed city administrator grants must retain their explicit tenant-wide scope independent of account organization.'
FROM authorization_scope_repair_20260812 repair
JOIN membership_role_grants grant_row ON grant_row.id = repair.grant_id
JOIN users member ON member.id = repair.membership_id AND member.id = grant_row.membership_id
JOIN tenants tenant ON tenant.id = member.tenant_id AND tenant.id = grant_row.tenant_id
WHERE tenant.system_key = 'DEFAULT_TENANT'
  AND member.username = '吕春珉'
  AND repair.previous_data_scope = 'TENANT_ALL'
  AND repair.previous_scope_org_id IS NULL
  AND grant_row.data_scope = 'ORG_TREE'
  AND grant_row.scope_org_id = member.org_unit_id;

UPDATE membership_role_grants grant_row
JOIN authorization_scope_restore_20260813 restoration ON restoration.grant_id = grant_row.id
SET grant_row.data_scope = restoration.restored_data_scope,
    grant_row.scope_org_id = restoration.restored_scope_org_id;

UPDATE users member
JOIN (
    SELECT DISTINCT membership_id
    FROM authorization_scope_restore_20260813
) affected ON affected.membership_id = member.id
SET member.authorization_version = member.authorization_version + 1,
    member.updated_at = CURRENT_TIMESTAMP;

--rollback UPDATE membership_role_grants grant_row JOIN authorization_scope_restore_20260813 restoration ON restoration.grant_id = grant_row.id SET grant_row.data_scope = restoration.previous_data_scope, grant_row.scope_org_id = restoration.previous_scope_org_id;
--rollback UPDATE users member JOIN (SELECT DISTINCT membership_id FROM authorization_scope_restore_20260813) affected ON affected.membership_id = member.id SET member.authorization_version = member.authorization_version + 1, member.updated_at = CURRENT_TIMESTAMP;
--rollback DROP TABLE authorization_scope_restore_20260813;

--changeset e-report:2026-08-13-031-city-administrator-scope-restoration-h2 dbms:h2
--comment H2 equivalent for the auditable city administrator scope restoration.
CREATE TABLE authorization_scope_restore_20260813 (
    grant_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    role_id UUID NOT NULL,
    previous_data_scope VARCHAR(30) NOT NULL,
    previous_scope_org_id UUID NULL,
    restored_data_scope VARCHAR(30) NOT NULL,
    restored_scope_org_id UUID NULL,
    restored_at TIMESTAMP(6) NOT NULL,
    restore_reason VARCHAR(255) NOT NULL,
    PRIMARY KEY (grant_id)
);

INSERT INTO authorization_scope_restore_20260813 (
    grant_id, tenant_id, membership_id, role_id,
    previous_data_scope, previous_scope_org_id,
    restored_data_scope, restored_scope_org_id,
    restored_at, restore_reason
)
SELECT grant_row.id, grant_row.tenant_id, grant_row.membership_id, grant_row.role_id,
       grant_row.data_scope, grant_row.scope_org_id,
       repair.previous_data_scope, repair.previous_scope_org_id,
       CURRENT_TIMESTAMP,
       'Confirmed city administrator grants must retain their explicit tenant-wide scope independent of account organization.'
FROM authorization_scope_repair_20260812 repair
JOIN membership_role_grants grant_row ON grant_row.id = repair.grant_id
JOIN users member ON member.id = repair.membership_id AND member.id = grant_row.membership_id
JOIN tenants tenant ON tenant.id = member.tenant_id AND tenant.id = grant_row.tenant_id
WHERE tenant.system_key = 'DEFAULT_TENANT'
  AND member.username = '吕春珉'
  AND repair.previous_data_scope = 'TENANT_ALL'
  AND repair.previous_scope_org_id IS NULL
  AND grant_row.data_scope = 'ORG_TREE'
  AND grant_row.scope_org_id = member.org_unit_id;

UPDATE membership_role_grants
SET data_scope = (
        SELECT restoration.restored_data_scope
        FROM authorization_scope_restore_20260813 restoration
        WHERE restoration.grant_id = membership_role_grants.id
    ),
    scope_org_id = (
        SELECT restoration.restored_scope_org_id
        FROM authorization_scope_restore_20260813 restoration
        WHERE restoration.grant_id = membership_role_grants.id
    )
WHERE id IN (SELECT grant_id FROM authorization_scope_restore_20260813);

UPDATE users
SET authorization_version = authorization_version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id IN (SELECT membership_id FROM authorization_scope_restore_20260813);

--rollback UPDATE membership_role_grants SET data_scope = (SELECT restoration.previous_data_scope FROM authorization_scope_restore_20260813 restoration WHERE restoration.grant_id = membership_role_grants.id), scope_org_id = (SELECT restoration.previous_scope_org_id FROM authorization_scope_restore_20260813 restoration WHERE restoration.grant_id = membership_role_grants.id) WHERE id IN (SELECT grant_id FROM authorization_scope_restore_20260813);
--rollback UPDATE users SET authorization_version = authorization_version + 1, updated_at = CURRENT_TIMESTAMP WHERE id IN (SELECT membership_id FROM authorization_scope_restore_20260813);
--rollback DROP TABLE authorization_scope_restore_20260813;

--changeset e-report:2026-08-13-031-city-administrator-scope-restoration-assertions dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM authorization_scope_restore_20260813 restoration JOIN membership_role_grants grant_row ON grant_row.id = restoration.grant_id WHERE grant_row.data_scope <> restoration.restored_data_scope OR grant_row.scope_org_id IS NOT NULL
--precondition-sql-check expectedResult:0 SELECT CASE WHEN EXISTS (SELECT 1 FROM authorization_scope_restore_20260813) AND (SELECT COUNT(*) FROM authorization_scope_restore_20260813) <> 34 THEN 1 ELSE 0 END
--comment Halt if the confirmed administrator restoration is incomplete or no longer tenant-wide.
SELECT 1;

--rollback SELECT 1;
