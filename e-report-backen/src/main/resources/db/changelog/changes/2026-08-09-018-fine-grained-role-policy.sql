--liquibase formatted sql

--changeset e-report:2026-08-09-018-role-policy-schema-mysql dbms:mysql
--comment Add scoped role-permission rules and tenant-isolated customizable role bundles.
ALTER TABLE role_permissions
    ADD COLUMN scope_profile VARCHAR(40) NOT NULL DEFAULT 'GRANT_SCOPE';

CREATE TABLE role_bundles (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    system_key VARCHAR(60),
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 100,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_role_bundles PRIMARY KEY (id),
    CONSTRAINT uk_role_bundles_tenant_name UNIQUE (tenant_id, display_name),
    CONSTRAINT uk_role_bundles_tenant_system_key UNIQUE (tenant_id, system_key),
    CONSTRAINT fk_role_bundles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_role_bundles_tenant_active ON role_bundles (tenant_id, active, sort_order);

CREATE TABLE role_bundle_roles (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    role_bundle_id BINARY(16) NOT NULL,
    role_id BINARY(16) NOT NULL,
    CONSTRAINT pk_role_bundle_roles PRIMARY KEY (id),
    CONSTRAINT uk_role_bundle_roles_bundle_role UNIQUE (role_bundle_id, role_id),
    CONSTRAINT fk_role_bundle_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_role_bundle_roles_bundle FOREIGN KEY (role_bundle_id) REFERENCES role_bundles (id),
    CONSTRAINT fk_role_bundle_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);
CREATE INDEX idx_role_bundle_roles_bundle ON role_bundle_roles (tenant_id, role_bundle_id);
CREATE INDEX idx_role_bundle_roles_role ON role_bundle_roles (role_id);

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-role-policy-schema-h2 dbms:h2
--comment H2 equivalent for scoped role-permission rules and role bundles.
ALTER TABLE role_permissions
    ADD COLUMN scope_profile VARCHAR(40) DEFAULT 'GRANT_SCOPE' NOT NULL;

CREATE TABLE role_bundles (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    system_key VARCHAR(60),
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    built_in BOOLEAN DEFAULT FALSE NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    sort_order INT DEFAULT 100 NOT NULL,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_role_bundles PRIMARY KEY (id),
    CONSTRAINT uk_role_bundles_tenant_name UNIQUE (tenant_id, display_name),
    CONSTRAINT uk_role_bundles_tenant_system_key UNIQUE (tenant_id, system_key),
    CONSTRAINT fk_role_bundles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX idx_role_bundles_tenant_active ON role_bundles (tenant_id, active, sort_order);

CREATE TABLE role_bundle_roles (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    role_bundle_id UUID NOT NULL,
    role_id UUID NOT NULL,
    CONSTRAINT pk_role_bundle_roles PRIMARY KEY (id),
    CONSTRAINT uk_role_bundle_roles_bundle_role UNIQUE (role_bundle_id, role_id),
    CONSTRAINT fk_role_bundle_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_role_bundle_roles_bundle FOREIGN KEY (role_bundle_id) REFERENCES role_bundles (id),
    CONSTRAINT fk_role_bundle_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);
CREATE INDEX idx_role_bundle_roles_bundle ON role_bundle_roles (tenant_id, role_bundle_id);
CREATE INDEX idx_role_bundle_roles_role ON role_bundle_roles (role_id);

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-policy-staging dbms:mysql,h2
--comment Materialize the fixed role contract only for this migration; application catalogs remain the runtime source of truth.
CREATE TABLE work_order_type_map_20260809 (
    role_prefix VARCHAR(40) NOT NULL,
    permission_slug VARCHAR(50) NOT NULL,
    type_display_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (role_prefix)
);
INSERT INTO work_order_type_map_20260809 (role_prefix, permission_slug, type_display_name) VALUES
('NORMAL_FAULT', 'normal-fault', '普通故障工单'),
('UNCLOSED_BOX', 'unclosed-box', '未关箱工单'),
('BOX_REPLACEMENT', 'damaged-box-replacement', '破损换箱工单');

CREATE TABLE work_order_duty_policy_20260809 (
    duty VARCHAR(20) NOT NULL,
    duty_display_name VARCHAR(30) NOT NULL,
    permission_action VARCHAR(30) NOT NULL,
    scope_profile VARCHAR(40) NOT NULL,
    PRIMARY KEY (duty, permission_action)
);
INSERT INTO work_order_duty_policy_20260809 (duty, duty_display_name, permission_action, scope_profile) VALUES
('REPORTER', '提单员', 'report', 'MEMBERSHIP_ORG'),
('REPORTER', '提单员', 'view', 'REPORTER'),
('REPORTER', '提单员', 'history:view', 'REPORTER'),
('REPORTER', '提单员', 'download', 'REPORTER'),
('REPORTER', '提单员', 'verify', 'REPORTER'),
('REPORTER', '提单员', 'withdraw', 'REPORTER'),
('WORKER', '处理员', 'view', 'CLAIMABLE_OR_PARTICIPANT'),
('WORKER', '处理员', 'history:view', 'PARTICIPANT'),
('WORKER', '处理员', 'download', 'PARTICIPANT'),
('WORKER', '处理员', 'claim', 'GRANT_SCOPE'),
('WORKER', '处理员', 'respond', 'PENDING_OR_CURRENT_HANDLER'),
('WORKER', '处理员', 'hold', 'CURRENT_HANDLER'),
('DISPATCHER', '调度员', 'view', 'GRANT_SCOPE'),
('DISPATCHER', '调度员', 'history:view', 'GRANT_SCOPE'),
('DISPATCHER', '调度员', 'download', 'GRANT_SCOPE'),
('DISPATCHER', '调度员', 'assign', 'GRANT_SCOPE'),
('DISPATCHER', '调度员', 'reassign', 'GRANT_SCOPE'),
('VERIFIER', '验收员', 'view', 'GRANT_SCOPE'),
('VERIFIER', '验收员', 'history:view', 'GRANT_SCOPE'),
('VERIFIER', '验收员', 'download', 'GRANT_SCOPE'),
('VERIFIER', '验收员', 'verify', 'GRANT_SCOPE'),
('CONTROLLER', '管控员', 'view', 'GRANT_SCOPE'),
('CONTROLLER', '管控员', 'history:view', 'GRANT_SCOPE'),
('CONTROLLER', '管控员', 'download', 'GRANT_SCOPE'),
('CONTROLLER', '管控员', 'cancel', 'GRANT_SCOPE'),
('AUDITOR', '审计员', 'view', 'GRANT_SCOPE'),
('AUDITOR', '审计员', 'history:view', 'GRANT_SCOPE'),
('AUDITOR', '审计员', 'download', 'GRANT_SCOPE'),
('AUDITOR', '审计员', 'audit:view', 'GRANT_SCOPE');

CREATE TABLE built_in_role_seed_20260809 (
    role_name VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL,
    platform_only BOOLEAN NOT NULL,
    PRIMARY KEY (role_name)
);
INSERT INTO built_in_role_seed_20260809 (role_name, display_name, description, platform_only) VALUES
('ROLE_USER', '普通用户', '基础登录身份，不包含业务操作权限。', FALSE),
('ROLE_ADMIN', '市公司管理员', '市公司管理身份标记；实际能力由窄职责角色组合。', FALSE),
('ROLE_COUNTY_ADMIN', '区县管理员', '区县管理身份标记；实际能力由窄职责角色组合并受组织范围约束。', FALSE),
('ROLE_PLATFORM_ADMIN', '平台租户管理员', '平台管理身份标记；租户维护能力由窄职责角色组合。', TRUE),
('ROLE_USER_VIEWER', '用户查看员', '只读查看授权范围内的用户和角色授权。', FALSE),
('ROLE_USER_DETAILS_EDITOR', '用户资料维护员', '查看并维护授权范围内的用户姓名、手机号和邮箱。', FALSE),
('ROLE_USER_LIFECYCLE_MANAGER', '用户账号管理员', '创建、启停、删除和恢复授权范围内的用户账号。', FALSE),
('ROLE_USER_CREDENTIAL_MANAGER', '用户密码重置员', '重置授权范围内用户的登录密码。', FALSE),
('ROLE_USER_AUTHORIZATION_MANAGER', '用户授权管理员', '分配授权范围内用户的岗位角色和数据范围。', FALSE),
('ROLE_ROLE_VIEWER', '角色查看员', '只读查看当前租户的角色、权限和岗位模板。', FALSE),
('ROLE_ROLE_EDITOR', '角色编辑员', '维护当前租户的自定义角色和岗位模板。', FALSE),
('ROLE_ORG_VIEWER', '组织查看员', '只读查看授权范围内的组织目录。', FALSE),
('ROLE_ORG_EDITOR', '组织编辑员', '创建和修改授权范围内的组织节点。', FALSE),
('ROLE_SYSTEM_CONFIG_VIEWER', '系统配置查看员', '只读查看授权范围内的系统配置。', FALSE),
('ROLE_SYSTEM_CONFIG_EDITOR', '系统配置编辑员', '创建、修改和删除授权范围内的系统配置。', FALSE),
('ROLE_NOTIFICATION_CONFIG_VIEWER', '通知配置查看员', '只读查看授权范围内的通知渠道和定时通知配置。', FALSE),
('ROLE_NOTIFICATION_CONFIG_EDITOR', '通知配置编辑员', '修改授权范围内的通知渠道和定时通知配置。', FALSE),
('ROLE_SLA_VIEWER', '时限查看员', '只读查看当前市公司的故障时限策略。', FALSE),
('ROLE_SLA_EDITOR', '时限编辑员', '查看和修改当前市公司的故障时限策略。', FALSE),
('ROLE_RELEASE_VIEWER', '安装包查看员', '只读查看安卓应用安装包发布记录。', FALSE),
('ROLE_RELEASE_PUBLISHER', '安装包发布员', '查看并登记发布安卓应用安装包。', FALSE),
('ROLE_RELEASE_STORAGE_MANAGER', '安装包存储管理员', '维护和清理安装包发布记录及对象存储。', FALSE),
('ROLE_DASHBOARD_VIEWER', '工单仪表盘查看员', '查看授权组织范围内的工单统计、时限和区县对比。', FALSE),
('ROLE_OBSERVABILITY_VIEWER', '运行诊断查看员', '查看授权组织范围内的客户端运行诊断。', FALSE),
('ROLE_TENANT_VIEWER', '市公司租户查看员', '只读查看平台内的市公司租户。', TRUE),
('ROLE_TENANT_PROVISIONER', '市公司租户创建员', '创建市公司租户及其初始管理员。', TRUE),
('ROLE_TENANT_STATUS_MANAGER', '市公司租户状态管理员', '启用或停用市公司租户。', TRUE);

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-role-replacement-staging dbms:mysql,h2
--comment Map every retired broad role to one or more fine-grained responsibilities.
CREATE TABLE role_replacement_map_20260809 (
    source_role_name VARCHAR(50) NOT NULL,
    target_role_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (source_role_name, target_role_name)
);
INSERT INTO role_replacement_map_20260809 (source_role_name, target_role_name) VALUES
('ROLE_FRONTLINE_REPORTER', 'ROLE_NORMAL_FAULT_REPORTER'),
('ROLE_FRONTLINE_REPORTER', 'ROLE_UNCLOSED_BOX_REPORTER'),
('ROLE_FRONTLINE_REPORTER', 'ROLE_BOX_REPLACEMENT_REPORTER'),
('ROLE_WORK_ORDER_DISPATCHER', 'ROLE_NORMAL_FAULT_DISPATCHER'),
('ROLE_WORK_ORDER_DISPATCHER', 'ROLE_UNCLOSED_BOX_DISPATCHER'),
('ROLE_WORK_ORDER_DISPATCHER', 'ROLE_BOX_REPLACEMENT_DISPATCHER'),
('ROLE_WORK_ORDER_VERIFIER', 'ROLE_NORMAL_FAULT_VERIFIER'),
('ROLE_WORK_ORDER_VERIFIER', 'ROLE_UNCLOSED_BOX_VERIFIER'),
('ROLE_WORK_ORDER_VERIFIER', 'ROLE_BOX_REPLACEMENT_VERIFIER'),
('ROLE_WORK_ORDER_CONTROLLER', 'ROLE_NORMAL_FAULT_CONTROLLER'),
('ROLE_WORK_ORDER_CONTROLLER', 'ROLE_UNCLOSED_BOX_CONTROLLER'),
('ROLE_WORK_ORDER_CONTROLLER', 'ROLE_BOX_REPLACEMENT_CONTROLLER'),
('ROLE_WORK_ORDER_AUDITOR', 'ROLE_NORMAL_FAULT_AUDITOR'),
('ROLE_WORK_ORDER_AUDITOR', 'ROLE_UNCLOSED_BOX_AUDITOR'),
('ROLE_WORK_ORDER_AUDITOR', 'ROLE_BOX_REPLACEMENT_AUDITOR'),
('ROLE_WORK_ORDER_AUDITOR', 'ROLE_DASHBOARD_VIEWER'),
('ROLE_USER_MANAGER', 'ROLE_USER_DETAILS_EDITOR'),
('ROLE_USER_MANAGER', 'ROLE_USER_LIFECYCLE_MANAGER'),
('ROLE_USER_MANAGER', 'ROLE_USER_CREDENTIAL_MANAGER'),
('ROLE_USER_MANAGER', 'ROLE_USER_AUTHORIZATION_MANAGER'),
('ROLE_ROLE_MANAGER', 'ROLE_ROLE_EDITOR'),
('ROLE_ORG_MANAGER', 'ROLE_ORG_EDITOR'),
('ROLE_CONFIG_VIEWER', 'ROLE_SYSTEM_CONFIG_VIEWER'),
('ROLE_CONFIG_VIEWER', 'ROLE_NOTIFICATION_CONFIG_VIEWER'),
('ROLE_CONFIG_MANAGER', 'ROLE_SYSTEM_CONFIG_EDITOR'),
('ROLE_CONFIG_MANAGER', 'ROLE_NOTIFICATION_CONFIG_EDITOR'),
('ROLE_SLA_MANAGER', 'ROLE_SLA_EDITOR'),
('ROLE_RELEASE_MANAGER', 'ROLE_RELEASE_PUBLISHER'),
('ROLE_RELEASE_MANAGER', 'ROLE_RELEASE_STORAGE_MANAGER'),
('ROLE_TENANT_MANAGER', 'ROLE_TENANT_PROVISIONER'),
('ROLE_TENANT_MANAGER', 'ROLE_TENANT_STATUS_MANAGER');

CREATE TABLE management_role_policy_20260809 (
    role_name VARCHAR(50) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_name, permission_name)
);
INSERT INTO management_role_policy_20260809 (role_name, permission_name) VALUES
('ROLE_USER_VIEWER', 'management:access'),
('ROLE_USER_VIEWER', 'user:manage:view'),
('ROLE_USER_DETAILS_EDITOR', 'management:access'),
('ROLE_USER_DETAILS_EDITOR', 'user:manage:view'),
('ROLE_USER_DETAILS_EDITOR', 'user:manage:update_details'),
('ROLE_USER_LIFECYCLE_MANAGER', 'management:access'),
('ROLE_USER_LIFECYCLE_MANAGER', 'user:manage:view'),
('ROLE_USER_LIFECYCLE_MANAGER', 'user:manage:create'),
('ROLE_USER_LIFECYCLE_MANAGER', 'user:manage:update_status'),
('ROLE_USER_LIFECYCLE_MANAGER', 'user:manage:delete'),
('ROLE_USER_LIFECYCLE_MANAGER', 'user:manage:restore'),
('ROLE_USER_CREDENTIAL_MANAGER', 'management:access'),
('ROLE_USER_CREDENTIAL_MANAGER', 'user:manage:view'),
('ROLE_USER_CREDENTIAL_MANAGER', 'user:manage:reset_password'),
('ROLE_USER_AUTHORIZATION_MANAGER', 'management:access'),
('ROLE_USER_AUTHORIZATION_MANAGER', 'user:manage:view'),
('ROLE_USER_AUTHORIZATION_MANAGER', 'user:manage:assign_roles'),
('ROLE_USER_AUTHORIZATION_MANAGER', 'role-bundle:manage:view'),
('ROLE_ROLE_VIEWER', 'management:access'),
('ROLE_ROLE_VIEWER', 'role:manage:view'),
('ROLE_ROLE_VIEWER', 'role-bundle:manage:view'),
('ROLE_ROLE_EDITOR', 'management:access'),
('ROLE_ROLE_EDITOR', 'role:manage:view'),
('ROLE_ROLE_EDITOR', 'role:manage:create'),
('ROLE_ROLE_EDITOR', 'role:manage:update'),
('ROLE_ROLE_EDITOR', 'role:manage:delete'),
('ROLE_ROLE_EDITOR', 'role-bundle:manage:view'),
('ROLE_ROLE_EDITOR', 'role-bundle:manage:create'),
('ROLE_ROLE_EDITOR', 'role-bundle:manage:update'),
('ROLE_ROLE_EDITOR', 'role-bundle:manage:delete'),
('ROLE_ORG_VIEWER', 'management:access'),
('ROLE_ORG_VIEWER', 'organization:manage:view'),
('ROLE_ORG_EDITOR', 'management:access'),
('ROLE_ORG_EDITOR', 'organization:manage:view'),
('ROLE_ORG_EDITOR', 'organization:manage:create'),
('ROLE_ORG_EDITOR', 'organization:manage:update'),
('ROLE_SYSTEM_CONFIG_VIEWER', 'management:access'),
('ROLE_SYSTEM_CONFIG_VIEWER', 'system:config:view'),
('ROLE_SYSTEM_CONFIG_EDITOR', 'management:access'),
('ROLE_SYSTEM_CONFIG_EDITOR', 'system:config:view'),
('ROLE_SYSTEM_CONFIG_EDITOR', 'system:config:create'),
('ROLE_SYSTEM_CONFIG_EDITOR', 'system:config:update'),
('ROLE_SYSTEM_CONFIG_EDITOR', 'system:config:delete'),
('ROLE_NOTIFICATION_CONFIG_VIEWER', 'management:access'),
('ROLE_NOTIFICATION_CONFIG_VIEWER', 'notification:config:view'),
('ROLE_NOTIFICATION_CONFIG_EDITOR', 'management:access'),
('ROLE_NOTIFICATION_CONFIG_EDITOR', 'notification:config:view'),
('ROLE_NOTIFICATION_CONFIG_EDITOR', 'notification:config:update'),
('ROLE_SLA_VIEWER', 'management:access'),
('ROLE_SLA_VIEWER', 'sla-policy:view'),
('ROLE_SLA_EDITOR', 'management:access'),
('ROLE_SLA_EDITOR', 'sla-policy:view'),
('ROLE_SLA_EDITOR', 'sla-policy:update'),
('ROLE_RELEASE_VIEWER', 'management:access'),
('ROLE_RELEASE_VIEWER', 'app-update:view'),
('ROLE_RELEASE_PUBLISHER', 'management:access'),
('ROLE_RELEASE_PUBLISHER', 'app-update:view'),
('ROLE_RELEASE_PUBLISHER', 'app-update:publish'),
('ROLE_RELEASE_STORAGE_MANAGER', 'management:access'),
('ROLE_RELEASE_STORAGE_MANAGER', 'app-update:view'),
('ROLE_RELEASE_STORAGE_MANAGER', 'app-update:delete'),
('ROLE_RELEASE_STORAGE_MANAGER', 'app-update:storage'),
('ROLE_DASHBOARD_VIEWER', 'management:access'),
('ROLE_DASHBOARD_VIEWER', 'dashboard:statistics:view'),
('ROLE_OBSERVABILITY_VIEWER', 'management:access'),
('ROLE_OBSERVABILITY_VIEWER', 'observability:view'),
('ROLE_TENANT_VIEWER', 'management:access'),
('ROLE_TENANT_VIEWER', 'tenant:manage:view'),
('ROLE_TENANT_PROVISIONER', 'management:access'),
('ROLE_TENANT_PROVISIONER', 'tenant:manage:view'),
('ROLE_TENANT_PROVISIONER', 'tenant:manage:create'),
('ROLE_TENANT_STATUS_MANAGER', 'management:access'),
('ROLE_TENANT_STATUS_MANAGER', 'tenant:manage:view'),
('ROLE_TENANT_STATUS_MANAGER', 'tenant:manage:update-status');

--rollback SELECT 1;
--changeset e-report:2026-08-09-018-normalize-staging-collation-mysql dbms:mysql
--comment Keep migration contracts comparable with existing MySQL 8 business tables even when a restored database has a different default collation.
ALTER TABLE work_order_type_map_20260809
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE work_order_duty_policy_20260809
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE built_in_role_seed_20260809
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE role_replacement_map_20260809
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE management_role_policy_20260809
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-permissions-and-roles-mysql dbms:mysql
--comment Seed fixed permissions and fine-grained built-in roles with migration UUIDs.
INSERT INTO permissions (id, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), seed.name, seed.display_name, seed.description
FROM (
    SELECT 'notification:config:view' name, '通知配置查看' display_name, '查看授权组织范围内的通知渠道和定时通知配置' description
    UNION ALL SELECT 'notification:config:update', '通知配置修改', '修改授权组织范围内的通知渠道和定时通知配置'
    UNION ALL SELECT 'role-bundle:manage:view', '岗位模板查看', '查看当前租户的内置和自定义岗位模板'
    UNION ALL SELECT 'role-bundle:manage:create', '岗位模板创建', '创建或复制当前租户的自定义岗位模板'
    UNION ALL SELECT 'role-bundle:manage:update', '岗位模板修改', '修改当前租户的自定义岗位模板'
    UNION ALL SELECT 'role-bundle:manage:delete', '岗位模板删除', '删除当前租户不再使用的自定义岗位模板'
    UNION ALL SELECT 'work-order:normal-fault:history:view', '普通故障工单流转查看', '查看授权范围内的普通故障工单流转、回单和验收结果'
    UNION ALL SELECT 'work-order:normal-fault:audit:view', '普通故障工单内部审计', '查看授权范围内的普通故障工单完整操作人和内部审计字段'
    UNION ALL SELECT 'work-order:normal-fault:withdraw', '普通故障工单提单撤回', '撤回本人提交且尚未进入处理的普通故障工单'
    UNION ALL SELECT 'work-order:unclosed-box:history:view', '未关箱工单流转查看', '查看授权范围内的未关箱工单流转、回单和验收结果'
    UNION ALL SELECT 'work-order:unclosed-box:audit:view', '未关箱工单内部审计', '查看授权范围内的未关箱工单完整操作人和内部审计字段'
    UNION ALL SELECT 'work-order:unclosed-box:withdraw', '未关箱工单提单撤回', '撤回本人提交且尚未进入处理的未关箱工单'
    UNION ALL SELECT 'work-order:damaged-box-replacement:history:view', '破损换箱工单流转查看', '查看授权范围内的破损换箱工单流转、回单和验收结果'
    UNION ALL SELECT 'work-order:damaged-box-replacement:audit:view', '破损换箱工单内部审计', '查看授权范围内的破损换箱工单完整操作人和内部审计字段'
    UNION ALL SELECT 'work-order:damaged-box-replacement:withdraw', '破损换箱工单提单撤回', '撤回本人提交且尚未进入处理的破损换箱工单'
) seed
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = seed.name);

