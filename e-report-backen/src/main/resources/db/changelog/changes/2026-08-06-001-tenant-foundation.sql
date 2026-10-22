--liquibase formatted sql

--changeset e-report:2026-08-06-001-tenant-foundation dbms:mysql,h2
CREATE TABLE tenant_groups (
    id BIGINT AUTO_INCREMENT NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_tenant_groups PRIMARY KEY (id),
    CONSTRAINT uk_tenant_groups_code UNIQUE (code)
);

CREATE TABLE tenants (
    id BIGINT AUTO_INCREMENT NOT NULL,
    tenant_group_id BIGINT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_tenants PRIMARY KEY (id),
    CONSTRAINT uk_tenants_code UNIQUE (code),
    CONSTRAINT fk_tenants_group FOREIGN KEY (tenant_group_id) REFERENCES tenant_groups (id)
);
CREATE INDEX idx_tenants_group ON tenants (tenant_group_id);

CREATE TABLE org_units (
    id BIGINT AUTO_INCREMENT NOT NULL,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    unit_type VARCHAR(30) NOT NULL,
    path VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_org_units PRIMARY KEY (id),
    CONSTRAINT uk_org_units_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_org_units_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_org_units_parent FOREIGN KEY (parent_id) REFERENCES org_units (id)
);
CREATE INDEX idx_org_units_tenant_path ON org_units (tenant_id, path);

