--liquibase formatted sql

--changeset e-report:2026-08-09-017-seed-role-access-mysql dbms:mysql
--comment Seed management gates and narrow read/read-write role definitions using binary UUID identifiers.
INSERT INTO permissions (id, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), 'management:access', '管理工作台访问', '进入管理工作台；具体页面和操作仍需对应权限'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'management:access');

INSERT INTO permissions (id, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), 'dashboard:statistics:view', '管理仪表盘查看', '查看授权组织范围内的工单统计、时限和区县对比'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'dashboard:statistics:view');

INSERT INTO roles (id, tenant_id, built_in, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), t.id, TRUE, seed.name, seed.display_name, seed.description
FROM tenants t
CROSS JOIN (
    SELECT 'ROLE_USER' name, '普通用户' display_name, '基础登录角色，不包含工单操作权限。' description, FALSE platform_only
    UNION ALL SELECT 'ROLE_FRONTLINE_REPORTER', '一线提单者', '负责现场工单上报、跟踪并验收本人提交的工单。', FALSE
    UNION ALL SELECT 'ROLE_NORMAL_FAULT_WORKER', '故障工单处理人员', '负责普通故障工单的接单、处理、挂起和附件下载。', FALSE
    UNION ALL SELECT 'ROLE_UNCLOSED_BOX_WORKER', '未关箱工单处理人员', '负责未关箱工单的接单、处理、挂起和附件下载。', FALSE
    UNION ALL SELECT 'ROLE_BOX_REPLACEMENT_WORKER', '破损换箱工单处理人员', '负责破损换箱工单的接单、处理、挂起和附件下载。', FALSE
    UNION ALL SELECT 'ROLE_WORK_ORDER_DISPATCHER', '工单调度员', '负责授权范围内工单的派单和调单。', FALSE
    UNION ALL SELECT 'ROLE_WORK_ORDER_VERIFIER', '工单验收员', '负责授权范围内工单的验收通过和整改驳回，不包含派调权限。', FALSE
    UNION ALL SELECT 'ROLE_WORK_ORDER_CONTROLLER', '工单管控员', '负责授权范围内工单的附件查看和异常取消，不包含派调或验收权限。', FALSE
    UNION ALL SELECT 'ROLE_WORK_ORDER_AUDITOR', '工单审计员', '只读查看授权范围内工单、附件、生命周期和管理仪表盘。', FALSE
    UNION ALL SELECT 'ROLE_USER_VIEWER', '用户查看员', '只读查看授权范围内的用户和角色授权。', FALSE
    UNION ALL SELECT 'ROLE_USER_MANAGER', '用户管理员', '负责用户资料、状态、角色和密码维护。', FALSE
    UNION ALL SELECT 'ROLE_ROLE_VIEWER', '角色查看员', '只读查看当前租户的角色和固定权限目录。', FALSE
    UNION ALL SELECT 'ROLE_ROLE_MANAGER', '角色管理员', '负责自定义角色及固定权限组合管理。', FALSE
    UNION ALL SELECT 'ROLE_ORG_VIEWER', '组织查看员', '只读查看授权范围内的组织目录。', FALSE
    UNION ALL SELECT 'ROLE_ORG_MANAGER', '组织管理员', '负责授权范围内的组织目录维护。', FALSE
    UNION ALL SELECT 'ROLE_CONFIG_VIEWER', '配置查看员', '只读查看授权范围内的系统和通知配置。', FALSE
    UNION ALL SELECT 'ROLE_CONFIG_MANAGER', '配置管理员', '负责授权范围内的系统和通知配置。', FALSE
    UNION ALL SELECT 'ROLE_SLA_VIEWER', '时限查看员', '只读查看当前市公司的故障时限策略。', FALSE
    UNION ALL SELECT 'ROLE_SLA_MANAGER', '时限管理员', '负责查看和维护当前市公司的故障时限策略。', FALSE
    UNION ALL SELECT 'ROLE_RELEASE_VIEWER', '安装包查看员', '只读查看安卓应用安装包发布记录。', FALSE
    UNION ALL SELECT 'ROLE_RELEASE_MANAGER', '安装包发布员', '负责安卓应用安装包的查看、发布和存储维护。', FALSE
    UNION ALL SELECT 'ROLE_OBSERVABILITY_VIEWER', '运行诊断查看员', '查看授权组织范围内的客户端运行诊断。', FALSE
    UNION ALL SELECT 'ROLE_COUNTY_ADMIN', '区县管理员', '区县管理身份标记；实际能力由窄职责角色组合并受组织范围约束。', FALSE
    UNION ALL SELECT 'ROLE_ADMIN', '市公司管理员', '市公司管理身份标记；实际能力由窄职责角色组合。', FALSE
    UNION ALL SELECT 'ROLE_TENANT_VIEWER', '市公司租户查看员', '只读查看平台内的市公司租户。', TRUE
    UNION ALL SELECT 'ROLE_TENANT_MANAGER', '市公司租户管理员', '负责市公司租户的创建和启停维护。', TRUE
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', '平台租户管理员', '平台管理身份标记；租户维护能力由窄职责角色组合。', TRUE
) seed
WHERE (seed.platform_only = FALSE OR t.code = '${defaultTenantCode}')
  AND NOT EXISTS (
      SELECT 1 FROM roles existing WHERE existing.tenant_id = t.id AND existing.name = seed.name
  );