INSERT INTO roles (id, tenant_id, built_in, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), tenant.id, TRUE, seed.role_name, seed.display_name, seed.description
FROM tenants tenant
JOIN built_in_role_seed_20260809 seed
  ON seed.platform_only = FALSE OR tenant.code = '${defaultTenantCode}'
WHERE NOT EXISTS (
    SELECT 1 FROM roles existing WHERE existing.tenant_id = tenant.id AND existing.name = seed.role_name
);

INSERT INTO roles (id, tenant_id, built_in, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), tenant.id, TRUE,
       CONCAT(CONCAT('ROLE_', type_map.role_prefix), CONCAT('_', duty_map.duty)),
       CONCAT(type_map.type_display_name, duty_map.duty_display_name),
       CONCAT('负责', CONCAT(type_map.type_display_name, CONCAT('的', duty_map.duty_display_name)))
FROM tenants tenant
CROSS JOIN work_order_type_map_20260809 type_map
CROSS JOIN (SELECT DISTINCT duty, duty_display_name FROM work_order_duty_policy_20260809) duty_map
WHERE NOT EXISTS (
    SELECT 1 FROM roles existing
    WHERE existing.tenant_id = tenant.id
      AND existing.name = CONCAT(CONCAT('ROLE_', type_map.role_prefix), CONCAT('_', duty_map.duty))
);

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-permissions-and-roles-h2 dbms:h2
--comment H2 equivalent for fixed permissions and fine-grained roles.
INSERT INTO permissions (id, name, display_name, description)
SELECT RANDOM_UUID(), seed.name, seed.display_name, seed.description
FROM (
    SELECT 'notification:config:view' name, '通知配置查看' display_name, '查看授权组织范围内的通知渠道和定时通知配置' description
    UNION ALL SELECT 'notification:config:update', '通知配置修改', '修改授权组织范围内的通知渠道和定时通知配置'
    UNION ALL SELECT 'role-bundle:manage:view', '岗位模板查看', '查看当前租户的内置和自定义岗位模板'
    UNION ALL SELECT 'role-bundle:manage:create', '岗位模板创建', '创建或复制当前租户的自定义岗位模板'
    UNION ALL SELECT 'role-bundle:manage:update', '岗位模板修改', '修改当前租户的自定义岗位模板'
    UNION ALL SELECT 'role-bundle:manage:delete', '岗位模板删除', '删除当前租户不再使用的自定义岗位模板'
    UNION ALL SELECT 'work-order:normal-fault:history:view', '普通故障工单流转查看', '查看授权范围内的普通故障工单流转、回单和验收结果'
    UNION ALL SELECT 'work-order:normal-fault:audit:view', '普通故障工单内部审计', '查看授权范围内的普通故障工单完整操作人和内部审计字段'
    UNION ALL SELECT 'work-order:normal-fault:withdraw', '普通故障工单提单撤回', '撤回本人提交且尚未进入处理的普通故障工单'
    UNION ALL SELECT 'work-order:unclosed-box:history:view', '未关箱工单流转查看', '查看授权范围内的未关箱工单流转、回单和验收结果'
    UNION ALL SELECT 'work-order:unclosed-box:audit:view', '未关箱工单内部审计', '查看授权范围内的未关箱工单完整操作人和内部审计字段'
    UNION ALL SELECT 'work-order:unclosed-box:withdraw', '未关箱工单提单撤回', '撤回本人提交且尚未进入处理的未关箱工单'
    UNION ALL SELECT 'work-order:damaged-box-replacement:history:view', '破损换箱工单流转查看', '查看授权范围内的破损换箱工单流转、回单和验收结果'
    UNION ALL SELECT 'work-order:damaged-box-replacement:audit:view', '破损换箱工单内部审计', '查看授权范围内的破损换箱工单完整操作人和内部审计字段'
    UNION ALL SELECT 'work-order:damaged-box-replacement:withdraw', '破损换箱工单提单撤回', '撤回本人提交且尚未进入处理的破损换箱工单'
) seed
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = seed.name);

