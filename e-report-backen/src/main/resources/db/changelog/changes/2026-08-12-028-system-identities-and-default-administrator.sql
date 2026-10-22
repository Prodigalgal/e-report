--liquibase formatted sql

--changeset e-report:2026-08-12-028-system-identities-mysql dbms:mysql
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM tenants WHERE code = '${defaultTenantCode}'
--precondition-sql-check expectedResult:0 SELECT CASE WHEN COUNT(*) > 1 THEN 1 ELSE 0 END FROM users WHERE username = '${defaultAdminUsername}'
--precondition-sql-check expectedResult:0 SELECT CASE WHEN (SELECT COUNT(*) FROM users) > 0 AND (SELECT COUNT(*) FROM users WHERE username = '${defaultAdminUsername}') <> 1 THEN 1 ELSE 0 END
--comment Persist default tenant and bootstrap administrator identities in the database so runtime behavior no longer depends on ENV codes or usernames.
ALTER TABLE tenants ADD COLUMN system_key VARCHAR(40) NULL AFTER root_org_unit_id;
ALTER TABLE users ADD COLUMN system_key VARCHAR(40) NULL AFTER authorization_version;

UPDATE tenants
SET system_key = 'DEFAULT_TENANT',
    updated_at = CURRENT_TIMESTAMP
WHERE code = '${defaultTenantCode}';

UPDATE users member
JOIN tenants tenant ON tenant.id = member.tenant_id
SET member.system_key = 'DEFAULT_ADMINISTRATOR',
    member.updated_at = CURRENT_TIMESTAMP
WHERE tenant.system_key = 'DEFAULT_TENANT'
  AND member.username = '${defaultAdminUsername}';

ALTER TABLE tenants ADD CONSTRAINT uk_tenants_system_key UNIQUE (system_key);
ALTER TABLE users ADD CONSTRAINT uk_users_system_key UNIQUE (system_key);

--rollback ALTER TABLE users DROP INDEX uk_users_system_key;
--rollback ALTER TABLE tenants DROP INDEX uk_tenants_system_key;
--rollback ALTER TABLE users DROP COLUMN system_key;
--rollback ALTER TABLE tenants DROP COLUMN system_key;

--changeset e-report:2026-08-12-028-system-identities-h2 dbms:h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM tenants WHERE code = '${defaultTenantCode}'
--precondition-sql-check expectedResult:0 SELECT CASE WHEN COUNT(*) > 1 THEN 1 ELSE 0 END FROM users WHERE username = '${defaultAdminUsername}'
--comment H2 equivalent for database-owned system identities.
ALTER TABLE tenants ADD COLUMN system_key VARCHAR(40) NULL;
ALTER TABLE users ADD COLUMN system_key VARCHAR(40) NULL;

UPDATE tenants
SET system_key = 'DEFAULT_TENANT',
    updated_at = CURRENT_TIMESTAMP
WHERE code = '${defaultTenantCode}';

UPDATE users member
SET system_key = 'DEFAULT_ADMINISTRATOR',
    updated_at = CURRENT_TIMESTAMP
WHERE member.tenant_id = (SELECT tenant.id FROM tenants tenant WHERE tenant.system_key = 'DEFAULT_TENANT')
  AND member.username = '${defaultAdminUsername}';

ALTER TABLE tenants ADD CONSTRAINT uk_tenants_system_key UNIQUE (system_key);
ALTER TABLE users ADD CONSTRAINT uk_users_system_key UNIQUE (system_key);

--rollback ALTER TABLE users DROP CONSTRAINT uk_users_system_key;
--rollback ALTER TABLE tenants DROP CONSTRAINT uk_tenants_system_key;
--rollback ALTER TABLE users DROP COLUMN system_key;
--rollback ALTER TABLE tenants DROP COLUMN system_key;