--rollback SELECT 1;

--changeset e-report:2026-08-09-017-seed-role-access-h2 dbms:h2
--comment H2 equivalent for management gates and narrow role definitions.
INSERT INTO permissions (id, name, display_name, description)
SELECT RANDOM_UUID(), 'management:access', '管理工作台访问', '进入管理工作台；具体页面和操作仍需对应权限'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'management:access');

INSERT INTO permissions (id, name, display_name, description)
SELECT RANDOM_UUID(), 'dashboard:statistics:view', '管理仪表盘查看', '查看授权组织范围内的工单统计、时限和区县对比'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'dashboard:statistics:view');

INSERT INTO roles (id, tenant_id, built_in, name, display_name, description)
SELECT RANDOM_UUID(), t.id, TRUE, seed.name, seed.display_name, seed.description
FROM tenants t
CROSS JOIN (
    SELECT 'ROLE_USER' name, '普通用户' display_name, '基础登录角色，不包含工单操作权限。' description, FALSE platform_only
    UNION ALL SELECT 'ROLE_FRONTLINE_REPORTER', '一线提单者', '负责现场工单上报、跟踪并验收本人提交的工单。', FALSE
    UNION ALL SELECT 'ROLE_NORMAL_FAULT_WORKER', '故障工单处理人员', '负责普通故障工单的接单、处理、挂起和附件下载。', FALSE
    UNION ALL SELECT 'ROLE_UNCLOSED_BOX_WORKER', '未关箱工单处理人员', '负责未关箱工单的接单、处理、挂起和附件下载。', FALSE
    UNION ALL SELECT 'ROLE_BOX_REPLACEMENT_WORKER', '破损换箱工单处理人员', '负责破损换箱工单的接单、处理、挂起和附件下载。', FALSE
    UNION ALL SELECT 'ROLE_WORK_ORDER_DISPATCHER', '工单调度员', '负责授权范围内工单的派单和调单。', FALSE
    UNION ALL SELECT 'ROLE_WORK_ORDER_VERIFIER', '工单验收员', '负责授权范围内工单的验收通过和整改驳回，不包含派调权限。', FALSE
    UNION ALL SELECT 'ROLE_WORK_ORDER_CONTROLLER', '工单管控员', '负责授权范围内工单的附件查看和异常取消，不包含派调或验收权限。', FALSE
    UNION ALL SELECT 'ROLE_WORK_ORDER_AUDITOR', '工单审计员', '只读查看授权范围内工单、附件、生命周期和管理仪表盘。', FALSE
    UNION ALL SELECT 'ROLE_USER_VIEWER', '用户查看员', '只读查看授权范围内的用户和角色授权。', FALSE
    UNION ALL SELECT 'ROLE_USER_MANAGER', '用户管理员', '负责用户资料、状态、角色和密码维护。', FALSE
    UNION ALL SELECT 'ROLE_ROLE_VIEWER', '角色查看员', '只读查看当前租户的角色和固定权限目录。', FALSE
    UNION ALL SELECT 'ROLE_ROLE_MANAGER', '角色管理员', '负责自定义角色及固定权限组合管理。', FALSE
    UNION ALL SELECT 'ROLE_ORG_VIEWER', '组织查看员', '只读查看授权范围内的组织目录。', FALSE
    UNION ALL SELECT 'ROLE_ORG_MANAGER', '组织管理员', '负责授权范围内的组织目录维护。', FALSE
    UNION ALL SELECT 'ROLE_CONFIG_VIEWER', '配置查看员', '只读查看授权范围内的系统和通知配置。', FALSE
    UNION ALL SELECT 'ROLE_CONFIG_MANAGER', '配置管理员', '负责授权范围内的系统和通知配置。', FALSE
    UNION ALL SELECT 'ROLE_SLA_VIEWER', '时限查看员', '只读查看当前市公司的故障时限策略。', FALSE
    UNION ALL SELECT 'ROLE_SLA_MANAGER', '时限管理员', '负责查看和维护当前市公司的故障时限策略。', FALSE
    UNION ALL SELECT 'ROLE_RELEASE_VIEWER', '安装包查看员', '只读查看安卓应用安装包发布记录。', FALSE
    UNION ALL SELECT 'ROLE_RELEASE_MANAGER', '安装包发布员', '负责安卓应用安装包的查看、发布和存储维护。', FALSE
    UNION ALL SELECT 'ROLE_OBSERVABILITY_VIEWER', '运行诊断查看员', '查看授权组织范围内的客户端运行诊断。', FALSE
    UNION ALL SELECT 'ROLE_COUNTY_ADMIN', '区县管理员', '区县管理身份标记；实际能力由窄职责角色组合并受组织范围约束。', FALSE
    UNION ALL SELECT 'ROLE_ADMIN', '市公司管理员', '市公司管理身份标记；实际能力由窄职责角色组合。', FALSE
    UNION ALL SELECT 'ROLE_TENANT_VIEWER', '市公司租户查看员', '只读查看平台内的市公司租户。', TRUE
    UNION ALL SELECT 'ROLE_TENANT_MANAGER', '市公司租户管理员', '负责市公司租户的创建和启停维护。', TRUE
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', '平台租户管理员', '平台管理身份标记；租户维护能力由窄职责角色组合。', TRUE
) seed
WHERE (seed.platform_only = FALSE OR t.code = '${defaultTenantCode}')
  AND NOT EXISTS (
      SELECT 1 FROM roles existing WHERE existing.tenant_id = t.id AND existing.name = seed.name
  );

