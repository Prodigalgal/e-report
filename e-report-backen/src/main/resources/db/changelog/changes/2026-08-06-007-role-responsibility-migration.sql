--liquibase formatted sql

--changeset e-report:2026-08-06-007-split-management-permissions dbms:mysql,h2
--comment Replace broad management permissions with endpoint-level permissions before application startup.
INSERT INTO permissions (name, display_name, description)
SELECT 'role:manage:view', '角色查看', '查看当前租户角色和固定权限目录' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'role:manage:view');
INSERT INTO permissions (name, display_name, description)
SELECT 'role:manage:create', '角色创建', '创建当前租户自定义角色' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'role:manage:create');
INSERT INTO permissions (name, display_name, description)
SELECT 'role:manage:update', '角色修改', '修改当前租户自定义角色' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'role:manage:update');
INSERT INTO permissions (name, display_name, description)
SELECT 'role:manage:delete', '角色删除', '删除未被用户使用的自定义角色' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'role:manage:delete');
INSERT INTO permissions (name, display_name, description)
SELECT 'organization:manage:view', '组织查看', '查看授权范围内的组织目录' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'organization:manage:view');
INSERT INTO permissions (name, display_name, description)
SELECT 'organization:manage:create', '组织创建', '在授权范围内创建组织节点' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'organization:manage:create');
INSERT INTO permissions (name, display_name, description)
SELECT 'organization:manage:update', '组织修改', '修改授权范围内的组织节点' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'organization:manage:update');
INSERT INTO permissions (name, display_name, description)
SELECT 'app-update:view', '安装包查看', '查看 Android 安装包发布记录' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'app-update:view');
INSERT INTO permissions (name, display_name, description)
SELECT 'app-update:publish', '安装包发布', '登记并发布 Android 安装包' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'app-update:publish');
INSERT INTO permissions (name, display_name, description)
SELECT 'app-update:delete', '安装包删除', '删除不再使用的安装包发布记录' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'app-update:delete');
INSERT INTO permissions (name, display_name, description)
SELECT 'app-update:storage', '安装包存储维护', '同步或清理安装包对象存储' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'app-update:storage');
INSERT INTO permissions (name, display_name, description)
SELECT 'tenant:manage:view', '市公司租户查看', '查看市公司租户和省级租户组' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'tenant:manage:view');
INSERT INTO permissions (name, display_name, description)
SELECT 'tenant:manage:create', '市公司租户创建', '创建市公司租户并初始化管理员' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'tenant:manage:create');
INSERT INTO permissions (name, display_name, description)
SELECT 'tenant:manage:update-status', '市公司租户状态修改', '启用或停用市公司租户' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'tenant:manage:update-status');
INSERT INTO permissions (name, display_name, description)
SELECT 'sla-policy:view', 'SLA策略查看', '查看当前市公司的故障 SLA 策略' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'sla-policy:view');
INSERT INTO permissions (name, display_name, description)
SELECT 'sla-policy:update', 'SLA策略修改', '修改当前市公司的故障 SLA 策略' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'sla-policy:update');

INSERT INTO role_permissions (role_id, permission_id)
SELECT legacy_role_permission.role_id, replacement_permission.id
FROM role_permissions legacy_role_permission
JOIN permissions legacy_permission ON legacy_permission.id = legacy_role_permission.permission_id
JOIN (
    SELECT 'role:manage:all' legacy_name, 'role:manage:view' replacement_name
    UNION ALL SELECT 'role:manage:all', 'role:manage:create'
    UNION ALL SELECT 'role:manage:all', 'role:manage:update'
    UNION ALL SELECT 'role:manage:all', 'role:manage:delete'
    UNION ALL SELECT 'organization:manage:all', 'organization:manage:view'
    UNION ALL SELECT 'organization:manage:all', 'organization:manage:create'
    UNION ALL SELECT 'organization:manage:all', 'organization:manage:update'
    UNION ALL SELECT 'app-update:manage', 'app-update:view'
    UNION ALL SELECT 'app-update:manage', 'app-update:publish'
    UNION ALL SELECT 'app-update:manage', 'app-update:delete'
    UNION ALL SELECT 'app-update:manage', 'app-update:storage'
    UNION ALL SELECT 'tenant:manage:all', 'tenant:manage:view'
    UNION ALL SELECT 'tenant:manage:all', 'tenant:manage:create'
    UNION ALL SELECT 'tenant:manage:all', 'tenant:manage:update-status'
    UNION ALL SELECT 'work-order:close', 'work-order:normal-fault:verify'
    UNION ALL SELECT 'work-order:close', 'work-order:unclosed-box:verify'
    UNION ALL SELECT 'work-order:close', 'work-order:damaged-box-replacement:verify'
) permission_mapping ON permission_mapping.legacy_name = legacy_permission.name
JOIN permissions replacement_permission ON replacement_permission.name = permission_mapping.replacement_name
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions existing
    WHERE existing.role_id = legacy_role_permission.role_id
      AND existing.permission_id = replacement_permission.id
);

