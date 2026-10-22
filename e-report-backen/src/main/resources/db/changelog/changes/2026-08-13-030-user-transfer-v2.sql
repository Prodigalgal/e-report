--liquibase formatted sql

--changeset e-report:2026-08-13-030-user-transfer-v2-mysql dbms:mysql
--comment Replace role-name bundles with explicit grant rules and add resumable user-transfer jobs.
CREATE TABLE role_bundle_grant_rules (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    role_bundle_id BINARY(16) NOT NULL,
    role_id BINARY(16) NOT NULL,
    scope_strategy VARCHAR(30) NOT NULL,
    default_duration_days INT,
    sort_order INT NOT NULL DEFAULT 100,
    CONSTRAINT pk_role_bundle_grant_rules PRIMARY KEY (id),
    CONSTRAINT uk_role_bundle_grant_rules_bundle_role UNIQUE (role_bundle_id, role_id),
    CONSTRAINT fk_role_bundle_grant_rules_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_role_bundle_grant_rules_bundle FOREIGN KEY (role_bundle_id) REFERENCES role_bundles (id),
    CONSTRAINT fk_role_bundle_grant_rules_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT ck_role_bundle_grant_rules_duration CHECK (default_duration_days IS NULL OR default_duration_days > 0)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_role_bundle_grant_rules_bundle ON role_bundle_grant_rules (tenant_id, role_bundle_id, sort_order);
CREATE INDEX idx_role_bundle_grant_rules_role ON role_bundle_grant_rules (role_id);

INSERT INTO role_bundle_grant_rules (
    id, tenant_id, role_bundle_id, role_id, scope_strategy, default_duration_days, sort_order
)
SELECT bundle_role.id,
       bundle_role.tenant_id,
       bundle_role.role_bundle_id,
       bundle_role.role_id,
       CASE
           WHEN bundle.system_key IN ('TENANT_ADMINISTRATOR', 'PLATFORM_ADMINISTRATOR') THEN 'TENANT_ALL'
           WHEN role.name = 'ROLE_USER' OR RIGHT(role.name, 9) = '_REPORTER' THEN 'ACCOUNT_ORG'
           ELSE 'ACCOUNT_ORG_TREE'
       END,
       NULL,
       100
FROM role_bundle_roles bundle_role
JOIN role_bundles bundle ON bundle.id = bundle_role.role_bundle_id
JOIN roles role ON role.id = bundle_role.role_id;

DROP TABLE role_bundle_roles;

UPDATE membership_role_grants SET data_scope = 'ORG' WHERE data_scope = 'CUSTOM_ORGS';

CREATE TABLE user_transfer_jobs (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    created_by_membership_id BINARY(16) NOT NULL,
    created_by_username VARCHAR(100) NOT NULL,
    job_type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    input_file_id BINARY(16),
    result_file_id BINARY(16),
    error_file_id BINARY(16),
    input_sha256 VARCHAR(64),
    template_version VARCHAR(30),
    catalog_digest VARCHAR(64),
    user_snapshot_digest VARCHAR(64),
    preview_snapshot_digest VARCHAR(64),
    preview_expires_at DATETIME(6),
    idempotency_key VARCHAR(100),
    request_hash VARCHAR(64),
    total_rows INT NOT NULL DEFAULT 0,
    valid_rows INT NOT NULL DEFAULT 0,
    invalid_rows INT NOT NULL DEFAULT 0,
    processed_rows INT NOT NULL DEFAULT 0,
    succeeded_rows INT NOT NULL DEFAULT 0,
    failed_rows INT NOT NULL DEFAULT 0,
    summary_json TEXT,
    failure_message VARCHAR(1000),
    started_at DATETIME(6),
    completed_at DATETIME(6),
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_transfer_jobs PRIMARY KEY (id),
    CONSTRAINT uk_user_transfer_jobs_idempotency UNIQUE (tenant_id, created_by_membership_id, idempotency_key),
    CONSTRAINT fk_user_transfer_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_user_transfer_jobs_creator FOREIGN KEY (created_by_membership_id) REFERENCES users (id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_user_transfer_jobs_owner ON user_transfer_jobs (tenant_id, created_by_membership_id, created_at);
CREATE INDEX idx_user_transfer_jobs_status ON user_transfer_jobs (status, updated_at);
CREATE INDEX idx_user_transfer_jobs_expires ON user_transfer_jobs (expires_at);

CREATE TABLE user_transfer_job_rows (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    job_id BINARY(16) NOT NULL,
    sheet_name VARCHAR(40) NOT NULL,
    source_row_number INT NOT NULL,
    subject_key_hash VARCHAR(64),
    command_type VARCHAR(30),
    validation_status VARCHAR(20) NOT NULL,
    execution_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    field_diff_json TEXT,
    grant_diff_json TEXT,
    validation_errors_json TEXT,
    execution_message VARCHAR(1000),
    before_version BIGINT,
    after_version BIGINT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_user_transfer_job_rows PRIMARY KEY (id),
    CONSTRAINT uk_user_transfer_job_rows_position UNIQUE (job_id, sheet_name, source_row_number),
    CONSTRAINT fk_user_transfer_job_rows_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_user_transfer_job_rows_job FOREIGN KEY (job_id) REFERENCES user_transfer_jobs (id) ON DELETE CASCADE
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_user_transfer_job_rows_page ON user_transfer_job_rows (tenant_id, job_id, validation_status, source_row_number);

--rollback DROP TABLE user_transfer_job_rows;
--rollback DROP TABLE user_transfer_jobs;
--rollback CREATE TABLE role_bundle_roles (id BINARY(16) NOT NULL, tenant_id BINARY(16) NOT NULL, role_bundle_id BINARY(16) NOT NULL, role_id BINARY(16) NOT NULL, CONSTRAINT pk_role_bundle_roles PRIMARY KEY (id), CONSTRAINT uk_role_bundle_roles_bundle_role UNIQUE (role_bundle_id, role_id), CONSTRAINT fk_role_bundle_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id), CONSTRAINT fk_role_bundle_roles_bundle FOREIGN KEY (role_bundle_id) REFERENCES role_bundles (id), CONSTRAINT fk_role_bundle_roles_role FOREIGN KEY (role_id) REFERENCES roles (id));
--rollback INSERT INTO role_bundle_roles (id, tenant_id, role_bundle_id, role_id) SELECT id, tenant_id, role_bundle_id, role_id FROM role_bundle_grant_rules;
--rollback DROP TABLE role_bundle_grant_rules;

--changeset e-report:2026-08-13-030-user-transfer-v2-h2 dbms:h2
--comment H2 equivalent for role-bundle grant rules and resumable user-transfer jobs.
CREATE TABLE role_bundle_grant_rules (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    role_bundle_id UUID NOT NULL,
    role_id UUID NOT NULL,
    scope_strategy VARCHAR(30) NOT NULL,
    default_duration_days INT,
    sort_order INT DEFAULT 100 NOT NULL,
    CONSTRAINT pk_role_bundle_grant_rules PRIMARY KEY (id),
    CONSTRAINT uk_role_bundle_grant_rules_bundle_role UNIQUE (role_bundle_id, role_id),
    CONSTRAINT fk_role_bundle_grant_rules_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_role_bundle_grant_rules_bundle FOREIGN KEY (role_bundle_id) REFERENCES role_bundles (id),
    CONSTRAINT fk_role_bundle_grant_rules_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT ck_role_bundle_grant_rules_duration CHECK (default_duration_days IS NULL OR default_duration_days > 0)
);
CREATE INDEX idx_role_bundle_grant_rules_bundle ON role_bundle_grant_rules (tenant_id, role_bundle_id, sort_order);
CREATE INDEX idx_role_bundle_grant_rules_role ON role_bundle_grant_rules (role_id);

INSERT INTO role_bundle_grant_rules (
    id, tenant_id, role_bundle_id, role_id, scope_strategy, default_duration_days, sort_order
)
SELECT bundle_role.id,
       bundle_role.tenant_id,
       bundle_role.role_bundle_id,
       bundle_role.role_id,
       CASE
           WHEN bundle.system_key IN ('TENANT_ADMINISTRATOR', 'PLATFORM_ADMINISTRATOR') THEN 'TENANT_ALL'
           WHEN role.name = 'ROLE_USER' OR role.name LIKE '%\_REPORTER' ESCAPE '\' THEN 'ACCOUNT_ORG'
           ELSE 'ACCOUNT_ORG_TREE'
       END,
       NULL,
       100
FROM role_bundle_roles bundle_role
JOIN role_bundles bundle ON bundle.id = bundle_role.role_bundle_id
JOIN roles role ON role.id = bundle_role.role_id;

DROP TABLE role_bundle_roles;
UPDATE membership_role_grants SET data_scope = 'ORG' WHERE data_scope = 'CUSTOM_ORGS';

CREATE TABLE user_transfer_jobs (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    created_by_membership_id UUID NOT NULL,
    created_by_username VARCHAR(100) NOT NULL,
    job_type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    input_file_id UUID,
    result_file_id UUID,
    error_file_id UUID,
    input_sha256 VARCHAR(64),
    template_version VARCHAR(30),
    catalog_digest VARCHAR(64),
    user_snapshot_digest VARCHAR(64),
    preview_snapshot_digest VARCHAR(64),
    preview_expires_at TIMESTAMP(6),
    idempotency_key VARCHAR(100),
    request_hash VARCHAR(64),
    total_rows INT DEFAULT 0 NOT NULL,
    valid_rows INT DEFAULT 0 NOT NULL,
    invalid_rows INT DEFAULT 0 NOT NULL,
    processed_rows INT DEFAULT 0 NOT NULL,
    succeeded_rows INT DEFAULT 0 NOT NULL,
    failed_rows INT DEFAULT 0 NOT NULL,
    summary_json TEXT,
    failure_message VARCHAR(1000),
    started_at TIMESTAMP(6),
    completed_at TIMESTAMP(6),
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_user_transfer_jobs PRIMARY KEY (id),
    CONSTRAINT uk_user_transfer_jobs_idempotency UNIQUE (tenant_id, created_by_membership_id, idempotency_key),
    CONSTRAINT fk_user_transfer_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_user_transfer_jobs_creator FOREIGN KEY (created_by_membership_id) REFERENCES users (id)
);
CREATE INDEX idx_user_transfer_jobs_owner ON user_transfer_jobs (tenant_id, created_by_membership_id, created_at);
CREATE INDEX idx_user_transfer_jobs_status ON user_transfer_jobs (status, updated_at);
CREATE INDEX idx_user_transfer_jobs_expires ON user_transfer_jobs (expires_at);

CREATE TABLE user_transfer_job_rows (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    job_id UUID NOT NULL,
    sheet_name VARCHAR(40) NOT NULL,
    source_row_number INT NOT NULL,
    subject_key_hash VARCHAR(64),
    command_type VARCHAR(30),
    validation_status VARCHAR(20) NOT NULL,
    execution_status VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    field_diff_json TEXT,
    grant_diff_json TEXT,
    validation_errors_json TEXT,
    execution_message VARCHAR(1000),
    before_version BIGINT,
    after_version BIGINT,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_user_transfer_job_rows PRIMARY KEY (id),
    CONSTRAINT uk_user_transfer_job_rows_position UNIQUE (job_id, sheet_name, source_row_number),
    CONSTRAINT fk_user_transfer_job_rows_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_user_transfer_job_rows_job FOREIGN KEY (job_id) REFERENCES user_transfer_jobs (id) ON DELETE CASCADE
);
CREATE INDEX idx_user_transfer_job_rows_page ON user_transfer_job_rows (tenant_id, job_id, validation_status, source_row_number);

--rollback DROP TABLE user_transfer_job_rows;
--rollback DROP TABLE user_transfer_jobs;
--rollback CREATE TABLE role_bundle_roles (id UUID NOT NULL, tenant_id UUID NOT NULL, role_bundle_id UUID NOT NULL, role_id UUID NOT NULL, CONSTRAINT pk_role_bundle_roles PRIMARY KEY (id), CONSTRAINT uk_role_bundle_roles_bundle_role UNIQUE (role_bundle_id, role_id), CONSTRAINT fk_role_bundle_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id), CONSTRAINT fk_role_bundle_roles_bundle FOREIGN KEY (role_bundle_id) REFERENCES role_bundles (id), CONSTRAINT fk_role_bundle_roles_role FOREIGN KEY (role_id) REFERENCES roles (id));
--rollback INSERT INTO role_bundle_roles (id, tenant_id, role_bundle_id, role_id) SELECT id, tenant_id, role_bundle_id, role_id FROM role_bundle_grant_rules;
--rollback DROP TABLE role_bundle_grant_rules;
