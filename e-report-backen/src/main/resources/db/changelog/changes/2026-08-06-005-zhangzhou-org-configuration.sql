--liquibase formatted sql

--changeset e-report:2026-08-06-005-org-configuration dbms:mysql,h2
CREATE TABLE org_app_config (
    id BIGINT AUTO_INCREMENT NOT NULL,
    tenant_id BIGINT NOT NULL,
    org_unit_id BIGINT NOT NULL,
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT,
    description VARCHAR(255),
    last_updated_time DATETIME(6) NOT NULL,
    CONSTRAINT pk_org_app_config PRIMARY KEY (id),
    CONSTRAINT uk_org_app_config_scope_key UNIQUE (tenant_id, org_unit_id, config_key),
    CONSTRAINT fk_org_app_config_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_org_app_config_org FOREIGN KEY (org_unit_id) REFERENCES org_units (id)
);
CREATE INDEX idx_org_app_config_scope ON org_app_config (tenant_id, org_unit_id);

--rollback DROP TABLE org_app_config;

--changeset e-report:2026-08-06-005-zhangzhou-counties dbms:mysql,h2
INSERT INTO org_units (tenant_id, parent_id, code, name, unit_type, path, status, created_at, updated_at)
SELECT t.id, root.id, '350602', '芗城区', 'COUNTY', CONCAT(root.path, '350602/'), 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants t JOIN org_units root ON root.tenant_id = t.id AND root.parent_id IS NULL
WHERE t.code = '${defaultTenantCode}' AND NOT EXISTS (SELECT 1 FROM org_units o WHERE o.tenant_id = t.id AND o.code = '350602');
INSERT INTO org_units (tenant_id, parent_id, code, name, unit_type, path, status, created_at, updated_at)
SELECT t.id, root.id, '350603', '龙文区', 'COUNTY', CONCAT(root.path, '350603/'), 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants t JOIN org_units root ON root.tenant_id = t.id AND root.parent_id IS NULL
WHERE t.code = '${defaultTenantCode}' AND NOT EXISTS (SELECT 1 FROM org_units o WHERE o.tenant_id = t.id AND o.code = '350603');
INSERT INTO org_units (tenant_id, parent_id, code, name, unit_type, path, status, created_at, updated_at)
SELECT t.id, root.id, '350604', '龙海区', 'COUNTY', CONCAT(root.path, '350604/'), 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants t JOIN org_units root ON root.tenant_id = t.id AND root.parent_id IS NULL
WHERE t.code = '${defaultTenantCode}' AND NOT EXISTS (SELECT 1 FROM org_units o WHERE o.tenant_id = t.id AND o.code = '350604');
INSERT INTO org_units (tenant_id, parent_id, code, name, unit_type, path, status, created_at, updated_at)
SELECT t.id, root.id, '350605', '长泰区', 'COUNTY', CONCAT(root.path, '350605/'), 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants t JOIN org_units root ON root.tenant_id = t.id AND root.parent_id IS NULL
WHERE t.code = '${defaultTenantCode}' AND NOT EXISTS (SELECT 1 FROM org_units o WHERE o.tenant_id = t.id AND o.code = '350605');
INSERT INTO org_units (tenant_id, parent_id, code, name, unit_type, path, status, created_at, updated_at)
SELECT t.id, root.id, '350622', '云霄县', 'COUNTY', CONCAT(root.path, '350622/'), 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants t JOIN org_units root ON root.tenant_id = t.id AND root.parent_id IS NULL
WHERE t.code = '${defaultTenantCode}' AND NOT EXISTS (SELECT 1 FROM org_units o WHERE o.tenant_id = t.id AND o.code = '350622');
INSERT INTO org_units (tenant_id, parent_id, code, name, unit_type, path, status, created_at, updated_at)
SELECT t.id, root.id, '350623', '漳浦县', 'COUNTY', CONCAT(root.path, '350623/'), 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants t JOIN org_units root ON root.tenant_id = t.id AND root.parent_id IS NULL
WHERE t.code = '${defaultTenantCode}' AND NOT EXISTS (SELECT 1 FROM org_units o WHERE o.tenant_id = t.id AND o.code = '350623');
INSERT INTO org_units (tenant_id, parent_id, code, name, unit_type, path, status, created_at, updated_at)
SELECT t.id, root.id, '350624', '诏安县', 'COUNTY', CONCAT(root.path, '350624/'), 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants t JOIN org_units root ON root.tenant_id = t.id AND root.parent_id IS NULL
WHERE t.code = '${defaultTenantCode}' AND NOT EXISTS (SELECT 1 FROM org_units o WHERE o.tenant_id = t.id AND o.code = '350624');
INSERT INTO org_units (tenant_id, parent_id, code, name, unit_type, path, status, created_at, updated_at)
SELECT t.id, root.id, '350626', '东山县', 'COUNTY', CONCAT(root.path, '350626/'), 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants t JOIN org_units root ON root.tenant_id = t.id AND root.parent_id IS NULL
WHERE t.code = '${defaultTenantCode}' AND NOT EXISTS (SELECT 1 FROM org_units o WHERE o.tenant_id = t.id AND o.code = '350626');
INSERT INTO org_units (tenant_id, parent_id, code, name, unit_type, path, status, created_at, updated_at)
SELECT t.id, root.id, '350627', '南靖县', 'COUNTY', CONCAT(root.path, '350627/'), 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants t JOIN org_units root ON root.tenant_id = t.id AND root.parent_id IS NULL
WHERE t.code = '${defaultTenantCode}' AND NOT EXISTS (SELECT 1 FROM org_units o WHERE o.tenant_id = t.id AND o.code = '350627');
INSERT INTO org_units (tenant_id, parent_id, code, name, unit_type, path, status, created_at, updated_at)
SELECT t.id, root.id, '350628', '平和县', 'COUNTY', CONCAT(root.path, '350628/'), 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants t JOIN org_units root ON root.tenant_id = t.id AND root.parent_id IS NULL
WHERE t.code = '${defaultTenantCode}' AND NOT EXISTS (SELECT 1 FROM org_units o WHERE o.tenant_id = t.id AND o.code = '350628');
INSERT INTO org_units (tenant_id, parent_id, code, name, unit_type, path, status, created_at, updated_at)
SELECT t.id, root.id, '350629', '华安县', 'COUNTY', CONCAT(root.path, '350629/'), 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants t JOIN org_units root ON root.tenant_id = t.id AND root.parent_id IS NULL
WHERE t.code = '${defaultTenantCode}' AND NOT EXISTS (SELECT 1 FROM org_units o WHERE o.tenant_id = t.id AND o.code = '350629');

