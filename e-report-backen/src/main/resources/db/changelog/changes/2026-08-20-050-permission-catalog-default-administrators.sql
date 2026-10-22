--liquibase formatted sql

--changeset e-report:2026-08-20-050-permission-catalog-manifest dbms:mysql,h2
--comment Persist the exact 2.5.1 fixed permission catalog used to classify retired database rows without trusting historical mappings.
CREATE TABLE permission_catalog_release_20260820 (
    permission_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (permission_name)
);

INSERT INTO permission_catalog_release_20260820 (permission_name) VALUES
('app-update:delete'),
('app-update:policy-update'),
('app-update:publish'),
('app-update:storage:delete'),
('app-update:storage:sync'),
('app-update:storage:view'),
('app-update:view'),
('authorization-profile:assignment:upgrade'),
('authorization-profile:copy'),
('authorization-profile:create'),
('authorization-profile:draft:update'),
('authorization-profile:publish'),
('authorization-profile:retire'),
('authorization-profile:view'),
('dashboard:statistics:view'),
('management:access'),
('notification:channel:update'),
('notification:config:view'),
('notification:recipient:county'),
('notification:recipient:tenant'),
('notification:schedule:update'),
('notification:sender:update'),
('observability:redis:view'),
('observability:view'),
('organization:manage:create'),
('organization:manage:move'),
('organization:manage:status:update'),
('organization:manage:update'),
('organization:manage:view'),
('sla-policy:update'),
('sla-policy:view'),
('system:config:create'),
('system:config:delete'),
('system:config:update'),
('system:config:view'),
('tenant:manage:create'),
('tenant:manage:status:activate'),
('tenant:manage:status:deactivate'),
('tenant:manage:view'),
('user:authorization:adjust'),
('user:authorization:grant'),
('user:authorization:revoke'),
('user:authorization:view'),
('user:export:create'),
('user:export:download'),
('user:import:cancel'),
('user:import:execute'),
('user:import:preview'),
('user:import:template:download'),
('user:manage:create'),
('user:manage:delete'),
('user:manage:details:update'),
('user:manage:organization:move'),
('user:manage:password:reset'),
('user:manage:restore'),
('user:manage:status:activate'),
('user:manage:status:deactivate'),
('user:manage:status:lock'),
('user:manage:status:unlock'),
('user:manage:view'),
('work-order:box-door-open:assignment:accept'),
('work-order:box-door-open:assignment:create'),
('work-order:box-door-open:assignment:reassign'),
('work-order:box-door-open:assignment:reject'),
('work-order:box-door-open:attachment:read'),
('work-order:box-door-open:audit:view'),
('work-order:box-door-open:cancel'),
('work-order:box-door-open:claim'),
('work-order:box-door-open:export'),
('work-order:box-door-open:history:view'),
('work-order:box-door-open:hold'),
('work-order:box-door-open:report'),
('work-order:box-door-open:response:submit'),
('work-order:box-door-open:resume'),
('work-order:box-door-open:verification:approve'),
('work-order:box-door-open:verification:reject'),
('work-order:box-door-open:verification:self-approve'),
('work-order:box-door-open:verification:self-reject'),
('work-order:box-door-open:view'),
('work-order:box-door-open:withdraw'),
('work-order:damaged-box-replacement:assignment:accept'),
('work-order:damaged-box-replacement:assignment:create'),
('work-order:damaged-box-replacement:assignment:reassign'),
('work-order:damaged-box-replacement:assignment:reject'),
('work-order:damaged-box-replacement:attachment:read'),
('work-order:damaged-box-replacement:audit:view'),
('work-order:damaged-box-replacement:cancel'),
('work-order:damaged-box-replacement:claim'),
('work-order:damaged-box-replacement:export'),
('work-order:damaged-box-replacement:history:view'),
('work-order:damaged-box-replacement:hold'),
('work-order:damaged-box-replacement:report'),
('work-order:damaged-box-replacement:response:submit'),
('work-order:damaged-box-replacement:resume'),
('work-order:damaged-box-replacement:verification:approve'),
('work-order:damaged-box-replacement:verification:reject'),
('work-order:damaged-box-replacement:verification:self-approve'),
('work-order:damaged-box-replacement:verification:self-reject'),
('work-order:damaged-box-replacement:view'),
('work-order:damaged-box-replacement:withdraw'),
('work-order:network-fault:assignment:accept'),
('work-order:network-fault:assignment:create'),
('work-order:network-fault:assignment:reassign'),
('work-order:network-fault:assignment:reject'),
('work-order:network-fault:attachment:read'),
('work-order:network-fault:audit:view'),
('work-order:network-fault:cancel'),
('work-order:network-fault:claim'),
('work-order:network-fault:export'),
('work-order:network-fault:history:view'),
('work-order:network-fault:hold'),
('work-order:network-fault:report'),
('work-order:network-fault:response:submit'),
('work-order:network-fault:resume'),
('work-order:network-fault:verification:approve'),
('work-order:network-fault:verification:reject'),
('work-order:network-fault:verification:self-approve'),
('work-order:network-fault:verification:self-reject'),
('work-order:network-fault:view'),
('work-order:network-fault:withdraw');

