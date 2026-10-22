--liquibase formatted sql

--changeset e-report:2026-08-24-065-otel-observation-model-mysql dbms:mysql
ALTER TABLE client_observation_events
    ADD COLUMN observation_schema_version integer NOT NULL DEFAULT 1,
    ADD COLUMN signal_type varchar(10),
    ADD COLUMN request_id varchar(100),
    ADD COLUMN otel_trace_id varchar(32),
    ADD COLUMN otel_span_id varchar(16),
    ADD COLUMN otel_parent_span_id varchar(16),
    ADD COLUMN otel_trace_flags integer,
    ADD COLUMN severity_number integer,
    ADD COLUMN severity_text varchar(20),
    ADD COLUMN body text,
    ADD COLUMN scope_name varchar(255),
    ADD COLUMN scope_version varchar(50),
    ADD COLUMN resource_json text,
    ADD COLUMN scope_attributes_json text,
    ADD COLUMN attributes_json text,
    ADD COLUMN span_name varchar(255),
    ADD COLUMN span_kind varchar(20),
    ADD COLUMN span_status_code varchar(20),
    ADD COLUMN span_status_message varchar(255),
    ADD COLUMN duration_nanos bigint,
    ADD COLUMN span_events_json text;
CREATE INDEX idx_observation_tenant_otel_trace ON client_observation_events (tenant_id, otel_trace_id, received_at);
CREATE INDEX idx_observation_tenant_signal_received ON client_observation_events (tenant_id, signal_type, received_at, id);

--changeset e-report:2026-08-24-065-otel-observation-model-h2 dbms:h2
ALTER TABLE client_observation_events ADD COLUMN observation_schema_version integer DEFAULT 1 NOT NULL;
ALTER TABLE client_observation_events ADD COLUMN signal_type varchar(10);
ALTER TABLE client_observation_events ADD COLUMN request_id varchar(100);
ALTER TABLE client_observation_events ADD COLUMN otel_trace_id varchar(32);
ALTER TABLE client_observation_events ADD COLUMN otel_span_id varchar(16);
ALTER TABLE client_observation_events ADD COLUMN otel_parent_span_id varchar(16);
ALTER TABLE client_observation_events ADD COLUMN otel_trace_flags integer;
ALTER TABLE client_observation_events ADD COLUMN severity_number integer;
ALTER TABLE client_observation_events ADD COLUMN severity_text varchar(20);
ALTER TABLE client_observation_events ADD COLUMN body text;
ALTER TABLE client_observation_events ADD COLUMN scope_name varchar(255);
ALTER TABLE client_observation_events ADD COLUMN scope_version varchar(50);
ALTER TABLE client_observation_events ADD COLUMN resource_json text;
ALTER TABLE client_observation_events ADD COLUMN scope_attributes_json text;
ALTER TABLE client_observation_events ADD COLUMN attributes_json text;
ALTER TABLE client_observation_events ADD COLUMN span_name varchar(255);
ALTER TABLE client_observation_events ADD COLUMN span_kind varchar(20);
ALTER TABLE client_observation_events ADD COLUMN span_status_code varchar(20);
ALTER TABLE client_observation_events ADD COLUMN span_status_message varchar(255);
ALTER TABLE client_observation_events ADD COLUMN duration_nanos bigint;
ALTER TABLE client_observation_events ADD COLUMN span_events_json text;
CREATE INDEX idx_observation_tenant_otel_trace ON client_observation_events (tenant_id, otel_trace_id, received_at);
CREATE INDEX idx_observation_tenant_signal_received ON client_observation_events (tenant_id, signal_type, received_at, id);