--rollback SELECT 1;

--changeset e-report:2026-08-09-017-sync-narrow-role-permissions dbms:mysql,h2
--comment Assign management entry only to management-plane responsibilities and seed explicit read roles.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'management:access'
WHERE r.name IN (
    'ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN',
    'ROLE_WORK_ORDER_DISPATCHER', 'ROLE_WORK_ORDER_VERIFIER', 'ROLE_WORK_ORDER_CONTROLLER', 'ROLE_WORK_ORDER_AUDITOR',
    'ROLE_USER_VIEWER', 'ROLE_USER_MANAGER', 'ROLE_ROLE_VIEWER', 'ROLE_ROLE_MANAGER',
    'ROLE_ORG_VIEWER', 'ROLE_ORG_MANAGER', 'ROLE_CONFIG_VIEWER', 'ROLE_CONFIG_MANAGER',
    'ROLE_SLA_VIEWER', 'ROLE_SLA_MANAGER', 'ROLE_RELEASE_VIEWER', 'ROLE_RELEASE_MANAGER',
    'ROLE_OBSERVABILITY_VIEWER', 'ROLE_TENANT_VIEWER', 'ROLE_TENANT_MANAGER'
)
AND NOT EXISTS (
    SELECT 1 FROM role_permissions existing
    WHERE existing.role_id = r.id AND existing.permission_id = p.id
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON (
    (r.name = 'ROLE_USER_VIEWER' AND p.name = 'user:manage:view')
    OR (r.name = 'ROLE_ROLE_VIEWER' AND p.name = 'role:manage:view')
    OR (r.name = 'ROLE_ORG_VIEWER' AND p.name = 'organization:manage:view')
    OR (r.name = 'ROLE_CONFIG_VIEWER' AND p.name = 'system:config:view')
    OR (r.name = 'ROLE_SLA_VIEWER' AND p.name = 'sla-policy:view')
    OR (r.name = 'ROLE_RELEASE_VIEWER' AND p.name = 'app-update:view')
    OR (r.name = 'ROLE_TENANT_VIEWER' AND p.name = 'tenant:manage:view')
    OR (r.name = 'ROLE_TENANT_MANAGER' AND p.name IN ('tenant:manage:view', 'tenant:manage:create', 'tenant:manage:update-status'))
    OR (r.name = 'ROLE_WORK_ORDER_AUDITOR' AND (
        p.name = 'dashboard:statistics:view'
        OR p.name LIKE 'work-order:%:view'
        OR p.name LIKE 'work-order:%:download'
    ))
)
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions existing
    WHERE existing.role_id = r.id AND existing.permission_id = p.id
);

