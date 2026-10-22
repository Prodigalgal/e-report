--liquibase formatted sql

--changeset e-report:2026-08-12-027-validate-tenant-root-candidates dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM (SELECT tenant.id FROM tenants tenant LEFT JOIN org_units organization ON organization.tenant_id = tenant.id AND organization.parent_id IS NULL GROUP BY tenant.id HAVING COUNT(organization.id) <> 1) invalid_tenant_roots
--comment Every existing tenant must have exactly one legacy root candidate before introducing the explicit root reference.
SELECT 1;

--rollback SELECT 1;

--changeset e-report:2026-08-12-027-tenant-root-reference-mysql dbms:mysql
--comment Persist the tenant root relationship explicitly instead of deriving it from code, name, type, or parent columns at runtime.
ALTER TABLE tenants ADD COLUMN root_org_unit_id BINARY(16) NULL AFTER tenant_group_id;

UPDATE tenants tenant
SET tenant.root_org_unit_id = (
    SELECT organization.id
    FROM org_units organization
    WHERE organization.tenant_id = tenant.id
      AND organization.parent_id IS NULL
);

ALTER TABLE tenants
    ADD CONSTRAINT uk_tenants_root_org UNIQUE (root_org_unit_id);
ALTER TABLE org_units ADD CONSTRAINT uk_org_units_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE tenants
    ADD CONSTRAINT fk_tenants_root_org FOREIGN KEY (id, root_org_unit_id)
        REFERENCES org_units (tenant_id, id);

--rollback ALTER TABLE tenants DROP FOREIGN KEY fk_tenants_root_org;
--rollback ALTER TABLE org_units DROP INDEX uk_org_units_tenant_id;
--rollback ALTER TABLE tenants DROP INDEX uk_tenants_root_org;
--rollback ALTER TABLE tenants DROP COLUMN root_org_unit_id;

--changeset e-report:2026-08-12-027-tenant-root-reference-h2 dbms:h2
--comment H2 equivalent for the explicit tenant root relationship.
ALTER TABLE tenants ADD COLUMN root_org_unit_id UUID NULL;

UPDATE tenants tenant
SET root_org_unit_id = (
    SELECT organization.id
    FROM org_units organization
    WHERE organization.tenant_id = tenant.id
      AND organization.parent_id IS NULL
);

ALTER TABLE tenants ADD CONSTRAINT uk_tenants_root_org UNIQUE (root_org_unit_id);
ALTER TABLE org_units ADD CONSTRAINT uk_org_units_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE tenants ADD CONSTRAINT fk_tenants_root_org FOREIGN KEY (id, root_org_unit_id) REFERENCES org_units (tenant_id, id);

--rollback ALTER TABLE tenants DROP CONSTRAINT fk_tenants_root_org;
--rollback ALTER TABLE org_units DROP CONSTRAINT uk_org_units_tenant_id;
--rollback ALTER TABLE tenants DROP CONSTRAINT uk_tenants_root_org;
--rollback ALTER TABLE tenants DROP COLUMN root_org_unit_id;

--changeset e-report:2026-08-12-027-zhangzhou-root-code dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM org_units organization JOIN tenants tenant ON tenant.id = organization.tenant_id WHERE tenant.code = '${defaultTenantCode}' AND organization.code = '3506' AND organization.id <> tenant.root_org_unit_id
--comment Replace the historical root placeholder with Zhangzhou's administrative division code without changing organization identity or hierarchy.
UPDATE org_units organization
SET organization.code = '3506',
    organization.updated_at = CURRENT_TIMESTAMP
WHERE organization.id = (
    SELECT tenant.root_org_unit_id
    FROM tenants tenant
    WHERE tenant.code = '${defaultTenantCode}'
);

--rollback UPDATE org_units organization SET organization.code = 'ROOT', organization.updated_at = CURRENT_TIMESTAMP WHERE organization.id = (SELECT tenant.root_org_unit_id FROM tenants WHERE code = '${defaultTenantCode}');

--changeset e-report:2026-08-12-027-scope-repair-snapshot-mysql dbms:mysql
--comment Snapshot every non-root tenant-wide grant before constraining it to the member's organization tree.
CREATE TABLE authorization_scope_repair_20260812 (
    grant_id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    membership_id BINARY(16) NOT NULL,
    role_id BINARY(16) NOT NULL,
    previous_data_scope VARCHAR(30) NOT NULL,
    previous_scope_org_id BINARY(16) NULL,
    repaired_data_scope VARCHAR(30) NOT NULL,
    repaired_scope_org_id BINARY(16) NOT NULL,
    repaired_at DATETIME(6) NOT NULL,
    repair_reason VARCHAR(255) NOT NULL,
    PRIMARY KEY (grant_id)
);

INSERT INTO authorization_scope_repair_20260812 (
    grant_id, tenant_id, membership_id, role_id,
    previous_data_scope, previous_scope_org_id,
    repaired_data_scope, repaired_scope_org_id, repaired_at, repair_reason
)
SELECT grant_row.id, grant_row.tenant_id, grant_row.membership_id, grant_row.role_id,
       grant_row.data_scope, grant_row.scope_org_id,
       'ORG_TREE', member.org_unit_id, CURRENT_TIMESTAMP,
       'Historical administrator expansion retained TENANT_ALL after the member moved to a non-root organization.'