DELETE FROM role_permissions
WHERE permission_id IN (
    SELECT id FROM permissions
    WHERE name IN ('role:manage:all', 'organization:manage:all', 'app-update:manage', 'tenant:manage:all', 'work-order:close')
);
DELETE FROM permissions
WHERE name IN ('role:manage:all', 'organization:manage:all', 'app-update:manage', 'tenant:manage:all', 'work-order:close');

--rollback SELECT 1;

--changeset e-report:2026-08-06-007-seed-responsibility-roles dbms:mysql,h2
--comment Seed the split responsibility roles before migrating legacy grants.
INSERT INTO roles (tenant_id, built_in, name, display_name, description)
SELECT t.id, TRUE, 'ROLE_WORK_ORDER_VERIFIER', '工单验收员', '负责授权范围内工单的验收通过和整改驳回，不包含派调权限。'
FROM tenants t
WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.tenant_id = t.id AND r.name = 'ROLE_WORK_ORDER_VERIFIER');

INSERT INTO roles (tenant_id, built_in, name, display_name, description)
SELECT t.id, TRUE, 'ROLE_WORK_ORDER_CONTROLLER', '工单管控员', '负责授权范围内工单的附件查看和异常取消，不包含派调或验收权限。'
FROM tenants t
WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.tenant_id = t.id AND r.name = 'ROLE_WORK_ORDER_CONTROLLER');

INSERT INTO roles (tenant_id, built_in, name, display_name, description)
SELECT t.id, TRUE, 'ROLE_ROLE_MANAGER', '角色管理员', '负责自定义角色及固定权限组合管理。'
FROM tenants t
WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.tenant_id = t.id AND r.name = 'ROLE_ROLE_MANAGER');

INSERT INTO roles (tenant_id, built_in, name, display_name, description)
SELECT t.id, TRUE, 'ROLE_ORG_MANAGER', '组织管理员', '负责授权范围内的组织目录维护。'
FROM tenants t
WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.tenant_id = t.id AND r.name = 'ROLE_ORG_MANAGER');

INSERT INTO roles (tenant_id, built_in, name, display_name, description)
SELECT t.id, TRUE, 'ROLE_CONFIG_MANAGER', '配置管理员', '负责授权范围内的系统和通知配置。'
FROM tenants t
WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.tenant_id = t.id AND r.name = 'ROLE_CONFIG_MANAGER');

INSERT INTO roles (tenant_id, built_in, name, display_name, description)
SELECT t.id, TRUE, 'ROLE_SLA_MANAGER', 'SLA管理员', '负责查看和维护当前市公司的故障 SLA 策略。'
FROM tenants t
WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.tenant_id = t.id AND r.name = 'ROLE_SLA_MANAGER');

INSERT INTO roles (tenant_id, built_in, name, display_name, description)
SELECT t.id, TRUE, 'ROLE_RELEASE_MANAGER', '安装包发布员', '负责 Android 安装包的查看、发布和存储维护。'
FROM tenants t
WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.tenant_id = t.id AND r.name = 'ROLE_RELEASE_MANAGER');

--rollback SELECT 1;

--changeset e-report:2026-08-06-007-migrate-legacy-responsibility-roles dbms:mysql,h2
--comment Map the duplicate responder and supervisor roles to explicit responsibilities while preserving every grant scope.
INSERT INTO user_roles (user_id, role_id)
SELECT ur.user_id, target.id
FROM user_roles ur
JOIN roles source ON source.id = ur.role_id
JOIN roles target ON target.tenant_id = source.tenant_id
    AND target.name IN ('ROLE_NORMAL_FAULT_WORKER', 'ROLE_UNCLOSED_BOX_WORKER', 'ROLE_BOX_REPLACEMENT_WORKER')