INSERT INTO roles (id, tenant_id, built_in, name, display_name, description)
SELECT RANDOM_UUID(), tenant.id, TRUE, seed.role_name, seed.display_name, seed.description
FROM tenants tenant
JOIN built_in_role_seed_20260809 seed
  ON seed.platform_only = FALSE OR tenant.code = '${defaultTenantCode}'
WHERE NOT EXISTS (
    SELECT 1 FROM roles existing WHERE existing.tenant_id = tenant.id AND existing.name = seed.role_name
);

INSERT INTO roles (id, tenant_id, built_in, name, display_name, description)
SELECT RANDOM_UUID(), tenant.id, TRUE,
       CONCAT(CONCAT('ROLE_', type_map.role_prefix), CONCAT('_', duty_map.duty)),
       CONCAT(type_map.type_display_name, duty_map.duty_display_name),
       CONCAT('负责', CONCAT(type_map.type_display_name, CONCAT('的', duty_map.duty_display_name)))
FROM tenants tenant
CROSS JOIN work_order_type_map_20260809 type_map
CROSS JOIN (SELECT DISTINCT duty, duty_display_name FROM work_order_duty_policy_20260809) duty_map
WHERE NOT EXISTS (
    SELECT 1 FROM roles existing
    WHERE existing.tenant_id = tenant.id
      AND existing.name = CONCAT(CONCAT('ROLE_', type_map.role_prefix), CONCAT('_', duty_map.duty))
);

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-sync-role-metadata dbms:mysql,h2
--comment Ensure reused and newly created roles have the exact fixed metadata.
UPDATE roles role_row
SET display_name = (
        SELECT seed.display_name FROM built_in_role_seed_20260809 seed WHERE seed.role_name = role_row.name
    ),
    description = (
        SELECT seed.description FROM built_in_role_seed_20260809 seed WHERE seed.role_name = role_row.name
    ),
    built_in = TRUE