INSERT INTO tenant_groups (id, code, name, status, created_at, updated_at)
VALUES (1, '${defaultTenantGroupCode}', '${defaultTenantGroupName}', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO tenants (id, tenant_group_id, code, name, status, created_at, updated_at)
VALUES (1, 1, '${defaultTenantCode}', '${defaultTenantName}', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO org_units (id, tenant_id, parent_id, code, name, unit_type, path, status, created_at, updated_at)
VALUES (1, 1, NULL, 'ROOT', '${defaultTenantRootOrgName}', 'CITY', '/1/', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

ALTER TABLE users ADD COLUMN tenant_id BIGINT;
ALTER TABLE users ADD COLUMN org_unit_id BIGINT;
ALTER TABLE users ADD COLUMN authorization_version BIGINT DEFAULT 1;
UPDATE users SET tenant_id = 1, org_unit_id = 1, authorization_version = 1 WHERE tenant_id IS NULL;
ALTER TABLE users ADD CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE users ADD CONSTRAINT fk_users_org_unit FOREIGN KEY (org_unit_id) REFERENCES org_units (id);
CREATE INDEX idx_users_tenant_status ON users (tenant_id, status);

ALTER TABLE roles ADD COLUMN tenant_id BIGINT;
ALTER TABLE roles ADD COLUMN built_in BOOLEAN DEFAULT FALSE;
UPDATE roles SET tenant_id = 1, built_in = TRUE WHERE tenant_id IS NULL;
ALTER TABLE roles ADD CONSTRAINT fk_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);

CREATE TABLE membership_role_grants (
    id BIGINT AUTO_INCREMENT NOT NULL,
    tenant_id BIGINT NOT NULL,
    membership_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    data_scope VARCHAR(30) NOT NULL,
    scope_org_id BIGINT,
    granted_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6),
    CONSTRAINT pk_membership_role_grants PRIMARY KEY (id),
    CONSTRAINT uk_membership_role_grants UNIQUE (tenant_id, membership_id, role_id, data_scope, scope_org_id),
    CONSTRAINT fk_role_grants_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_role_grants_membership FOREIGN KEY (membership_id) REFERENCES users (id),
    CONSTRAINT fk_role_grants_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_grants_scope_org FOREIGN KEY (scope_org_id) REFERENCES org_units (id)
);
CREATE INDEX idx_role_grants_member ON membership_role_grants (tenant_id, membership_id);

INSERT INTO membership_role_grants (tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at)
SELECT 1, ur.user_id, ur.role_id,
       CASE WHEN r.name = 'ROLE_ADMIN' THEN 'TENANT_ALL'
            WHEN r.name = 'ROLE_FRONTLINE_REPORTER' OR r.name = 'ROLE_USER' THEN 'SELF'
            ELSE 'ORG_TREE' END,
       CASE WHEN r.name = 'ROLE_ADMIN' OR r.name = 'ROLE_FRONTLINE_REPORTER' OR r.name = 'ROLE_USER' THEN NULL ELSE 1 END,
       CURRENT_TIMESTAMP
FROM user_roles ur JOIN roles r ON r.id = ur.role_id;

ALTER TABLE fault_reports ADD COLUMN tenant_id BIGINT;
ALTER TABLE fault_reports ADD COLUMN reporting_org_id BIGINT;
ALTER TABLE fault_reports ADD COLUMN responsible_org_id BIGINT;
UPDATE fault_reports SET tenant_id = 1, reporting_org_id = 1, responsible_org_id = 1 WHERE tenant_id IS NULL;
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_reporting_org FOREIGN KEY (reporting_org_id) REFERENCES org_units (id);
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_responsible_org FOREIGN KEY (responsible_org_id) REFERENCES org_units (id);

ALTER TABLE attachments ADD COLUMN tenant_id BIGINT;
UPDATE attachments a SET tenant_id = (SELECT f.tenant_id FROM fault_reports f WHERE f.id = a.fault_report_id) WHERE tenant_id IS NULL;
ALTER TABLE attachments ADD CONSTRAINT fk_attachments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
CREATE INDEX idx_attachments_tenant_work_order ON attachments (tenant_id, fault_report_id);

ALTER TABLE work_order_material_usages ADD COLUMN tenant_id BIGINT;
UPDATE work_order_material_usages m SET tenant_id = (SELECT f.tenant_id FROM fault_reports f WHERE f.id = m.fault_report_id) WHERE tenant_id IS NULL;
ALTER TABLE work_order_material_usages ADD CONSTRAINT fk_material_usage_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
CREATE INDEX idx_material_usage_tenant_work_order ON work_order_material_usages (tenant_id, fault_report_id);

ALTER TABLE file_transfer_upload_sessions ADD COLUMN tenant_id BIGINT;
ALTER TABLE file_transfer_upload_sessions ADD COLUMN created_by_membership_id BIGINT;
UPDATE file_transfer_upload_sessions s SET tenant_id = 1,
    created_by_membership_id = (SELECT MIN(u.id) FROM users u WHERE u.tenant_id = 1 AND u.username = s.created_by_username)
WHERE tenant_id IS NULL;
ALTER TABLE file_transfer_upload_sessions ADD CONSTRAINT fk_file_transfer_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE file_transfer_upload_sessions ADD CONSTRAINT fk_file_transfer_membership FOREIGN KEY (created_by_membership_id) REFERENCES users (id);
CREATE INDEX idx_file_transfer_tenant_user_status ON file_transfer_upload_sessions (tenant_id, created_by_membership_id, status);

--rollback ALTER TABLE file_transfer_upload_sessions DROP COLUMN created_by_membership_id;
--rollback ALTER TABLE file_transfer_upload_sessions DROP COLUMN tenant_id;
--rollback ALTER TABLE work_order_material_usages DROP COLUMN tenant_id;
--rollback ALTER TABLE attachments DROP COLUMN tenant_id;
--rollback ALTER TABLE fault_reports DROP COLUMN responsible_org_id;
--rollback ALTER TABLE fault_reports DROP COLUMN reporting_org_id;
--rollback ALTER TABLE fault_reports DROP COLUMN tenant_id;
--rollback DROP TABLE membership_role_grants;
--rollback ALTER TABLE roles DROP COLUMN built_in;
--rollback ALTER TABLE roles DROP COLUMN tenant_id;
--rollback ALTER TABLE users DROP COLUMN authorization_version;
--rollback ALTER TABLE users DROP COLUMN org_unit_id;
--rollback ALTER TABLE users DROP COLUMN tenant_id;
--rollback DROP TABLE org_units;
--rollback DROP TABLE tenants;
--rollback DROP TABLE tenant_groups;

--changeset e-report:2026-08-06-001-tenant-constraints-mysql dbms:mysql
ALTER TABLE users MODIFY COLUMN tenant_id BIGINT NOT NULL;
ALTER TABLE users MODIFY COLUMN authorization_version BIGINT NOT NULL;
ALTER TABLE users DROP INDEX UKr43af9ap4edm43mmtq01oddj6;
ALTER TABLE users ADD CONSTRAINT uk_users_tenant_username UNIQUE (tenant_id, username);
ALTER TABLE roles MODIFY COLUMN tenant_id BIGINT NOT NULL;
ALTER TABLE roles MODIFY COLUMN built_in BOOLEAN NOT NULL;
ALTER TABLE roles DROP INDEX UKofx66keruapi6vyqpv6f2or37;
ALTER TABLE roles ADD CONSTRAINT uk_roles_tenant_name UNIQUE (tenant_id, name);
ALTER TABLE fault_reports MODIFY COLUMN tenant_id BIGINT NOT NULL;
ALTER TABLE attachments MODIFY COLUMN tenant_id BIGINT NOT NULL;
ALTER TABLE work_order_material_usages MODIFY COLUMN tenant_id BIGINT NOT NULL;
ALTER TABLE file_transfer_upload_sessions MODIFY COLUMN tenant_id BIGINT NOT NULL;

--rollback ALTER TABLE roles DROP INDEX uk_roles_tenant_name;
--rollback ALTER TABLE roles ADD CONSTRAINT UKofx66keruapi6vyqpv6f2or37 UNIQUE (name);
--rollback ALTER TABLE users DROP INDEX uk_users_tenant_username;
--rollback ALTER TABLE users ADD CONSTRAINT UKr43af9ap4edm43mmtq01oddj6 UNIQUE (username);

--changeset e-report:2026-08-06-001-tenant-constraints-h2 dbms:h2
ALTER TABLE users ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE users ALTER COLUMN authorization_version SET NOT NULL;
ALTER TABLE users DROP CONSTRAINT UKr43af9ap4edm43mmtq01oddj6;
ALTER TABLE users ADD CONSTRAINT uk_users_tenant_username UNIQUE (tenant_id, username);
ALTER TABLE roles ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE roles ALTER COLUMN built_in SET NOT NULL;
ALTER TABLE roles DROP CONSTRAINT UKofx66keruapi6vyqpv6f2or37;
ALTER TABLE roles ADD CONSTRAINT uk_roles_tenant_name UNIQUE (tenant_id, name);
ALTER TABLE fault_reports ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE attachments ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE work_order_material_usages ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE file_transfer_upload_sessions ALTER COLUMN tenant_id SET NOT NULL;

--rollback ALTER TABLE roles DROP CONSTRAINT uk_roles_tenant_name;
--rollback ALTER TABLE roles ADD CONSTRAINT UKofx66keruapi6vyqpv6f2or37 UNIQUE (name);
--rollback ALTER TABLE users DROP CONSTRAINT uk_users_tenant_username;
--rollback ALTER TABLE users ADD CONSTRAINT UKr43af9ap4edm43mmtq01oddj6 UNIQUE (username);
