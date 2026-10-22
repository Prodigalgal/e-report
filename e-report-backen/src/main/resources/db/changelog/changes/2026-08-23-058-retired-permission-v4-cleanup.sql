--liquibase formatted sql

--changeset e-report:2026-08-23-058-retired-permission-map dbms:mysql,h2
CREATE TABLE permission_catalog_v4_code_map_20260823 (
    source_permission_name VARCHAR(100) NOT NULL,
    target_permission_name VARCHAR(100),
    migration_reason VARCHAR(500) NOT NULL,
    PRIMARY KEY (source_permission_name)
);
INSERT INTO permission_catalog_v4_code_map_20260823 VALUES
('authorization-profile:view', 'authorization-template:view', '岗位模板查看动作更名'),
('authorization-profile:create', 'authorization-template:create', '岗位模板创建动作更名'),
('authorization-profile:copy', 'authorization-template:clone', '岗位模板复制动作更名'),
('authorization-profile:draft:update', 'authorization-template:update', '岗位模板调整动作收敛'),
('authorization-profile:publish', 'authorization-template:enable', '岗位模板发布动作收敛为启用'),
('authorization-profile:retire', 'authorization-template:disable', '岗位模板退役动作收敛为停用'),
('authorization-profile:assignment:upgrade', NULL, 'V4 不再保留岗位绑定或岗位升级'),
('authorization:delegation:view', NULL, 'V4 由 Grant 链直接表达转授权'),
('authorization:delegation:grant', NULL, 'V4 由原子 Grant 动作表达转授权'),
('authorization:delegation:revoke', NULL, 'V4 由原子 Grant 动作表达撤销'),
('authorization:domain-owner:replace', NULL, 'V4 删除管理域及默认管理员身份指针'),
('authorization:privileged-action:approve', NULL, 'V4 删除通用审批角色模型'),
('authorization:protection:view', NULL, 'V4 删除账号保护等级模型'),
('authorization:protection:manage', NULL, 'V4 删除账号保护等级模型'),
('management:access', NULL, 'V4 删除管理后台总闸门并逐接口鉴权'),
('organization:manage:status:update', NULL, 'V4 已拆分组织启用与停用动作');
--rollback DROP TABLE permission_catalog_v4_code_map_20260823;

--changeset e-report:2026-08-23-058-retired-permission-target-assert dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permission_catalog_v4_code_map_20260823 mapping LEFT JOIN permissions target_permission ON target_permission.name = mapping.target_permission_name WHERE mapping.target_permission_name IS NOT NULL AND target_permission.id IS NULL
SELECT 1;
--rollback SELECT 1;

--changeset e-report:2026-08-23-058-retired-permission-audit-mysql dbms:mysql
CREATE TABLE permission_catalog_v4_migration_audit_20260823 (
    id BINARY(16) NOT NULL,
    source_permission_name VARCHAR(100) NOT NULL,
    target_permission_name VARCHAR(100),
    profile_rule_references BIGINT NOT NULL,
    permission_grant_references BIGINT NOT NULL,
    api_token_references BIGINT NOT NULL,
    migration_reason VARCHAR(500) NOT NULL,
    migration_actor VARCHAR(100) NOT NULL,
    migrated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_catalog_v4_audit_source (source_permission_name)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
INSERT INTO permission_catalog_v4_migration_audit_20260823
(id, source_permission_name, target_permission_name, profile_rule_references,
 permission_grant_references, api_token_references, migration_reason, migration_actor, migrated_at)
SELECT UUID_TO_BIN(UUID(), 1), mapping.source_permission_name, mapping.target_permission_name,
       (SELECT COUNT(*) FROM authorization_profile_permission_rules rule_row JOIN permissions permission_row ON permission_row.id = rule_row.permission_id WHERE permission_row.name = mapping.source_permission_name),
       (SELECT COUNT(*) FROM permission_grants grant_row JOIN permissions permission_row ON permission_row.id = grant_row.permission_id WHERE permission_row.name = mapping.source_permission_name),
       (SELECT COUNT(*) FROM api_token_permissions token_permission WHERE token_permission.permission_name = mapping.source_permission_name),
       mapping.migration_reason, 'LIQUIBASE:2026-08-23-058', CURRENT_TIMESTAMP(6)
FROM permission_catalog_v4_code_map_20260823 mapping;
--rollback DROP TABLE permission_catalog_v4_migration_audit_20260823;

--changeset e-report:2026-08-23-058-retired-permission-audit-h2 dbms:h2
CREATE TABLE permission_catalog_v4_migration_audit_20260823 (
    id UUID NOT NULL PRIMARY KEY,
    source_permission_name VARCHAR(100) NOT NULL,
    target_permission_name VARCHAR(100),
    profile_rule_references BIGINT NOT NULL,
    permission_grant_references BIGINT NOT NULL,
    api_token_references BIGINT NOT NULL,
    migration_reason VARCHAR(500) NOT NULL,
    migration_actor VARCHAR(100) NOT NULL,
    migrated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_permission_catalog_v4_audit_source UNIQUE (source_permission_name)
);
INSERT INTO permission_catalog_v4_migration_audit_20260823
(id, source_permission_name, target_permission_name, profile_rule_references,
 permission_grant_references, api_token_references, migration_reason, migration_actor, migrated_at)
SELECT RANDOM_UUID(), mapping.source_permission_name, mapping.target_permission_name,
       (SELECT COUNT(*) FROM authorization_profile_permission_rules rule_row JOIN permissions permission_row ON permission_row.id = rule_row.permission_id WHERE permission_row.name = mapping.source_permission_name),
       (SELECT COUNT(*) FROM permission_grants grant_row JOIN permissions permission_row ON permission_row.id = grant_row.permission_id WHERE permission_row.name = mapping.source_permission_name),
       (SELECT COUNT(*) FROM api_token_permissions token_permission WHERE token_permission.permission_name = mapping.source_permission_name),
       mapping.migration_reason, 'LIQUIBASE:2026-08-23-058', CURRENT_TIMESTAMP
FROM permission_catalog_v4_code_map_20260823 mapping;
--rollback DROP TABLE permission_catalog_v4_migration_audit_20260823;

--changeset e-report:2026-08-23-058-retired-permission-profile-rules-mysql dbms:mysql
UPDATE permission_grants
SET assignment_id = NULL, profile_rule_id = NULL, data_scope_id = NULL, relation_constraint = NULL;
INSERT INTO authorization_profile_permission_rules
(id, tenant_id, profile_version_id, permission_id, relation_constraint, sort_order)
SELECT UUID_TO_BIN(UUID(), 1), source_rule.tenant_id, source_rule.profile_version_id,
       target_permission.id, NULL, MIN(source_rule.sort_order)
FROM authorization_profile_permission_rules source_rule
JOIN permissions source_permission ON source_permission.id = source_rule.permission_id
JOIN permission_catalog_v4_code_map_20260823 mapping ON mapping.source_permission_name = source_permission.name
JOIN permissions target_permission ON target_permission.name = mapping.target_permission_name
WHERE mapping.target_permission_name IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM authorization_profile_permission_rules target_rule
      WHERE target_rule.profile_version_id = source_rule.profile_version_id
        AND target_rule.permission_id = target_permission.id
  )