WHERE EXISTS (
    SELECT 1 FROM built_in_role_seed_20260809 seed WHERE seed.role_name = role_row.name
);

UPDATE roles role_row
SET display_name = (
        SELECT CONCAT(type_map.type_display_name, duty_map.duty_display_name)
        FROM work_order_type_map_20260809 type_map
        CROSS JOIN (SELECT DISTINCT duty, duty_display_name FROM work_order_duty_policy_20260809) duty_map
        WHERE role_row.name = CONCAT(CONCAT('ROLE_', type_map.role_prefix), CONCAT('_', duty_map.duty))
    ),
    built_in = TRUE
WHERE EXISTS (
    SELECT 1
    FROM work_order_type_map_20260809 type_map
    CROSS JOIN (SELECT DISTINCT duty FROM work_order_duty_policy_20260809) duty_map
    WHERE role_row.name = CONCAT(CONCAT('ROLE_', type_map.role_prefix), CONCAT('_', duty_map.duty))
);

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-copy-user-role-assignments dbms:mysql,h2
--comment Expand user role assignments before removing any broad role.
INSERT INTO user_roles (user_id, role_id)
SELECT DISTINCT source_assignment.user_id, target_role.id
FROM (
    SELECT user_role.user_id, source_role.tenant_id, source_role.name source_role_name
    FROM user_roles user_role
    JOIN roles source_role ON source_role.id = user_role.role_id
) source_assignment
JOIN role_replacement_map_20260809 replacement
  ON replacement.source_role_name = source_assignment.source_role_name