--rollback DELETE FROM org_units WHERE code IN ('350602','350603','350604','350605','350622','350623','350624','350626','350627','350628','350629') AND tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}');

--changeset e-report:2026-08-06-005-adopt-existing-county-accounts dbms:mysql,h2
UPDATE users SET org_unit_id = (SELECT o.id FROM org_units o JOIN tenants t ON t.id = o.tenant_id WHERE t.code = '${defaultTenantCode}' AND o.code = '350602')
WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}') AND username = 'xiangcheng';
UPDATE users SET org_unit_id = (SELECT o.id FROM org_units o JOIN tenants t ON t.id = o.tenant_id WHERE t.code = '${defaultTenantCode}' AND o.code = '350603')
WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}') AND username = 'longwen';
UPDATE users SET org_unit_id = (SELECT o.id FROM org_units o JOIN tenants t ON t.id = o.tenant_id WHERE t.code = '${defaultTenantCode}' AND o.code = '350604')
WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}') AND username = 'longhai';
UPDATE users SET org_unit_id = (SELECT o.id FROM org_units o JOIN tenants t ON t.id = o.tenant_id WHERE t.code = '${defaultTenantCode}' AND o.code = '350605')
WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}') AND username = 'changtai';
UPDATE users SET org_unit_id = (SELECT o.id FROM org_units o JOIN tenants t ON t.id = o.tenant_id WHERE t.code = '${defaultTenantCode}' AND o.code = '350622')
WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}') AND username = 'yunxiao';
UPDATE users SET org_unit_id = (SELECT o.id FROM org_units o JOIN tenants t ON t.id = o.tenant_id WHERE t.code = '${defaultTenantCode}' AND o.code = '350623')
WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}') AND username = 'zhangpu';
UPDATE users SET org_unit_id = (SELECT o.id FROM org_units o JOIN tenants t ON t.id = o.tenant_id WHERE t.code = '${defaultTenantCode}' AND o.code = '350624')
WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}') AND username = 'zhaoan';
UPDATE users SET org_unit_id = (SELECT o.id FROM org_units o JOIN tenants t ON t.id = o.tenant_id WHERE t.code = '${defaultTenantCode}' AND o.code = '350626')
WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}') AND username = 'dongshan';
UPDATE users SET org_unit_id = (SELECT o.id FROM org_units o JOIN tenants t ON t.id = o.tenant_id WHERE t.code = '${defaultTenantCode}' AND o.code = '350627')
WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}') AND username = 'nanjing';
UPDATE users SET org_unit_id = (SELECT o.id FROM org_units o JOIN tenants t ON t.id = o.tenant_id WHERE t.code = '${defaultTenantCode}' AND o.code = '350628')
WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}') AND username = 'pinghe';
UPDATE users SET org_unit_id = (SELECT o.id FROM org_units o JOIN tenants t ON t.id = o.tenant_id WHERE t.code = '${defaultTenantCode}' AND o.code = '350629')
WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}') AND username = 'huaan';

--rollback UPDATE users SET org_unit_id = (SELECT id FROM org_units WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}') AND parent_id IS NULL) WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}') AND username IN ('xiangcheng','longwen','longhai','changtai','yunxiao','zhangpu','zhaoan','dongshan','nanjing','pinghe','huaan');
