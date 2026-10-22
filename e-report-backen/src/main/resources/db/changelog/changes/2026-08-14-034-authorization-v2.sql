--liquibase formatted sql

--changeset e-report:2026-08-14-034-authorization-v2-schema-mysql dbms:mysql
--comment Introduce the authorization V2 fact model. Legacy role tables remain read-only rollback snapshots for one release.
CREATE TABLE authorization_profiles (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    code VARCHAR(80) NOT NULL,
    system_key VARCHAR(80),
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    scope_seed_strategy VARCHAR(30) NOT NULL,
    current_version_id BINARY(16),
    sort_order INT NOT NULL DEFAULT 100,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_authorization_profiles PRIMARY KEY (id),
    CONSTRAINT uk_authorization_profiles_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uk_authorization_profiles_tenant_system UNIQUE (tenant_id, system_key),
    CONSTRAINT fk_authorization_profiles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_authorization_profiles_tenant_active ON authorization_profiles (tenant_id, active, sort_order);

CREATE TABLE authorization_profile_versions (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    profile_id BINARY(16) NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    change_summary VARCHAR(500),
    created_by_membership_id BINARY(16),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_by_membership_id BINARY(16),
    published_at DATETIME(6),
    CONSTRAINT pk_authorization_profile_versions PRIMARY KEY (id),
    CONSTRAINT uk_authorization_profile_versions_number UNIQUE (profile_id, version_no),
    CONSTRAINT fk_authorization_profile_versions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_authorization_profile_versions_profile FOREIGN KEY (profile_id) REFERENCES authorization_profiles (id),
    CONSTRAINT fk_authorization_profile_versions_creator FOREIGN KEY (created_by_membership_id) REFERENCES users (id),
    CONSTRAINT fk_authorization_profile_versions_publisher FOREIGN KEY (published_by_membership_id) REFERENCES users (id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_authorization_profile_versions_status ON authorization_profile_versions (tenant_id, profile_id, status);
ALTER TABLE authorization_profiles ADD CONSTRAINT fk_authorization_profiles_current_version
    FOREIGN KEY (current_version_id) REFERENCES authorization_profile_versions (id);

CREATE TABLE authorization_profile_permission_rules (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    profile_version_id BINARY(16) NOT NULL,
    permission_id BINARY(16) NOT NULL,
    relation_constraint VARCHAR(40) NOT NULL,
    sort_order INT NOT NULL DEFAULT 100,
    CONSTRAINT pk_authorization_profile_permission_rules PRIMARY KEY (id),
    CONSTRAINT uk_authorization_profile_rules_relation UNIQUE (profile_version_id, permission_id, relation_constraint),
    CONSTRAINT fk_authorization_profile_rules_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_authorization_profile_rules_version FOREIGN KEY (profile_version_id) REFERENCES authorization_profile_versions (id),
    CONSTRAINT fk_authorization_profile_rules_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_authorization_profile_rules_version ON authorization_profile_permission_rules (tenant_id, profile_version_id, sort_order);

CREATE TABLE authorization_assignments (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    membership_id BINARY(16) NOT NULL,
    profile_version_id BINARY(16),
    source_type VARCHAR(20) NOT NULL,
    source_label VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(1000),
    granted_by_membership_id BINARY(16),
    granted_at DATETIME(6) NOT NULL,
    revoked_by_membership_id BINARY(16),
    revoked_at DATETIME(6),
    revoke_reason VARCHAR(1000),
    CONSTRAINT pk_authorization_assignments PRIMARY KEY (id),
    CONSTRAINT fk_authorization_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_authorization_assignments_member FOREIGN KEY (membership_id) REFERENCES users (id),
    CONSTRAINT fk_authorization_assignments_profile_version FOREIGN KEY (profile_version_id) REFERENCES authorization_profile_versions (id),
    CONSTRAINT fk_authorization_assignments_grantor FOREIGN KEY (granted_by_membership_id) REFERENCES users (id),
    CONSTRAINT fk_authorization_assignments_revoker FOREIGN KEY (revoked_by_membership_id) REFERENCES users (id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_authorization_assignments_member ON authorization_assignments (tenant_id, membership_id, status, granted_at);

CREATE TABLE authorization_data_scopes (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    tenant_wide BOOLEAN NOT NULL DEFAULT FALSE,
    canonical_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_authorization_data_scopes PRIMARY KEY (id),
    CONSTRAINT uk_authorization_data_scopes_hash UNIQUE (tenant_id, canonical_hash),
    CONSTRAINT fk_authorization_data_scopes_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_authorization_data_scopes_tenant ON authorization_data_scopes (tenant_id, tenant_wide);

CREATE TABLE authorization_data_scope_nodes (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    scope_id BINARY(16) NOT NULL,
    org_unit_id BINARY(16) NOT NULL,
    include_descendants BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_authorization_data_scope_nodes PRIMARY KEY (id),
    CONSTRAINT uk_authorization_scope_nodes_org UNIQUE (scope_id, org_unit_id),
    CONSTRAINT fk_authorization_scope_nodes_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_authorization_scope_nodes_scope FOREIGN KEY (scope_id) REFERENCES authorization_data_scopes (id),
    CONSTRAINT fk_authorization_scope_nodes_org FOREIGN KEY (org_unit_id) REFERENCES org_units (id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_authorization_scope_nodes_scope ON authorization_data_scope_nodes (tenant_id, scope_id);
CREATE INDEX idx_authorization_scope_nodes_org ON authorization_data_scope_nodes (tenant_id, org_unit_id, scope_id);

CREATE TABLE permission_grants (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    membership_id BINARY(16) NOT NULL,
    assignment_id BINARY(16) NOT NULL,
    profile_rule_id BINARY(16),
    permission_id BINARY(16) NOT NULL,
    data_scope_id BINARY(16) NOT NULL,
    relation_constraint VARCHAR(40) NOT NULL,
    valid_from DATETIME(6),
    expires_at DATETIME(6),
    status VARCHAR(20) NOT NULL,
    customized BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(1000),
    granted_by_membership_id BINARY(16),
    granted_at DATETIME(6) NOT NULL,
    revoked_by_membership_id BINARY(16),
    revoked_at DATETIME(6),
    revoke_reason VARCHAR(1000),
    CONSTRAINT pk_permission_grants PRIMARY KEY (id),
    CONSTRAINT fk_permission_grants_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_permission_grants_member FOREIGN KEY (membership_id) REFERENCES users (id),
    CONSTRAINT fk_permission_grants_assignment FOREIGN KEY (assignment_id) REFERENCES authorization_assignments (id),
    CONSTRAINT fk_permission_grants_profile_rule FOREIGN KEY (profile_rule_id) REFERENCES authorization_profile_permission_rules (id),
    CONSTRAINT fk_permission_grants_permission FOREIGN KEY (permission_id) REFERENCES permissions (id),
    CONSTRAINT fk_permission_grants_scope FOREIGN KEY (data_scope_id) REFERENCES authorization_data_scopes (id),
    CONSTRAINT fk_permission_grants_grantor FOREIGN KEY (granted_by_membership_id) REFERENCES users (id),
    CONSTRAINT fk_permission_grants_revoker FOREIGN KEY (revoked_by_membership_id) REFERENCES users (id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_permission_grants_member ON permission_grants (tenant_id, membership_id, status, expires_at);
CREATE INDEX idx_permission_grants_permission ON permission_grants (tenant_id, permission_id, status, expires_at);
CREATE INDEX idx_permission_grants_assignment ON permission_grants (assignment_id, status);

CREATE TABLE authorization_v2_permission_code_map_20260814 (
    source_permission_id BINARY(16) NOT NULL,
    target_permission_id BINARY(16) NOT NULL,
    source_permission_name VARCHAR(100) NOT NULL,
    target_permission_name VARCHAR(100) NOT NULL,
    migration_reason VARCHAR(255) NOT NULL,
    CONSTRAINT pk_authorization_v2_permission_map PRIMARY KEY (source_permission_id, target_permission_id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

--rollback DROP TABLE authorization_v2_permission_code_map_20260814;
--rollback DROP TABLE permission_grants;
--rollback DROP TABLE authorization_data_scope_nodes;
--rollback DROP TABLE authorization_data_scopes;
--rollback DROP TABLE authorization_assignments;
--rollback DROP TABLE authorization_profile_permission_rules;
--rollback ALTER TABLE authorization_profiles DROP FOREIGN KEY fk_authorization_profiles_current_version;
--rollback DROP TABLE authorization_profile_versions;
--rollback DROP TABLE authorization_profiles;

--changeset e-report:2026-08-14-034-authorization-v2-schema-h2 dbms:h2
--comment H2 equivalent of the authorization V2 fact model.
CREATE TABLE authorization_profiles (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(80) NOT NULL,
    system_key VARCHAR(80),
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    built_in BOOLEAN DEFAULT FALSE NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    scope_seed_strategy VARCHAR(30) NOT NULL,
    current_version_id UUID,
    sort_order INT DEFAULT 100 NOT NULL,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_authorization_profiles_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uk_authorization_profiles_tenant_system UNIQUE (tenant_id, system_key),
    CONSTRAINT fk_authorization_profiles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX idx_authorization_profiles_tenant_active ON authorization_profiles (tenant_id, active, sort_order);
CREATE TABLE authorization_profile_versions (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    profile_id UUID NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    change_summary VARCHAR(500),
    created_by_membership_id UUID,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    published_by_membership_id UUID,
    published_at TIMESTAMP(6),
    CONSTRAINT uk_authorization_profile_versions_number UNIQUE (profile_id, version_no),
    CONSTRAINT fk_authorization_profile_versions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_authorization_profile_versions_profile FOREIGN KEY (profile_id) REFERENCES authorization_profiles (id),
    CONSTRAINT fk_authorization_profile_versions_creator FOREIGN KEY (created_by_membership_id) REFERENCES users (id),
    CONSTRAINT fk_authorization_profile_versions_publisher FOREIGN KEY (published_by_membership_id) REFERENCES users (id)
);
CREATE INDEX idx_authorization_profile_versions_status ON authorization_profile_versions (tenant_id, profile_id, status);
ALTER TABLE authorization_profiles ADD CONSTRAINT fk_authorization_profiles_current_version FOREIGN KEY (current_version_id) REFERENCES authorization_profile_versions (id);
CREATE TABLE authorization_profile_permission_rules (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    profile_version_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    relation_constraint VARCHAR(40) NOT NULL,
    sort_order INT DEFAULT 100 NOT NULL,
    CONSTRAINT uk_authorization_profile_rules_relation UNIQUE (profile_version_id, permission_id, relation_constraint),
    CONSTRAINT fk_authorization_profile_rules_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_authorization_profile_rules_version FOREIGN KEY (profile_version_id) REFERENCES authorization_profile_versions (id),
    CONSTRAINT fk_authorization_profile_rules_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
);
CREATE INDEX idx_authorization_profile_rules_version ON authorization_profile_permission_rules (tenant_id, profile_version_id, sort_order);
CREATE TABLE authorization_assignments (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    profile_version_id UUID,
    source_type VARCHAR(20) NOT NULL,
    source_label VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(1000),
    granted_by_membership_id UUID,
    granted_at TIMESTAMP(6) NOT NULL,
    revoked_by_membership_id UUID,
    revoked_at TIMESTAMP(6),
    revoke_reason VARCHAR(1000),
    CONSTRAINT fk_authorization_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_authorization_assignments_member FOREIGN KEY (membership_id) REFERENCES users (id),
    CONSTRAINT fk_authorization_assignments_profile_version FOREIGN KEY (profile_version_id) REFERENCES authorization_profile_versions (id),
    CONSTRAINT fk_authorization_assignments_grantor FOREIGN KEY (granted_by_membership_id) REFERENCES users (id),
    CONSTRAINT fk_authorization_assignments_revoker FOREIGN KEY (revoked_by_membership_id) REFERENCES users (id)
);
CREATE INDEX idx_authorization_assignments_member ON authorization_assignments (tenant_id, membership_id, status, granted_at);
CREATE TABLE authorization_data_scopes (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    tenant_wide BOOLEAN DEFAULT FALSE NOT NULL,
    canonical_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_authorization_data_scopes_hash UNIQUE (tenant_id, canonical_hash),
    CONSTRAINT fk_authorization_data_scopes_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX idx_authorization_data_scopes_tenant ON authorization_data_scopes (tenant_id, tenant_wide);
CREATE TABLE authorization_data_scope_nodes (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    scope_id UUID NOT NULL,
    org_unit_id UUID NOT NULL,
    include_descendants BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT uk_authorization_scope_nodes_org UNIQUE (scope_id, org_unit_id),
    CONSTRAINT fk_authorization_scope_nodes_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_authorization_scope_nodes_scope FOREIGN KEY (scope_id) REFERENCES authorization_data_scopes (id),
    CONSTRAINT fk_authorization_scope_nodes_org FOREIGN KEY (org_unit_id) REFERENCES org_units (id)
);
CREATE INDEX idx_authorization_scope_nodes_scope ON authorization_data_scope_nodes (tenant_id, scope_id);
CREATE INDEX idx_authorization_scope_nodes_org ON authorization_data_scope_nodes (tenant_id, org_unit_id, scope_id);
CREATE TABLE permission_grants (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    assignment_id UUID NOT NULL,
    profile_rule_id UUID,
    permission_id UUID NOT NULL,
    data_scope_id UUID NOT NULL,
    relation_constraint VARCHAR(40) NOT NULL,
    valid_from TIMESTAMP(6),
    expires_at TIMESTAMP(6),
    status VARCHAR(20) NOT NULL,
    customized BOOLEAN DEFAULT FALSE NOT NULL,
    reason VARCHAR(1000),
    granted_by_membership_id UUID,
    granted_at TIMESTAMP(6) NOT NULL,
    revoked_by_membership_id UUID,
    revoked_at TIMESTAMP(6),
    revoke_reason VARCHAR(1000),
    CONSTRAINT fk_permission_grants_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_permission_grants_member FOREIGN KEY (membership_id) REFERENCES users (id),
    CONSTRAINT fk_permission_grants_assignment FOREIGN KEY (assignment_id) REFERENCES authorization_assignments (id),
    CONSTRAINT fk_permission_grants_profile_rule FOREIGN KEY (profile_rule_id) REFERENCES authorization_profile_permission_rules (id),
    CONSTRAINT fk_permission_grants_permission FOREIGN KEY (permission_id) REFERENCES permissions (id),
    CONSTRAINT fk_permission_grants_scope FOREIGN KEY (data_scope_id) REFERENCES authorization_data_scopes (id),
    CONSTRAINT fk_permission_grants_grantor FOREIGN KEY (granted_by_membership_id) REFERENCES users (id),
    CONSTRAINT fk_permission_grants_revoker FOREIGN KEY (revoked_by_membership_id) REFERENCES users (id)
);
CREATE INDEX idx_permission_grants_member ON permission_grants (tenant_id, membership_id, status, expires_at);
CREATE INDEX idx_permission_grants_permission ON permission_grants (tenant_id, permission_id, status, expires_at);
CREATE INDEX idx_permission_grants_assignment ON permission_grants (assignment_id, status);
CREATE TABLE authorization_v2_permission_code_map_20260814 (
    source_permission_id UUID NOT NULL,
    target_permission_id UUID NOT NULL,
    source_permission_name VARCHAR(100) NOT NULL,
    target_permission_name VARCHAR(100) NOT NULL,
    migration_reason VARCHAR(255) NOT NULL,
    CONSTRAINT pk_authorization_v2_permission_map PRIMARY KEY (source_permission_id, target_permission_id)
);

--rollback DROP TABLE authorization_v2_permission_code_map_20260814;
--rollback DROP TABLE permission_grants;
--rollback DROP TABLE authorization_data_scope_nodes;
--rollback DROP TABLE authorization_data_scopes;
--rollback DROP TABLE authorization_assignments;
--rollback DROP TABLE authorization_profile_permission_rules;
--rollback ALTER TABLE authorization_profiles DROP CONSTRAINT fk_authorization_profiles_current_version;
--rollback DROP TABLE authorization_profile_versions;
--rollback DROP TABLE authorization_profiles;

--changeset e-report:2026-08-14-034-authorization-v2-data-preconditions dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM membership_role_grants WHERE data_scope NOT IN ('ORG', 'ORG_TREE', 'TENANT_ALL')
--comment V2 has no legacy pseudo data scope; unresolved data-scope rows must halt migration. Combined legacy action relations are normalized by target atomic action below.
SELECT 1;

--rollback SELECT 1;

--changeset e-report:2026-08-14-034-authorization-v2-permission-prerequisite-mysql dbms:mysql
--comment Create atomic permission targets before the V2 data expansion.
INSERT INTO permissions (id, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), seed.name, seed.name, '授权 V2 原子权限；显示文案由固定目录同步'
FROM (
    SELECT CONCAT('work-order:', work_type.slug, ':', action_name.suffix) name
    FROM (
        SELECT 'network-fault' slug UNION ALL SELECT 'box-door-open' UNION ALL SELECT 'damaged-box-replacement'
    ) work_type
    CROSS JOIN (
        SELECT 'response:submit' suffix UNION ALL SELECT 'attachment:read'
        UNION ALL SELECT 'assignment:accept' UNION ALL SELECT 'assignment:reject'
        UNION ALL SELECT 'assignment:create' UNION ALL SELECT 'assignment:reassign'
        UNION ALL SELECT 'verification:approve' UNION ALL SELECT 'verification:reject'
        UNION ALL SELECT 'resume'
    ) action_name
    UNION ALL SELECT 'user:manage:details:update'
    UNION ALL SELECT 'user:manage:status:activate' UNION ALL SELECT 'user:manage:status:deactivate'
    UNION ALL SELECT 'user:manage:status:lock' UNION ALL SELECT 'user:manage:status:unlock'
    UNION ALL SELECT 'user:manage:password:reset'
    UNION ALL SELECT 'user:authorization:view' UNION ALL SELECT 'user:authorization:grant'
    UNION ALL SELECT 'user:authorization:adjust' UNION ALL SELECT 'user:authorization:revoke'
    UNION ALL SELECT 'user:import:template:download' UNION ALL SELECT 'user:import:preview'
    UNION ALL SELECT 'user:import:execute' UNION ALL SELECT 'user:import:cancel'
    UNION ALL SELECT 'user:export:create' UNION ALL SELECT 'user:export:download'
    UNION ALL SELECT 'notification:channel:update' UNION ALL SELECT 'notification:schedule:update'
    UNION ALL SELECT 'authorization-profile:view' UNION ALL SELECT 'authorization-profile:create'
    UNION ALL SELECT 'authorization-profile:draft:update' UNION ALL SELECT 'authorization-profile:copy'
    UNION ALL SELECT 'authorization-profile:publish' UNION ALL SELECT 'authorization-profile:retire'
    UNION ALL SELECT 'authorization-profile:assignment:upgrade'
    UNION ALL SELECT 'organization:manage:move' UNION ALL SELECT 'organization:manage:status:update'
    UNION ALL SELECT 'app-update:storage:view' UNION ALL SELECT 'app-update:storage:sync'
    UNION ALL SELECT 'app-update:storage:delete'
    UNION ALL SELECT 'tenant:manage:status:activate' UNION ALL SELECT 'tenant:manage:status:deactivate'
    UNION ALL SELECT 'observability:redis:view'
) seed
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = seed.name);

--rollback SELECT 1;

--changeset e-report:2026-08-14-034-authorization-v2-permission-prerequisite-h2 dbms:h2
--comment H2 equivalent of the prerequisite atomic permission targets.
INSERT INTO permissions (id, name, display_name, description)
SELECT RANDOM_UUID(), seed.name, seed.name, '授权 V2 原子权限；显示文案由固定目录同步'
FROM (
    SELECT CONCAT('work-order:', work_type.slug, ':', action_name.suffix) name
    FROM (
        SELECT 'network-fault' slug UNION ALL SELECT 'box-door-open' UNION ALL SELECT 'damaged-box-replacement'
    ) work_type
    CROSS JOIN (
        SELECT 'response:submit' suffix UNION ALL SELECT 'attachment:read'
        UNION ALL SELECT 'assignment:accept' UNION ALL SELECT 'assignment:reject'
        UNION ALL SELECT 'assignment:create' UNION ALL SELECT 'assignment:reassign'
        UNION ALL SELECT 'verification:approve' UNION ALL SELECT 'verification:reject'
        UNION ALL SELECT 'resume'
    ) action_name
    UNION ALL SELECT 'user:manage:details:update'
    UNION ALL SELECT 'user:manage:status:activate' UNION ALL SELECT 'user:manage:status:deactivate'
    UNION ALL SELECT 'user:manage:status:lock' UNION ALL SELECT 'user:manage:status:unlock'
    UNION ALL SELECT 'user:manage:password:reset'
    UNION ALL SELECT 'user:authorization:view' UNION ALL SELECT 'user:authorization:grant'
    UNION ALL SELECT 'user:authorization:adjust' UNION ALL SELECT 'user:authorization:revoke'
    UNION ALL SELECT 'user:import:template:download' UNION ALL SELECT 'user:import:preview'
    UNION ALL SELECT 'user:import:execute' UNION ALL SELECT 'user:import:cancel'
    UNION ALL SELECT 'user:export:create' UNION ALL SELECT 'user:export:download'
    UNION ALL SELECT 'notification:channel:update' UNION ALL SELECT 'notification:schedule:update'
    UNION ALL SELECT 'authorization-profile:view' UNION ALL SELECT 'authorization-profile:create'
    UNION ALL SELECT 'authorization-profile:draft:update' UNION ALL SELECT 'authorization-profile:copy'
    UNION ALL SELECT 'authorization-profile:publish' UNION ALL SELECT 'authorization-profile:retire'
    UNION ALL SELECT 'authorization-profile:assignment:upgrade'
    UNION ALL SELECT 'organization:manage:move' UNION ALL SELECT 'organization:manage:status:update'
    UNION ALL SELECT 'app-update:storage:view' UNION ALL SELECT 'app-update:storage:sync'
    UNION ALL SELECT 'app-update:storage:delete'
    UNION ALL SELECT 'tenant:manage:status:activate' UNION ALL SELECT 'tenant:manage:status:deactivate'
    UNION ALL SELECT 'observability:redis:view'
) seed
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = seed.name);

--rollback SELECT 1;

--changeset e-report:2026-08-14-034-authorization-v2-permission-map-prerequisite dbms:mysql,h2
--comment Build the auditable mapping before profiles and grants are expanded.
INSERT INTO authorization_v2_permission_code_map_20260814 (
    source_permission_id, target_permission_id, source_permission_name, target_permission_name, migration_reason
)
SELECT permission_row.id, permission_row.id, permission_row.name, permission_row.name, '原子权限编码保持不变'
FROM permissions permission_row
WHERE permission_row.name IN (
    'management:access','dashboard:statistics:view',
    'user:manage:view','user:manage:create','user:manage:delete','user:manage:restore',
    'system:config:view','system:config:create','system:config:update','system:config:delete',
    'notification:config:view','notification:recipient:county','notification:recipient:tenant',
    'organization:manage:view','organization:manage:create','organization:manage:update',
    'app-update:view','app-update:publish','app-update:policy-update','app-update:delete',
    'tenant:manage:view','tenant:manage:create','sla-policy:view','sla-policy:update','observability:view'
)
OR (permission_row.name LIKE 'work-order:%' AND (
    permission_row.name LIKE '%:report' OR permission_row.name LIKE '%:view'
    OR permission_row.name LIKE '%:history:view' OR permission_row.name LIKE '%:audit:view'
    OR permission_row.name LIKE '%:claim' OR permission_row.name LIKE '%:assignment:accept'
    OR permission_row.name LIKE '%:assignment:reject' OR permission_row.name LIKE '%:cancel'
    OR permission_row.name LIKE '%:hold' OR permission_row.name LIKE '%:resume'
    OR permission_row.name LIKE '%:withdraw'
));

INSERT INTO authorization_v2_permission_code_map_20260814 (
    source_permission_id, target_permission_id, source_permission_name, target_permission_name, migration_reason
)
SELECT source_permission.id, target_permission.id, source_permission.name, target_permission.name,
       '旧复合权限迁移为主要原子动作'
FROM permissions source_permission
JOIN permissions target_permission ON target_permission.name = CASE
    WHEN source_permission.name LIKE 'work-order:%:respond' THEN REPLACE(source_permission.name, ':respond', ':response:submit')
    WHEN source_permission.name LIKE 'work-order:%:download' THEN REPLACE(source_permission.name, ':download', ':attachment:read')
    WHEN source_permission.name LIKE 'work-order:%:assign' THEN REPLACE(source_permission.name, ':assign', ':assignment:create')
    WHEN source_permission.name LIKE 'work-order:%:reassign' THEN REPLACE(source_permission.name, ':reassign', ':assignment:reassign')
    WHEN source_permission.name LIKE 'work-order:%:verify' THEN REPLACE(source_permission.name, ':verify', ':verification:approve')
    WHEN source_permission.name = 'user:manage:export' THEN 'user:export:create'
    WHEN source_permission.name = 'user:manage:update_details' THEN 'user:manage:details:update'
    WHEN source_permission.name = 'user:manage:update_status' THEN 'user:manage:status:deactivate'
    WHEN source_permission.name = 'user:manage:assign_roles' THEN 'user:authorization:adjust'
    WHEN source_permission.name = 'user:manage:reset_password' THEN 'user:manage:password:reset'
    WHEN source_permission.name = 'notification:config:update' THEN 'notification:channel:update'
    WHEN source_permission.name IN ('role:manage:view','role-bundle:manage:view') THEN 'authorization-profile:view'
    WHEN source_permission.name IN ('role:manage:create','role-bundle:manage:create') THEN 'authorization-profile:create'
    WHEN source_permission.name IN ('role:manage:update','role-bundle:manage:update') THEN 'authorization-profile:draft:update'
    WHEN source_permission.name IN ('role:manage:delete','role-bundle:manage:delete') THEN 'authorization-profile:retire'
    WHEN source_permission.name = 'app-update:storage' THEN 'app-update:storage:sync'
    WHEN source_permission.name = 'tenant:manage:update-status' THEN 'tenant:manage:status:deactivate'
END
WHERE source_permission.name IN (
    'user:manage:export','user:manage:update_details','user:manage:update_status','user:manage:assign_roles',
    'user:manage:reset_password','notification:config:update',
    'role:manage:view','role:manage:create','role:manage:update','role:manage:delete',
    'role-bundle:manage:view','role-bundle:manage:create','role-bundle:manage:update','role-bundle:manage:delete',
    'app-update:storage','tenant:manage:update-status'
)
OR source_permission.name LIKE 'work-order:%:respond'
OR source_permission.name LIKE 'work-order:%:download'
OR source_permission.name LIKE 'work-order:%:assign'
OR source_permission.name LIKE 'work-order:%:reassign'
OR source_permission.name LIKE 'work-order:%:verify';

INSERT INTO authorization_v2_permission_code_map_20260814 (
    source_permission_id, target_permission_id, source_permission_name, target_permission_name, migration_reason
)
SELECT source_permission.id, target_permission.id, source_permission.name, target_permission.name, pair.reason
FROM (
    SELECT 'user:manage:export' source_name, 'user:export:download' target_name, '拆分导出结果下载' reason
    UNION ALL SELECT 'user:manage:update_status', 'user:manage:status:activate', '拆分用户启用'
    UNION ALL SELECT 'user:manage:update_status', 'user:manage:status:lock', '拆分用户锁定'
    UNION ALL SELECT 'user:manage:update_status', 'user:manage:status:unlock', '拆分用户解锁'
    UNION ALL SELECT 'user:manage:assign_roles', 'user:authorization:view', '拆分授权查看'
    UNION ALL SELECT 'user:manage:assign_roles', 'user:authorization:grant', '拆分授权授予'
    UNION ALL SELECT 'user:manage:assign_roles', 'user:authorization:revoke', '拆分授权撤销'
    UNION ALL SELECT 'notification:config:update', 'notification:schedule:update', '拆分通知计划修改'
    UNION ALL SELECT 'role-bundle:manage:create', 'authorization-profile:copy', '拆分模板复制'
    UNION ALL SELECT 'role-bundle:manage:update', 'authorization-profile:publish', '拆分模板发布'
    UNION ALL SELECT 'app-update:storage', 'app-update:storage:view', '拆分存储查看'
    UNION ALL SELECT 'app-update:storage', 'app-update:storage:delete', '拆分对象删除'
    UNION ALL SELECT 'tenant:manage:update-status', 'tenant:manage:status:activate', '拆分租户启用'
    UNION ALL SELECT 'user:manage:create', 'user:import:template:download', '保留旧用户写权限的导入模板能力'
    UNION ALL SELECT 'user:manage:create', 'user:import:preview', '保留旧用户写权限的导入预览能力'
    UNION ALL SELECT 'user:manage:create', 'user:import:execute', '保留旧用户写权限的导入执行能力'
    UNION ALL SELECT 'user:manage:create', 'user:import:cancel', '保留旧用户写权限的导入取消能力'
    UNION ALL SELECT 'user:manage:update_details', 'user:import:template:download', '保留旧用户写权限的导入模板能力'
    UNION ALL SELECT 'user:manage:update_details', 'user:import:preview', '保留旧用户写权限的导入预览能力'
    UNION ALL SELECT 'user:manage:update_details', 'user:import:execute', '保留旧用户写权限的导入执行能力'
    UNION ALL SELECT 'user:manage:update_details', 'user:import:cancel', '保留旧用户写权限的导入取消能力'
    UNION ALL SELECT 'user:manage:update_status', 'user:import:template:download', '保留旧用户写权限的导入模板能力'
    UNION ALL SELECT 'user:manage:update_status', 'user:import:preview', '保留旧用户写权限的导入预览能力'
    UNION ALL SELECT 'user:manage:update_status', 'user:import:execute', '保留旧用户写权限的导入执行能力'
    UNION ALL SELECT 'user:manage:update_status', 'user:import:cancel', '保留旧用户写权限的导入取消能力'
    UNION ALL SELECT 'user:manage:assign_roles', 'user:import:template:download', '保留旧用户写权限的导入模板能力'
    UNION ALL SELECT 'user:manage:assign_roles', 'user:import:preview', '保留旧用户写权限的导入预览能力'
    UNION ALL SELECT 'user:manage:assign_roles', 'user:import:execute', '保留旧用户写权限的导入执行能力'
    UNION ALL SELECT 'user:manage:assign_roles', 'user:import:cancel', '保留旧用户写权限的导入取消能力'
) pair
JOIN permissions source_permission ON source_permission.name = pair.source_name
JOIN permissions target_permission ON target_permission.name = pair.target_name;

INSERT INTO authorization_v2_permission_code_map_20260814 (
    source_permission_id, target_permission_id, source_permission_name, target_permission_name, migration_reason
)
SELECT source_permission.id, target_permission.id, source_permission.name, target_permission.name, split_mapping.reason
FROM (
    SELECT source_permission.name source_name, REPLACE(source_permission.name, ':respond', ':assignment:accept') target_name, '拆分接受派单' reason
    FROM permissions source_permission WHERE source_permission.name LIKE 'work-order:%:respond'
    UNION ALL SELECT source_permission.name, REPLACE(source_permission.name, ':respond', ':assignment:reject'), '拆分拒绝派单'
    FROM permissions source_permission WHERE source_permission.name LIKE 'work-order:%:respond'
    UNION ALL SELECT source_permission.name, REPLACE(source_permission.name, ':verify', ':verification:reject'), '拆分验收驳回'
    FROM permissions source_permission WHERE source_permission.name LIKE 'work-order:%:verify'
    UNION ALL SELECT source_permission.name, REPLACE(source_permission.name, ':hold', ':resume'), '拆分恢复处理'
    FROM permissions source_permission WHERE source_permission.name LIKE 'work-order:%:hold'
) split_mapping
JOIN permissions source_permission ON source_permission.name = split_mapping.source_name
JOIN permissions target_permission ON target_permission.name = split_mapping.target_name;

--rollback DELETE FROM authorization_v2_permission_code_map_20260814;

--changeset e-report:2026-08-14-034-authorization-v2-data-mysql dbms:mysql
--comment Expand every legacy role grant into immutable profile metadata, one explicit data scope, and atomic permission grants.
INSERT INTO authorization_profiles (
    id, tenant_id, code, system_key, display_name, description, built_in, active,
    scope_seed_strategy, current_version_id, sort_order, created_at, updated_at
)
SELECT role_row.id,
       role_row.tenant_id,
       role_row.name,
       CASE WHEN role_row.built_in THEN role_row.name ELSE NULL END,
       COALESCE(NULLIF(role_row.display_name, ''), role_row.name),
       role_row.description,
       role_row.built_in,
       TRUE,
       'ACCOUNT_ORG_TREE',
       NULL,
       100,
       CURRENT_TIMESTAMP(6),
       CURRENT_TIMESTAMP(6)
FROM roles role_row;

INSERT INTO authorization_profiles (
    id, tenant_id, code, system_key, display_name, description, built_in, active,
    scope_seed_strategy, current_version_id, sort_order, created_at, updated_at
)
SELECT bundle.id,
       bundle.tenant_id,
       COALESCE(bundle.system_key, CONCAT('LEGACY_BUNDLE_', BIN_TO_UUID(bundle.id, 1))),
       bundle.system_key,
       bundle.display_name,
       bundle.description,
       bundle.built_in,
       bundle.active,
       CASE
           WHEN EXISTS (SELECT 1 FROM role_bundle_grant_rules rule WHERE rule.role_bundle_id = bundle.id AND rule.scope_strategy = 'TENANT_ALL') THEN 'TENANT_ALL'
           WHEN EXISTS (SELECT 1 FROM role_bundle_grant_rules rule WHERE rule.role_bundle_id = bundle.id AND rule.scope_strategy = 'ACCOUNT_ORG_TREE') THEN 'ACCOUNT_ORG_TREE'
           ELSE 'ACCOUNT_ORG'
       END,
       NULL,
       bundle.sort_order,
       bundle.created_at,
       bundle.updated_at
FROM role_bundles bundle;

INSERT INTO authorization_profile_versions (
    id, tenant_id, profile_id, version_no, status, change_summary,
    created_by_membership_id, created_at, published_by_membership_id, published_at
)
SELECT role_row.id,
       role_row.tenant_id,
       role_row.id,
       1,
       'PUBLISHED',
       '由 1.0.47 角色定义确定性迁移',
       NULL,
       CURRENT_TIMESTAMP(6),
       NULL,
       CURRENT_TIMESTAMP(6)
FROM roles role_row;

INSERT INTO authorization_profile_versions (
    id, tenant_id, profile_id, version_no, status, change_summary,
    created_by_membership_id, created_at, published_by_membership_id, published_at
)
SELECT bundle.id,
       bundle.tenant_id,
       bundle.id,
       1,
       'PUBLISHED',
       '由 1.0.47 岗位模板确定性迁移',
       NULL,
       CURRENT_TIMESTAMP(6),
       NULL,
       CURRENT_TIMESTAMP(6)
FROM role_bundles bundle;

UPDATE authorization_profiles SET current_version_id = id;

INSERT INTO authorization_profile_permission_rules (
    id, tenant_id, profile_version_id, permission_id, relation_constraint, sort_order
)
SELECT UUID_TO_BIN(UUID(), 1), normalized.tenant_id, normalized.profile_version_id,
       normalized.permission_id, normalized.relation_constraint, 100
FROM (
    SELECT DISTINCT role_row.tenant_id,
           role_row.id profile_version_id,
           code_map.target_permission_id permission_id,
           CASE
               WHEN code_map.target_permission_name IN (
                   'management:access', 'notification:recipient:county', 'notification:recipient:tenant'
               ) THEN 'NONE'
               WHEN code_map.target_permission_name LIKE 'work-order:%:response:submit'
                 OR code_map.target_permission_name LIKE 'work-order:%:hold'
                 OR code_map.target_permission_name LIKE 'work-order:%:resume'
                   THEN 'CURRENT_HANDLER'
               WHEN code_map.target_permission_name LIKE 'work-order:%:assignment:accept'
                 OR code_map.target_permission_name LIKE 'work-order:%:assignment:reject'
                   THEN 'PENDING_HANDLER'
               WHEN role_rule.scope_profile = 'GRANT_SCOPE' THEN 'ANY_IN_SCOPE'
               WHEN role_rule.scope_profile = 'MEMBERSHIP_ORG' THEN 'MEMBERSHIP_ORG'
               WHEN role_rule.scope_profile = 'REPORTER' THEN 'REPORTER'
               WHEN role_rule.scope_profile IN ('PARTICIPANT', 'CLAIMABLE_OR_PARTICIPANT') THEN 'PARTICIPANT'
               WHEN role_rule.scope_profile = 'PENDING_HANDLER' THEN 'PENDING_HANDLER'
               WHEN role_rule.scope_profile IN ('CURRENT_HANDLER', 'PENDING_OR_CURRENT_HANDLER') THEN 'CURRENT_HANDLER'
           END relation_constraint
    FROM roles role_row
    JOIN role_permissions role_rule ON role_rule.role_id = role_row.id
    JOIN authorization_v2_permission_code_map_20260814 code_map
      ON code_map.source_permission_id = role_rule.permission_id
) normalized;

INSERT INTO authorization_profile_permission_rules (
    id, tenant_id, profile_version_id, permission_id, relation_constraint, sort_order
)
SELECT UUID_TO_BIN(UUID(), 1), normalized.tenant_id, normalized.profile_version_id,
       normalized.permission_id, normalized.relation_constraint, 100
FROM (
    SELECT DISTINCT bundle.tenant_id,
           bundle.id profile_version_id,
           code_map.target_permission_id permission_id,
           CASE
               WHEN code_map.target_permission_name IN (
                   'management:access', 'notification:recipient:county', 'notification:recipient:tenant'
               ) THEN 'NONE'
               WHEN code_map.target_permission_name LIKE 'work-order:%:response:submit'
                 OR code_map.target_permission_name LIKE 'work-order:%:hold'
                 OR code_map.target_permission_name LIKE 'work-order:%:resume'
                   THEN 'CURRENT_HANDLER'
               WHEN code_map.target_permission_name LIKE 'work-order:%:assignment:accept'
                 OR code_map.target_permission_name LIKE 'work-order:%:assignment:reject'
                   THEN 'PENDING_HANDLER'
               WHEN role_rule.scope_profile = 'GRANT_SCOPE' THEN 'ANY_IN_SCOPE'
               WHEN role_rule.scope_profile = 'MEMBERSHIP_ORG' THEN 'MEMBERSHIP_ORG'
               WHEN role_rule.scope_profile = 'REPORTER' THEN 'REPORTER'
               WHEN role_rule.scope_profile IN ('PARTICIPANT', 'CLAIMABLE_OR_PARTICIPANT') THEN 'PARTICIPANT'
               WHEN role_rule.scope_profile = 'PENDING_HANDLER' THEN 'PENDING_HANDLER'
               WHEN role_rule.scope_profile IN ('CURRENT_HANDLER', 'PENDING_OR_CURRENT_HANDLER') THEN 'CURRENT_HANDLER'
           END relation_constraint
    FROM role_bundles bundle
    JOIN role_bundle_grant_rules bundle_rule ON bundle_rule.role_bundle_id = bundle.id
    JOIN role_permissions role_rule ON role_rule.role_id = bundle_rule.role_id
    JOIN authorization_v2_permission_code_map_20260814 code_map
      ON code_map.source_permission_id = role_rule.permission_id
) normalized;

INSERT INTO authorization_data_scopes (id, tenant_id, tenant_wide, canonical_hash, created_at)
SELECT legacy_grant.id,
       legacy_grant.tenant_id,
       legacy_grant.data_scope = 'TENANT_ALL',
       CONCAT('LEGACY:', HEX(legacy_grant.id)),
       COALESCE(legacy_grant.granted_at, CURRENT_TIMESTAMP(6))
FROM membership_role_grants legacy_grant;

INSERT INTO authorization_data_scope_nodes (
    id, tenant_id, scope_id, org_unit_id, include_descendants
)
SELECT UUID_TO_BIN(UUID(), 1),
       legacy_grant.tenant_id,
       legacy_grant.id,
       legacy_grant.scope_org_id,
       legacy_grant.data_scope = 'ORG_TREE'
FROM membership_role_grants legacy_grant
WHERE legacy_grant.data_scope IN ('ORG', 'ORG_TREE');

INSERT INTO authorization_assignments (
    id, tenant_id, membership_id, profile_version_id, source_type, source_label,
    status, reason, granted_by_membership_id, granted_at,
    revoked_by_membership_id, revoked_at, revoke_reason
)
SELECT legacy_grant.id,
       legacy_grant.tenant_id,
       legacy_grant.membership_id,
       legacy_grant.role_id,
       'MIGRATION',
       COALESCE(NULLIF(role_row.display_name, ''), role_row.name),
       'ACTIVE',
       '由 1.0.47 角色授权确定性迁移',
       NULL,
       COALESCE(legacy_grant.granted_at, CURRENT_TIMESTAMP(6)),
       NULL,
       NULL,
       NULL
FROM membership_role_grants legacy_grant
JOIN roles role_row ON role_row.id = legacy_grant.role_id;

INSERT INTO permission_grants (
    id, tenant_id, membership_id, assignment_id, profile_rule_id,
    permission_id, data_scope_id, relation_constraint, valid_from, expires_at,
    status, customized, reason, granted_by_membership_id, granted_at,
    revoked_by_membership_id, revoked_at, revoke_reason
)
SELECT UUID_TO_BIN(UUID(), 1), normalized.tenant_id, normalized.membership_id,
       normalized.assignment_id, profile_rule.id, normalized.permission_id,
       normalized.data_scope_id, normalized.relation_constraint,
       normalized.valid_from, normalized.expires_at,
       'ACTIVE', FALSE, '由 1.0.47 角色权限规则展开', NULL, normalized.valid_from,
       NULL, NULL, NULL
FROM (
    SELECT DISTINCT legacy_grant.tenant_id,
           legacy_grant.membership_id,
           legacy_grant.id assignment_id,
           legacy_grant.id data_scope_id,
           legacy_grant.role_id profile_version_id,
           code_map.target_permission_id permission_id,
           CASE
               WHEN code_map.target_permission_name IN (
                   'management:access', 'notification:recipient:county', 'notification:recipient:tenant'
               ) THEN 'NONE'
               WHEN code_map.target_permission_name LIKE 'work-order:%:response:submit'
                 OR code_map.target_permission_name LIKE 'work-order:%:hold'
                 OR code_map.target_permission_name LIKE 'work-order:%:resume'
                   THEN 'CURRENT_HANDLER'
               WHEN code_map.target_permission_name LIKE 'work-order:%:assignment:accept'
                 OR code_map.target_permission_name LIKE 'work-order:%:assignment:reject'
                   THEN 'PENDING_HANDLER'
               WHEN role_rule.scope_profile = 'GRANT_SCOPE' THEN 'ANY_IN_SCOPE'
               WHEN role_rule.scope_profile = 'MEMBERSHIP_ORG' THEN 'MEMBERSHIP_ORG'
               WHEN role_rule.scope_profile = 'REPORTER' THEN 'REPORTER'
               WHEN role_rule.scope_profile IN ('PARTICIPANT', 'CLAIMABLE_OR_PARTICIPANT') THEN 'PARTICIPANT'
               WHEN role_rule.scope_profile = 'PENDING_HANDLER' THEN 'PENDING_HANDLER'
               WHEN role_rule.scope_profile IN ('CURRENT_HANDLER', 'PENDING_OR_CURRENT_HANDLER') THEN 'CURRENT_HANDLER'
           END relation_constraint,
           COALESCE(legacy_grant.granted_at, CURRENT_TIMESTAMP(6)) valid_from,
           legacy_grant.expires_at
    FROM membership_role_grants legacy_grant
    JOIN role_permissions role_rule ON role_rule.role_id = legacy_grant.role_id
    JOIN authorization_v2_permission_code_map_20260814 code_map
      ON code_map.source_permission_id = role_rule.permission_id
) normalized
JOIN authorization_profile_permission_rules profile_rule
  ON profile_rule.profile_version_id = normalized.profile_version_id
 AND profile_rule.permission_id = normalized.permission_id
 AND profile_rule.relation_constraint = normalized.relation_constraint;

UPDATE users member
SET member.authorization_version = member.authorization_version + 1,
    member.updated_at = CURRENT_TIMESTAMP(6)
WHERE EXISTS (
    SELECT 1 FROM authorization_assignments assignment_row
    WHERE assignment_row.membership_id = member.id
);

--rollback UPDATE users member SET member.authorization_version = member.authorization_version - 1 WHERE EXISTS (SELECT 1 FROM authorization_assignments assignment_row WHERE assignment_row.membership_id = member.id);
--rollback DELETE FROM permission_grants;
--rollback DELETE FROM authorization_assignments;
--rollback DELETE FROM authorization_data_scope_nodes;
--rollback DELETE FROM authorization_data_scopes;
--rollback DELETE FROM authorization_profile_permission_rules;
--rollback UPDATE authorization_profiles SET current_version_id = NULL;
--rollback DELETE FROM authorization_profile_versions;
--rollback DELETE FROM authorization_profiles;

--changeset e-report:2026-08-14-034-authorization-v2-data-h2 dbms:h2
--comment H2 equivalent of the deterministic authorization V2 expansion.
INSERT INTO authorization_profiles (
    id, tenant_id, code, system_key, display_name, description, built_in, active,
    scope_seed_strategy, current_version_id, sort_order, created_at, updated_at
)
SELECT role_row.id,
       role_row.tenant_id,
       role_row.name,
       CASE WHEN role_row.built_in THEN role_row.name ELSE NULL END,
       COALESCE(NULLIF(role_row.display_name, ''), role_row.name),
       role_row.description,
       role_row.built_in,
       TRUE,
       'ACCOUNT_ORG_TREE',
       NULL,
       100,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM roles role_row;

INSERT INTO authorization_profiles (
    id, tenant_id, code, system_key, display_name, description, built_in, active,
    scope_seed_strategy, current_version_id, sort_order, created_at, updated_at
)
SELECT bundle.id,
       bundle.tenant_id,
       COALESCE(bundle.system_key, CONCAT('LEGACY_BUNDLE_', CAST(bundle.id AS VARCHAR))),
       bundle.system_key,
       bundle.display_name,
       bundle.description,
       bundle.built_in,
       bundle.active,
       CASE
           WHEN EXISTS (SELECT 1 FROM role_bundle_grant_rules rule WHERE rule.role_bundle_id = bundle.id AND rule.scope_strategy = 'TENANT_ALL') THEN 'TENANT_ALL'
           WHEN EXISTS (SELECT 1 FROM role_bundle_grant_rules rule WHERE rule.role_bundle_id = bundle.id AND rule.scope_strategy = 'ACCOUNT_ORG_TREE') THEN 'ACCOUNT_ORG_TREE'
           ELSE 'ACCOUNT_ORG'
       END,
       NULL,
       bundle.sort_order,
       bundle.created_at,
       bundle.updated_at
FROM role_bundles bundle;

INSERT INTO authorization_profile_versions (
    id, tenant_id, profile_id, version_no, status, change_summary,
    created_by_membership_id, created_at, published_by_membership_id, published_at
)
SELECT role_row.id,
       role_row.tenant_id,
       role_row.id,
       1,
       'PUBLISHED',
       '由 1.0.47 角色定义确定性迁移',
       NULL,
       CURRENT_TIMESTAMP,
       NULL,
       CURRENT_TIMESTAMP
FROM roles role_row;

INSERT INTO authorization_profile_versions (
    id, tenant_id, profile_id, version_no, status, change_summary,
    created_by_membership_id, created_at, published_by_membership_id, published_at
)
SELECT bundle.id,
       bundle.tenant_id,
       bundle.id,
       1,
       'PUBLISHED',
       '由 1.0.47 岗位模板确定性迁移',
       NULL,
       CURRENT_TIMESTAMP,
       NULL,
       CURRENT_TIMESTAMP
FROM role_bundles bundle;

UPDATE authorization_profiles SET current_version_id = id;

INSERT INTO authorization_profile_permission_rules (
    id, tenant_id, profile_version_id, permission_id, relation_constraint, sort_order
)
SELECT RANDOM_UUID(), normalized.tenant_id, normalized.profile_version_id,
       normalized.permission_id, normalized.relation_constraint, 100
FROM (
    SELECT DISTINCT role_row.tenant_id,
           role_row.id profile_version_id,
           code_map.target_permission_id permission_id,
           CASE
               WHEN code_map.target_permission_name IN (
                   'management:access', 'notification:recipient:county', 'notification:recipient:tenant'
               ) THEN 'NONE'
               WHEN code_map.target_permission_name LIKE 'work-order:%:response:submit'
                 OR code_map.target_permission_name LIKE 'work-order:%:hold'
                 OR code_map.target_permission_name LIKE 'work-order:%:resume'
                   THEN 'CURRENT_HANDLER'
               WHEN code_map.target_permission_name LIKE 'work-order:%:assignment:accept'
                 OR code_map.target_permission_name LIKE 'work-order:%:assignment:reject'
                   THEN 'PENDING_HANDLER'
               WHEN role_rule.scope_profile = 'GRANT_SCOPE' THEN 'ANY_IN_SCOPE'
               WHEN role_rule.scope_profile = 'MEMBERSHIP_ORG' THEN 'MEMBERSHIP_ORG'
               WHEN role_rule.scope_profile = 'REPORTER' THEN 'REPORTER'
               WHEN role_rule.scope_profile IN ('PARTICIPANT', 'CLAIMABLE_OR_PARTICIPANT') THEN 'PARTICIPANT'
               WHEN role_rule.scope_profile = 'PENDING_HANDLER' THEN 'PENDING_HANDLER'
               WHEN role_rule.scope_profile IN ('CURRENT_HANDLER', 'PENDING_OR_CURRENT_HANDLER') THEN 'CURRENT_HANDLER'
           END relation_constraint
    FROM roles role_row
    JOIN role_permissions role_rule ON role_rule.role_id = role_row.id
    JOIN authorization_v2_permission_code_map_20260814 code_map
      ON code_map.source_permission_id = role_rule.permission_id
) normalized;

INSERT INTO authorization_profile_permission_rules (
    id, tenant_id, profile_version_id, permission_id, relation_constraint, sort_order
)
SELECT RANDOM_UUID(), normalized.tenant_id, normalized.profile_version_id,
       normalized.permission_id, normalized.relation_constraint, 100
FROM (
    SELECT DISTINCT bundle.tenant_id,
           bundle.id profile_version_id,
           code_map.target_permission_id permission_id,
           CASE
               WHEN code_map.target_permission_name IN (
                   'management:access', 'notification:recipient:county', 'notification:recipient:tenant'
               ) THEN 'NONE'
               WHEN code_map.target_permission_name LIKE 'work-order:%:response:submit'
                 OR code_map.target_permission_name LIKE 'work-order:%:hold'
                 OR code_map.target_permission_name LIKE 'work-order:%:resume'
                   THEN 'CURRENT_HANDLER'
               WHEN code_map.target_permission_name LIKE 'work-order:%:assignment:accept'
                 OR code_map.target_permission_name LIKE 'work-order:%:assignment:reject'
                   THEN 'PENDING_HANDLER'
               WHEN role_rule.scope_profile = 'GRANT_SCOPE' THEN 'ANY_IN_SCOPE'
               WHEN role_rule.scope_profile = 'MEMBERSHIP_ORG' THEN 'MEMBERSHIP_ORG'
               WHEN role_rule.scope_profile = 'REPORTER' THEN 'REPORTER'
               WHEN role_rule.scope_profile IN ('PARTICIPANT', 'CLAIMABLE_OR_PARTICIPANT') THEN 'PARTICIPANT'
               WHEN role_rule.scope_profile = 'PENDING_HANDLER' THEN 'PENDING_HANDLER'
               WHEN role_rule.scope_profile IN ('CURRENT_HANDLER', 'PENDING_OR_CURRENT_HANDLER') THEN 'CURRENT_HANDLER'
           END relation_constraint
    FROM role_bundles bundle
    JOIN role_bundle_grant_rules bundle_rule ON bundle_rule.role_bundle_id = bundle.id
    JOIN role_permissions role_rule ON role_rule.role_id = bundle_rule.role_id
    JOIN authorization_v2_permission_code_map_20260814 code_map
      ON code_map.source_permission_id = role_rule.permission_id
) normalized;

INSERT INTO authorization_data_scopes (id, tenant_id, tenant_wide, canonical_hash, created_at)
SELECT legacy_grant.id,
       legacy_grant.tenant_id,
       legacy_grant.data_scope = 'TENANT_ALL',
       CONCAT('LEGACY:', CAST(legacy_grant.id AS VARCHAR)),
       COALESCE(legacy_grant.granted_at, CURRENT_TIMESTAMP)
FROM membership_role_grants legacy_grant;

INSERT INTO authorization_data_scope_nodes (
    id, tenant_id, scope_id, org_unit_id, include_descendants
)
SELECT RANDOM_UUID(),
       legacy_grant.tenant_id,
       legacy_grant.id,
       legacy_grant.scope_org_id,
       legacy_grant.data_scope = 'ORG_TREE'
FROM membership_role_grants legacy_grant
WHERE legacy_grant.data_scope IN ('ORG', 'ORG_TREE');

INSERT INTO authorization_assignments (
    id, tenant_id, membership_id, profile_version_id, source_type, source_label,
    status, reason, granted_by_membership_id, granted_at,
    revoked_by_membership_id, revoked_at, revoke_reason
)
SELECT legacy_grant.id,
       legacy_grant.tenant_id,
       legacy_grant.membership_id,
       legacy_grant.role_id,
       'MIGRATION',
       COALESCE(NULLIF(role_row.display_name, ''), role_row.name),
       'ACTIVE',
       '由 1.0.47 角色授权确定性迁移',
       NULL,
       COALESCE(legacy_grant.granted_at, CURRENT_TIMESTAMP),
       NULL,
       NULL,
       NULL
FROM membership_role_grants legacy_grant
JOIN roles role_row ON role_row.id = legacy_grant.role_id;

INSERT INTO permission_grants (
    id, tenant_id, membership_id, assignment_id, profile_rule_id,
    permission_id, data_scope_id, relation_constraint, valid_from, expires_at,
    status, customized, reason, granted_by_membership_id, granted_at,
    revoked_by_membership_id, revoked_at, revoke_reason
)
SELECT RANDOM_UUID(), normalized.tenant_id, normalized.membership_id,
       normalized.assignment_id, profile_rule.id, normalized.permission_id,
       normalized.data_scope_id, normalized.relation_constraint,
       normalized.valid_from, normalized.expires_at,
       'ACTIVE', FALSE, '由 1.0.47 角色权限规则展开', NULL, normalized.valid_from,
       NULL, NULL, NULL
FROM (
    SELECT DISTINCT legacy_grant.tenant_id,
           legacy_grant.membership_id,
           legacy_grant.id assignment_id,
           legacy_grant.id data_scope_id,
           legacy_grant.role_id profile_version_id,
           code_map.target_permission_id permission_id,
           CASE
               WHEN code_map.target_permission_name IN (
                   'management:access', 'notification:recipient:county', 'notification:recipient:tenant'
               ) THEN 'NONE'
               WHEN code_map.target_permission_name LIKE 'work-order:%:response:submit'
                 OR code_map.target_permission_name LIKE 'work-order:%:hold'
                 OR code_map.target_permission_name LIKE 'work-order:%:resume'
                   THEN 'CURRENT_HANDLER'
               WHEN code_map.target_permission_name LIKE 'work-order:%:assignment:accept'
                 OR code_map.target_permission_name LIKE 'work-order:%:assignment:reject'
                   THEN 'PENDING_HANDLER'
               WHEN role_rule.scope_profile = 'GRANT_SCOPE' THEN 'ANY_IN_SCOPE'
               WHEN role_rule.scope_profile = 'MEMBERSHIP_ORG' THEN 'MEMBERSHIP_ORG'
               WHEN role_rule.scope_profile = 'REPORTER' THEN 'REPORTER'
               WHEN role_rule.scope_profile IN ('PARTICIPANT', 'CLAIMABLE_OR_PARTICIPANT') THEN 'PARTICIPANT'
               WHEN role_rule.scope_profile = 'PENDING_HANDLER' THEN 'PENDING_HANDLER'
               WHEN role_rule.scope_profile IN ('CURRENT_HANDLER', 'PENDING_OR_CURRENT_HANDLER') THEN 'CURRENT_HANDLER'
           END relation_constraint,
           COALESCE(legacy_grant.granted_at, CURRENT_TIMESTAMP) valid_from,
           legacy_grant.expires_at
    FROM membership_role_grants legacy_grant
    JOIN role_permissions role_rule ON role_rule.role_id = legacy_grant.role_id
    JOIN authorization_v2_permission_code_map_20260814 code_map
      ON code_map.source_permission_id = role_rule.permission_id
) normalized
JOIN authorization_profile_permission_rules profile_rule
  ON profile_rule.profile_version_id = normalized.profile_version_id
 AND profile_rule.permission_id = normalized.permission_id
 AND profile_rule.relation_constraint = normalized.relation_constraint;

UPDATE users member
SET authorization_version = authorization_version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE EXISTS (
    SELECT 1 FROM authorization_assignments assignment_row
    WHERE assignment_row.membership_id = member.id
);

--rollback UPDATE users member SET authorization_version = authorization_version - 1 WHERE EXISTS (SELECT 1 FROM authorization_assignments assignment_row WHERE assignment_row.membership_id = member.id);
--rollback DELETE FROM permission_grants;
--rollback DELETE FROM authorization_assignments;
--rollback DELETE FROM authorization_data_scope_nodes;
--rollback DELETE FROM authorization_data_scopes;
--rollback DELETE FROM authorization_profile_permission_rules;
--rollback UPDATE authorization_profiles SET current_version_id = NULL;
--rollback DELETE FROM authorization_profile_versions;
--rollback DELETE FROM authorization_profiles;

--changeset e-report:2026-08-14-034-authorization-v2-data-assertions dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM membership_role_grants legacy_grant LEFT JOIN authorization_assignments assignment_row ON assignment_row.id = legacy_grant.id WHERE assignment_row.id IS NULL
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permission_grants grant_row LEFT JOIN authorization_assignments assignment_row ON assignment_row.id = grant_row.assignment_id LEFT JOIN authorization_data_scopes scope_row ON scope_row.id = grant_row.data_scope_id LEFT JOIN permissions permission_row ON permission_row.id = grant_row.permission_id WHERE assignment_row.id IS NULL OR scope_row.id IS NULL OR permission_row.id IS NULL
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permission_grants grant_row WHERE EXISTS (SELECT 1 FROM authorization_v2_permission_code_map_20260814 changed_mapping WHERE changed_mapping.source_permission_id = grant_row.permission_id AND changed_mapping.source_permission_id <> changed_mapping.target_permission_id) AND NOT EXISTS (SELECT 1 FROM authorization_v2_permission_code_map_20260814 identity_mapping WHERE identity_mapping.source_permission_id = grant_row.permission_id AND identity_mapping.source_permission_id = identity_mapping.target_permission_id)
--comment Halt startup when any legacy grant failed to expand or a deprecated composite permission leaked into V2 runtime facts.
SELECT 1;

--rollback SELECT 1;

--changeset e-report:2026-08-14-034-authorization-v2-permission-seed-mysql dbms:mysql
--comment Seed every new atomic permission before expanding legacy role rules.
INSERT INTO permissions (id, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1),
       CONCAT('work-order:', work_type.slug, ':', work_action.suffix),
       CONCAT(work_type.label, work_action.label),
       CONCAT(work_action.description, work_type.label)
FROM (
    SELECT 'network-fault' slug, '网络故障工单' label
    UNION ALL SELECT 'box-door-open', '箱门未关工单'
    UNION ALL SELECT 'damaged-box-replacement', '破损箱体更换工单'
) work_type
CROSS JOIN (
    SELECT 'response:submit' suffix, '提交回单' label, '提交本人当前负责的' description
    UNION ALL SELECT 'attachment:read', '附件读取', '预览或下载授权范围内的'
    UNION ALL SELECT 'assignment:accept', '接受派单', '接受指派给本人的'
    UNION ALL SELECT 'assignment:reject', '拒绝派单', '拒绝指派给本人的'
    UNION ALL SELECT 'assignment:create', '派单', '将未分配工单派给合格处理人员：'
    UNION ALL SELECT 'assignment:reassign', '调单', '调整目标处理人员：'
    UNION ALL SELECT 'verification:approve', '验收通过', '验收通过并闭环：'
    UNION ALL SELECT 'verification:reject', '验收驳回', '驳回处理回单并返工：'
    UNION ALL SELECT 'resume', '恢复处理', '恢复本人当前挂起的'
) work_action
WHERE NOT EXISTS (
    SELECT 1 FROM permissions existing
    WHERE existing.name = CONCAT('work-order:', work_type.slug, ':', work_action.suffix)
);

INSERT INTO permissions (id, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), seed.name, seed.display_name, seed.description
FROM (
    SELECT 'user:manage:details:update' name, '用户资料修改' display_name, '更新授权组织范围内用户的基本信息' description
    UNION ALL SELECT 'user:manage:status:activate', '用户启用', '将用户恢复为可登录状态'
    UNION ALL SELECT 'user:manage:status:deactivate', '用户停用', '停用用户并阻止继续登录'
    UNION ALL SELECT 'user:manage:status:lock', '用户锁定', '因安全原因锁定用户账号'
    UNION ALL SELECT 'user:manage:status:unlock', '用户解锁', '解除用户账号锁定'
    UNION ALL SELECT 'user:manage:password:reset', '用户密码重置', '重置授权组织范围内用户的登录密码'
    UNION ALL SELECT 'user:authorization:view', '用户授权查看', '查看可管理的用户授权及最终权限摘要'
    UNION ALL SELECT 'user:authorization:grant', '用户授权授予', '新增不超过自身委派边界的权限授权'
    UNION ALL SELECT 'user:authorization:adjust', '用户授权调整', '调整自身可管理授权的范围、关系或有效期'
    UNION ALL SELECT 'user:authorization:revoke', '用户授权撤销', '撤销自身可管理的授权来源'
    UNION ALL SELECT 'user:import:template:download', '用户导入模板下载', '生成并下载按委派边界裁剪的用户导入模板'
    UNION ALL SELECT 'user:import:preview', '用户导入预览', '上传并校验用户及授权导入工作簿'
    UNION ALL SELECT 'user:import:execute', '用户导入执行', '执行已预览的用户及授权批量变更'
    UNION ALL SELECT 'user:import:cancel', '用户导入取消', '取消本人创建且尚未结束的导入任务'
    UNION ALL SELECT 'user:export:create', '用户导出创建', '创建授权组织范围内的用户数据导出任务'
    UNION ALL SELECT 'user:export:download', '用户导出下载', '下载本人创建的用户数据导出结果'
    UNION ALL SELECT 'notification:channel:update', '通知渠道开关修改', '单独开启或关闭授权组织范围内的通知渠道'
    UNION ALL SELECT 'notification:schedule:update', '通知计划修改', '修改授权组织范围内的未闭环工单日报计划'
    UNION ALL SELECT 'authorization-profile:view', '岗位权限模板查看', '查看可授予的岗位权限模板及版本'
    UNION ALL SELECT 'authorization-profile:create', '岗位权限模板创建', '创建租户自定义岗位权限模板草稿'
    UNION ALL SELECT 'authorization-profile:draft:update', '岗位权限模板草稿修改', '修改未发布的岗位权限模板草稿'
    UNION ALL SELECT 'authorization-profile:copy', '岗位权限模板复制', '从可见模板复制新的自定义模板草稿'
    UNION ALL SELECT 'authorization-profile:publish', '岗位权限模板发布', '发布不可变的岗位权限模板版本'
    UNION ALL SELECT 'authorization-profile:retire', '岗位权限模板退役', '停止模板用于新增授权'
    UNION ALL SELECT 'authorization-profile:assignment:upgrade', '岗位授权版本升级', '预览并批量升级既有岗位授权版本'
    UNION ALL SELECT 'organization:manage:move', '组织移动', '调整授权范围内组织节点的父级关系'
    UNION ALL SELECT 'organization:manage:status:update', '组织状态修改', '启用或停用授权范围内的组织节点'
    UNION ALL SELECT 'app-update:storage:view', '安装包存储查看', '查看安装包对象存储状态'
    UNION ALL SELECT 'app-update:storage:sync', '安装包存储同步', '将发布记录同步到对象存储'
    UNION ALL SELECT 'app-update:storage:delete', '安装包对象删除', '删除对象存储中的安装包对象'
    UNION ALL SELECT 'tenant:manage:status:activate', '市公司租户启用', '启用已停用的市公司租户'
    UNION ALL SELECT 'tenant:manage:status:deactivate', '市公司租户停用', '停用市公司租户及其登录能力'
    UNION ALL SELECT 'observability:redis:view', 'Redis 运行诊断查看', '查看 Redis 连接、缓存和轻量观测队列状态'
) seed
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = seed.name);

--rollback DELETE FROM permissions WHERE name IN ('user:manage:details:update','user:manage:status:activate','user:manage:status:deactivate','user:manage:status:lock','user:manage:status:unlock','user:manage:password:reset','user:authorization:view','user:authorization:grant','user:authorization:adjust','user:authorization:revoke','user:import:template:download','user:import:preview','user:import:execute','user:import:cancel','user:export:create','user:export:download','notification:channel:update','notification:schedule:update','authorization-profile:view','authorization-profile:create','authorization-profile:draft:update','authorization-profile:copy','authorization-profile:publish','authorization-profile:retire','authorization-profile:assignment:upgrade','organization:manage:move','organization:manage:status:update','app-update:storage:view','app-update:storage:sync','app-update:storage:delete','tenant:manage:status:activate','tenant:manage:status:deactivate','observability:redis:view');

--changeset e-report:2026-08-14-034-authorization-v2-permission-map dbms:mysql,h2
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM authorization_v2_permission_code_map_20260814
--comment Persist the one-to-one and one-to-many legacy permission migration map for audit and deterministic grant expansion.
INSERT INTO authorization_v2_permission_code_map_20260814 (
    source_permission_id, target_permission_id, source_permission_name, target_permission_name, migration_reason
)
SELECT permission_row.id, permission_row.id, permission_row.name, permission_row.name, '原子权限编码保持不变'
FROM permissions permission_row
WHERE permission_row.name IN (
    'management:access','dashboard:statistics:view',
    'user:manage:view','user:manage:create','user:manage:delete','user:manage:restore',
    'system:config:view','system:config:create','system:config:update','system:config:delete',
    'notification:config:view','notification:recipient:county','notification:recipient:tenant',
    'organization:manage:view','organization:manage:create','organization:manage:update',
    'app-update:view','app-update:publish','app-update:policy-update','app-update:delete',
    'tenant:manage:view','tenant:manage:create','sla-policy:view','sla-policy:update','observability:view'
)
OR (
    permission_row.name LIKE 'work-order:%'
    AND (
        permission_row.name LIKE '%:report' OR permission_row.name LIKE '%:view'
        OR permission_row.name LIKE '%:history:view' OR permission_row.name LIKE '%:audit:view'
        OR permission_row.name LIKE '%:claim' OR permission_row.name LIKE '%:assignment:accept'
        OR permission_row.name LIKE '%:assignment:reject' OR permission_row.name LIKE '%:cancel'
        OR permission_row.name LIKE '%:hold' OR permission_row.name LIKE '%:resume'
        OR permission_row.name LIKE '%:withdraw'
    )
);

INSERT INTO authorization_v2_permission_code_map_20260814 (
    source_permission_id, target_permission_id, source_permission_name, target_permission_name, migration_reason
)
SELECT source_permission.id,
       target_permission.id,
       source_permission.name,
       target_permission.name,
       '旧复合权限迁移为主要原子动作'
FROM permissions source_permission
JOIN permissions target_permission ON target_permission.name = CASE
    WHEN source_permission.name LIKE 'work-order:%:respond' THEN REPLACE(source_permission.name, ':respond', ':response:submit')
    WHEN source_permission.name LIKE 'work-order:%:download' THEN REPLACE(source_permission.name, ':download', ':attachment:read')
    WHEN source_permission.name LIKE 'work-order:%:assign' THEN REPLACE(source_permission.name, ':assign', ':assignment:create')
    WHEN source_permission.name LIKE 'work-order:%:reassign' THEN REPLACE(source_permission.name, ':reassign', ':assignment:reassign')
    WHEN source_permission.name LIKE 'work-order:%:verify' THEN REPLACE(source_permission.name, ':verify', ':verification:approve')
    WHEN source_permission.name = 'user:manage:export' THEN 'user:export:create'
    WHEN source_permission.name = 'user:manage:update_details' THEN 'user:manage:details:update'
    WHEN source_permission.name = 'user:manage:update_status' THEN 'user:manage:status:deactivate'
    WHEN source_permission.name = 'user:manage:assign_roles' THEN 'user:authorization:adjust'
    WHEN source_permission.name = 'user:manage:reset_password' THEN 'user:manage:password:reset'
    WHEN source_permission.name = 'notification:config:update' THEN 'notification:channel:update'
    WHEN source_permission.name = 'role:manage:view' THEN 'authorization-profile:view'
    WHEN source_permission.name = 'role:manage:create' THEN 'authorization-profile:create'
    WHEN source_permission.name = 'role:manage:update' THEN 'authorization-profile:draft:update'
    WHEN source_permission.name = 'role:manage:delete' THEN 'authorization-profile:retire'
    WHEN source_permission.name = 'role-bundle:manage:view' THEN 'authorization-profile:view'
    WHEN source_permission.name = 'role-bundle:manage:create' THEN 'authorization-profile:create'
    WHEN source_permission.name = 'role-bundle:manage:update' THEN 'authorization-profile:draft:update'
    WHEN source_permission.name = 'role-bundle:manage:delete' THEN 'authorization-profile:retire'
    WHEN source_permission.name = 'app-update:storage' THEN 'app-update:storage:sync'
    WHEN source_permission.name = 'tenant:manage:update-status' THEN 'tenant:manage:status:deactivate'
END
WHERE source_permission.name IN (
    'user:manage:export','user:manage:update_details','user:manage:update_status',
    'user:manage:assign_roles','user:manage:reset_password','notification:config:update',
    'role:manage:view','role:manage:create','role:manage:update','role:manage:delete',
    'role-bundle:manage:view','role-bundle:manage:create','role-bundle:manage:update','role-bundle:manage:delete',
    'app-update:storage','tenant:manage:update-status'
)
OR source_permission.name LIKE 'work-order:%:respond'
OR source_permission.name LIKE 'work-order:%:download'
OR source_permission.name LIKE 'work-order:%:assign'
OR source_permission.name LIKE 'work-order:%:reassign'
OR source_permission.name LIKE 'work-order:%:verify';

INSERT INTO authorization_v2_permission_code_map_20260814 (
    source_permission_id, target_permission_id, source_permission_name, target_permission_name, migration_reason
)
SELECT source_permission.id,
       target_permission.id,
       source_permission.name,
       target_permission.name,
       pair.reason
FROM (
    SELECT 'user:manage:export' source_name, 'user:export:download' target_name, '旧导出能力拆分结果下载' reason
    UNION ALL SELECT 'user:manage:update_status', 'user:manage:status:activate', '旧状态修改拆分用户启用'
    UNION ALL SELECT 'user:manage:update_status', 'user:manage:status:lock', '旧状态修改拆分用户锁定'
    UNION ALL SELECT 'user:manage:update_status', 'user:manage:status:unlock', '旧状态修改拆分用户解锁'
    UNION ALL SELECT 'user:manage:assign_roles', 'user:authorization:view', '旧角色分配拆分授权查看'
    UNION ALL SELECT 'user:manage:assign_roles', 'user:authorization:grant', '旧角色分配拆分授权授予'
    UNION ALL SELECT 'user:manage:assign_roles', 'user:authorization:revoke', '旧角色分配拆分授权撤销'
    UNION ALL SELECT 'notification:config:update', 'notification:schedule:update', '旧通知配置拆分通知计划修改'
    UNION ALL SELECT 'role-bundle:manage:create', 'authorization-profile:copy', '旧岗位模板创建拆分模板复制'
    UNION ALL SELECT 'role-bundle:manage:update', 'authorization-profile:publish', '旧岗位模板修改拆分模板发布'
    UNION ALL SELECT 'app-update:storage', 'app-update:storage:view', '旧存储维护拆分存储查看'
    UNION ALL SELECT 'app-update:storage', 'app-update:storage:delete', '旧存储维护拆分对象删除'
    UNION ALL SELECT 'tenant:manage:update-status', 'tenant:manage:status:activate', '旧租户状态修改拆分租户启用'
    UNION ALL SELECT 'user:manage:create', 'user:import:template:download', '保留旧用户写权限的导入模板能力'
    UNION ALL SELECT 'user:manage:create', 'user:import:preview', '保留旧用户写权限的导入预览能力'
    UNION ALL SELECT 'user:manage:create', 'user:import:execute', '保留旧用户写权限的导入执行能力'
    UNION ALL SELECT 'user:manage:create', 'user:import:cancel', '保留旧用户写权限的导入取消能力'
    UNION ALL SELECT 'user:manage:update_details', 'user:import:template:download', '保留旧用户写权限的导入模板能力'
    UNION ALL SELECT 'user:manage:update_details', 'user:import:preview', '保留旧用户写权限的导入预览能力'
    UNION ALL SELECT 'user:manage:update_details', 'user:import:execute', '保留旧用户写权限的导入执行能力'
    UNION ALL SELECT 'user:manage:update_details', 'user:import:cancel', '保留旧用户写权限的导入取消能力'
    UNION ALL SELECT 'user:manage:update_status', 'user:import:template:download', '保留旧用户写权限的导入模板能力'
    UNION ALL SELECT 'user:manage:update_status', 'user:import:preview', '保留旧用户写权限的导入预览能力'
    UNION ALL SELECT 'user:manage:update_status', 'user:import:execute', '保留旧用户写权限的导入执行能力'
    UNION ALL SELECT 'user:manage:update_status', 'user:import:cancel', '保留旧用户写权限的导入取消能力'
    UNION ALL SELECT 'user:manage:assign_roles', 'user:import:template:download', '保留旧用户写权限的导入模板能力'
    UNION ALL SELECT 'user:manage:assign_roles', 'user:import:preview', '保留旧用户写权限的导入预览能力'
    UNION ALL SELECT 'user:manage:assign_roles', 'user:import:execute', '保留旧用户写权限的导入执行能力'
    UNION ALL SELECT 'user:manage:assign_roles', 'user:import:cancel', '保留旧用户写权限的导入取消能力'
) pair
JOIN permissions source_permission ON source_permission.name = pair.source_name
JOIN permissions target_permission ON target_permission.name = pair.target_name;

INSERT INTO authorization_v2_permission_code_map_20260814 (
    source_permission_id, target_permission_id, source_permission_name, target_permission_name, migration_reason
)
SELECT source_permission.id,
       target_permission.id,
       source_permission.name,
       target_permission.name,
       split_mapping.reason
FROM (
    SELECT source_permission.name source_name,
           REPLACE(source_permission.name, ':respond', ':assignment:accept') target_name,
           '旧处理权限拆分接受派单' reason
    FROM permissions source_permission WHERE source_permission.name LIKE 'work-order:%:respond'
    UNION ALL
    SELECT source_permission.name,
           REPLACE(source_permission.name, ':respond', ':assignment:reject'),
           '旧处理权限拆分拒绝派单'
    FROM permissions source_permission WHERE source_permission.name LIKE 'work-order:%:respond'
    UNION ALL
    SELECT source_permission.name,
           REPLACE(source_permission.name, ':verify', ':verification:reject'),
           '旧验收权限拆分验收驳回'
    FROM permissions source_permission WHERE source_permission.name LIKE 'work-order:%:verify'
    UNION ALL
    SELECT source_permission.name,
           REPLACE(source_permission.name, ':hold', ':resume'),
           '旧挂起恢复复合能力拆分恢复处理'
    FROM permissions source_permission WHERE source_permission.name LIKE 'work-order:%:hold'
) split_mapping
JOIN permissions source_permission ON source_permission.name = split_mapping.source_name
JOIN permissions target_permission ON target_permission.name = split_mapping.target_name;

--rollback DELETE FROM authorization_v2_permission_code_map_20260814;

--changeset e-report:2026-08-14-034-authorization-v2-map-assertion dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM role_permissions role_rule LEFT JOIN authorization_v2_permission_code_map_20260814 code_map ON code_map.source_permission_id = role_rule.permission_id WHERE code_map.source_permission_id IS NULL
--comment Every currently assigned role permission must have an explicit V2 mapping; silent permission loss is forbidden.
SELECT 1;

--rollback SELECT 1;

--changeset e-report:2026-08-14-034-authorization-v2-permission-seed-h2 dbms:h2
--comment H2 equivalent of the atomic permission seed.
INSERT INTO permissions (id, name, display_name, description)
SELECT RANDOM_UUID(),
       CONCAT('work-order:', work_type.slug, ':', work_action.suffix),
       CONCAT(work_type.label, work_action.label),
       CONCAT(work_action.description, work_type.label)
FROM (
    SELECT 'network-fault' slug, '网络故障工单' label
    UNION ALL SELECT 'box-door-open', '箱门未关工单'
    UNION ALL SELECT 'damaged-box-replacement', '破损箱体更换工单'
) work_type
CROSS JOIN (
    SELECT 'response:submit' suffix, '提交回单' label, '提交本人当前负责的' description
    UNION ALL SELECT 'attachment:read', '附件读取', '预览或下载授权范围内的'
    UNION ALL SELECT 'assignment:accept', '接受派单', '接受指派给本人的'
    UNION ALL SELECT 'assignment:reject', '拒绝派单', '拒绝指派给本人的'
    UNION ALL SELECT 'assignment:create', '派单', '将未分配工单派给合格处理人员：'
    UNION ALL SELECT 'assignment:reassign', '调单', '调整目标处理人员：'
    UNION ALL SELECT 'verification:approve', '验收通过', '验收通过并闭环：'
    UNION ALL SELECT 'verification:reject', '验收驳回', '驳回处理回单并返工：'
    UNION ALL SELECT 'resume', '恢复处理', '恢复本人当前挂起的'
) work_action
WHERE NOT EXISTS (
    SELECT 1 FROM permissions existing
    WHERE existing.name = CONCAT('work-order:', work_type.slug, ':', work_action.suffix)
);

INSERT INTO permissions (id, name, display_name, description)
SELECT RANDOM_UUID(), seed.name, seed.display_name, seed.description
FROM (
    SELECT 'user:manage:details:update' name, '用户资料修改' display_name, '更新授权组织范围内用户的基本信息' description
    UNION ALL SELECT 'user:manage:status:activate', '用户启用', '将用户恢复为可登录状态'
    UNION ALL SELECT 'user:manage:status:deactivate', '用户停用', '停用用户并阻止继续登录'
    UNION ALL SELECT 'user:manage:status:lock', '用户锁定', '因安全原因锁定用户账号'
    UNION ALL SELECT 'user:manage:status:unlock', '用户解锁', '解除用户账号锁定'
    UNION ALL SELECT 'user:manage:password:reset', '用户密码重置', '重置授权组织范围内用户的登录密码'
    UNION ALL SELECT 'user:authorization:view', '用户授权查看', '查看可管理的用户授权及最终权限摘要'
    UNION ALL SELECT 'user:authorization:grant', '用户授权授予', '新增不超过自身委派边界的权限授权'
    UNION ALL SELECT 'user:authorization:adjust', '用户授权调整', '调整自身可管理授权的范围、关系或有效期'
    UNION ALL SELECT 'user:authorization:revoke', '用户授权撤销', '撤销自身可管理的授权来源'
    UNION ALL SELECT 'user:import:template:download', '用户导入模板下载', '生成并下载按委派边界裁剪的用户导入模板'
    UNION ALL SELECT 'user:import:preview', '用户导入预览', '上传并校验用户及授权导入工作簿'
    UNION ALL SELECT 'user:import:execute', '用户导入执行', '执行已预览的用户及授权批量变更'
    UNION ALL SELECT 'user:import:cancel', '用户导入取消', '取消本人创建且尚未结束的导入任务'
    UNION ALL SELECT 'user:export:create', '用户导出创建', '创建授权组织范围内的用户数据导出任务'
    UNION ALL SELECT 'user:export:download', '用户导出下载', '下载本人创建的用户数据导出结果'
    UNION ALL SELECT 'notification:channel:update', '通知渠道开关修改', '单独开启或关闭授权组织范围内的通知渠道'
    UNION ALL SELECT 'notification:schedule:update', '通知计划修改', '修改授权组织范围内的未闭环工单日报计划'
    UNION ALL SELECT 'authorization-profile:view', '岗位权限模板查看', '查看可授予的岗位权限模板及版本'
    UNION ALL SELECT 'authorization-profile:create', '岗位权限模板创建', '创建租户自定义岗位权限模板草稿'
    UNION ALL SELECT 'authorization-profile:draft:update', '岗位权限模板草稿修改', '修改未发布的岗位权限模板草稿'
    UNION ALL SELECT 'authorization-profile:copy', '岗位权限模板复制', '从可见模板复制新的自定义模板草稿'
    UNION ALL SELECT 'authorization-profile:publish', '岗位权限模板发布', '发布不可变的岗位权限模板版本'
    UNION ALL SELECT 'authorization-profile:retire', '岗位权限模板退役', '停止模板用于新增授权'
    UNION ALL SELECT 'authorization-profile:assignment:upgrade', '岗位授权版本升级', '预览并批量升级既有岗位授权版本'
    UNION ALL SELECT 'organization:manage:move', '组织移动', '调整授权范围内组织节点的父级关系'
    UNION ALL SELECT 'organization:manage:status:update', '组织状态修改', '启用或停用授权范围内的组织节点'
    UNION ALL SELECT 'app-update:storage:view', '安装包存储查看', '查看安装包对象存储状态'
    UNION ALL SELECT 'app-update:storage:sync', '安装包存储同步', '将发布记录同步到对象存储'
    UNION ALL SELECT 'app-update:storage:delete', '安装包对象删除', '删除对象存储中的安装包对象'
    UNION ALL SELECT 'tenant:manage:status:activate', '市公司租户启用', '启用已停用的市公司租户'
    UNION ALL SELECT 'tenant:manage:status:deactivate', '市公司租户停用', '停用市公司租户及其登录能力'
    UNION ALL SELECT 'observability:redis:view', 'Redis 运行诊断查看', '查看 Redis 连接、缓存和轻量观测队列状态'
) seed
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = seed.name);

--rollback DELETE FROM permissions WHERE name IN ('user:manage:details:update','user:manage:status:activate','user:manage:status:deactivate','user:manage:status:lock','user:manage:status:unlock','user:manage:password:reset','user:authorization:view','user:authorization:grant','user:authorization:adjust','user:authorization:revoke','user:import:template:download','user:import:preview','user:import:execute','user:import:cancel','user:export:create','user:export:download','notification:channel:update','notification:schedule:update','authorization-profile:view','authorization-profile:create','authorization-profile:draft:update','authorization-profile:copy','authorization-profile:publish','authorization-profile:retire','authorization-profile:assignment:upgrade','organization:manage:move','organization:manage:status:update','app-update:storage:view','app-update:storage:sync','app-update:storage:delete','tenant:manage:status:activate','tenant:manage:status:deactivate','observability:redis:view');