JOIN roles target_role
  ON target_role.tenant_id = source_assignment.tenant_id
 AND target_role.name = replacement.target_role_name
WHERE NOT EXISTS (
    SELECT 1 FROM user_roles existing
    WHERE existing.user_id = source_assignment.user_id
      AND existing.role_id = target_role.id
);

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-copy-role-grants-mysql dbms:mysql
--comment Preserve each source grant while converting retired SELF and ASSIGNED scopes to the member organization.
INSERT INTO membership_role_grants (
    id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at
)
SELECT UUID_TO_BIN(UUID(), 1), candidate.tenant_id, candidate.membership_id, candidate.role_id,
       candidate.data_scope, candidate.scope_org_id, candidate.granted_at, candidate.expires_at
FROM (
    SELECT DISTINCT source_grant.tenant_id,
           source_grant.membership_id,
           target_role.id role_id,
           CASE WHEN source_grant.data_scope IN ('SELF', 'ASSIGNED') THEN 'ORG' ELSE source_grant.data_scope END data_scope,
           CASE WHEN source_grant.data_scope IN ('SELF', 'ASSIGNED') THEN member.org_unit_id ELSE source_grant.scope_org_id END scope_org_id,
           source_grant.granted_at,
           source_grant.expires_at
    FROM membership_role_grants source_grant
    JOIN users member ON member.id = source_grant.membership_id AND member.tenant_id = source_grant.tenant_id
    JOIN roles source_role ON source_role.id = source_grant.role_id
    JOIN role_replacement_map_20260809 replacement ON replacement.source_role_name = source_role.name
    JOIN roles target_role
      ON target_role.tenant_id = source_role.tenant_id
     AND target_role.name = replacement.target_role_name
) candidate
WHERE (candidate.scope_org_id IS NOT NULL OR candidate.data_scope = 'TENANT_ALL')
  AND NOT EXISTS (
      SELECT 1 FROM membership_role_grants existing
      WHERE existing.tenant_id = candidate.tenant_id
        AND existing.membership_id = candidate.membership_id
        AND existing.role_id = candidate.role_id
        AND existing.data_scope = candidate.data_scope
        AND (existing.scope_org_id = candidate.scope_org_id
             OR (existing.scope_org_id IS NULL AND candidate.scope_org_id IS NULL))
  );

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-copy-role-grants-h2 dbms:h2
--comment H2 equivalent for copying and normalizing fine-grained grants.
INSERT INTO membership_role_grants (
    id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at
)
SELECT RANDOM_UUID(), candidate.tenant_id, candidate.membership_id, candidate.role_id,
       candidate.data_scope, candidate.scope_org_id, candidate.granted_at, candidate.expires_at