GROUP BY source_rule.tenant_id, source_rule.profile_version_id, target_permission.id;
DELETE source_rule
FROM authorization_profile_permission_rules source_rule
JOIN permissions source_permission ON source_permission.id = source_rule.permission_id
JOIN permission_catalog_v4_code_map_20260823 mapping ON mapping.source_permission_name = source_permission.name;
--rollback SELECT 1;

--changeset e-report:2026-08-23-058-retired-permission-profile-rules-h2 dbms:h2
UPDATE permission_grants
SET assignment_id = NULL, profile_rule_id = NULL, data_scope_id = NULL, relation_constraint = NULL;
INSERT INTO authorization_profile_permission_rules
(id, tenant_id, profile_version_id, permission_id, relation_constraint, sort_order)
SELECT RANDOM_UUID(), source_rule.tenant_id, source_rule.profile_version_id,
       target_permission.id, NULL, MIN(source_rule.sort_order)
FROM authorization_profile_permission_rules source_rule
JOIN permissions source_permission ON source_permission.id = source_rule.permission_id
JOIN permission_catalog_v4_code_map_20260823 mapping ON mapping.source_permission_name = source_permission.name
JOIN permissions target_permission ON target_permission.name = mapping.target_permission_name
WHERE mapping.target_permission_name IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM authorization_profile_permission_rules target_rule
      WHERE target_rule.profile_version_id = source_rule.profile_version_id
        AND target_rule.permission_id = target_permission.id
  )
GROUP BY source_rule.tenant_id, source_rule.profile_version_id, target_permission.id;
DELETE FROM authorization_profile_permission_rules source_rule
WHERE EXISTS (
    SELECT 1 FROM permissions source_permission
    JOIN permission_catalog_v4_code_map_20260823 mapping ON mapping.source_permission_name = source_permission.name
    WHERE source_permission.id = source_rule.permission_id
);
--rollback SELECT 1;

--changeset e-report:2026-08-23-058-retired-permission-grants-mysql dbms:mysql
UPDATE permission_grants grant_row
JOIN permissions source_permission ON source_permission.id = grant_row.permission_id
JOIN permission_catalog_v4_code_map_20260823 mapping ON mapping.source_permission_name = source_permission.name
JOIN permissions target_permission ON target_permission.name = mapping.target_permission_name
SET grant_row.permission_id = target_permission.id
WHERE mapping.target_permission_name IS NOT NULL;
UPDATE permission_grants duplicate_grant
JOIN permission_grants keeper_grant
  ON keeper_grant.tenant_id = duplicate_grant.tenant_id
 AND keeper_grant.membership_id = duplicate_grant.membership_id
 AND keeper_grant.permission_id = duplicate_grant.permission_id
 AND keeper_grant.status = 'ACTIVE'
 AND (keeper_grant.granted_at < duplicate_grant.granted_at
      OR (keeper_grant.granted_at = duplicate_grant.granted_at AND keeper_grant.id < duplicate_grant.id))