--changeset e-report:2026-08-12-028-default-administrator-repair-mysql dbms:mysql
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT CASE WHEN EXISTS (SELECT 1 FROM users WHERE system_key = 'DEFAULT_ADMINISTRATOR') AND (SELECT COUNT(*) FROM roles role_row JOIN tenants tenant ON tenant.id = role_row.tenant_id WHERE tenant.system_key = 'DEFAULT_TENANT' AND role_row.name IN ('ROLE_USER','ROLE_COUNTY_NOTIFICATION_RECIPIENT','ROLE_TENANT_NOTIFICATION_RECIPIENT','ROLE_NETWORK_FAULT_REPORTER','ROLE_NETWORK_FAULT_WORKER','ROLE_NETWORK_FAULT_DISPATCHER','ROLE_NETWORK_FAULT_VERIFIER','ROLE_NETWORK_FAULT_CONTROLLER','ROLE_NETWORK_FAULT_AUDITOR','ROLE_BOX_DOOR_OPEN_REPORTER','ROLE_BOX_DOOR_OPEN_WORKER','ROLE_BOX_DOOR_OPEN_DISPATCHER','ROLE_BOX_DOOR_OPEN_VERIFIER','ROLE_BOX_DOOR_OPEN_CONTROLLER','ROLE_BOX_DOOR_OPEN_AUDITOR','ROLE_DAMAGED_BOX_REPLACEMENT_REPORTER','ROLE_DAMAGED_BOX_REPLACEMENT_WORKER','ROLE_DAMAGED_BOX_REPLACEMENT_DISPATCHER','ROLE_DAMAGED_BOX_REPLACEMENT_VERIFIER','ROLE_DAMAGED_BOX_REPLACEMENT_CONTROLLER','ROLE_DAMAGED_BOX_REPLACEMENT_AUDITOR','ROLE_USER_DETAILS_EDITOR','ROLE_USER_EXPORTER','ROLE_USER_LIFECYCLE_MANAGER','ROLE_USER_CREDENTIAL_MANAGER','ROLE_USER_AUTHORIZATION_MANAGER','ROLE_ROLE_EDITOR','ROLE_ORG_EDITOR','ROLE_SYSTEM_CONFIG_EDITOR','ROLE_NOTIFICATION_CONFIG_EDITOR','ROLE_SLA_EDITOR','ROLE_RELEASE_PUBLISHER','ROLE_RELEASE_POLICY_MANAGER','ROLE_RELEASE_STORAGE_MANAGER','ROLE_DASHBOARD_VIEWER','ROLE_OBSERVABILITY_VIEWER','ROLE_TENANT_PROVISIONER','ROLE_TENANT_STATUS_MANAGER')) <> 38 THEN 1 ELSE 0 END
--comment Snapshot and repair the existing default administrator once; subsequent runtime changes remain database-owned.
CREATE TABLE default_admin_state_snapshot_20260812 (
    membership_id BINARY(16) NOT NULL,
    previous_password_change_required BOOLEAN NOT NULL,
    PRIMARY KEY (membership_id)
);

CREATE TABLE default_admin_role_snapshot_20260812 (
    membership_id BINARY(16) NOT NULL,
    role_id BINARY(16) NOT NULL,
    PRIMARY KEY (membership_id, role_id)
);

