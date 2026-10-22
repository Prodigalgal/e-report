--liquibase formatted sql

--changeset e-report:2026-08-23-061-work-order-transfer-permissions-mysql dbms:mysql
CREATE TABLE work_order_transfer_permission_manifest_20260823 (
    permission_name VARCHAR(100) NOT NULL PRIMARY KEY
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
INSERT INTO work_order_transfer_permission_manifest_20260823 (permission_name) VALUES
('work-order:network-fault:transfer:create'),
('work-order:network-fault:transfer:accept'),
('work-order:network-fault:transfer:reject'),
('work-order:network-fault:transfer:cancel'),
('work-order:box-door-open:transfer:create'),
('work-order:box-door-open:transfer:accept'),
('work-order:box-door-open:transfer:reject'),
('work-order:box-door-open:transfer:cancel'),
('work-order:damaged-box-replacement:transfer:create'),
('work-order:damaged-box-replacement:transfer:accept'),
('work-order:damaged-box-replacement:transfer:reject'),
('work-order:damaged-box-replacement:transfer:cancel');
INSERT INTO permissions (id, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), manifest.permission_name, NULL, NULL
FROM work_order_transfer_permission_manifest_20260823 manifest
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = manifest.permission_name);
--rollback DELETE FROM permissions WHERE name IN (SELECT permission_name FROM work_order_transfer_permission_manifest_20260823);
--rollback DROP TABLE work_order_transfer_permission_manifest_20260823;

--changeset e-report:2026-08-23-061-work-order-transfer-permissions-h2 dbms:h2
CREATE TABLE work_order_transfer_permission_manifest_20260823 (
    permission_name VARCHAR(100) NOT NULL PRIMARY KEY
);
INSERT INTO work_order_transfer_permission_manifest_20260823 (permission_name) VALUES
('work-order:network-fault:transfer:create'),
('work-order:network-fault:transfer:accept'),
('work-order:network-fault:transfer:reject'),
('work-order:network-fault:transfer:cancel'),
('work-order:box-door-open:transfer:create'),
('work-order:box-door-open:transfer:accept'),
('work-order:box-door-open:transfer:reject'),
('work-order:box-door-open:transfer:cancel'),
('work-order:damaged-box-replacement:transfer:create'),
('work-order:damaged-box-replacement:transfer:accept'),
('work-order:damaged-box-replacement:transfer:reject'),
('work-order:damaged-box-replacement:transfer:cancel');
INSERT INTO permissions (id, name, display_name, description)
SELECT RANDOM_UUID(), manifest.permission_name, NULL, NULL
FROM work_order_transfer_permission_manifest_20260823 manifest
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = manifest.permission_name);
--rollback DELETE FROM permissions WHERE name IN (SELECT permission_name FROM work_order_transfer_permission_manifest_20260823);
--rollback DROP TABLE work_order_transfer_permission_manifest_20260823;

--changeset e-report:2026-08-23-061-work-order-transfer-permissions-assert dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:12 SELECT COUNT(*) FROM permissions WHERE name IN (SELECT permission_name FROM work_order_transfer_permission_manifest_20260823)
SELECT 1;
--rollback SELECT 1;
