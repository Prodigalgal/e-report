--liquibase formatted sql

--changeset e-report:2026-08-13-032-county-administrator-normalization-mysql dbms:mysql
--validCheckSum: 8:2acea6e21d761950ebd8205c50a9e473
--validCheckSum: 9:ed3f7c311907043606c0f430f58735b5
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT CASE WHEN (SELECT COUNT(*) FROM users member JOIN tenants tenant ON tenant.id = member.tenant_id WHERE tenant.system_key = 'DEFAULT_TENANT' AND member.username IN ('${countyAdministratorUsernameA}','${countyAdministratorUsernameB}','${countyAdministratorUsernameC}')) IN (0, 3) THEN 0 ELSE 1 END
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM users member JOIN tenants tenant ON tenant.id = member.tenant_id WHERE tenant.system_key = 'DEFAULT_TENANT' AND ((member.username = '${countyAdministratorUsernameA}' AND member.name <> '${countyAdministratorNameA}') OR (member.username = '${countyAdministratorUsernameB}' AND member.name <> '${countyAdministratorNameB}') OR (member.username = '${countyAdministratorUsernameC}' AND member.name <> '${countyAdministratorNameC}'))
--precondition-sql-check expectedResult:0 SELECT CASE WHEN EXISTS (SELECT 1 FROM users member JOIN tenants tenant ON tenant.id = member.tenant_id WHERE tenant.system_key = 'DEFAULT_TENANT' AND member.username IN ('${countyAdministratorUsernameA}','${countyAdministratorUsernameB}','${countyAdministratorUsernameC}')) AND (SELECT COUNT(*) FROM role_bundle_grant_rules rule JOIN role_bundles bundle ON bundle.id = rule.role_bundle_id JOIN tenants tenant ON tenant.id = bundle.tenant_id WHERE tenant.system_key = 'DEFAULT_TENANT' AND bundle.system_key = 'COUNTY_ADMINISTRATOR') <> 32 THEN 1 ELSE 0 END
--precondition-sql-check expectedResult:0 SELECT CASE WHEN EXISTS (SELECT 1 FROM users member JOIN tenants tenant ON tenant.id = member.tenant_id WHERE tenant.system_key = 'DEFAULT_TENANT' AND member.username IN ('${countyAdministratorUsernameA}','${countyAdministratorUsernameB}','${countyAdministratorUsernameC}')) AND (SELECT COUNT(*) FROM org_units organization JOIN tenants tenant ON tenant.id = organization.tenant_id WHERE tenant.system_key = 'DEFAULT_TENANT' AND organization.code IN ('350623','350627') AND organization.unit_type = 'COUNTY' AND organization.status = 'ACTIVE') <> 2 THEN 1 ELSE 0 END
--comment Snapshot and normalize the three confirmed county administrators to the built-in county administrator bundle.
CREATE TABLE county_administrator_user_snapshot_20260813 (
    membership_id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    username VARCHAR(50) NOT NULL,
    previous_org_unit_id BINARY(16) NULL,
    target_org_unit_id BINARY(16) NOT NULL,
    previous_authorization_version BIGINT NOT NULL,
    normalized_at DATETIME(6) NOT NULL,
    PRIMARY KEY (membership_id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE county_administrator_role_snapshot_20260813 (
    membership_id BINARY(16) NOT NULL,
    role_id BINARY(16) NOT NULL,
    PRIMARY KEY (membership_id, role_id)
);

CREATE TABLE county_administrator_grant_snapshot_20260813 (
    grant_id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    membership_id BINARY(16) NOT NULL,
    role_id BINARY(16) NOT NULL,
    data_scope VARCHAR(30) NOT NULL,
    scope_org_id BINARY(16) NULL,
    granted_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NULL,
    PRIMARY KEY (grant_id)
);

INSERT INTO county_administrator_user_snapshot_20260813 (
    membership_id, tenant_id, username, previous_org_unit_id,
    target_org_unit_id, previous_authorization_version, normalized_at
)
SELECT member.id, member.tenant_id, member.username, member.org_unit_id,
       target_organization.id, member.authorization_version, CURRENT_TIMESTAMP
FROM users member
JOIN tenants tenant ON tenant.id = member.tenant_id AND tenant.system_key = 'DEFAULT_TENANT'
JOIN org_units target_organization
  ON target_organization.tenant_id = member.tenant_id
 AND target_organization.code = CASE WHEN member.username = '${countyAdministratorUsernameC}' THEN '350623' ELSE '350627' END
 AND target_organization.unit_type = 'COUNTY'
 AND target_organization.status = 'ACTIVE'
WHERE member.username IN ('${countyAdministratorUsernameA}','${countyAdministratorUsernameB}','${countyAdministratorUsernameC}');

INSERT INTO county_administrator_role_snapshot_20260813 (membership_id, role_id)
SELECT assignment.user_id, assignment.role_id
FROM user_roles assignment
JOIN county_administrator_user_snapshot_20260813 snapshot ON snapshot.membership_id = assignment.user_id;

INSERT INTO county_administrator_grant_snapshot_20260813 (
    grant_id, tenant_id, membership_id, role_id, data_scope,
    scope_org_id, granted_at, expires_at
)
SELECT grant_row.id, grant_row.tenant_id, grant_row.membership_id, grant_row.role_id,
       grant_row.data_scope, grant_row.scope_org_id, grant_row.granted_at, grant_row.expires_at
FROM membership_role_grants grant_row
JOIN county_administrator_user_snapshot_20260813 snapshot ON snapshot.membership_id = grant_row.membership_id;

DELETE grant_row
FROM membership_role_grants grant_row
JOIN county_administrator_user_snapshot_20260813 snapshot ON snapshot.membership_id = grant_row.membership_id;

DELETE assignment
FROM user_roles assignment
JOIN county_administrator_user_snapshot_20260813 snapshot ON snapshot.membership_id = assignment.user_id;

UPDATE users member
JOIN county_administrator_user_snapshot_20260813 snapshot ON snapshot.membership_id = member.id
SET member.org_unit_id = snapshot.target_org_unit_id,
    member.authorization_version = member.authorization_version + 1,
    member.updated_at = CURRENT_TIMESTAMP;

INSERT INTO user_roles (user_id, role_id)
SELECT snapshot.membership_id, rule.role_id
FROM county_administrator_user_snapshot_20260813 snapshot
JOIN role_bundles bundle
  ON bundle.tenant_id = snapshot.tenant_id
 AND bundle.system_key = 'COUNTY_ADMINISTRATOR'
JOIN role_bundle_grant_rules rule ON rule.role_bundle_id = bundle.id;

INSERT INTO membership_role_grants (
    id, tenant_id, membership_id, role_id, data_scope,
    scope_org_id, granted_at, expires_at
)
SELECT UUID_TO_BIN(UUID(), 1), snapshot.tenant_id, snapshot.membership_id, rule.role_id,
       CASE rule.scope_strategy
           WHEN 'ACCOUNT_ORG' THEN 'ORG'
           WHEN 'ACCOUNT_ORG_TREE' THEN 'ORG_TREE'
       END,
       snapshot.target_org_unit_id, CURRENT_TIMESTAMP, NULL
FROM county_administrator_user_snapshot_20260813 snapshot
JOIN role_bundles bundle
  ON bundle.tenant_id = snapshot.tenant_id
 AND bundle.system_key = 'COUNTY_ADMINISTRATOR'
JOIN role_bundle_grant_rules rule ON rule.role_bundle_id = bundle.id
WHERE rule.scope_strategy IN ('ACCOUNT_ORG', 'ACCOUNT_ORG_TREE');

--rollback DELETE grant_row FROM membership_role_grants grant_row JOIN county_administrator_user_snapshot_20260813 snapshot ON snapshot.membership_id = grant_row.membership_id;
--rollback DELETE assignment FROM user_roles assignment JOIN county_administrator_user_snapshot_20260813 snapshot ON snapshot.membership_id = assignment.user_id;
--rollback UPDATE users member JOIN county_administrator_user_snapshot_20260813 snapshot ON snapshot.membership_id = member.id SET member.org_unit_id = snapshot.previous_org_unit_id, member.authorization_version = member.authorization_version + 1, member.updated_at = CURRENT_TIMESTAMP;
--rollback INSERT INTO user_roles (user_id, role_id) SELECT membership_id, role_id FROM county_administrator_role_snapshot_20260813;
--rollback INSERT INTO membership_role_grants (id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at) SELECT grant_id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at FROM county_administrator_grant_snapshot_20260813;
--rollback DROP TABLE county_administrator_grant_snapshot_20260813;
--rollback DROP TABLE county_administrator_role_snapshot_20260813;
--rollback DROP TABLE county_administrator_user_snapshot_20260813;

--changeset e-report:2026-08-13-032-county-administrator-normalization-h2 dbms:h2
--validCheckSum: 8:0f70200173ebea38dea8799a41d0cee2
--validCheckSum: 9:3929d7bce18d66bf6e28c868a56d3e40
--comment H2 equivalent for the auditable county administrator normalization.
CREATE TABLE county_administrator_user_snapshot_20260813 (
    membership_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    username VARCHAR(50) NOT NULL,
    previous_org_unit_id UUID NULL,
    target_org_unit_id UUID NOT NULL,
    previous_authorization_version BIGINT NOT NULL,
    normalized_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (membership_id)
);

CREATE TABLE county_administrator_role_snapshot_20260813 (
    membership_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (membership_id, role_id)
);

CREATE TABLE county_administrator_grant_snapshot_20260813 (
    grant_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    role_id UUID NOT NULL,
    data_scope VARCHAR(30) NOT NULL,
    scope_org_id UUID NULL,
    granted_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NULL,
    PRIMARY KEY (grant_id)
);

INSERT INTO county_administrator_user_snapshot_20260813 (
    membership_id, tenant_id, username, previous_org_unit_id,
    target_org_unit_id, previous_authorization_version, normalized_at
)
SELECT member.id, member.tenant_id, member.username, member.org_unit_id,
       target_organization.id, member.authorization_version, CURRENT_TIMESTAMP
FROM users member
JOIN tenants tenant ON tenant.id = member.tenant_id AND tenant.system_key = 'DEFAULT_TENANT'
JOIN org_units target_organization
  ON target_organization.tenant_id = member.tenant_id
 AND target_organization.code = CASE WHEN member.username = '${countyAdministratorUsernameC}' THEN '350623' ELSE '350627' END
 AND target_organization.unit_type = 'COUNTY'
 AND target_organization.status = 'ACTIVE'
WHERE member.username IN ('${countyAdministratorUsernameA}','${countyAdministratorUsernameB}','${countyAdministratorUsernameC}');

INSERT INTO county_administrator_role_snapshot_20260813 (membership_id, role_id)
SELECT assignment.user_id, assignment.role_id
FROM user_roles assignment
JOIN county_administrator_user_snapshot_20260813 snapshot ON snapshot.membership_id = assignment.user_id;

INSERT INTO county_administrator_grant_snapshot_20260813 (
    grant_id, tenant_id, membership_id, role_id, data_scope,
    scope_org_id, granted_at, expires_at
)
SELECT grant_row.id, grant_row.tenant_id, grant_row.membership_id, grant_row.role_id,
       grant_row.data_scope, grant_row.scope_org_id, grant_row.granted_at, grant_row.expires_at
FROM membership_role_grants grant_row
JOIN county_administrator_user_snapshot_20260813 snapshot ON snapshot.membership_id = grant_row.membership_id;

DELETE FROM membership_role_grants
WHERE membership_id IN (SELECT membership_id FROM county_administrator_user_snapshot_20260813);

DELETE FROM user_roles
WHERE user_id IN (SELECT membership_id FROM county_administrator_user_snapshot_20260813);

UPDATE users
SET org_unit_id = (
        SELECT snapshot.target_org_unit_id
        FROM county_administrator_user_snapshot_20260813 snapshot
        WHERE snapshot.membership_id = users.id
    ),
    authorization_version = authorization_version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id IN (SELECT membership_id FROM county_administrator_user_snapshot_20260813);

INSERT INTO user_roles (user_id, role_id)
SELECT snapshot.membership_id, rule.role_id
FROM county_administrator_user_snapshot_20260813 snapshot
JOIN role_bundles bundle
  ON bundle.tenant_id = snapshot.tenant_id
 AND bundle.system_key = 'COUNTY_ADMINISTRATOR'
JOIN role_bundle_grant_rules rule ON rule.role_bundle_id = bundle.id;

INSERT INTO membership_role_grants (
    id, tenant_id, membership_id, role_id, data_scope,
    scope_org_id, granted_at, expires_at
)
SELECT RANDOM_UUID(), snapshot.tenant_id, snapshot.membership_id, rule.role_id,
       CASE rule.scope_strategy
           WHEN 'ACCOUNT_ORG' THEN 'ORG'
           WHEN 'ACCOUNT_ORG_TREE' THEN 'ORG_TREE'
       END,
       snapshot.target_org_unit_id, CURRENT_TIMESTAMP, NULL
FROM county_administrator_user_snapshot_20260813 snapshot
JOIN role_bundles bundle
  ON bundle.tenant_id = snapshot.tenant_id
 AND bundle.system_key = 'COUNTY_ADMINISTRATOR'
JOIN role_bundle_grant_rules rule ON rule.role_bundle_id = bundle.id
WHERE rule.scope_strategy IN ('ACCOUNT_ORG', 'ACCOUNT_ORG_TREE');

--rollback DELETE FROM membership_role_grants WHERE membership_id IN (SELECT membership_id FROM county_administrator_user_snapshot_20260813);
--rollback DELETE FROM user_roles WHERE user_id IN (SELECT membership_id FROM county_administrator_user_snapshot_20260813);
--rollback UPDATE users SET org_unit_id = (SELECT snapshot.previous_org_unit_id FROM county_administrator_user_snapshot_20260813 snapshot WHERE snapshot.membership_id = users.id), authorization_version = authorization_version + 1, updated_at = CURRENT_TIMESTAMP WHERE id IN (SELECT membership_id FROM county_administrator_user_snapshot_20260813);
--rollback INSERT INTO user_roles (user_id, role_id) SELECT membership_id, role_id FROM county_administrator_role_snapshot_20260813;
--rollback INSERT INTO membership_role_grants (id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at) SELECT grant_id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at FROM county_administrator_grant_snapshot_20260813;
--rollback DROP TABLE county_administrator_grant_snapshot_20260813;
--rollback DROP TABLE county_administrator_role_snapshot_20260813;
--rollback DROP TABLE county_administrator_user_snapshot_20260813;

--changeset e-report:2026-08-13-032-county-administrator-normalization-assertions dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT CASE WHEN COUNT(*) IN (0, 3) THEN 0 ELSE 1 END FROM county_administrator_user_snapshot_20260813
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM county_administrator_user_snapshot_20260813 snapshot JOIN users member ON member.id = snapshot.membership_id WHERE member.org_unit_id <> snapshot.target_org_unit_id
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM county_administrator_user_snapshot_20260813 snapshot CROSS JOIN role_bundle_grant_rules rule JOIN role_bundles bundle ON bundle.id = rule.role_bundle_id AND bundle.tenant_id = snapshot.tenant_id AND bundle.system_key = 'COUNTY_ADMINISTRATOR' LEFT JOIN membership_role_grants grant_row ON grant_row.membership_id = snapshot.membership_id AND grant_row.role_id = rule.role_id WHERE grant_row.id IS NULL
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM county_administrator_user_snapshot_20260813 snapshot JOIN membership_role_grants grant_row ON grant_row.membership_id = snapshot.membership_id LEFT JOIN role_bundle_grant_rules rule ON rule.role_id = grant_row.role_id AND rule.role_bundle_id = (SELECT bundle.id FROM role_bundles bundle WHERE bundle.tenant_id = snapshot.tenant_id AND bundle.system_key = 'COUNTY_ADMINISTRATOR') WHERE rule.id IS NULL
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM county_administrator_user_snapshot_20260813 snapshot JOIN membership_role_grants grant_row ON grant_row.membership_id = snapshot.membership_id JOIN role_bundle_grant_rules rule ON rule.role_id = grant_row.role_id AND rule.role_bundle_id = (SELECT bundle.id FROM role_bundles bundle WHERE bundle.tenant_id = snapshot.tenant_id AND bundle.system_key = 'COUNTY_ADMINISTRATOR') WHERE (rule.scope_strategy = 'ACCOUNT_ORG' AND (grant_row.data_scope <> 'ORG' OR grant_row.scope_org_id <> snapshot.target_org_unit_id)) OR (rule.scope_strategy = 'ACCOUNT_ORG_TREE' AND (grant_row.data_scope <> 'ORG_TREE' OR grant_row.scope_org_id <> snapshot.target_org_unit_id)) OR grant_row.expires_at IS NOT NULL
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM county_administrator_user_snapshot_20260813 snapshot JOIN user_roles assignment ON assignment.user_id = snapshot.membership_id LEFT JOIN role_bundle_grant_rules rule ON rule.role_id = assignment.role_id AND rule.role_bundle_id = (SELECT bundle.id FROM role_bundles bundle WHERE bundle.tenant_id = snapshot.tenant_id AND bundle.system_key = 'COUNTY_ADMINISTRATOR') WHERE rule.id IS NULL
--comment Halt if any confirmed county administrator differs from the built-in county administrator bundle after normalization.
SELECT 1;

--rollback SELECT 1;
