--liquibase formatted sql

--changeset e-report:2026-08-16-043-api-tokens-mysql dbms:mysql
--comment Add user-owned API tokens with fine-grained permission selection and one-way secret storage.
CREATE TABLE api_tokens (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    membership_id BINARY(16) NOT NULL,
    name VARCHAR(80) NOT NULL,
    lookup_key VARCHAR(22) NOT NULL,
    secret_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NULL,
    last_used_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    revoke_reason VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_api_token_lookup UNIQUE (lookup_key),
    CONSTRAINT uk_api_token_secret_hash UNIQUE (secret_hash),
    CONSTRAINT fk_api_token_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_api_token_membership FOREIGN KEY (membership_id) REFERENCES users(id),
    INDEX idx_api_token_owner_created (tenant_id, membership_id, created_at),
    INDEX idx_api_token_expiry (expires_at)
);

CREATE TABLE api_token_permissions (
    token_id BINARY(16) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (token_id, permission_name),
    CONSTRAINT fk_api_token_permission_token
        FOREIGN KEY (token_id) REFERENCES api_tokens(id) ON DELETE CASCADE,
    CONSTRAINT fk_api_token_permission_catalog
        FOREIGN KEY (permission_name) REFERENCES permissions(name)
);

--rollback DROP TABLE api_token_permissions;
--rollback DROP TABLE api_tokens;

--changeset e-report:2026-08-16-043-api-tokens-h2 dbms:h2
--comment H2 equivalent of user-owned API tokens and permission selection.
CREATE TABLE api_tokens (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    lookup_key VARCHAR(22) NOT NULL,
    secret_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6),
    last_used_at TIMESTAMP(6),
    revoked_at TIMESTAMP(6),
    revoke_reason VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT uk_api_token_lookup UNIQUE (lookup_key),
    CONSTRAINT uk_api_token_secret_hash UNIQUE (secret_hash),
    CONSTRAINT fk_api_token_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_api_token_membership FOREIGN KEY (membership_id) REFERENCES users(id)
);

CREATE INDEX idx_api_token_owner_created ON api_tokens (tenant_id, membership_id, created_at);
CREATE INDEX idx_api_token_expiry ON api_tokens (expires_at);

CREATE TABLE api_token_permissions (
    token_id UUID NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (token_id, permission_name),
    CONSTRAINT fk_api_token_permission_token
        FOREIGN KEY (token_id) REFERENCES api_tokens(id) ON DELETE CASCADE,
    CONSTRAINT fk_api_token_permission_catalog
        FOREIGN KEY (permission_name) REFERENCES permissions(name)
);

--rollback DROP TABLE api_token_permissions;
--rollback DROP TABLE api_tokens;
