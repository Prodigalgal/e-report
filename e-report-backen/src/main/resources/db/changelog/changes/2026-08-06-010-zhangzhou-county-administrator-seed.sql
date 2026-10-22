--liquibase formatted sql

--changeset e-report:2026-08-06-010-validate-county-admin-usernames dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM users u JOIN tenants t ON t.id = u.tenant_id WHERE u.username IN ('xiangcheng','longwen','longhai','changtai','yunxiao','zhangpu','zhaoan','dongshan','nanjing','pinghe','huaan') AND t.code <> '${defaultTenantCode}'
--comment Reserved Zhangzhou county administrator usernames must not belong to another tenant.
SELECT 1;

--rollback SELECT 1;

--changeset e-report:2026-08-06-010-seed-county-administrators dbms:mysql,h2
--comment One-time Zhangzhou tenant seed. Runtime code intentionally contains no county account catalog.
INSERT INTO roles (tenant_id, built_in, name, display_name, description)
SELECT t.id, TRUE, 'ROLE_USER', '普通用户', '基础登录角色，不包含工单操作权限。'
FROM tenants t
WHERE t.code = '${defaultTenantCode}'
  AND NOT EXISTS (SELECT 1 FROM roles r WHERE r.tenant_id = t.id AND r.name = 'ROLE_USER');

INSERT INTO roles (tenant_id, built_in, name, display_name, description)
SELECT t.id, TRUE, 'ROLE_COUNTY_ADMIN', '区县管理员', '管理授权区县组织树内的工单、用户、组织及独立配置，并查看全市 SLA 标准。'
FROM tenants t
WHERE t.code = '${defaultTenantCode}'
  AND NOT EXISTS (SELECT 1 FROM roles r WHERE r.tenant_id = t.id AND r.name = 'ROLE_COUNTY_ADMIN');

CREATE TABLE county_admin_seed_20260806 (
    org_code VARCHAR(50) NOT NULL,
    org_name VARCHAR(100) NOT NULL,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    CONSTRAINT pk_county_admin_seed_20260806 PRIMARY KEY (username)
);

INSERT INTO county_admin_seed_20260806 (org_code, org_name, username, password_hash) VALUES
('350602', '芗城区', 'xiangcheng', '$2b$10$u.ydwi5vq0RPa01NUk2eguQ63UNF.ebfrIj2PiYGIv5hs6EDezv42'),
('350603', '龙文区', 'longwen', '$2b$10$k8hZSmWWYrH23WdvrAmdq.43HpwC4gT5Y1tUbCI6ewHTiWwkhlb8q'),
('350604', '龙海区', 'longhai', '$2b$10$gOeJAhABo2CfplgMJhFcL.Nq68fSaELoObHOoWdlQsN08eE6zOgrq'),
('350605', '长泰区', 'changtai', '$2b$10$wQW5vnL/Mtmm/iCDmU.xLesgARgkfjhxylPc31Cb06bE9TlZ2Pq6q'),
('350622', '云霄县', 'yunxiao', '$2b$10$.TTfJcltCbBvEfdtk8sZw.geLN0mTmbIv3P3ZHq79CaBmxRjM2/uK'),
('350623', '漳浦县', 'zhangpu', '$2b$10$R/3pVV8TiK7VQKqvHRTNT.UUQ114/id5hk54jxywj10a7kIDNVI0K'),
('350624', '诏安县', 'zhaoan', '$2b$10$zg3lTtIwE6erJsTQ1ElJCuzG9bzWZD8sKQPMVcdG5/uglvS.imT8S'),
('350626', '东山县', 'dongshan', '$2b$10$Z0CFsDrqjHGKxmZPBSBwJ.CsKjRz4LG4TyuXqV4ANoE47S0hAwytq'),
('350627', '南靖县', 'nanjing', '$2b$10$YqqaorhMRrlzY5qMQcC6E.b5p1U2X6Qqs3AGcMcvtu3VAa1mbmOWq'),
('350628', '平和县', 'pinghe', '$2b$10$l9N4KtObPgWLQJAdnGpoKe5ecay0upOziTRtLllCzQPPwSiy90w6u'),
('350629', '华安县', 'huaan', '$2b$10$w9kVPF25yfB0C6X3mFZYNuB94DjYwZlzuoQi.ufNV6lPCFoJnJ9Pi');

