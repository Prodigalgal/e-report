--liquibase formatted sql

--changeset e-report:2026-08-23-060-reject-legacy-department-org-nodes dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM org_units WHERE unit_type IN ('DEPARTMENT','MAINTENANCE_UNIT','TEAM')
--comment V5 separates organization isolation from department collaboration. Legacy pseudo-organization nodes require an explicit data migration instead of silent reinterpretation.
SELECT 1;
--rollback SELECT 1;

--changeset e-report:2026-08-23-060-department-tables-mysql dbms:mysql
CREATE TABLE departments (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    org_unit_id BINARY(16) NOT NULL,
    parent_id BINARY(16) NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    path VARCHAR(700) NOT NULL,
    status VARCHAR(20) NOT NULL,
    merged_into_department_id BINARY(16) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_departments_tenant_org_code UNIQUE (tenant_id, org_unit_id, code),
    CONSTRAINT uk_departments_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_departments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_departments_org FOREIGN KEY (org_unit_id) REFERENCES org_units(id),
    CONSTRAINT fk_departments_parent FOREIGN KEY (parent_id) REFERENCES departments(id),
    CONSTRAINT fk_departments_merged_into FOREIGN KEY (merged_into_department_id) REFERENCES departments(id),
    CONSTRAINT ck_departments_status CHECK (status IN ('ACTIVE','SUSPENDED','MERGED'))
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_departments_tenant_org_path ON departments (tenant_id, org_unit_id, path);
CREATE INDEX idx_departments_parent_status ON departments (parent_id, status);

CREATE TABLE department_change_events (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    org_unit_id BINARY(16) NOT NULL,
    department_id BINARY(16) NOT NULL,
    operation VARCHAR(20) NOT NULL,
    old_parent_id BINARY(16) NULL,
    new_parent_id BINARY(16) NULL,
    target_department_id BINARY(16) NULL,
    operator_membership_id BINARY(16) NULL,
    reason VARCHAR(1000) NOT NULL,
    affected_users BIGINT NOT NULL DEFAULT 0,
    affected_work_orders BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_department_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_department_events_org FOREIGN KEY (org_unit_id) REFERENCES org_units(id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_department_events_department ON department_change_events (tenant_id, department_id, created_at);
CREATE INDEX idx_department_events_org ON department_change_events (tenant_id, org_unit_id, created_at);

ALTER TABLE users ADD COLUMN department_id BINARY(16) NULL AFTER org_unit_id;
ALTER TABLE users ADD CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments(id);
CREATE INDEX idx_users_tenant_department_status ON users (tenant_id, department_id, status);

ALTER TABLE fault_reports ADD COLUMN reporting_department_id BINARY(16) NULL AFTER responsible_org_id;
ALTER TABLE fault_reports ADD COLUMN responsible_department_id BINARY(16) NULL AFTER reporting_department_id;
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_reporting_department FOREIGN KEY (reporting_department_id) REFERENCES departments(id);
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_responsible_department FOREIGN KEY (responsible_department_id) REFERENCES departments(id);
CREATE INDEX idx_fault_reports_tenant_responsible_department_status
    ON fault_reports (tenant_id, responsible_department_id, status_code, submitted_at);

ALTER TABLE work_order_assignments ADD COLUMN responsible_department_id BINARY(16) NULL AFTER responsible_org_id;
ALTER TABLE work_order_assignments ADD CONSTRAINT fk_assignments_responsible_department
    FOREIGN KEY (responsible_department_id) REFERENCES departments(id);

CREATE TABLE work_order_department_participations (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    work_order_id BINARY(16) NOT NULL,
    department_id BINARY(16) NULL,
    department_name_snapshot VARCHAR(100) NOT NULL,
    relation_type VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    created_by_membership_id BINARY(16) NULL,
    reason VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_work_order_dept_participation_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_work_order_dept_participation_order FOREIGN KEY (work_order_id) REFERENCES fault_reports(id),
    CONSTRAINT fk_work_order_dept_participation_department FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_work_order_dept_participation_creator FOREIGN KEY (created_by_membership_id) REFERENCES users(id),
    CONSTRAINT ck_work_order_dept_relation CHECK (relation_type IN ('REPORTING','RESPONSIBLE','VERIFICATION','COPIED'))
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_work_order_dept_participation_order
    ON work_order_department_participations (tenant_id, work_order_id, relation_type, active);
CREATE INDEX idx_work_order_dept_participation_department
    ON work_order_department_participations (tenant_id, department_id, active);
--rollback DROP TABLE work_order_department_participations;
--rollback ALTER TABLE work_order_assignments DROP FOREIGN KEY fk_assignments_responsible_department;
--rollback ALTER TABLE work_order_assignments DROP COLUMN responsible_department_id;
--rollback ALTER TABLE fault_reports DROP FOREIGN KEY fk_fault_reports_responsible_department;
--rollback ALTER TABLE fault_reports DROP FOREIGN KEY fk_fault_reports_reporting_department;
--rollback ALTER TABLE fault_reports DROP COLUMN responsible_department_id;
--rollback ALTER TABLE fault_reports DROP COLUMN reporting_department_id;
--rollback ALTER TABLE users DROP FOREIGN KEY fk_users_department;
--rollback ALTER TABLE users DROP COLUMN department_id;
--rollback DROP TABLE department_change_events;
--rollback DROP TABLE departments;

--changeset e-report:2026-08-23-060-department-tables-h2 dbms:h2
CREATE TABLE departments (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    org_unit_id UUID NOT NULL,
    parent_id UUID,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    path VARCHAR(700) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','MERGED')),
    merged_into_department_id UUID,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_departments_tenant_org_code UNIQUE (tenant_id, org_unit_id, code),
    CONSTRAINT uk_departments_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_departments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_departments_org FOREIGN KEY (org_unit_id) REFERENCES org_units(id),
    CONSTRAINT fk_departments_parent FOREIGN KEY (parent_id) REFERENCES departments(id),
    CONSTRAINT fk_departments_merged_into FOREIGN KEY (merged_into_department_id) REFERENCES departments(id)
);
CREATE INDEX idx_departments_tenant_org_path ON departments (tenant_id, org_unit_id, path);
CREATE INDEX idx_departments_parent_status ON departments (parent_id, status);

CREATE TABLE department_change_events (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    org_unit_id UUID NOT NULL,
    department_id UUID NOT NULL,
    operation VARCHAR(20) NOT NULL,
    old_parent_id UUID,
    new_parent_id UUID,
    target_department_id UUID,
    operator_membership_id UUID,
    reason VARCHAR(1000) NOT NULL,
    affected_users BIGINT NOT NULL DEFAULT 0,
    affected_work_orders BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_department_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_department_events_org FOREIGN KEY (org_unit_id) REFERENCES org_units(id)
);
CREATE INDEX idx_department_events_department ON department_change_events (tenant_id, department_id, created_at);
CREATE INDEX idx_department_events_org ON department_change_events (tenant_id, org_unit_id, created_at);

ALTER TABLE users ADD COLUMN department_id UUID;
ALTER TABLE users ADD CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments(id);
CREATE INDEX idx_users_tenant_department_status ON users (tenant_id, department_id, status);

ALTER TABLE fault_reports ADD COLUMN reporting_department_id UUID;
ALTER TABLE fault_reports ADD COLUMN responsible_department_id UUID;
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_reporting_department FOREIGN KEY (reporting_department_id) REFERENCES departments(id);
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_responsible_department FOREIGN KEY (responsible_department_id) REFERENCES departments(id);
CREATE INDEX idx_fault_reports_tenant_responsible_department_status
    ON fault_reports (tenant_id, responsible_department_id, status_code, submitted_at);

ALTER TABLE work_order_assignments ADD COLUMN responsible_department_id UUID;
ALTER TABLE work_order_assignments ADD CONSTRAINT fk_assignments_responsible_department
    FOREIGN KEY (responsible_department_id) REFERENCES departments(id);

CREATE TABLE work_order_department_participations (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    work_order_id UUID NOT NULL,
    department_id UUID,
    department_name_snapshot VARCHAR(100) NOT NULL,
    relation_type VARCHAR(20) NOT NULL CHECK (relation_type IN ('REPORTING','RESPONSIBLE','VERIFICATION','COPIED')),
    active BOOLEAN NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    ended_at TIMESTAMP(6),
    created_by_membership_id UUID,
    reason VARCHAR(1000),
    CONSTRAINT fk_work_order_dept_participation_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_work_order_dept_participation_order FOREIGN KEY (work_order_id) REFERENCES fault_reports(id),
    CONSTRAINT fk_work_order_dept_participation_department FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_work_order_dept_participation_creator FOREIGN KEY (created_by_membership_id) REFERENCES users(id)
);
CREATE INDEX idx_work_order_dept_participation_order
    ON work_order_department_participations (tenant_id, work_order_id, relation_type, active);
CREATE INDEX idx_work_order_dept_participation_department
    ON work_order_department_participations (tenant_id, department_id, active);
--rollback DROP TABLE work_order_department_participations;
--rollback ALTER TABLE work_order_assignments DROP CONSTRAINT fk_assignments_responsible_department;
--rollback ALTER TABLE work_order_assignments DROP COLUMN responsible_department_id;
--rollback ALTER TABLE fault_reports DROP CONSTRAINT fk_fault_reports_responsible_department;
--rollback ALTER TABLE fault_reports DROP CONSTRAINT fk_fault_reports_reporting_department;
--rollback ALTER TABLE fault_reports DROP COLUMN responsible_department_id;
--rollback ALTER TABLE fault_reports DROP COLUMN reporting_department_id;
--rollback ALTER TABLE users DROP CONSTRAINT fk_users_department;
--rollback ALTER TABLE users DROP COLUMN department_id;
--rollback DROP TABLE department_change_events;
--rollback DROP TABLE departments;

--changeset e-report:2026-08-23-060-department-schema-assert dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:3 SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_SCHEMA) = LOWER(SCHEMA()) AND LOWER(TABLE_NAME) IN ('departments','department_change_events','work_order_department_participations')
SELECT 1;
--rollback SELECT 1;
