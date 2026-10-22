--liquibase formatted sql

--changeset e-report:2026-08-10-021-release-policy-role-mysql dbms:mysql
--comment Add a separate release-policy permission and role, then preserve existing publisher assignments and grant scopes.
INSERT INTO permissions (id, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), 'app-update:policy-update', '安装包策略修改',
       '修改已发布版本的强制更新、最低支持构建号和更新说明'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'app-update:policy-update'
);

INSERT INTO roles (id, tenant_id, built_in, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), tenant.id, TRUE, 'ROLE_RELEASE_POLICY_MANAGER',
       '安装包策略管理员', '查看并维护安卓应用安装包的升级策略。'
FROM tenants tenant
WHERE NOT EXISTS (
    SELECT 1 FROM roles existing
    WHERE existing.tenant_id = tenant.id
      AND existing.name = 'ROLE_RELEASE_POLICY_MANAGER'
);

INSERT INTO role_permissions (role_id, permission_id, scope_profile)
SELECT role_row.id, permission_row.id, 'GRANT_SCOPE'
FROM roles role_row
JOIN permissions permission_row
  ON permission_row.name IN ('management:access', 'app-update:view', 'app-update:policy-update')
WHERE role_row.name = 'ROLE_RELEASE_POLICY_MANAGER'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role_row.id
        AND existing.permission_id = permission_row.id
  );

INSERT INTO user_roles (user_id, role_id)
SELECT DISTINCT source_assignment.user_id, target_role.id
FROM user_roles source_assignment
JOIN roles source_role
  ON source_role.id = source_assignment.role_id
 AND source_role.name = 'ROLE_RELEASE_PUBLISHER'
JOIN roles target_role
  ON target_role.tenant_id = source_role.tenant_id
 AND target_role.name = 'ROLE_RELEASE_POLICY_MANAGER'
WHERE NOT EXISTS (
    SELECT 1 FROM user_roles existing
    WHERE existing.user_id = source_assignment.user_id
      AND existing.role_id = target_role.id
);

INSERT INTO membership_role_grants (
    id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at
)
SELECT UUID_TO_BIN(UUID(), 1), source_grant.tenant_id, source_grant.membership_id,
       target_role.id, source_grant.data_scope, source_grant.scope_org_id,
       source_grant.granted_at, source_grant.expires_at
FROM membership_role_grants source_grant
JOIN roles source_role
  ON source_role.id = source_grant.role_id
 AND source_role.name = 'ROLE_RELEASE_PUBLISHER'
JOIN roles target_role
  ON target_role.tenant_id = source_role.tenant_id
 AND target_role.name = 'ROLE_RELEASE_POLICY_MANAGER'
WHERE NOT EXISTS (
    SELECT 1 FROM membership_role_grants existing
    WHERE existing.tenant_id = source_grant.tenant_id
      AND existing.membership_id = source_grant.membership_id
      AND existing.role_id = target_role.id
);

UPDATE users member
SET authorization_version = authorization_version + 1
WHERE EXISTS (
    SELECT 1
    FROM user_roles assignment
    JOIN roles role_row ON role_row.id = assignment.role_id
    WHERE assignment.user_id = member.id
      AND role_row.name = 'ROLE_RELEASE_POLICY_MANAGER'
);

--rollback DELETE grant_row FROM membership_role_grants grant_row JOIN roles role_row ON role_row.id = grant_row.role_id WHERE role_row.name = 'ROLE_RELEASE_POLICY_MANAGER';
--rollback DELETE assignment FROM user_roles assignment JOIN roles role_row ON role_row.id = assignment.role_id WHERE role_row.name = 'ROLE_RELEASE_POLICY_MANAGER';
--rollback DELETE rule_row FROM role_permissions rule_row JOIN roles role_row ON role_row.id = rule_row.role_id WHERE role_row.name = 'ROLE_RELEASE_POLICY_MANAGER';
--rollback DELETE FROM roles WHERE name = 'ROLE_RELEASE_POLICY_MANAGER';
--rollback DELETE FROM permissions WHERE name = 'app-update:policy-update';