--rollback SELECT 1;

--changeset e-report:2026-08-09-017-migrate-admin-role-bundles-mysql dbms:mysql
--comment Preserve every existing administrator data scope while expanding identity roles into narrow responsibility roles.
DROP TABLE IF EXISTS admin_role_bundle_map_20260809;
CREATE TABLE admin_role_bundle_map_20260809 (
    source_role_name VARCHAR(50) NOT NULL,
    target_role_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (source_role_name, target_role_name)
);

INSERT INTO admin_role_bundle_map_20260809 (source_role_name, target_role_name) VALUES
('ROLE_ADMIN', 'ROLE_FRONTLINE_REPORTER'),
('ROLE_ADMIN', 'ROLE_NORMAL_FAULT_WORKER'),
('ROLE_ADMIN', 'ROLE_UNCLOSED_BOX_WORKER'),
('ROLE_ADMIN', 'ROLE_BOX_REPLACEMENT_WORKER'),
('ROLE_ADMIN', 'ROLE_WORK_ORDER_DISPATCHER'),
('ROLE_ADMIN', 'ROLE_WORK_ORDER_VERIFIER'),
('ROLE_ADMIN', 'ROLE_WORK_ORDER_CONTROLLER'),
('ROLE_ADMIN', 'ROLE_WORK_ORDER_AUDITOR'),
('ROLE_ADMIN', 'ROLE_USER_MANAGER'),
('ROLE_ADMIN', 'ROLE_ROLE_MANAGER'),
('ROLE_ADMIN', 'ROLE_ORG_MANAGER'),
('ROLE_ADMIN', 'ROLE_CONFIG_MANAGER'),
('ROLE_ADMIN', 'ROLE_SLA_MANAGER'),
('ROLE_ADMIN', 'ROLE_RELEASE_MANAGER'),
('ROLE_ADMIN', 'ROLE_OBSERVABILITY_VIEWER'),
('ROLE_COUNTY_ADMIN', 'ROLE_FRONTLINE_REPORTER'),
('ROLE_COUNTY_ADMIN', 'ROLE_NORMAL_FAULT_WORKER'),
('ROLE_COUNTY_ADMIN', 'ROLE_UNCLOSED_BOX_WORKER'),
('ROLE_COUNTY_ADMIN', 'ROLE_BOX_REPLACEMENT_WORKER'),
('ROLE_COUNTY_ADMIN', 'ROLE_WORK_ORDER_DISPATCHER'),
('ROLE_COUNTY_ADMIN', 'ROLE_WORK_ORDER_VERIFIER'),
('ROLE_COUNTY_ADMIN', 'ROLE_WORK_ORDER_CONTROLLER'),
('ROLE_COUNTY_ADMIN', 'ROLE_WORK_ORDER_AUDITOR'),
('ROLE_COUNTY_ADMIN', 'ROLE_USER_MANAGER'),
('ROLE_COUNTY_ADMIN', 'ROLE_ROLE_MANAGER'),
('ROLE_COUNTY_ADMIN', 'ROLE_ORG_MANAGER'),
('ROLE_COUNTY_ADMIN', 'ROLE_CONFIG_MANAGER'),
('ROLE_COUNTY_ADMIN', 'ROLE_SLA_VIEWER'),
('ROLE_COUNTY_ADMIN', 'ROLE_OBSERVABILITY_VIEWER'),
('ROLE_PLATFORM_ADMIN', 'ROLE_TENANT_MANAGER');

INSERT INTO user_roles (user_id, role_id)
SELECT DISTINCT source_user.user_id, target_role.id
FROM (
    SELECT DISTINCT ur.user_id, source_role.tenant_id, source_role.name source_role_name
    FROM user_roles ur
    JOIN roles source_role ON source_role.id = ur.role_id
    WHERE source_role.name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN')
) source_user
JOIN admin_role_bundle_map_20260809 mapping ON mapping.source_role_name = source_user.source_role_name
JOIN roles target_role ON target_role.tenant_id = source_user.tenant_id AND target_role.name = mapping.target_role_name
WHERE NOT EXISTS (
    SELECT 1 FROM user_roles existing
    WHERE existing.user_id = source_user.user_id AND existing.role_id = target_role.id
);