SET duplicate_grant.status = 'REVOKED',
    duplicate_grant.revoked_at = CURRENT_TIMESTAMP(6),
    duplicate_grant.status_changed_at = CURRENT_TIMESTAMP(6),
    duplicate_grant.revoke_reason = '5.0.6 权限码迁移合并重复 ACTIVE Grant'
WHERE duplicate_grant.status = 'ACTIVE';
DELETE grant_row
FROM permission_grants grant_row
JOIN permissions source_permission ON source_permission.id = grant_row.permission_id
JOIN permission_catalog_v4_code_map_20260823 mapping ON mapping.source_permission_name = source_permission.name
WHERE mapping.target_permission_name IS NULL;
--rollback SELECT 1;

--changeset e-report:2026-08-23-058-retired-permission-grants-h2 dbms:h2
UPDATE permission_grants grant_row
SET permission_id = (
    SELECT target_permission.id
    FROM permissions source_permission
    JOIN permission_catalog_v4_code_map_20260823 mapping ON mapping.source_permission_name = source_permission.name
    JOIN permissions target_permission ON target_permission.name = mapping.target_permission_name
    WHERE source_permission.id = grant_row.permission_id
)
WHERE EXISTS (
    SELECT 1 FROM permissions source_permission
    JOIN permission_catalog_v4_code_map_20260823 mapping ON mapping.source_permission_name = source_permission.name
    WHERE source_permission.id = grant_row.permission_id AND mapping.target_permission_name IS NOT NULL
);
UPDATE permission_grants duplicate_grant
SET status = 'REVOKED',
    revoked_at = CURRENT_TIMESTAMP,
    status_changed_at = CURRENT_TIMESTAMP,
    revoke_reason = '5.0.6 权限码迁移合并重复 ACTIVE Grant'
WHERE duplicate_grant.status = 'ACTIVE'
  AND EXISTS (
      SELECT 1 FROM permission_grants keeper_grant
      WHERE keeper_grant.tenant_id = duplicate_grant.tenant_id
        AND keeper_grant.membership_id = duplicate_grant.membership_id
        AND keeper_grant.permission_id = duplicate_grant.permission_id
        AND keeper_grant.status = 'ACTIVE'
        AND (keeper_grant.granted_at < duplicate_grant.granted_at
             OR (keeper_grant.granted_at = duplicate_grant.granted_at
                 AND CAST(keeper_grant.id AS VARCHAR) < CAST(duplicate_grant.id AS VARCHAR)))
  );
DELETE FROM permission_grants grant_row
WHERE EXISTS (
    SELECT 1 FROM permissions source_permission
    JOIN permission_catalog_v4_code_map_20260823 mapping ON mapping.source_permission_name = source_permission.name
    WHERE source_permission.id = grant_row.permission_id AND mapping.target_permission_name IS NULL
);
--rollback SELECT 1;

--changeset e-report:2026-08-23-058-retired-permission-api-tokens dbms:mysql,h2
INSERT INTO api_token_permissions (token_id, permission_name)
SELECT DISTINCT source_token.token_id, mapping.target_permission_name
FROM api_token_permissions source_token
JOIN permission_catalog_v4_code_map_20260823 mapping ON mapping.source_permission_name = source_token.permission_name
WHERE mapping.target_permission_name IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM api_token_permissions target_token
      WHERE target_token.token_id = source_token.token_id
        AND target_token.permission_name = mapping.target_permission_name
  );
DELETE FROM api_token_permissions
WHERE permission_name IN (SELECT source_permission_name FROM permission_catalog_v4_code_map_20260823);
--rollback SELECT 1;

--changeset e-report:2026-08-23-058-drop-v3-control-plane dbms:mysql,h2
DROP TABLE privileged_action_approvals;
DROP TABLE privileged_action_requests;
DROP TABLE delegation_grants;
DROP TABLE authority_actor_assignments;
DROP TABLE subject_protections;
DROP TABLE management_domains;
--rollback SELECT 1;

--changeset e-report:2026-08-23-058-retired-permission-delete-mysql dbms:mysql
DELETE source_permission
FROM permissions source_permission
JOIN permission_catalog_v4_code_map_20260823 mapping ON mapping.source_permission_name = source_permission.name;
--rollback SELECT 1;

--changeset e-report:2026-08-23-058-retired-permission-delete-h2 dbms:h2
DELETE FROM permissions
WHERE name IN (SELECT source_permission_name FROM permission_catalog_v4_code_map_20260823);
--rollback SELECT 1;

--changeset e-report:2026-08-23-058-retired-permission-final-assert dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permissions WHERE name IN (SELECT source_permission_name FROM permission_catalog_v4_code_map_20260823)
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM (SELECT tenant_id, membership_id, permission_id FROM permission_grants WHERE status = 'ACTIVE' GROUP BY tenant_id, membership_id, permission_id HAVING COUNT(*) > 1) duplicate_active_grants
SELECT 1;
--rollback SELECT 1;
