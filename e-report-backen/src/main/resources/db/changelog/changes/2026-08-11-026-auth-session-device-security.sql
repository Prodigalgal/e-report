--liquibase formatted sql

--changeset e-report:2026-08-11-026-user-password-security dbms:mysql,h2
--comment Add explicit password migration state without changing existing credentials.
ALTER TABLE users ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMP(6) NULL;

UPDATE users
SET password_change_required = TRUE
WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}')
  AND username IN ('xiangcheng','longwen','longhai','changtai','yunxiao','zhangpu','zhaoan','dongshan','nanjing','pinghe','huaan');

--rollback ALTER TABLE users DROP COLUMN password_changed_at;
--rollback ALTER TABLE users DROP COLUMN password_change_required;

--changeset e-report:2026-08-11-026-trusted-devices-mysql dbms:mysql
--comment Store privacy-preserving Android device proof and allow-listed risk context.
CREATE TABLE trusted_devices (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    membership_id BINARY(16) NOT NULL,
    key_id VARCHAR(64) NOT NULL,
    public_key_spki VARCHAR(1024) NOT NULL,
    installation_id_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    manufacturer VARCHAR(80) NULL,
    brand VARCHAR(80) NULL,
    model VARCHAR(120) NULL,
    device_name VARCHAR(120) NULL,
    product_name VARCHAR(120) NULL,
    hardware_name VARCHAR(120) NULL,
    board_name VARCHAR(120) NULL,
    supported_abis VARCHAR(255) NULL,
    os_release VARCHAR(50) NULL,
    sdk_int INTEGER NULL,
    security_patch VARCHAR(20) NULL,
    build_fingerprint_hash VARCHAR(64) NULL,
    screen_width INTEGER NULL,
    screen_height INTEGER NULL,
    screen_density INTEGER NULL,
    memory_mb_bucket INTEGER NULL,
    storage_mb_bucket INTEGER NULL,
    locale_tag VARCHAR(50) NULL,
    time_zone VARCHAR(80) NULL,
    app_version VARCHAR(30) NULL,
    app_build VARCHAR(20) NULL,
    installer_package VARCHAR(120) NULL,
    app_signature_sha256 VARCHAR(64) NULL,
    hardware_backed BOOLEAN NOT NULL DEFAULT FALSE,
    security_signals TEXT NULL,
    first_seen_at TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_trusted_device_member_key UNIQUE (tenant_id, membership_id, key_id),
    CONSTRAINT fk_trusted_device_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_trusted_device_member FOREIGN KEY (membership_id) REFERENCES users(id)
);

CREATE INDEX idx_trusted_device_key ON trusted_devices (key_id);
CREATE INDEX idx_trusted_device_member_status ON trusted_devices (tenant_id, membership_id, status);

--rollback DROP TABLE trusted_devices;

--changeset e-report:2026-08-11-026-trusted-devices-h2 dbms:h2
--comment H2 equivalent for privacy-preserving Android device proof.
CREATE TABLE trusted_devices (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    key_id VARCHAR(64) NOT NULL,
    public_key_spki VARCHAR(1024) NOT NULL,
    installation_id_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    manufacturer VARCHAR(80) NULL,
    brand VARCHAR(80) NULL,
    model VARCHAR(120) NULL,
    device_name VARCHAR(120) NULL,
    product_name VARCHAR(120) NULL,
    hardware_name VARCHAR(120) NULL,
    board_name VARCHAR(120) NULL,
    supported_abis VARCHAR(255) NULL,
    os_release VARCHAR(50) NULL,
    sdk_int INTEGER NULL,
    security_patch VARCHAR(20) NULL,
    build_fingerprint_hash VARCHAR(64) NULL,
    screen_width INTEGER NULL,
    screen_height INTEGER NULL,
    screen_density INTEGER NULL,
    memory_mb_bucket INTEGER NULL,
    storage_mb_bucket INTEGER NULL,
    locale_tag VARCHAR(50) NULL,
    time_zone VARCHAR(80) NULL,
    app_version VARCHAR(30) NULL,
    app_build VARCHAR(20) NULL,
    installer_package VARCHAR(120) NULL,
    app_signature_sha256 VARCHAR(64) NULL,
    hardware_backed BOOLEAN NOT NULL DEFAULT FALSE,
    security_signals TEXT NULL,
    first_seen_at TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_trusted_device_member_key UNIQUE (tenant_id, membership_id, key_id),
    CONSTRAINT fk_trusted_device_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_trusted_device_member FOREIGN KEY (membership_id) REFERENCES users(id)
);

