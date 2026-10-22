--liquibase formatted sql

--changeset e-report:2026-10-21-075-app-editions
--comment Belongs to e-report 6.8.0. APK identities and immutable versions are isolated by application edition.
ALTER TABLE app_releases ADD COLUMN edition VARCHAR(20) DEFAULT 'NATIVE' NOT NULL;
ALTER TABLE app_releases DROP CONSTRAINT uk_app_releases_version;
ALTER TABLE app_releases ADD CONSTRAINT uk_app_releases_version UNIQUE (edition, platform, channel, version_code);
DROP INDEX idx_app_releases_latest;
CREATE INDEX idx_app_releases_latest ON app_releases (edition, platform, channel, published, version_code);
DROP INDEX idx_app_releases_channel_time;
CREATE INDEX idx_app_releases_channel_time ON app_releases (edition, platform, channel, published_at);
