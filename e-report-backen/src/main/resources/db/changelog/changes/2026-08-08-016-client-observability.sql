--liquibase formatted sql

--changeset e-report:2026-08-08-016-client-observability-mysql dbms:mysql
CREATE TABLE client_observation_events (
    id BINARY(16) NOT NULL,
    client_event_id VARCHAR(64) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    org_unit_id BINARY(16),
    user_id BINARY(16),
    platform VARCHAR(16) NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    category VARCHAR(40) NOT NULL,
    event_name VARCHAR(100) NOT NULL,
    message VARCHAR(2000),
    stack_trace TEXT,
    route VARCHAR(255),
    operation_name VARCHAR(120),
    outcome VARCHAR(20),
    trace_id VARCHAR(100),
    installation_id VARCHAR(64),
    session_id VARCHAR(64),
    app_version VARCHAR(50),
    app_build VARCHAR(50),
    os_name VARCHAR(30),
    os_version VARCHAR(50),
    manufacturer VARCHAR(100),
    device_model VARCHAR(100),
    user_agent VARCHAR(500),
    network_type VARCHAR(50),
    context_json TEXT,
    breadcrumbs_json TEXT,
    client_occurred_at DATETIME(6),
    received_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_client_observation_events PRIMARY KEY (id),
    CONSTRAINT uk_client_observation_event_id UNIQUE (client_event_id),
    CONSTRAINT fk_client_observation_event_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_client_observation_event_org FOREIGN KEY (org_unit_id) REFERENCES org_units (id),
    CONSTRAINT fk_client_observation_event_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_observation_tenant_received
    ON client_observation_events (tenant_id, received_at, id);
CREATE INDEX idx_observation_org_received
    ON client_observation_events (tenant_id, org_unit_id, received_at, id);
CREATE INDEX idx_observation_severity_received
    ON client_observation_events (tenant_id, severity, received_at);
CREATE INDEX idx_observation_category_received
    ON client_observation_events (tenant_id, category, received_at);

--rollback DROP TABLE client_observation_events;

--changeset e-report:2026-08-08-016-client-observability-h2 dbms:h2
CREATE TABLE client_observation_events (
    id UUID NOT NULL,
    client_event_id VARCHAR(64) NOT NULL,
    tenant_id UUID NOT NULL,
    org_unit_id UUID,
    user_id UUID,
    platform VARCHAR(16) NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    category VARCHAR(40) NOT NULL,
    event_name VARCHAR(100) NOT NULL,
    message VARCHAR(2000),
    stack_trace VARCHAR(1000000),
    route VARCHAR(255),
    operation_name VARCHAR(120),
    outcome VARCHAR(20),
    trace_id VARCHAR(100),
    installation_id VARCHAR(64),
    session_id VARCHAR(64),
    app_version VARCHAR(50),
    app_build VARCHAR(50),
    os_name VARCHAR(30),
    os_version VARCHAR(50),
    manufacturer VARCHAR(100),
    device_model VARCHAR(100),
    user_agent VARCHAR(500),
    network_type VARCHAR(50),
    context_json VARCHAR(1000000),
    breadcrumbs_json VARCHAR(1000000),
    client_occurred_at TIMESTAMP(6),
    received_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_client_observation_events PRIMARY KEY (id),
    CONSTRAINT uk_client_observation_event_id UNIQUE (client_event_id),
    CONSTRAINT fk_client_observation_event_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_client_observation_event_org FOREIGN KEY (org_unit_id) REFERENCES org_units (id),
    CONSTRAINT fk_client_observation_event_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_observation_tenant_received
    ON client_observation_events (tenant_id, received_at, id);
CREATE INDEX idx_observation_org_received
    ON client_observation_events (tenant_id, org_unit_id, received_at, id);
CREATE INDEX idx_observation_severity_received
    ON client_observation_events (tenant_id, severity, received_at);
CREATE INDEX idx_observation_category_received
    ON client_observation_events (tenant_id, category, received_at);

--rollback DROP TABLE client_observation_events;