FROM membership_role_grants grant_row
JOIN users member ON member.id = grant_row.membership_id AND member.tenant_id = grant_row.tenant_id
JOIN tenants tenant ON tenant.id = member.tenant_id
WHERE grant_row.data_scope = 'TENANT_ALL'
  AND tenant.code = '${defaultTenantCode}'
  AND member.org_unit_id <> tenant.root_org_unit_id;

UPDATE membership_role_grants grant_row
JOIN authorization_scope_repair_20260812 repair ON repair.grant_id = grant_row.id
SET grant_row.data_scope = repair.repaired_data_scope,
    grant_row.scope_org_id = repair.repaired_scope_org_id;

UPDATE users member
JOIN (
    SELECT DISTINCT membership_id
    FROM authorization_scope_repair_20260812
) affected ON affected.membership_id = member.id
SET member.authorization_version = member.authorization_version + 1,
    member.updated_at = CURRENT_TIMESTAMP;

--rollback UPDATE membership_role_grants grant_row JOIN authorization_scope_repair_20260812 repair ON repair.grant_id = grant_row.id SET grant_row.data_scope = repair.previous_data_scope, grant_row.scope_org_id = repair.previous_scope_org_id;
--rollback UPDATE users member JOIN (SELECT DISTINCT membership_id FROM authorization_scope_repair_20260812) affected ON affected.membership_id = member.id SET member.authorization_version = member.authorization_version + 1, member.updated_at = CURRENT_TIMESTAMP;
--rollback DROP TABLE authorization_scope_repair_20260812;

--changeset e-report:2026-08-12-027-scope-repair-snapshot-h2 dbms:h2
--comment H2 equivalent for the auditable historical grant repair.
CREATE TABLE authorization_scope_repair_20260812 (
    grant_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    role_id UUID NOT NULL,
    previous_data_scope VARCHAR(30) NOT NULL,
    previous_scope_org_id UUID NULL,
    repaired_data_scope VARCHAR(30) NOT NULL,
    repaired_scope_org_id UUID NOT NULL,
    repaired_at TIMESTAMP(6) NOT NULL,
    repair_reason VARCHAR(255) NOT NULL,
    PRIMARY KEY (grant_id)
);

INSERT INTO authorization_scope_repair_20260812 (
    grant_id, tenant_id, membership_id, role_id,
    previous_data_scope, previous_scope_org_id,
    repaired_data_scope, repaired_scope_org_id, repaired_at, repair_reason
)
SELECT grant_row.id, grant_row.tenant_id, grant_row.membership_id, grant_row.role_id,
       grant_row.data_scope, grant_row.scope_org_id,
       'ORG_TREE', member.org_unit_id, CURRENT_TIMESTAMP,
       'Historical administrator expansion retained TENANT_ALL after the member moved to a non-root organization.'
FROM membership_role_grants grant_row
JOIN users member ON member.id = grant_row.membership_id AND member.tenant_id = grant_row.tenant_id
JOIN tenants tenant ON tenant.id = member.tenant_id
WHERE grant_row.data_scope = 'TENANT_ALL'
  AND tenant.code = '${defaultTenantCode}'
  AND member.org_unit_id <> tenant.root_org_unit_id;

UPDATE membership_role_grants grant_row
SET data_scope = 'ORG_TREE',
    scope_org_id = (
        SELECT repair.repaired_scope_org_id
        FROM authorization_scope_repair_20260812 repair
        WHERE repair.grant_id = grant_row.id
    )
WHERE grant_row.id IN (SELECT grant_id FROM authorization_scope_repair_20260812);

UPDATE users member
SET authorization_version = authorization_version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE member.id IN (SELECT membership_id FROM authorization_scope_repair_20260812);

--rollback UPDATE membership_role_grants grant_row SET data_scope = (SELECT repair.previous_data_scope FROM authorization_scope_repair_20260812 repair WHERE repair.grant_id = grant_row.id), scope_org_id = (SELECT repair.previous_scope_org_id FROM authorization_scope_repair_20260812 repair WHERE repair.grant_id = grant_row.id) WHERE grant_row.id IN (SELECT grant_id FROM authorization_scope_repair_20260812);
--rollback UPDATE users member SET authorization_version = authorization_version + 1, updated_at = CURRENT_TIMESTAMP WHERE member.id IN (SELECT membership_id FROM authorization_scope_repair_20260812);
--rollback DROP TABLE authorization_scope_repair_20260812;

--changeset e-report:2026-08-12-027-assert-root-and-grant-boundaries dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM tenants tenant LEFT JOIN org_units root_organization ON root_organization.id = tenant.root_org_unit_id AND root_organization.tenant_id = tenant.id WHERE tenant.root_org_unit_id IS NULL OR root_organization.id IS NULL
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM tenants tenant JOIN org_units root_organization ON root_organization.id = tenant.root_org_unit_id WHERE tenant.code = '${defaultTenantCode}' AND root_organization.code = '3506'
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM membership_role_grants grant_row JOIN users member ON member.id = grant_row.membership_id AND member.tenant_id = grant_row.tenant_id JOIN tenants tenant ON tenant.id = member.tenant_id WHERE tenant.code = '${defaultTenantCode}' AND grant_row.data_scope = 'TENANT_ALL' AND member.org_unit_id <> tenant.root_org_unit_id
--comment Refuse application startup if a tenant root is unresolved or a migrated non-root account remains tenant-wide.
SELECT 1;

--rollback SELECT 1;