WHERE source.name = 'ROLE_WORK_ORDER_RESPONDER'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles existing
      WHERE existing.user_id = ur.user_id AND existing.role_id = target.id
  );

INSERT INTO membership_role_grants (tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at)
SELECT grant_row.tenant_id, grant_row.membership_id, target.id, grant_row.data_scope,
       grant_row.scope_org_id, grant_row.granted_at, grant_row.expires_at
FROM membership_role_grants grant_row
JOIN roles source ON source.id = grant_row.role_id
JOIN roles target ON target.tenant_id = source.tenant_id
    AND target.name IN ('ROLE_NORMAL_FAULT_WORKER', 'ROLE_UNCLOSED_BOX_WORKER', 'ROLE_BOX_REPLACEMENT_WORKER')
WHERE source.name = 'ROLE_WORK_ORDER_RESPONDER'
  AND NOT EXISTS (
      SELECT 1 FROM membership_role_grants existing
      WHERE existing.tenant_id = grant_row.tenant_id
        AND existing.membership_id = grant_row.membership_id
        AND existing.role_id = target.id
        AND existing.data_scope = grant_row.data_scope
        AND (existing.scope_org_id = grant_row.scope_org_id OR (existing.scope_org_id IS NULL AND grant_row.scope_org_id IS NULL))
  );

INSERT INTO user_roles (user_id, role_id)
SELECT ur.user_id, target.id
FROM user_roles ur
JOIN roles source ON source.id = ur.role_id
JOIN roles target ON target.tenant_id = source.tenant_id
    AND target.name IN ('ROLE_WORK_ORDER_DISPATCHER', 'ROLE_WORK_ORDER_VERIFIER', 'ROLE_WORK_ORDER_CONTROLLER')
WHERE source.name = 'ROLE_WORK_ORDER_SUPERVISOR'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles existing
      WHERE existing.user_id = ur.user_id AND existing.role_id = target.id
  );

INSERT INTO membership_role_grants (tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at)
SELECT grant_row.tenant_id, grant_row.membership_id, target.id, grant_row.data_scope,
       grant_row.scope_org_id, grant_row.granted_at, grant_row.expires_at
FROM membership_role_grants grant_row
JOIN roles source ON source.id = grant_row.role_id
JOIN roles target ON target.tenant_id = source.tenant_id
    AND target.name IN ('ROLE_WORK_ORDER_DISPATCHER', 'ROLE_WORK_ORDER_VERIFIER', 'ROLE_WORK_ORDER_CONTROLLER')
WHERE source.name = 'ROLE_WORK_ORDER_SUPERVISOR'
  AND NOT EXISTS (
      SELECT 1 FROM membership_role_grants existing
      WHERE existing.tenant_id = grant_row.tenant_id
        AND existing.membership_id = grant_row.membership_id
        AND existing.role_id = target.id
        AND existing.data_scope = grant_row.data_scope
        AND (existing.scope_org_id = grant_row.scope_org_id OR (existing.scope_org_id IS NULL AND grant_row.scope_org_id IS NULL))
  );

DELETE FROM membership_role_grants
WHERE role_id IN (SELECT id FROM roles WHERE name IN ('ROLE_WORK_ORDER_RESPONDER', 'ROLE_WORK_ORDER_SUPERVISOR'));
DELETE FROM user_roles
WHERE role_id IN (SELECT id FROM roles WHERE name IN ('ROLE_WORK_ORDER_RESPONDER', 'ROLE_WORK_ORDER_SUPERVISOR'));
DELETE FROM role_permissions
WHERE role_id IN (SELECT id FROM roles WHERE name IN ('ROLE_WORK_ORDER_RESPONDER', 'ROLE_WORK_ORDER_SUPERVISOR'));
DELETE FROM roles
WHERE name IN ('ROLE_WORK_ORDER_RESPONDER', 'ROLE_WORK_ORDER_SUPERVISOR') AND built_in = TRUE;

--rollback SELECT 1;