CREATE TABLE default_admin_grant_snapshot_20260812 (
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

INSERT INTO default_admin_state_snapshot_20260812 (membership_id, previous_password_change_required)
SELECT id, password_change_required FROM users WHERE system_key = 'DEFAULT_ADMINISTRATOR';

INSERT INTO default_admin_role_snapshot_20260812 (membership_id, role_id)
SELECT assignment.user_id, assignment.role_id
FROM user_roles assignment
JOIN users member ON member.id = assignment.user_id
WHERE member.system_key = 'DEFAULT_ADMINISTRATOR';

INSERT INTO default_admin_grant_snapshot_20260812 (
    grant_id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at
)
SELECT grant_row.id, grant_row.tenant_id, grant_row.membership_id, grant_row.role_id,
       grant_row.data_scope, grant_row.scope_org_id, grant_row.granted_at, grant_row.expires_at
FROM membership_role_grants grant_row
JOIN users member ON member.id = grant_row.membership_id
WHERE member.system_key = 'DEFAULT_ADMINISTRATOR';

DELETE grant_row
FROM membership_role_grants grant_row
JOIN users member ON member.id = grant_row.membership_id
WHERE member.system_key = 'DEFAULT_ADMINISTRATOR';

DELETE assignment
FROM user_roles assignment
JOIN users member ON member.id = assignment.user_id
WHERE member.system_key = 'DEFAULT_ADMINISTRATOR';

INSERT INTO user_roles (user_id, role_id)
SELECT member.id, role_row.id
FROM users member
JOIN tenants tenant ON tenant.id = member.tenant_id AND tenant.system_key = 'DEFAULT_TENANT'
JOIN roles role_row ON role_row.tenant_id = tenant.id
WHERE member.system_key = 'DEFAULT_ADMINISTRATOR'
  AND role_row.name IN ('ROLE_USER','ROLE_COUNTY_NOTIFICATION_RECIPIENT','ROLE_TENANT_NOTIFICATION_RECIPIENT','ROLE_NETWORK_FAULT_REPORTER','ROLE_NETWORK_FAULT_WORKER','ROLE_NETWORK_FAULT_DISPATCHER','ROLE_NETWORK_FAULT_VERIFIER','ROLE_NETWORK_FAULT_CONTROLLER','ROLE_NETWORK_FAULT_AUDITOR','ROLE_BOX_DOOR_OPEN_REPORTER','ROLE_BOX_DOOR_OPEN_WORKER','ROLE_BOX_DOOR_OPEN_DISPATCHER','ROLE_BOX_DOOR_OPEN_VERIFIER','ROLE_BOX_DOOR_OPEN_CONTROLLER','ROLE_BOX_DOOR_OPEN_AUDITOR','ROLE_DAMAGED_BOX_REPLACEMENT_REPORTER','ROLE_DAMAGED_BOX_REPLACEMENT_WORKER','ROLE_DAMAGED_BOX_REPLACEMENT_DISPATCHER','ROLE_DAMAGED_BOX_REPLACEMENT_VERIFIER','ROLE_DAMAGED_BOX_REPLACEMENT_CONTROLLER','ROLE_DAMAGED_BOX_REPLACEMENT_AUDITOR','ROLE_USER_DETAILS_EDITOR','ROLE_USER_EXPORTER','ROLE_USER_LIFECYCLE_MANAGER','ROLE_USER_CREDENTIAL_MANAGER','ROLE_USER_AUTHORIZATION_MANAGER','ROLE_ROLE_EDITOR','ROLE_ORG_EDITOR','ROLE_SYSTEM_CONFIG_EDITOR','ROLE_NOTIFICATION_CONFIG_EDITOR','ROLE_SLA_EDITOR','ROLE_RELEASE_PUBLISHER','ROLE_RELEASE_POLICY_MANAGER','ROLE_RELEASE_STORAGE_MANAGER','ROLE_DASHBOARD_VIEWER','ROLE_OBSERVABILITY_VIEWER','ROLE_TENANT_PROVISIONER','ROLE_TENANT_STATUS_MANAGER');

INSERT INTO membership_role_grants (
    id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at
)
SELECT UUID_TO_BIN(UUID(), 1), member.tenant_id, member.id, role_row.id,
       CASE WHEN role_row.name = 'ROLE_USER' THEN 'ORG' ELSE 'TENANT_ALL' END,
       CASE WHEN role_row.name = 'ROLE_USER' THEN tenant.root_org_unit_id ELSE NULL END,
       CURRENT_TIMESTAMP, NULL
FROM users member
JOIN tenants tenant ON tenant.id = member.tenant_id AND tenant.system_key = 'DEFAULT_TENANT'
JOIN roles role_row ON role_row.tenant_id = tenant.id
WHERE member.system_key = 'DEFAULT_ADMINISTRATOR'
  AND role_row.name IN ('ROLE_USER','ROLE_COUNTY_NOTIFICATION_RECIPIENT','ROLE_TENANT_NOTIFICATION_RECIPIENT','ROLE_NETWORK_FAULT_REPORTER','ROLE_NETWORK_FAULT_WORKER','ROLE_NETWORK_FAULT_DISPATCHER','ROLE_NETWORK_FAULT_VERIFIER','ROLE_NETWORK_FAULT_CONTROLLER','ROLE_NETWORK_FAULT_AUDITOR','ROLE_BOX_DOOR_OPEN_REPORTER','ROLE_BOX_DOOR_OPEN_WORKER','ROLE_BOX_DOOR_OPEN_DISPATCHER','ROLE_BOX_DOOR_OPEN_VERIFIER','ROLE_BOX_DOOR_OPEN_CONTROLLER','ROLE_BOX_DOOR_OPEN_AUDITOR','ROLE_DAMAGED_BOX_REPLACEMENT_REPORTER','ROLE_DAMAGED_BOX_REPLACEMENT_WORKER','ROLE_DAMAGED_BOX_REPLACEMENT_DISPATCHER','ROLE_DAMAGED_BOX_REPLACEMENT_VERIFIER','ROLE_DAMAGED_BOX_REPLACEMENT_CONTROLLER','ROLE_DAMAGED_BOX_REPLACEMENT_AUDITOR','ROLE_USER_DETAILS_EDITOR','ROLE_USER_EXPORTER','ROLE_USER_LIFECYCLE_MANAGER','ROLE_USER_CREDENTIAL_MANAGER','ROLE_USER_AUTHORIZATION_MANAGER','ROLE_ROLE_EDITOR','ROLE_ORG_EDITOR','ROLE_SYSTEM_CONFIG_EDITOR','ROLE_NOTIFICATION_CONFIG_EDITOR','ROLE_SLA_EDITOR','ROLE_RELEASE_PUBLISHER','ROLE_RELEASE_POLICY_MANAGER','ROLE_RELEASE_STORAGE_MANAGER','ROLE_DASHBOARD_VIEWER','ROLE_OBSERVABILITY_VIEWER','ROLE_TENANT_PROVISIONER','ROLE_TENANT_STATUS_MANAGER');

UPDATE users
SET password_change_required = CASE WHEN password_changed_at IS NULL THEN TRUE ELSE password_change_required END,
    authorization_version = authorization_version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE system_key = 'DEFAULT_ADMINISTRATOR';

--rollback DELETE grant_row FROM membership_role_grants grant_row JOIN users member ON member.id = grant_row.membership_id WHERE member.system_key = 'DEFAULT_ADMINISTRATOR';
--rollback DELETE assignment FROM user_roles assignment JOIN users member ON member.id = assignment.user_id WHERE member.system_key = 'DEFAULT_ADMINISTRATOR';
--rollback INSERT INTO user_roles (user_id, role_id) SELECT membership_id, role_id FROM default_admin_role_snapshot_20260812;
--rollback INSERT INTO membership_role_grants (id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at) SELECT grant_id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at FROM default_admin_grant_snapshot_20260812;
--rollback UPDATE users member JOIN default_admin_state_snapshot_20260812 snapshot ON snapshot.membership_id = member.id SET member.password_change_required = snapshot.previous_password_change_required, member.authorization_version = member.authorization_version + 1, member.updated_at = CURRENT_TIMESTAMP;
--rollback DROP TABLE default_admin_grant_snapshot_20260812;
--rollback DROP TABLE default_admin_role_snapshot_20260812;
--rollback DROP TABLE default_admin_state_snapshot_20260812;

--changeset e-report:2026-08-12-028-default-administrator-repair-h2 dbms:h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT CASE WHEN EXISTS (SELECT 1 FROM users WHERE system_key = 'DEFAULT_ADMINISTRATOR') AND (SELECT COUNT(*) FROM roles role_row JOIN tenants tenant ON tenant.id = role_row.tenant_id WHERE tenant.system_key = 'DEFAULT_TENANT' AND role_row.name IN ('ROLE_USER','ROLE_COUNTY_NOTIFICATION_RECIPIENT','ROLE_TENANT_NOTIFICATION_RECIPIENT','ROLE_NETWORK_FAULT_REPORTER','ROLE_NETWORK_FAULT_WORKER','ROLE_NETWORK_FAULT_DISPATCHER','ROLE_NETWORK_FAULT_VERIFIER','ROLE_NETWORK_FAULT_CONTROLLER','ROLE_NETWORK_FAULT_AUDITOR','ROLE_BOX_DOOR_OPEN_REPORTER','ROLE_BOX_DOOR_OPEN_WORKER','ROLE_BOX_DOOR_OPEN_DISPATCHER','ROLE_BOX_DOOR_OPEN_VERIFIER','ROLE_BOX_DOOR_OPEN_CONTROLLER','ROLE_BOX_DOOR_OPEN_AUDITOR','ROLE_DAMAGED_BOX_REPLACEMENT_REPORTER','ROLE_DAMAGED_BOX_REPLACEMENT_WORKER','ROLE_DAMAGED_BOX_REPLACEMENT_DISPATCHER','ROLE_DAMAGED_BOX_REPLACEMENT_VERIFIER','ROLE_DAMAGED_BOX_REPLACEMENT_CONTROLLER','ROLE_DAMAGED_BOX_REPLACEMENT_AUDITOR','ROLE_USER_DETAILS_EDITOR','ROLE_USER_EXPORTER','ROLE_USER_LIFECYCLE_MANAGER','ROLE_USER_CREDENTIAL_MANAGER','ROLE_USER_AUTHORIZATION_MANAGER','ROLE_ROLE_EDITOR','ROLE_ORG_EDITOR','ROLE_SYSTEM_CONFIG_EDITOR','ROLE_NOTIFICATION_CONFIG_EDITOR','ROLE_SLA_EDITOR','ROLE_RELEASE_PUBLISHER','ROLE_RELEASE_POLICY_MANAGER','ROLE_RELEASE_STORAGE_MANAGER','ROLE_DASHBOARD_VIEWER','ROLE_OBSERVABILITY_VIEWER','ROLE_TENANT_PROVISIONER','ROLE_TENANT_STATUS_MANAGER')) <> 38 THEN 1 ELSE 0 END
--comment H2 equivalent for the auditable one-time default administrator repair.
CREATE TABLE default_admin_state_snapshot_20260812 (
    membership_id UUID NOT NULL PRIMARY KEY,
    previous_password_change_required BOOLEAN NOT NULL
);

CREATE TABLE default_admin_role_snapshot_20260812 (
    membership_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (membership_id, role_id)
);

CREATE TABLE default_admin_grant_snapshot_20260812 (
    grant_id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    role_id UUID NOT NULL,
    data_scope VARCHAR(30) NOT NULL,
    scope_org_id UUID NULL,
    granted_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NULL
);

INSERT INTO default_admin_state_snapshot_20260812 (membership_id, previous_password_change_required)
SELECT id, password_change_required FROM users WHERE system_key = 'DEFAULT_ADMINISTRATOR';

INSERT INTO default_admin_role_snapshot_20260812 (membership_id, role_id)
SELECT assignment.user_id, assignment.role_id
FROM user_roles assignment
WHERE assignment.user_id IN (SELECT id FROM users WHERE system_key = 'DEFAULT_ADMINISTRATOR');

INSERT INTO default_admin_grant_snapshot_20260812 (
    grant_id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at
)
SELECT id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at
FROM membership_role_grants
WHERE membership_id IN (SELECT id FROM users WHERE system_key = 'DEFAULT_ADMINISTRATOR');

DELETE FROM membership_role_grants
WHERE membership_id IN (SELECT id FROM users WHERE system_key = 'DEFAULT_ADMINISTRATOR');

DELETE FROM user_roles
WHERE user_id IN (SELECT id FROM users WHERE system_key = 'DEFAULT_ADMINISTRATOR');

INSERT INTO user_roles (user_id, role_id)
SELECT member.id, role_row.id
FROM users member
JOIN tenants tenant ON tenant.id = member.tenant_id AND tenant.system_key = 'DEFAULT_TENANT'
JOIN roles role_row ON role_row.tenant_id = tenant.id
WHERE member.system_key = 'DEFAULT_ADMINISTRATOR'
  AND role_row.name IN ('ROLE_USER','ROLE_COUNTY_NOTIFICATION_RECIPIENT','ROLE_TENANT_NOTIFICATION_RECIPIENT','ROLE_NETWORK_FAULT_REPORTER','ROLE_NETWORK_FAULT_WORKER','ROLE_NETWORK_FAULT_DISPATCHER','ROLE_NETWORK_FAULT_VERIFIER','ROLE_NETWORK_FAULT_CONTROLLER','ROLE_NETWORK_FAULT_AUDITOR','ROLE_BOX_DOOR_OPEN_REPORTER','ROLE_BOX_DOOR_OPEN_WORKER','ROLE_BOX_DOOR_OPEN_DISPATCHER','ROLE_BOX_DOOR_OPEN_VERIFIER','ROLE_BOX_DOOR_OPEN_CONTROLLER','ROLE_BOX_DOOR_OPEN_AUDITOR','ROLE_DAMAGED_BOX_REPLACEMENT_REPORTER','ROLE_DAMAGED_BOX_REPLACEMENT_WORKER','ROLE_DAMAGED_BOX_REPLACEMENT_DISPATCHER','ROLE_DAMAGED_BOX_REPLACEMENT_VERIFIER','ROLE_DAMAGED_BOX_REPLACEMENT_CONTROLLER','ROLE_DAMAGED_BOX_REPLACEMENT_AUDITOR','ROLE_USER_DETAILS_EDITOR','ROLE_USER_EXPORTER','ROLE_USER_LIFECYCLE_MANAGER','ROLE_USER_CREDENTIAL_MANAGER','ROLE_USER_AUTHORIZATION_MANAGER','ROLE_ROLE_EDITOR','ROLE_ORG_EDITOR','ROLE_SYSTEM_CONFIG_EDITOR','ROLE_NOTIFICATION_CONFIG_EDITOR','ROLE_SLA_EDITOR','ROLE_RELEASE_PUBLISHER','ROLE_RELEASE_POLICY_MANAGER','ROLE_RELEASE_STORAGE_MANAGER','ROLE_DASHBOARD_VIEWER','ROLE_OBSERVABILITY_VIEWER','ROLE_TENANT_PROVISIONER','ROLE_TENANT_STATUS_MANAGER');

INSERT INTO membership_role_grants (
    id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at
)
SELECT RANDOM_UUID(), member.tenant_id, member.id, role_row.id,
       CASE WHEN role_row.name = 'ROLE_USER' THEN 'ORG' ELSE 'TENANT_ALL' END,
       CASE WHEN role_row.name = 'ROLE_USER' THEN tenant.root_org_unit_id ELSE NULL END,
       CURRENT_TIMESTAMP, NULL
FROM users member
JOIN tenants tenant ON tenant.id = member.tenant_id AND tenant.system_key = 'DEFAULT_TENANT'
JOIN roles role_row ON role_row.tenant_id = tenant.id
WHERE member.system_key = 'DEFAULT_ADMINISTRATOR'
  AND role_row.name IN ('ROLE_USER','ROLE_COUNTY_NOTIFICATION_RECIPIENT','ROLE_TENANT_NOTIFICATION_RECIPIENT','ROLE_NETWORK_FAULT_REPORTER','ROLE_NETWORK_FAULT_WORKER','ROLE_NETWORK_FAULT_DISPATCHER','ROLE_NETWORK_FAULT_VERIFIER','ROLE_NETWORK_FAULT_CONTROLLER','ROLE_NETWORK_FAULT_AUDITOR','ROLE_BOX_DOOR_OPEN_REPORTER','ROLE_BOX_DOOR_OPEN_WORKER','ROLE_BOX_DOOR_OPEN_DISPATCHER','ROLE_BOX_DOOR_OPEN_VERIFIER','ROLE_BOX_DOOR_OPEN_CONTROLLER','ROLE_BOX_DOOR_OPEN_AUDITOR','ROLE_DAMAGED_BOX_REPLACEMENT_REPORTER','ROLE_DAMAGED_BOX_REPLACEMENT_WORKER','ROLE_DAMAGED_BOX_REPLACEMENT_DISPATCHER','ROLE_DAMAGED_BOX_REPLACEMENT_VERIFIER','ROLE_DAMAGED_BOX_REPLACEMENT_CONTROLLER','ROLE_DAMAGED_BOX_REPLACEMENT_AUDITOR','ROLE_USER_DETAILS_EDITOR','ROLE_USER_EXPORTER','ROLE_USER_LIFECYCLE_MANAGER','ROLE_USER_CREDENTIAL_MANAGER','ROLE_USER_AUTHORIZATION_MANAGER','ROLE_ROLE_EDITOR','ROLE_ORG_EDITOR','ROLE_SYSTEM_CONFIG_EDITOR','ROLE_NOTIFICATION_CONFIG_EDITOR','ROLE_SLA_EDITOR','ROLE_RELEASE_PUBLISHER','ROLE_RELEASE_POLICY_MANAGER','ROLE_RELEASE_STORAGE_MANAGER','ROLE_DASHBOARD_VIEWER','ROLE_OBSERVABILITY_VIEWER','ROLE_TENANT_PROVISIONER','ROLE_TENANT_STATUS_MANAGER');

UPDATE users
SET password_change_required = CASE WHEN password_changed_at IS NULL THEN TRUE ELSE password_change_required END,
    authorization_version = authorization_version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE system_key = 'DEFAULT_ADMINISTRATOR';

--rollback DELETE FROM membership_role_grants WHERE membership_id IN (SELECT id FROM users WHERE system_key = 'DEFAULT_ADMINISTRATOR');
--rollback DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE system_key = 'DEFAULT_ADMINISTRATOR');
--rollback INSERT INTO user_roles (user_id, role_id) SELECT membership_id, role_id FROM default_admin_role_snapshot_20260812;
--rollback INSERT INTO membership_role_grants (id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at) SELECT grant_id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at FROM default_admin_grant_snapshot_20260812;
--rollback UPDATE users member SET password_change_required = (SELECT snapshot.previous_password_change_required FROM default_admin_state_snapshot_20260812 snapshot WHERE snapshot.membership_id = member.id), authorization_version = authorization_version + 1, updated_at = CURRENT_TIMESTAMP WHERE member.id IN (SELECT membership_id FROM default_admin_state_snapshot_20260812);
--rollback DROP TABLE default_admin_grant_snapshot_20260812;
--rollback DROP TABLE default_admin_role_snapshot_20260812;
--rollback DROP TABLE default_admin_state_snapshot_20260812;

