--liquibase formatted sql

--changeset e-report:2026-08-06-000-current-schema-baseline dbms:mysql,h2
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_SCHEMA) = LOWER(SCHEMA()) AND LOWER(TABLE_NAME) = 'users'
CREATE TABLE app_config (
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT,
    description VARCHAR(255),
    last_updated_time DATETIME(6) NOT NULL,
    CONSTRAINT pk_app_config PRIMARY KEY (config_key)
);

CREATE TABLE app_releases (
    id BIGINT AUTO_INCREMENT NOT NULL,
    platform VARCHAR(20) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    version_code INT NOT NULL,
    version_name VARCHAR(50) NOT NULL,
    runtime_version VARCHAR(100) NOT NULL,
    min_supported_version_code INT NOT NULL,
    force_update BOOLEAN NOT NULL,
    release_notes TEXT,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    published BOOLEAN NOT NULL,
    published_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_app_releases PRIMARY KEY (id),
    CONSTRAINT uk_app_releases_version UNIQUE (platform, channel, version_code)
);
CREATE INDEX idx_app_releases_latest ON app_releases (platform, channel, published, version_code);
CREATE INDEX idx_app_releases_channel_time ON app_releases (platform, channel, published_at);

CREATE TABLE permissions (
    id BIGINT AUTO_INCREMENT NOT NULL,
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100),
    description VARCHAR(255),
    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT UKpnvtwliis6p05pn6i3ndjrqt2 UNIQUE (name)
);

CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT NOT NULL,
    name VARCHAR(50) NOT NULL,
    display_name VARCHAR(100),
    description VARCHAR(255),
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT UKofx66keruapi6vyqpv6f2or37 UNIQUE (name)
);

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT NOT NULL,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(100),
    status ENUM('ACTIVE','DELETED','DISABLED','INACTIVE','LOCKED') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT UKr43af9ap4edm43mmtq01oddj6 UNIQUE (username)
);

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE TABLE fault_reports (
    id BIGINT AUTO_INCREMENT NOT NULL,
    location VARCHAR(255) NOT NULL,
    gps_coordinates VARCHAR(100),
    township VARCHAR(100),
    village_or_street VARCHAR(100),
    reason TEXT NOT NULL,
    phone VARCHAR(20) NOT NULL,
    contact_person VARCHAR(100) NOT NULL,
    status VARCHAR(50),
    report_type ENUM('DAMAGED_BOX_REPLACEMENT','NORMAL_FAULT','UNCLOSED_BOX'),
    box_quantity INT,
    replacement_required BOOLEAN,
    replacement_completed BOOLEAN,
    replacement_result TEXT,
    report_time DATETIME(6) NOT NULL,
    last_updated_time DATETIME(6) NOT NULL,
    resolution_details TEXT,
    resolution_method VARCHAR(255),
    resolution_time DATETIME(6),
    closure_reason TEXT,
    closure_time DATETIME(6),
    reported_by_user_id BIGINT,
    resolved_by_user_id BIGINT,
    closed_by_user_id BIGINT,
    CONSTRAINT pk_fault_reports PRIMARY KEY (id),
    CONSTRAINT fk_fault_reports_reporter FOREIGN KEY (reported_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_fault_reports_resolver FOREIGN KEY (resolved_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_fault_reports_closer FOREIGN KEY (closed_by_user_id) REFERENCES users (id)
);
CREATE INDEX idx_fault_reports_type_status ON fault_reports (report_type, status);
CREATE INDEX idx_fault_reports_type_status_time ON fault_reports (report_type, status, report_time);
CREATE INDEX idx_fault_reports_type_time ON fault_reports (report_type, report_time);
CREATE INDEX idx_fault_reports_reported_by_time ON fault_reports (reported_by_user_id, report_time);
CREATE INDEX idx_fault_reports_status_time ON fault_reports (status, report_time);
CREATE INDEX idx_fault_reports_report_time ON fault_reports (report_time);

CREATE TABLE attachments (
    id BIGINT AUTO_INCREMENT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    attachment_stage ENUM('REPORT','RESPONSE'),
    fault_report_id BIGINT NOT NULL,
    CONSTRAINT pk_attachments PRIMARY KEY (id),
    CONSTRAINT fk_attachments_work_order FOREIGN KEY (fault_report_id) REFERENCES fault_reports (id)
);
CREATE INDEX idx_attachments_fault_report_id ON attachments (fault_report_id);
CREATE INDEX idx_attachments_fault_stored_name ON attachments (fault_report_id, stored_file_name);

CREATE TABLE work_order_material_usages (
    id BIGINT AUTO_INCREMENT NOT NULL,
    category ENUM('JOINT_BOX','OPTICAL_CABLE','SPLITTER') NOT NULL,
    specification VARCHAR(50) NOT NULL,
    quantity INT NOT NULL,
    fault_report_id BIGINT NOT NULL,
    CONSTRAINT pk_work_order_material_usages PRIMARY KEY (id),
    CONSTRAINT fk_material_usage_work_order FOREIGN KEY (fault_report_id) REFERENCES fault_reports (id)
);
CREATE INDEX idx_material_usage_fault_report_id ON work_order_material_usages (fault_report_id);
CREATE INDEX idx_material_usage_category_spec ON work_order_material_usages (category, specification);

CREATE TABLE file_transfer_upload_sessions (
    id VARCHAR(36) NOT NULL,
    purpose ENUM('APP_RELEASE','WORK_ORDER_ATTACHMENT') NOT NULL,
    status ENUM('ABORTED','CLAIMED','COMPLETED','INITIATED') NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    total_size BIGINT NOT NULL,
    expected_sha256 VARCHAR(64),
    actual_sha256 VARCHAR(64),
    chunk_size BIGINT NOT NULL,
    received_bytes BIGINT NOT NULL,
    temp_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255),
    created_by_username VARCHAR(100) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_file_transfer_upload_sessions PRIMARY KEY (id)
);
CREATE INDEX idx_file_transfer_user_status ON file_transfer_upload_sessions (created_by_username, status);
CREATE INDEX idx_file_transfer_expires ON file_transfer_upload_sessions (expires_at);

--rollback DROP TABLE file_transfer_upload_sessions;
--rollback DROP TABLE work_order_material_usages;
--rollback DROP TABLE attachments;
--rollback DROP TABLE fault_reports;
--rollback DROP TABLE user_roles;
--rollback DROP TABLE role_permissions;
--rollback DROP TABLE users;
--rollback DROP TABLE roles;
--rollback DROP TABLE permissions;
--rollback DROP TABLE app_releases;
--rollback DROP TABLE app_config;

--changeset e-report:2026-08-06-000-h2-lob-compatibility dbms:h2
ALTER TABLE app_config ALTER COLUMN config_value CLOB;
ALTER TABLE fault_reports ALTER COLUMN reason CLOB;
ALTER TABLE fault_reports ALTER COLUMN replacement_result CLOB;
ALTER TABLE fault_reports ALTER COLUMN resolution_details CLOB;
ALTER TABLE fault_reports ALTER COLUMN closure_reason CLOB;

--rollback ALTER TABLE fault_reports ALTER COLUMN closure_reason VARCHAR;
--rollback ALTER TABLE fault_reports ALTER COLUMN resolution_details VARCHAR;
--rollback ALTER TABLE fault_reports ALTER COLUMN replacement_result VARCHAR;
--rollback ALTER TABLE fault_reports ALTER COLUMN reason VARCHAR;
--rollback ALTER TABLE app_config ALTER COLUMN config_value VARCHAR;
