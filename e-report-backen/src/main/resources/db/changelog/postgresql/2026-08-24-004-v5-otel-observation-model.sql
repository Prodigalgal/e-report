--liquibase formatted sql

--changeset codex:2026-08-24-004-v5-otel-observation-model dbms:postgresql
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

ALTER TABLE client_observation_events
    ADD CONSTRAINT ck_observation_schema_version CHECK (observation_schema_version IN (1, 2)),
    ADD CONSTRAINT ck_observation_signal_type CHECK (signal_type IS NULL OR signal_type IN ('LOG', 'SPAN')),
    ADD CONSTRAINT ck_observation_otel_trace_id CHECK (otel_trace_id IS NULL OR (otel_trace_id ~ '^[0-9a-f]{32}$' AND otel_trace_id <> repeat('0', 32))),
    ADD CONSTRAINT ck_observation_otel_span_id CHECK (otel_span_id IS NULL OR (otel_span_id ~ '^[0-9a-f]{16}$' AND otel_span_id <> repeat('0', 16))),
    ADD CONSTRAINT ck_observation_otel_parent_span_id CHECK (otel_parent_span_id IS NULL OR (otel_parent_span_id ~ '^[0-9a-f]{16}$' AND otel_parent_span_id <> repeat('0', 16))),
    ADD CONSTRAINT ck_observation_otel_trace_flags CHECK (otel_trace_flags IS NULL OR otel_trace_flags BETWEEN 0 AND 255),
    ADD CONSTRAINT ck_observation_severity_number CHECK (severity_number IS NULL OR severity_number BETWEEN 0 AND 24),
    ADD CONSTRAINT ck_observation_span_kind CHECK (span_kind IS NULL OR span_kind IN ('INTERNAL', 'CLIENT', 'SERVER')),
    ADD CONSTRAINT ck_observation_span_status CHECK (span_status_code IS NULL OR span_status_code IN ('UNSET', 'OK', 'ERROR')),
    ADD CONSTRAINT ck_observation_duration CHECK (duration_nanos IS NULL OR duration_nanos >= 0);

CREATE INDEX idx_observation_tenant_otel_trace
    ON client_observation_events (tenant_id, otel_trace_id, received_at)
    WHERE otel_trace_id IS NOT NULL;
CREATE INDEX idx_observation_tenant_signal_received
    ON client_observation_events (tenant_id, signal_type, received_at, id)
    WHERE signal_type IS NOT NULL;

COMMENT ON COLUMN client_observation_events.observation_schema_version IS '1=legacy, 2=ereport-otel/1';
COMMENT ON COLUMN client_observation_events.trace_id IS 'Legacy correlation projection; canonical trace id is otel_trace_id';

--rollback DROP INDEX IF EXISTS idx_observation_tenant_signal_received;
--rollback DROP INDEX IF EXISTS idx_observation_tenant_otel_trace;
