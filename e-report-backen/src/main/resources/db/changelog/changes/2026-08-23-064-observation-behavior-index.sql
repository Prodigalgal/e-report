--liquibase formatted sql

--changeset e-report:2026-08-23-064-observation-behavior-index-mysql dbms:mysql
--comment Add the user behavior query index for the retired MySQL source schema.
CREATE INDEX idx_observation_behavior_user_received
    ON client_observation_events (tenant_id, event_type, user_id, received_at, id);
--rollback DROP INDEX idx_observation_behavior_user_received ON client_observation_events;

--changeset e-report:2026-08-23-064-observation-behavior-index-h2 dbms:h2
--comment H2 equivalent for observation behavior query tests.
CREATE INDEX idx_observation_behavior_user_received
    ON client_observation_events (tenant_id, event_type, user_id, received_at, id);
--rollback DROP INDEX idx_observation_behavior_user_received;
