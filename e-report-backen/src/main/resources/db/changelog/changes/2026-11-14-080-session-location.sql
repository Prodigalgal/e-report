--liquibase formatted sql

--changeset e-report:2026-11-14-080-session-location-postgresql dbms:postgresql
-- 登录态最新位置：每个会话仅保留一条最新坐标，不做历史轨迹。
-- 坐标系与来源一并记录，便于前端按 GCJ-02 渲染并回溯定位链路。
ALTER TABLE auth_sessions ADD COLUMN location_latitude DOUBLE PRECISION NULL;
ALTER TABLE auth_sessions ADD COLUMN location_longitude DOUBLE PRECISION NULL;
ALTER TABLE auth_sessions ADD COLUMN location_coordinate_system VARCHAR(20) NULL;
ALTER TABLE auth_sessions ADD COLUMN location_accuracy_meters DOUBLE PRECISION NULL;
ALTER TABLE auth_sessions ADD COLUMN location_provider VARCHAR(40) NULL;
ALTER TABLE auth_sessions ADD COLUMN location_fixed_at TIMESTAMP(6) NULL;
ALTER TABLE auth_sessions ADD COLUMN location_reported_at TIMESTAMP(6) NULL;

-- 来源 IP 的明文形态，仅用于管理员排障与归属核对。原 ip_hash 继续保留，
-- 仍作为会话劫持比对的依据，不因新增明文字段而放松。
ALTER TABLE auth_sessions ADD COLUMN client_ip VARCHAR(64) NULL;

-- 地图聚合按“最近上报过位置”的活跃会话检索，需要能走索引。
CREATE INDEX IF NOT EXISTS idx_auth_session_location_reported_at
    ON auth_sessions (tenant_id, location_reported_at);

--rollback DROP INDEX IF EXISTS idx_auth_session_location_reported_at;
--rollback ALTER TABLE auth_sessions DROP COLUMN client_ip;
--rollback ALTER TABLE auth_sessions DROP COLUMN location_reported_at;
--rollback ALTER TABLE auth_sessions DROP COLUMN location_fixed_at;
--rollback ALTER TABLE auth_sessions DROP COLUMN location_provider;
--rollback ALTER TABLE auth_sessions DROP COLUMN location_accuracy_meters;
--rollback ALTER TABLE auth_sessions DROP COLUMN location_coordinate_system;
--rollback ALTER TABLE auth_sessions DROP COLUMN location_longitude;
--rollback ALTER TABLE auth_sessions DROP COLUMN location_latitude;

--changeset e-report:2026-11-14-080-session-location-h2 dbms:h2
ALTER TABLE auth_sessions ADD COLUMN location_latitude DOUBLE PRECISION NULL;
ALTER TABLE auth_sessions ADD COLUMN location_longitude DOUBLE PRECISION NULL;
ALTER TABLE auth_sessions ADD COLUMN location_coordinate_system VARCHAR(20) NULL;
ALTER TABLE auth_sessions ADD COLUMN location_accuracy_meters DOUBLE PRECISION NULL;
ALTER TABLE auth_sessions ADD COLUMN location_provider VARCHAR(40) NULL;
ALTER TABLE auth_sessions ADD COLUMN location_fixed_at TIMESTAMP(6) NULL;
ALTER TABLE auth_sessions ADD COLUMN location_reported_at TIMESTAMP(6) NULL;
ALTER TABLE auth_sessions ADD COLUMN client_ip VARCHAR(64) NULL;

CREATE INDEX IF NOT EXISTS idx_auth_session_location_reported_at
    ON auth_sessions (tenant_id, location_reported_at);

--rollback DROP INDEX IF EXISTS idx_auth_session_location_reported_at;
--rollback ALTER TABLE auth_sessions DROP COLUMN client_ip;
--rollback ALTER TABLE auth_sessions DROP COLUMN location_reported_at;
--rollback ALTER TABLE auth_sessions DROP COLUMN location_fixed_at;
--rollback ALTER TABLE auth_sessions DROP COLUMN location_provider;
--rollback ALTER TABLE auth_sessions DROP COLUMN location_accuracy_meters;
--rollback ALTER TABLE auth_sessions DROP COLUMN location_coordinate_system;
--rollback ALTER TABLE auth_sessions DROP COLUMN location_longitude;
--rollback ALTER TABLE auth_sessions DROP COLUMN location_latitude;

--changeset e-report:2026-11-14-080-session-location-permission-postgresql dbms:postgresql
-- 新增“登录态位置查看”原子权限，仅分配管理员，用于后台态势地图与会话位置详情。
CREATE TABLE permission_catalog_release_20261114 (
    permission_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (permission_name)
);

INSERT INTO permission_catalog_release_20261114 (permission_name) VALUES
('user:session-location:view');

INSERT INTO permissions (id, name, display_name, description)
SELECT gen_random_uuid(), catalog.permission_name, '登录态位置查看', '查看授权组织范围内已登录用户的位置、设备与会话详情'
FROM permission_catalog_release_20261114 catalog
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = catalog.permission_name);

--rollback DELETE FROM permissions WHERE name IN (SELECT permission_name FROM permission_catalog_release_20261114);
--rollback DROP TABLE permission_catalog_release_20261114;

--changeset e-report:2026-11-14-080-session-location-permission-h2 dbms:h2
CREATE TABLE permission_catalog_release_20261114 (
    permission_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (permission_name)
);

INSERT INTO permission_catalog_release_20261114 (permission_name) VALUES
('user:session-location:view');

INSERT INTO permissions (id, name, display_name, description)
SELECT RANDOM_UUID(), catalog.permission_name, '登录态位置查看', '查看授权组织范围内已登录用户的位置、设备与会话详情'
FROM permission_catalog_release_20261114 catalog
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = catalog.permission_name);

--rollback DELETE FROM permissions WHERE name IN (SELECT permission_name FROM permission_catalog_release_20261114);
--rollback DROP TABLE permission_catalog_release_20261114;