FROM (
    SELECT DISTINCT source_grant.tenant_id,
           source_grant.membership_id,
           target_role.id role_id,
           CASE WHEN source_grant.data_scope IN ('SELF', 'ASSIGNED') THEN 'ORG' ELSE source_grant.data_scope END data_scope,
           CASE WHEN source_grant.data_scope IN ('SELF', 'ASSIGNED') THEN member.org_unit_id ELSE source_grant.scope_org_id END scope_org_id,
           source_grant.granted_at,
           source_grant.expires_at
    FROM membership_role_grants source_grant
    JOIN users member ON member.id = source_grant.membership_id AND member.tenant_id = source_grant.tenant_id
    JOIN roles source_role ON source_role.id = source_grant.role_id
    JOIN role_replacement_map_20260809 replacement ON replacement.source_role_name = source_role.name
    JOIN roles target_role
      ON target_role.tenant_id = source_role.tenant_id
     AND target_role.name = replacement.target_role_name
) candidate
WHERE (candidate.scope_org_id IS NOT NULL OR candidate.data_scope = 'TENANT_ALL')
  AND NOT EXISTS (
      SELECT 1 FROM membership_role_grants existing
      WHERE existing.tenant_id = candidate.tenant_id
        AND existing.membership_id = candidate.membership_id
        AND existing.role_id = candidate.role_id
        AND existing.data_scope = candidate.data_scope
        AND (existing.scope_org_id = candidate.scope_org_id
             OR (existing.scope_org_id IS NULL AND candidate.scope_org_id IS NULL))
  );

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-normalize-existing-grants-mysql dbms:mysql
--comment Replace every retired relationship scope with an explicit organization grant without creating duplicates.
DELETE legacy_grant
FROM membership_role_grants legacy_grant
JOIN users member ON member.id = legacy_grant.membership_id
JOIN membership_role_grants existing
  ON existing.tenant_id = legacy_grant.tenant_id
 AND existing.membership_id = legacy_grant.membership_id
 AND existing.role_id = legacy_grant.role_id
 AND existing.data_scope = 'ORG'
 AND existing.scope_org_id = member.org_unit_id