--rollback DROP TABLE permission_catalog_release_20260820;

--changeset e-report:2026-08-20-050-permission-catalog-complete-mysql dbms:mysql
--comment Complete the persisted fixed catalog before profile synchronization; production rows normally already exist.
INSERT INTO permissions (id, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), catalog.permission_name, NULL, NULL
FROM permission_catalog_release_20260820 catalog
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = catalog.permission_name);

--rollback SELECT 1;

--changeset e-report:2026-08-20-050-permission-catalog-complete-h2 dbms:h2
--comment H2 equivalent for clean-database migration verification.
INSERT INTO permissions (id, name, display_name, description)
SELECT RANDOM_UUID(), catalog.permission_name, NULL, NULL
FROM permission_catalog_release_20260820 catalog
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = catalog.permission_name);

--rollback SELECT 1;

--changeset e-report:2026-08-20-050-permission-cleanup-audit-mysql dbms:mysql
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permissions source_permission LEFT JOIN permission_catalog_release_20260820 current_catalog ON current_catalog.permission_name = source_permission.name WHERE current_catalog.permission_name IS NULL AND ((SELECT COUNT(*) FROM authorization_profile_permission_rules profile_rule WHERE profile_rule.permission_id = source_permission.id) + (SELECT COUNT(*) FROM permission_grants permission_grant WHERE permission_grant.permission_id = source_permission.id) + (SELECT COUNT(*) FROM api_token_permissions token_permission WHERE token_permission.permission_name = source_permission.name)) > 0 AND (SELECT COUNT(DISTINCT mapping.target_permission_name) FROM authorization_v2_permission_code_map_20260814 mapping JOIN permission_catalog_release_20260820 target_catalog ON target_catalog.permission_name = mapping.target_permission_name JOIN permissions target_permission ON target_permission.name = mapping.target_permission_name WHERE mapping.source_permission_name = source_permission.name) <> 1
--comment Fail closed when a referenced retired permission has no unique approved current target.
CREATE TABLE permission_catalog_migration_audit_20260820 (
    id BINARY(16) NOT NULL,
    source_permission_id BINARY(16) NOT NULL,
    source_permission_name VARCHAR(100) NOT NULL,
    target_permission_names VARCHAR(1000) NULL,
    migration_action VARCHAR(40) NOT NULL,
    profile_rule_references BIGINT NOT NULL,
    permission_grant_references BIGINT NOT NULL,
    api_token_references BIGINT NOT NULL,
    migration_reason VARCHAR(1000) NOT NULL,
    migration_actor VARCHAR(100) NOT NULL,
    migrated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_catalog_audit_source (source_permission_id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

INSERT INTO permission_catalog_migration_audit_20260820 (
    id, source_permission_id, source_permission_name, target_permission_names,
    migration_action, profile_rule_references, permission_grant_references,
    api_token_references, migration_reason, migration_actor, migrated_at
)
SELECT UUID_TO_BIN(UUID(), 1), source_permission.id, source_permission.name,
       (SELECT GROUP_CONCAT(DISTINCT mapping.target_permission_name ORDER BY mapping.target_permission_name)
          FROM authorization_v2_permission_code_map_20260814 mapping
          JOIN permission_catalog_release_20260820 target_catalog
            ON target_catalog.permission_name = mapping.target_permission_name
         WHERE mapping.source_permission_name = source_permission.name),
       CASE WHEN ((SELECT COUNT(*) FROM authorization_profile_permission_rules profile_rule WHERE profile_rule.permission_id = source_permission.id)
                     + (SELECT COUNT(*) FROM permission_grants permission_grant WHERE permission_grant.permission_id = source_permission.id)
                     + (SELECT COUNT(*) FROM api_token_permissions token_permission WHERE token_permission.permission_name = source_permission.name)) = 0
            THEN 'DELETE_ZERO_REFERENCE' ELSE 'MIGRATE_SINGLE_TARGET' END,
       (SELECT COUNT(*) FROM authorization_profile_permission_rules profile_rule WHERE profile_rule.permission_id = source_permission.id),
       (SELECT COUNT(*) FROM permission_grants permission_grant WHERE permission_grant.permission_id = source_permission.id),
       (SELECT COUNT(*) FROM api_token_permissions token_permission WHERE token_permission.permission_name = source_permission.name),
       '2.5.1 固定权限目录治理；一对多映射未经批准不得自动扩权',
       'LIQUIBASE:2026-08-20-050', CURRENT_TIMESTAMP(6)
FROM permissions source_permission
LEFT JOIN permission_catalog_release_20260820 current_catalog
  ON current_catalog.permission_name = source_permission.name
WHERE current_catalog.permission_name IS NULL;

-- Repoint profile-rule references when the target rule already exists in the same profile version.
UPDATE permission_grants permission_grant
JOIN authorization_profile_permission_rules source_rule ON source_rule.id = permission_grant.profile_rule_id
JOIN permissions source_permission ON source_permission.id = source_rule.permission_id
JOIN (
    SELECT mapping.source_permission_name, MIN(target_permission.id) target_permission_id
    FROM authorization_v2_permission_code_map_20260814 mapping
    JOIN permission_catalog_release_20260820 target_catalog ON target_catalog.permission_name = mapping.target_permission_name
    JOIN permissions target_permission ON target_permission.name = mapping.target_permission_name
    GROUP BY mapping.source_permission_name
    HAVING COUNT(DISTINCT mapping.target_permission_name) = 1
) resolution ON resolution.source_permission_name = source_permission.name
JOIN authorization_profile_permission_rules target_rule
  ON target_rule.profile_version_id = source_rule.profile_version_id
 AND target_rule.permission_id = resolution.target_permission_id
 AND target_rule.relation_constraint = source_rule.relation_constraint
SET permission_grant.profile_rule_id = target_rule.id
WHERE NOT EXISTS (SELECT 1 FROM permission_catalog_release_20260820 current_catalog WHERE current_catalog.permission_name = source_permission.name);

DELETE source_rule
FROM authorization_profile_permission_rules source_rule
JOIN permissions source_permission ON source_permission.id = source_rule.permission_id
JOIN (
    SELECT mapping.source_permission_name, MIN(target_permission.id) target_permission_id
    FROM authorization_v2_permission_code_map_20260814 mapping
    JOIN permission_catalog_release_20260820 target_catalog ON target_catalog.permission_name = mapping.target_permission_name
    JOIN permissions target_permission ON target_permission.name = mapping.target_permission_name
    GROUP BY mapping.source_permission_name
    HAVING COUNT(DISTINCT mapping.target_permission_name) = 1
) resolution ON resolution.source_permission_name = source_permission.name
JOIN authorization_profile_permission_rules target_rule
  ON target_rule.profile_version_id = source_rule.profile_version_id
 AND target_rule.permission_id = resolution.target_permission_id
 AND target_rule.relation_constraint = source_rule.relation_constraint
WHERE NOT EXISTS (SELECT 1 FROM permission_catalog_release_20260820 current_catalog WHERE current_catalog.permission_name = source_permission.name);

UPDATE authorization_profile_permission_rules source_rule
JOIN permissions source_permission ON source_permission.id = source_rule.permission_id
JOIN (
    SELECT mapping.source_permission_name, MIN(target_permission.id) target_permission_id
    FROM authorization_v2_permission_code_map_20260814 mapping
    JOIN permission_catalog_release_20260820 target_catalog ON target_catalog.permission_name = mapping.target_permission_name
    JOIN permissions target_permission ON target_permission.name = mapping.target_permission_name
    GROUP BY mapping.source_permission_name
    HAVING COUNT(DISTINCT mapping.target_permission_name) = 1
) resolution ON resolution.source_permission_name = source_permission.name
SET source_rule.permission_id = resolution.target_permission_id
WHERE NOT EXISTS (SELECT 1 FROM permission_catalog_release_20260820 current_catalog WHERE current_catalog.permission_name = source_permission.name);

UPDATE permission_grants permission_grant
JOIN permissions source_permission ON source_permission.id = permission_grant.permission_id
JOIN (
    SELECT mapping.source_permission_name, MIN(target_permission.id) target_permission_id
    FROM authorization_v2_permission_code_map_20260814 mapping
    JOIN permission_catalog_release_20260820 target_catalog ON target_catalog.permission_name = mapping.target_permission_name
    JOIN permissions target_permission ON target_permission.name = mapping.target_permission_name
    GROUP BY mapping.source_permission_name
    HAVING COUNT(DISTINCT mapping.target_permission_name) = 1
) resolution ON resolution.source_permission_name = source_permission.name
SET permission_grant.permission_id = resolution.target_permission_id
WHERE NOT EXISTS (SELECT 1 FROM permission_catalog_release_20260820 current_catalog WHERE current_catalog.permission_name = source_permission.name);

INSERT IGNORE INTO api_token_permissions (token_id, permission_name)
SELECT token_permission.token_id, resolution.target_permission_name
FROM api_token_permissions token_permission
JOIN (
    SELECT mapping.source_permission_name, MIN(mapping.target_permission_name) target_permission_name
    FROM authorization_v2_permission_code_map_20260814 mapping
    JOIN permission_catalog_release_20260820 target_catalog ON target_catalog.permission_name = mapping.target_permission_name
    JOIN permissions target_permission ON target_permission.name = mapping.target_permission_name
    GROUP BY mapping.source_permission_name
    HAVING COUNT(DISTINCT mapping.target_permission_name) = 1
) resolution ON resolution.source_permission_name = token_permission.permission_name
WHERE NOT EXISTS (SELECT 1 FROM permission_catalog_release_20260820 current_catalog WHERE current_catalog.permission_name = token_permission.permission_name);

DELETE token_permission
FROM api_token_permissions token_permission
LEFT JOIN permission_catalog_release_20260820 current_catalog
  ON current_catalog.permission_name = token_permission.permission_name
WHERE current_catalog.permission_name IS NULL;

DELETE source_permission
FROM permissions source_permission
LEFT JOIN permission_catalog_release_20260820 AS valid_catalog
  ON valid_catalog.permission_name = source_permission.name
WHERE valid_catalog.permission_name IS NULL
  AND NOT EXISTS (SELECT 1 FROM authorization_profile_permission_rules profile_rule WHERE profile_rule.permission_id = source_permission.id)
  AND NOT EXISTS (SELECT 1 FROM permission_grants permission_grant WHERE permission_grant.permission_id = source_permission.id)
  AND NOT EXISTS (SELECT 1 FROM api_token_permissions token_permission WHERE token_permission.permission_name = source_permission.name);

--rollback SELECT 1;

--changeset e-report:2026-08-20-050-permission-cleanup-h2 dbms:h2
--comment H2 keeps the same fail-closed rule and removes only zero-reference database-only permissions.
CREATE TABLE permission_catalog_migration_audit_20260820 (
    id UUID NOT NULL PRIMARY KEY,
    source_permission_id UUID NOT NULL,
    source_permission_name VARCHAR(100) NOT NULL,
    target_permission_names VARCHAR(1000),
    migration_action VARCHAR(40) NOT NULL,
    profile_rule_references BIGINT NOT NULL,
    permission_grant_references BIGINT NOT NULL,
    api_token_references BIGINT NOT NULL,
    migration_reason VARCHAR(1000) NOT NULL,
    migration_actor VARCHAR(100) NOT NULL,
    migrated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_permission_catalog_audit_source UNIQUE (source_permission_id)
);

INSERT INTO permission_catalog_migration_audit_20260820 (
    id, source_permission_id, source_permission_name, target_permission_names,
    migration_action, profile_rule_references, permission_grant_references,
    api_token_references, migration_reason, migration_actor, migrated_at
)
SELECT RANDOM_UUID(), source_permission.id, source_permission.name, NULL,
       'DELETE_ZERO_REFERENCE', 0, 0, 0,
       '2.5.1 固定权限目录治理；H2 仅删除零引用样本',
       'LIQUIBASE:2026-08-20-050', CURRENT_TIMESTAMP
FROM permissions source_permission
LEFT JOIN permission_catalog_release_20260820 AS valid_catalog
  ON valid_catalog.permission_name = source_permission.name
WHERE valid_catalog.permission_name IS NULL
  AND NOT EXISTS (SELECT 1 FROM authorization_profile_permission_rules profile_rule WHERE profile_rule.permission_id = source_permission.id)
  AND NOT EXISTS (SELECT 1 FROM permission_grants permission_grant WHERE permission_grant.permission_id = source_permission.id)
  AND NOT EXISTS (SELECT 1 FROM api_token_permissions token_permission WHERE token_permission.permission_name = source_permission.name);

DELETE FROM permissions
WHERE name NOT IN (SELECT permission_name FROM permission_catalog_release_20260820)
  AND id NOT IN (SELECT permission_id FROM authorization_profile_permission_rules)
  AND id NOT IN (SELECT permission_id FROM permission_grants)
  AND name NOT IN (SELECT permission_name FROM api_token_permissions);

--rollback SELECT 1;

--changeset e-report:2026-08-20-050-default-administrator-organization-mysql dbms:mysql
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM users member JOIN tenants tenant ON tenant.id = member.tenant_id AND tenant.system_key = 'DEFAULT_TENANT' JOIN org_units organization ON organization.tenant_id = member.tenant_id AND organization.code = CASE member.username WHEN 'xiangcheng' THEN '350602' WHEN 'longwen' THEN '350603' WHEN 'longhai' THEN '350604' WHEN 'changtai' THEN '350605' WHEN 'yunxiao' THEN '350622' WHEN 'zhangpu' THEN '350623' WHEN 'zhaoan' THEN '350624' WHEN 'dongshan' THEN '350626' WHEN 'nanjing' THEN '350627' WHEN 'pinghe' THEN '350628' WHEN 'huaan' THEN '350629' END WHERE member.username IN ('xiangcheng','longwen','longhai','changtai','yunxiao','zhangpu','zhaoan','dongshan','nanjing','pinghe','huaan') AND member.org_unit_id <> organization.id
--comment Persist organization-owned default administrators; runtime uses this relationship instead of hard-coded usernames.
ALTER TABLE org_units ADD COLUMN default_administrator_user_id BINARY(16) NULL AFTER parent_id;
ALTER TABLE org_units ADD CONSTRAINT fk_org_units_default_administrator
    FOREIGN KEY (default_administrator_user_id) REFERENCES users(id);
CREATE UNIQUE INDEX uk_org_units_default_administrator ON org_units (default_administrator_user_id);

UPDATE org_units root_organization
JOIN tenants tenant ON tenant.root_org_unit_id = root_organization.id
JOIN users administrator ON administrator.tenant_id = tenant.id
  AND administrator.system_key = 'DEFAULT_ADMINISTRATOR'
SET root_organization.default_administrator_user_id = administrator.id;

UPDATE org_units organization
JOIN tenants tenant ON tenant.id = organization.tenant_id AND tenant.system_key = 'DEFAULT_TENANT'
JOIN users administrator ON administrator.tenant_id = tenant.id
 AND administrator.username = CASE organization.code
     WHEN '350602' THEN 'xiangcheng' WHEN '350603' THEN 'longwen'
     WHEN '350604' THEN 'longhai' WHEN '350605' THEN 'changtai'
     WHEN '350622' THEN 'yunxiao' WHEN '350623' THEN 'zhangpu'
     WHEN '350624' THEN 'zhaoan' WHEN '350626' THEN 'dongshan'
     WHEN '350627' THEN 'nanjing' WHEN '350628' THEN 'pinghe'
     WHEN '350629' THEN 'huaan' END
SET organization.default_administrator_user_id = administrator.id
WHERE organization.code IN ('350602','350603','350604','350605','350622','350623','350624','350626','350627','350628','350629');

UPDATE users
SET system_key = NULL
WHERE system_key = 'DEFAULT_ADMINISTRATOR';

--rollback DROP INDEX uk_org_units_default_administrator ON org_units;
--rollback ALTER TABLE org_units DROP FOREIGN KEY fk_org_units_default_administrator;
--rollback ALTER TABLE org_units DROP COLUMN default_administrator_user_id;

--changeset e-report:2026-08-20-050-default-administrator-organization-h2 dbms:h2
--comment H2 equivalent of organization-owned default administrators.
ALTER TABLE org_units ADD COLUMN default_administrator_user_id UUID;
ALTER TABLE org_units ADD CONSTRAINT fk_org_units_default_administrator
    FOREIGN KEY (default_administrator_user_id) REFERENCES users(id);
CREATE UNIQUE INDEX uk_org_units_default_administrator ON org_units (default_administrator_user_id);

UPDATE org_units
SET default_administrator_user_id = (
    SELECT administrator.id FROM users administrator
    JOIN tenants tenant ON tenant.id = administrator.tenant_id
    WHERE tenant.root_org_unit_id = org_units.id
      AND administrator.system_key = 'DEFAULT_ADMINISTRATOR'
)
WHERE id IN (SELECT root_org_unit_id FROM tenants)
  AND EXISTS (
    SELECT 1 FROM users administrator
    JOIN tenants tenant ON tenant.id = administrator.tenant_id
    WHERE tenant.root_org_unit_id = org_units.id
      AND administrator.system_key = 'DEFAULT_ADMINISTRATOR'
  );

UPDATE org_units organization
SET default_administrator_user_id = (
    SELECT administrator.id
    FROM users administrator
    JOIN tenants tenant ON tenant.id = administrator.tenant_id
    WHERE tenant.system_key = 'DEFAULT_TENANT'
      AND administrator.tenant_id = organization.tenant_id
      AND administrator.username = CASE organization.code
          WHEN '350602' THEN 'xiangcheng' WHEN '350603' THEN 'longwen'
          WHEN '350604' THEN 'longhai' WHEN '350605' THEN 'changtai'
          WHEN '350622' THEN 'yunxiao' WHEN '350623' THEN 'zhangpu'
          WHEN '350624' THEN 'zhaoan' WHEN '350626' THEN 'dongshan'
          WHEN '350627' THEN 'nanjing' WHEN '350628' THEN 'pinghe'
          WHEN '350629' THEN 'huaan' END
)
WHERE organization.code IN ('350602','350603','350604','350605','350622','350623','350624','350626','350627','350628','350629');

UPDATE users
SET system_key = NULL
WHERE system_key = 'DEFAULT_ADMINISTRATOR';

--rollback DROP INDEX uk_org_units_default_administrator;
--rollback ALTER TABLE org_units DROP CONSTRAINT fk_org_units_default_administrator;
--rollback ALTER TABLE org_units DROP COLUMN default_administrator_user_id;

--changeset e-report:2026-08-20-050-permission-catalog-assertions dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permission_catalog_release_20260820 valid_catalog LEFT JOIN permissions permission_row ON permission_row.name = valid_catalog.permission_name WHERE permission_row.id IS NULL
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permissions permission_row LEFT JOIN permission_catalog_release_20260820 valid_catalog ON valid_catalog.permission_name = permission_row.name WHERE valid_catalog.permission_name IS NULL
--comment The post-migration database permission directory must exactly equal the 2.5.1 fixed catalog.
SELECT 1;

--rollback SELECT 1;