--changeset e-report:2026-08-12-028-system-identity-assertions dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM tenants WHERE system_key = 'DEFAULT_TENANT'
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM users member JOIN tenants tenant ON tenant.id = member.tenant_id WHERE member.system_key = 'DEFAULT_ADMINISTRATOR' AND tenant.system_key <> 'DEFAULT_TENANT'
--precondition-sql-check expectedResult:0 SELECT CASE WHEN EXISTS (SELECT 1 FROM users WHERE system_key = 'DEFAULT_ADMINISTRATOR') AND (SELECT COUNT(*) FROM membership_role_grants grant_row JOIN users member ON member.id = grant_row.membership_id WHERE member.system_key = 'DEFAULT_ADMINISTRATOR') <> 38 THEN 1 ELSE 0 END
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM membership_role_grants grant_row JOIN users member ON member.id = grant_row.membership_id JOIN roles role_row ON role_row.id = grant_row.role_id JOIN tenants tenant ON tenant.id = member.tenant_id WHERE member.system_key = 'DEFAULT_ADMINISTRATOR' AND ((role_row.name = 'ROLE_USER' AND (grant_row.data_scope <> 'ORG' OR grant_row.scope_org_id <> tenant.root_org_unit_id)) OR (role_row.name <> 'ROLE_USER' AND (grant_row.data_scope <> 'TENANT_ALL' OR grant_row.scope_org_id IS NOT NULL)))
--comment Halt if database-owned system identities or the repaired administrator boundary are inconsistent.
SELECT 1;

--rollback SELECT 1;