INSERT INTO users (tenant_id, org_unit_id, authorization_version, username, password, name, status, created_at, updated_at)
SELECT t.id, o.id, 1, seed.username, seed.password_hash, CONCAT(seed.org_name, '管理员'), 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM county_admin_seed_20260806 seed
JOIN tenants t ON t.code = '${defaultTenantCode}'
JOIN org_units o ON o.tenant_id = t.id AND o.code = seed.org_code
WHERE NOT EXISTS (SELECT 1 FROM users existing WHERE existing.username = seed.username);

UPDATE users
SET org_unit_id = (
    SELECT o.id
    FROM county_admin_seed_20260806 seed
    JOIN tenants t ON t.code = '${defaultTenantCode}'
    JOIN org_units o ON o.tenant_id = t.id AND o.code = seed.org_code
    WHERE seed.username = users.username
)
WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}')
  AND username IN (SELECT username FROM county_admin_seed_20260806);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM county_admin_seed_20260806 seed
JOIN tenants t ON t.code = '${defaultTenantCode}'
JOIN users u ON u.tenant_id = t.id AND u.username = seed.username
JOIN roles r ON r.tenant_id = t.id AND r.name IN ('ROLE_COUNTY_ADMIN', 'ROLE_USER')
WHERE NOT EXISTS (SELECT 1 FROM user_roles existing WHERE existing.user_id = u.id AND existing.role_id = r.id);

INSERT INTO membership_role_grants (tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at)
SELECT t.id, u.id, r.id, 'ORG_TREE', o.id, CURRENT_TIMESTAMP
FROM county_admin_seed_20260806 seed
JOIN tenants t ON t.code = '${defaultTenantCode}'
JOIN org_units o ON o.tenant_id = t.id AND o.code = seed.org_code
JOIN users u ON u.tenant_id = t.id AND u.username = seed.username
JOIN roles r ON r.tenant_id = t.id AND r.name = 'ROLE_COUNTY_ADMIN'
WHERE NOT EXISTS (
    SELECT 1 FROM membership_role_grants existing
    WHERE existing.tenant_id = t.id AND existing.membership_id = u.id AND existing.role_id = r.id
      AND existing.data_scope = 'ORG_TREE' AND existing.scope_org_id = o.id
);

INSERT INTO membership_role_grants (tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at)
SELECT t.id, u.id, r.id, 'SELF', NULL, CURRENT_TIMESTAMP
FROM county_admin_seed_20260806 seed
JOIN tenants t ON t.code = '${defaultTenantCode}'
JOIN users u ON u.tenant_id = t.id AND u.username = seed.username
JOIN roles r ON r.tenant_id = t.id AND r.name = 'ROLE_USER'
WHERE NOT EXISTS (
    SELECT 1 FROM membership_role_grants existing
    WHERE existing.tenant_id = t.id AND existing.membership_id = u.id AND existing.role_id = r.id
      AND existing.data_scope = 'SELF' AND existing.scope_org_id IS NULL
);

DROP TABLE county_admin_seed_20260806;

--rollback DELETE FROM membership_role_grants WHERE membership_id IN (SELECT id FROM users WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}') AND username IN ('xiangcheng','longwen','longhai','changtai','yunxiao','zhangpu','zhaoan','dongshan','nanjing','pinghe','huaan'));
--rollback DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}') AND username IN ('xiangcheng','longwen','longhai','changtai','yunxiao','zhangpu','zhaoan','dongshan','nanjing','pinghe','huaan'));
--rollback DELETE FROM users WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}') AND username IN ('xiangcheng','longwen','longhai','changtai','yunxiao','zhangpu','zhaoan','dongshan','nanjing','pinghe','huaan');