INSERT INTO membership_role_grants (
    id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at
)
SELECT UUID_TO_BIN(UUID(), 1), candidate.tenant_id, candidate.membership_id, candidate.role_id,
       candidate.data_scope, candidate.scope_org_id, candidate.granted_at, candidate.expires_at
FROM (
    SELECT DISTINCT source_grant.tenant_id, source_grant.membership_id, target_role.id role_id,
           source_grant.data_scope, source_grant.scope_org_id, source_grant.granted_at, source_grant.expires_at
    FROM membership_role_grants source_grant
    JOIN roles source_role ON source_role.id = source_grant.role_id
    JOIN admin_role_bundle_map_20260809 mapping ON mapping.source_role_name = source_role.name
    JOIN roles target_role ON target_role.tenant_id = source_role.tenant_id AND target_role.name = mapping.target_role_name
) candidate
WHERE NOT EXISTS (
    SELECT 1 FROM membership_role_grants existing
    WHERE existing.tenant_id = candidate.tenant_id
      AND existing.membership_id = candidate.membership_id
      AND existing.role_id = candidate.role_id
      AND existing.data_scope = candidate.data_scope
      AND (existing.scope_org_id = candidate.scope_org_id
           OR (existing.scope_org_id IS NULL AND candidate.scope_org_id IS NULL))
      AND (existing.expires_at = candidate.expires_at
           OR (existing.expires_at IS NULL AND candidate.expires_at IS NULL))
);

UPDATE users affected
JOIN (
    SELECT DISTINCT ur.user_id
    FROM user_roles ur
    JOIN roles marker ON marker.id = ur.role_id
    WHERE marker.name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN')
) marker_users ON marker_users.user_id = affected.id
SET affected.authorization_version = affected.authorization_version + 1;

DROP TABLE admin_role_bundle_map_20260809;

--rollback SELECT 1;

--changeset e-report:2026-08-09-017-migrate-admin-role-bundles-h2 dbms:h2
--comment H2 equivalent for administrator role bundle expansion.
DROP TABLE IF EXISTS admin_role_bundle_map_20260809;
CREATE TABLE admin_role_bundle_map_20260809 (
    source_role_name VARCHAR(50) NOT NULL,
    target_role_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (source_role_name, target_role_name)
);

INSERT INTO admin_role_bundle_map_20260809 (source_role_name, target_role_name) VALUES
('ROLE_ADMIN', 'ROLE_FRONTLINE_REPORTER'),
('ROLE_ADMIN', 'ROLE_NORMAL_FAULT_WORKER'),
('ROLE_ADMIN', 'ROLE_UNCLOSED_BOX_WORKER'),
('ROLE_ADMIN', 'ROLE_BOX_REPLACEMENT_WORKER'),
('ROLE_ADMIN', 'ROLE_WORK_ORDER_DISPATCHER'),
('ROLE_ADMIN', 'ROLE_WORK_ORDER_VERIFIER'),
('ROLE_ADMIN', 'ROLE_WORK_ORDER_CONTROLLER'),
('ROLE_ADMIN', 'ROLE_WORK_ORDER_AUDITOR'),
('ROLE_ADMIN', 'ROLE_USER_MANAGER'),
('ROLE_ADMIN', 'ROLE_ROLE_MANAGER'),
('ROLE_ADMIN', 'ROLE_ORG_MANAGER'),
('ROLE_ADMIN', 'ROLE_CONFIG_MANAGER'),
('ROLE_ADMIN', 'ROLE_SLA_MANAGER'),
('ROLE_ADMIN', 'ROLE_RELEASE_MANAGER'),
('ROLE_ADMIN', 'ROLE_OBSERVABILITY_VIEWER'),
('ROLE_COUNTY_ADMIN', 'ROLE_FRONTLINE_REPORTER'),
('ROLE_COUNTY_ADMIN', 'ROLE_NORMAL_FAULT_WORKER'),
('ROLE_COUNTY_ADMIN', 'ROLE_UNCLOSED_BOX_WORKER'),
('ROLE_COUNTY_ADMIN', 'ROLE_BOX_REPLACEMENT_WORKER'),
('ROLE_COUNTY_ADMIN', 'ROLE_WORK_ORDER_DISPATCHER'),
('ROLE_COUNTY_ADMIN', 'ROLE_WORK_ORDER_VERIFIER'),
('ROLE_COUNTY_ADMIN', 'ROLE_WORK_ORDER_CONTROLLER'),
('ROLE_COUNTY_ADMIN', 'ROLE_WORK_ORDER_AUDITOR'),
('ROLE_COUNTY_ADMIN', 'ROLE_USER_MANAGER'),
('ROLE_COUNTY_ADMIN', 'ROLE_ROLE_MANAGER'),
('ROLE_COUNTY_ADMIN', 'ROLE_ORG_MANAGER'),
('ROLE_COUNTY_ADMIN', 'ROLE_CONFIG_MANAGER'),
('ROLE_COUNTY_ADMIN', 'ROLE_SLA_VIEWER'),
('ROLE_COUNTY_ADMIN', 'ROLE_OBSERVABILITY_VIEWER'),
('ROLE_PLATFORM_ADMIN', 'ROLE_TENANT_MANAGER');

