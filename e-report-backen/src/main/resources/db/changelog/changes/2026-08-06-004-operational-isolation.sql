--liquibase formatted sql

--changeset e-report:2026-08-06-004-operational-isolation dbms:mysql,h2
CREATE TABLE tenant_app_config (
    id BIGINT AUTO_INCREMENT NOT NULL,
    tenant_id BIGINT NOT NULL,
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT,
    description VARCHAR(255),
    last_updated_time DATETIME(6) NOT NULL,
    CONSTRAINT pk_tenant_app_config PRIMARY KEY (id),
    CONSTRAINT uk_tenant_app_config_key UNIQUE (tenant_id, config_key),
    CONSTRAINT fk_tenant_app_config_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE TABLE command_idempotency_records (
    id BIGINT AUTO_INCREMENT NOT NULL,
    tenant_id BIGINT NOT NULL,
    membership_id BIGINT NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    state VARCHAR(20) NOT NULL,
    response_body TEXT,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6),
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_command_idempotency_records PRIMARY KEY (id),
    CONSTRAINT uk_command_idempotency_scope UNIQUE (tenant_id, membership_id, endpoint, idempotency_key),
    CONSTRAINT fk_command_idempotency_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_command_idempotency_membership FOREIGN KEY (membership_id) REFERENCES users (id)
);
CREATE INDEX idx_command_idempotency_expiry ON command_idempotency_records (expires_at);

CREATE TABLE outbox_events (
    id BIGINT AUTO_INCREMENT NOT NULL,
    tenant_id BIGINT NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload TEXT NOT NULL,
    state VARCHAR(20) NOT NULL,
    attempts INT NOT NULL,
    available_at DATETIME(6) NOT NULL,
    locked_at DATETIME(6),
    processed_at DATETIME(6),
    last_error VARCHAR(1000),
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT fk_outbox_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX idx_outbox_delivery ON outbox_events (state, available_at, id);
CREATE INDEX idx_outbox_tenant_aggregate ON outbox_events (tenant_id, aggregate_type, aggregate_id);

--rollback DROP TABLE outbox_events;
--rollback DROP TABLE command_idempotency_records;
--rollback DROP TABLE tenant_app_config;
