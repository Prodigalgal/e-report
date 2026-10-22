--liquibase formatted sql

--changeset e-report:2026-08-23-002-v5-observation-behavior dbms:postgresql
--comment Add behavior-query indexing and fail-closed row-level isolation for observation events.
CREATE INDEX idx_observation_behavior_user_received
    ON client_observation_events (tenant_id, event_type, user_id, received_at, id);

ALTER TABLE client_observation_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE client_observation_events FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON client_observation_events;
CREATE POLICY tenant_isolation ON client_observation_events
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

--rollback DROP POLICY IF EXISTS tenant_isolation ON client_observation_events;
--rollback ALTER TABLE client_observation_events DISABLE ROW LEVEL SECURITY;
--rollback DROP INDEX IF EXISTS idx_observation_behavior_user_received;