CREATE INDEX idx_trusted_device_key ON trusted_devices (key_id);
CREATE INDEX idx_trusted_device_member_status ON trusted_devices (tenant_id, membership_id, status);

--rollback DROP TABLE trusted_devices;

--changeset e-report:2026-08-11-026-auth-sessions-mysql dbms:mysql
--comment Add server-side session facts for web cookies and device-bound Android tokens.
CREATE TABLE auth_sessions (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    membership_id BINARY(16) NOT NULL,
    trusted_device_id BINARY(16) NULL,
    client_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    web_secret_hash VARCHAR(64) NULL,
    refresh_token_hash VARCHAR(64) NULL,
    previous_refresh_token_hash VARCHAR(64) NULL,
    refresh_rotated_at TIMESTAMP(6) NULL,
    authorization_version BIGINT NOT NULL,
    password_change_required BOOLEAN NOT NULL DEFAULT FALSE,
    ip_hash VARCHAR(64) NOT NULL,
    user_agent_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    idle_expires_at TIMESTAMP(6) NOT NULL,
    absolute_expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    revoke_reason VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_session_web_secret UNIQUE (web_secret_hash),
    CONSTRAINT uk_auth_session_refresh_token UNIQUE (refresh_token_hash),
    CONSTRAINT fk_auth_session_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_auth_session_member FOREIGN KEY (membership_id) REFERENCES users(id),
    CONSTRAINT fk_auth_session_device FOREIGN KEY (trusted_device_id) REFERENCES trusted_devices(id)
);

CREATE INDEX idx_auth_session_member_status ON auth_sessions (tenant_id, membership_id, status);
CREATE INDEX idx_auth_session_expiry ON auth_sessions (status, idle_expires_at, absolute_expires_at);

--rollback DROP TABLE auth_sessions;

--changeset e-report:2026-08-11-026-auth-sessions-h2 dbms:h2
--comment H2 equivalent for server-side web and Android sessions.
CREATE TABLE auth_sessions (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    trusted_device_id UUID NULL,
    client_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    web_secret_hash VARCHAR(64) NULL,
    refresh_token_hash VARCHAR(64) NULL,
    previous_refresh_token_hash VARCHAR(64) NULL,
    refresh_rotated_at TIMESTAMP(6) NULL,
    authorization_version BIGINT NOT NULL,
    password_change_required BOOLEAN NOT NULL DEFAULT FALSE,
    ip_hash VARCHAR(64) NOT NULL,
    user_agent_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    idle_expires_at TIMESTAMP(6) NOT NULL,
    absolute_expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    revoke_reason VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_session_web_secret UNIQUE (web_secret_hash),
    CONSTRAINT uk_auth_session_refresh_token UNIQUE (refresh_token_hash),
    CONSTRAINT fk_auth_session_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_auth_session_member FOREIGN KEY (membership_id) REFERENCES users(id),
    CONSTRAINT fk_auth_session_device FOREIGN KEY (trusted_device_id) REFERENCES trusted_devices(id)
);

CREATE INDEX idx_auth_session_member_status ON auth_sessions (tenant_id, membership_id, status);
CREATE INDEX idx_auth_session_expiry ON auth_sessions (status, idle_expires_at, absolute_expires_at);

--rollback DROP TABLE auth_sessions;