WHERE legacy_grant.data_scope IN ('SELF', 'ASSIGNED')
  AND member.org_unit_id IS NOT NULL;

UPDATE membership_role_grants legacy_grant
JOIN users member ON member.id = legacy_grant.membership_id
SET legacy_grant.data_scope = 'ORG', legacy_grant.scope_org_id = member.org_unit_id
WHERE legacy_grant.data_scope IN ('SELF', 'ASSIGNED')
  AND member.org_unit_id IS NOT NULL;

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-normalize-existing-grants-h2 dbms:h2
--comment H2 equivalent for complete legacy grant normalization.
DELETE FROM membership_role_grants legacy_grant
WHERE legacy_grant.data_scope IN ('SELF', 'ASSIGNED')
  AND EXISTS (
      SELECT 1
      FROM users member
      JOIN membership_role_grants existing
        ON existing.tenant_id = legacy_grant.tenant_id
       AND existing.membership_id = legacy_grant.membership_id
       AND existing.role_id = legacy_grant.role_id
       AND existing.data_scope = 'ORG'
       AND existing.scope_org_id = member.org_unit_id
      WHERE member.id = legacy_grant.membership_id
  );

UPDATE membership_role_grants legacy_grant
SET data_scope = 'ORG',
    scope_org_id = (SELECT member.org_unit_id FROM users member WHERE member.id = legacy_grant.membership_id)
WHERE legacy_grant.data_scope IN ('SELF', 'ASSIGNED')
  AND EXISTS (
      SELECT 1 FROM users member WHERE member.id = legacy_grant.membership_id AND member.org_unit_id IS NOT NULL
  );

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-assert-no-legacy-grant-scopes dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM membership_role_grants WHERE data_scope IN ('SELF', 'ASSIGNED')
--comment Refuse deployment when a legacy relationship grant cannot be mapped to an organization.
SELECT 1;

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-sync-role-permission-policies dbms:mysql,h2
--comment Replace all built-in role permissions atomically with the fixed fine-grained policy catalog.
DELETE FROM role_permissions
WHERE role_id IN (
    SELECT role_row.id
    FROM roles role_row
    WHERE role_row.built_in = TRUE
      AND (
          EXISTS (SELECT 1 FROM built_in_role_seed_20260809 seed WHERE seed.role_name = role_row.name)
          OR EXISTS (
              SELECT 1
              FROM work_order_type_map_20260809 type_map
              CROSS JOIN (SELECT DISTINCT duty FROM work_order_duty_policy_20260809) duty_map
              WHERE role_row.name = CONCAT(CONCAT('ROLE_', type_map.role_prefix), CONCAT('_', duty_map.duty))
          )
      )
);