INSERT INTO user_roles (user_id, role_id)
SELECT DISTINCT source_user.user_id, target_role.id
FROM (
    SELECT DISTINCT ur.user_id, source_role.tenant_id, source_role.name source_role_name
    FROM user_roles ur
    JOIN roles source_role ON source_role.id = ur.role_id
    WHERE source_role.name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN')
) source_user
JOIN admin_role_bundle_map_20260809 mapping ON mapping.source_role_name = source_user.source_role_name
JOIN roles target_role ON target_role.tenant_id = source_user.tenant_id AND target_role.name = mapping.target_role_name
WHERE NOT EXISTS (
    SELECT 1 FROM user_roles existing
    WHERE existing.user_id = source_user.user_id AND existing.role_id = target_role.id
);

INSERT INTO membership_role_grants (
    id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at
)
SELECT RANDOM_UUID(), candidate.tenant_id, candidate.membership_id, candidate.role_id,
       candidate.data_scope, candidate.scope_org_id, candidate.granted_at, candidate.expires_at
FROM (
    SELECT DISTINCT source_grant.tenant_id, source_grant.membership_id, target_role.id role_id,
           source_grant.data_scope, source_grant.scope_org_id, source_grant.granted_at, source_grant.expires_at
    FROM membership_role_grants source_grant
    JOIN roles source_role ON source_role.id = source_grant.role_id
    JOIN admin_role_bundle_map_20260809 mapping ON mapping.source_role_name = source_role.name
    JOIN roles target_role ON target_role.tenant_id = source_role.tenant_id AND target_role.name = mapping.target_role_name
) candidate
WHERE NOT EXISTS (
    SELECT 1 FROM membership_role_grants existing
    WHERE existing.tenant_id = candidate.tenant_id
      AND existing.membership_id = candidate.membership_id
      AND existing.role_id = candidate.role_id
      AND existing.data_scope = candidate.data_scope
      AND (existing.scope_org_id = candidate.scope_org_id
           OR (existing.scope_org_id IS NULL AND candidate.scope_org_id IS NULL))
      AND (existing.expires_at = candidate.expires_at
           OR (existing.expires_at IS NULL AND candidate.expires_at IS NULL))
);

UPDATE users
SET authorization_version = authorization_version + 1
WHERE id IN (
    SELECT ur.user_id
    FROM user_roles ur
    JOIN roles marker ON marker.id = ur.role_id
    WHERE marker.name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN')
);

DROP TABLE admin_role_bundle_map_20260809;

--rollback SELECT 1;

--changeset e-report:2026-08-09-017-slim-identity-roles-and-config dbms:mysql,h2
--comment Identity roles retain only the management-plane gate after bundle grants have been copied.
DELETE FROM role_permissions
WHERE role_id IN (
    SELECT id FROM roles WHERE name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN')
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'management:access'
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      WHERE existing.role_id = r.id AND existing.permission_id = p.id
  );

UPDATE roles
SET description = CASE name
    WHEN 'ROLE_ADMIN' THEN '市公司管理身份标记；实际能力由窄职责角色组合。'
    WHEN 'ROLE_COUNTY_ADMIN' THEN '区县管理身份标记；实际能力由窄职责角色组合并受组织范围约束。'
    WHEN 'ROLE_PLATFORM_ADMIN' THEN '平台管理身份标记；租户维护能力由窄职责角色组合。'
    ELSE description
END
WHERE name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN');

UPDATE app_config
SET description = '是否采集失败的关键写操作事件'
WHERE config_key = 'observability.client.operation-enabled';

--rollback SELECT 1;