--changeset e-report:2026-08-10-021-release-policy-role-h2 dbms:h2
--comment H2 equivalent for the release-policy permission, role and preserved publisher assignments.
INSERT INTO permissions (id, name, display_name, description)
SELECT RANDOM_UUID(), 'app-update:policy-update', '安装包策略修改',
       '修改已发布版本的强制更新、最低支持构建号和更新说明'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'app-update:policy-update'
);

INSERT INTO roles (id, tenant_id, built_in, name, display_name, description)
SELECT RANDOM_UUID(), tenant.id, TRUE, 'ROLE_RELEASE_POLICY_MANAGER',
       '安装包策略管理员', '查看并维护安卓应用安装包的升级策略。'
FROM tenants tenant
WHERE NOT EXISTS (
    SELECT 1 FROM roles existing
    WHERE existing.tenant_id = tenant.id
      AND existing.name = 'ROLE_RELEASE_POLICY_MANAGER'
);

INSERT INTO role_permissions (role_id, permission_id, scope_profile)
SELECT role_row.id, permission_row.id, 'GRANT_SCOPE'
FROM roles role_row
JOIN permissions permission_row
  ON permission_row.name IN ('management:access', 'app-update:view', 'app-update:policy-update')
WHERE role_row.name = 'ROLE_RELEASE_POLICY_MANAGER'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = role_row.id
        AND existing.permission_id = permission_row.id
  );

INSERT INTO user_roles (user_id, role_id)
SELECT DISTINCT source_assignment.user_id, target_role.id
FROM user_roles source_assignment
JOIN roles source_role
  ON source_role.id = source_assignment.role_id
 AND source_role.name = 'ROLE_RELEASE_PUBLISHER'
JOIN roles target_role
  ON target_role.tenant_id = source_role.tenant_id
 AND target_role.name = 'ROLE_RELEASE_POLICY_MANAGER'
WHERE NOT EXISTS (
    SELECT 1 FROM user_roles existing
    WHERE existing.user_id = source_assignment.user_id
      AND existing.role_id = target_role.id
);

INSERT INTO membership_role_grants (
    id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at
)
SELECT RANDOM_UUID(), source_grant.tenant_id, source_grant.membership_id,
       target_role.id, source_grant.data_scope, source_grant.scope_org_id,
       source_grant.granted_at, source_grant.expires_at
FROM membership_role_grants source_grant
JOIN roles source_role
  ON source_role.id = source_grant.role_id
 AND source_role.name = 'ROLE_RELEASE_PUBLISHER'
JOIN roles target_role
  ON target_role.tenant_id = source_role.tenant_id
 AND target_role.name = 'ROLE_RELEASE_POLICY_MANAGER'
WHERE NOT EXISTS (
    SELECT 1 FROM membership_role_grants existing
    WHERE existing.tenant_id = source_grant.tenant_id
      AND existing.membership_id = source_grant.membership_id
      AND existing.role_id = target_role.id
);

UPDATE users member
SET authorization_version = authorization_version + 1
WHERE EXISTS (
    SELECT 1
    FROM user_roles assignment
    JOIN roles role_row ON role_row.id = assignment.role_id
    WHERE assignment.user_id = member.id
      AND role_row.name = 'ROLE_RELEASE_POLICY_MANAGER'
);

--rollback DELETE FROM membership_role_grants WHERE role_id IN (SELECT id FROM roles WHERE name = 'ROLE_RELEASE_POLICY_MANAGER');
--rollback DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE name = 'ROLE_RELEASE_POLICY_MANAGER');
--rollback DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE name = 'ROLE_RELEASE_POLICY_MANAGER');
--rollback DELETE FROM roles WHERE name = 'ROLE_RELEASE_POLICY_MANAGER';
--rollback DELETE FROM permissions WHERE name = 'app-update:policy-update';