INSERT INTO role_permissions (role_id, permission_id, scope_profile)
SELECT role_row.id, permission_row.id, duty_policy.scope_profile
FROM roles role_row
CROSS JOIN work_order_type_map_20260809 type_map
CROSS JOIN work_order_duty_policy_20260809 duty_policy
JOIN permissions permission_row
  ON permission_row.name = CONCAT(
      CONCAT('work-order:', type_map.permission_slug),
      CONCAT(':', duty_policy.permission_action)
  )
WHERE role_row.name = CONCAT(CONCAT('ROLE_', type_map.role_prefix), CONCAT('_', duty_policy.duty));

INSERT INTO role_permissions (role_id, permission_id, scope_profile)
SELECT role_row.id, permission_row.id, 'GRANT_SCOPE'
FROM roles role_row
JOIN management_role_policy_20260809 policy ON policy.role_name = role_row.name
JOIN permissions permission_row ON permission_row.name = policy.permission_name;

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-retire-broad-roles dbms:mysql,h2
--comment Invalidate cached authorization, then remove source grants, assignments, permissions, and broad roles.
UPDATE users
SET authorization_version = authorization_version + 1;

DELETE FROM membership_role_grants
WHERE role_id IN (
    SELECT role_row.id FROM roles role_row
    WHERE role_row.built_in = TRUE
      AND EXISTS (
          SELECT 1 FROM role_replacement_map_20260809 replacement
          WHERE replacement.source_role_name = role_row.name
      )
);

DELETE FROM user_roles
WHERE role_id IN (
    SELECT role_row.id FROM roles role_row
    WHERE role_row.built_in = TRUE
      AND EXISTS (
          SELECT 1 FROM role_replacement_map_20260809 replacement
          WHERE replacement.source_role_name = role_row.name
      )
);

DELETE FROM role_permissions
WHERE role_id IN (
    SELECT role_row.id FROM roles role_row
    WHERE role_row.built_in = TRUE
      AND EXISTS (
          SELECT 1 FROM role_replacement_map_20260809 replacement
          WHERE replacement.source_role_name = role_row.name
      )
);

DELETE FROM roles
WHERE built_in = TRUE
  AND EXISTS (
      SELECT 1 FROM role_replacement_map_20260809 replacement
      WHERE replacement.source_role_name = roles.name
  );

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-backfill-reporter-mysql dbms:mysql
--comment Backfill only unambiguous reporter identities from the immutable submitted event.
UPDATE fault_reports report
SET reported_by_user_id = (
    SELECT event_row.operator_membership_id
    FROM work_order_events event_row
    WHERE event_row.tenant_id = report.tenant_id
      AND event_row.work_order_id = report.id
      AND event_row.event_type = 'SUBMITTED'
      AND event_row.operator_membership_id IS NOT NULL
    ORDER BY event_row.occurred_at, event_row.id
    LIMIT 1
)
WHERE report.reported_by_user_id IS NULL
  AND 1 = (
      SELECT COUNT(DISTINCT event_row.operator_membership_id)
      FROM work_order_events event_row
      WHERE event_row.tenant_id = report.tenant_id
        AND event_row.work_order_id = report.id
        AND event_row.event_type = 'SUBMITTED'
        AND event_row.operator_membership_id IS NOT NULL
  );

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-enforce-reporter-mysql dbms:mysql
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM fault_reports WHERE reported_by_user_id IS NULL
--comment Refuse deployment if any historical reporter remains unresolved.
ALTER TABLE fault_reports
    MODIFY COLUMN reported_by_user_id BINARY(16) NOT NULL;

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-backfill-reporter-h2 dbms:h2
--comment H2 equivalent for reliable reporter backfill.
UPDATE fault_reports report
SET reported_by_user_id = (
    SELECT event_row.operator_membership_id
    FROM work_order_events event_row
    WHERE event_row.tenant_id = report.tenant_id
      AND event_row.work_order_id = report.id
      AND event_row.event_type = 'SUBMITTED'
      AND event_row.operator_membership_id IS NOT NULL
    ORDER BY event_row.occurred_at, event_row.id
    FETCH FIRST 1 ROW ONLY
)
WHERE report.reported_by_user_id IS NULL
  AND 1 = (
      SELECT COUNT(DISTINCT event_row.operator_membership_id)
      FROM work_order_events event_row
      WHERE event_row.tenant_id = report.tenant_id
        AND event_row.work_order_id = report.id
        AND event_row.event_type = 'SUBMITTED'
        AND event_row.operator_membership_id IS NOT NULL
  );

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-enforce-reporter-h2 dbms:h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM fault_reports WHERE reported_by_user_id IS NULL
--comment Refuse deployment if any historical reporter remains unresolved.
ALTER TABLE fault_reports ALTER COLUMN reported_by_user_id SET NOT NULL;

--rollback SELECT 1;

--changeset e-report:2026-08-09-018-drop-policy-staging dbms:mysql,h2
--comment Remove all migration-only mapping tables after validation.
DROP TABLE management_role_policy_20260809;
DROP TABLE role_replacement_map_20260809;
DROP TABLE built_in_role_seed_20260809;
DROP TABLE work_order_duty_policy_20260809;
DROP TABLE work_order_type_map_20260809;

--rollback SELECT 1;
