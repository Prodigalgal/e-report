--liquibase formatted sql

--changeset e-report:2026-10-21-076-expo-updates
--comment Belongs to e-report 6.8.0. Signed immutable manifests are activated only after all assets are distributed.
CREATE TABLE expo_updates (
    id UUID PRIMARY KEY,
    environment VARCHAR(20) NOT NULL,
    runtime_version VARCHAR(100) NOT NULL,
    version_name VARCHAR(50) NOT NULL,
    source_sha VARCHAR(40) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    payload TEXT NOT NULL,
    signature VARCHAR(1024) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_message VARCHAR(255),
    attempt_id UUID,
    last_attempt_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);
CREATE INDEX idx_expo_updates_latest ON expo_updates (environment, runtime_version, created_at);
--rollback DROP TABLE expo_updates;
