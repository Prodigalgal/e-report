--liquibase formatted sql

--changeset e-report:2026-08-23-059-audit-default-administrator-pointer-mysql dbms:mysql
CREATE TABLE organization_default_administrator_retirement_audit_20260823 (
    organization_id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    administrator_membership_id BINARY(16) NOT NULL,
    retired_at DATETIME(6) NOT NULL,
    migration_actor VARCHAR(100) NOT NULL,
    PRIMARY KEY (organization_id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
INSERT INTO organization_default_administrator_retirement_audit_20260823
(organization_id, tenant_id, administrator_membership_id, retired_at, migration_actor)
SELECT id, tenant_id, default_administrator_user_id, CURRENT_TIMESTAMP(6),
       'LIQUIBASE:2026-08-23-059'
FROM org_units
WHERE default_administrator_user_id IS NOT NULL;
--rollback DROP TABLE organization_default_administrator_retirement_audit_20260823;

--changeset e-report:2026-08-23-059-audit-default-administrator-pointer-h2 dbms:h2
CREATE TABLE organization_default_administrator_retirement_audit_20260823 (
    organization_id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    administrator_membership_id UUID NOT NULL,
    retired_at TIMESTAMP(6) NOT NULL,
    migration_actor VARCHAR(100) NOT NULL
);
INSERT INTO organization_default_administrator_retirement_audit_20260823
(organization_id, tenant_id, administrator_membership_id, retired_at, migration_actor)
SELECT id, tenant_id, default_administrator_user_id, CURRENT_TIMESTAMP,
       'LIQUIBASE:2026-08-23-059'
FROM org_units
WHERE default_administrator_user_id IS NOT NULL;
--rollback DROP TABLE organization_default_administrator_retirement_audit_20260823;

--changeset e-report:2026-08-23-059-retire-default-administrator-permission dbms:mysql,h2
DELETE FROM api_token_permissions
WHERE permission_name = 'organization:default-administrator:update';
DELETE FROM authorization_profile_permission_rules
WHERE permission_id IN (
    SELECT id FROM permissions WHERE name = 'organization:default-administrator:update'
);
DELETE FROM permission_grants
WHERE permission_id IN (
    SELECT id FROM permissions WHERE name = 'organization:default-administrator:update'
);
DELETE FROM permissions
WHERE name = 'organization:default-administrator:update';
--rollback SELECT 1;

--changeset e-report:2026-08-23-059-drop-default-administrator-pointer-mysql dbms:mysql
ALTER TABLE org_units DROP FOREIGN KEY fk_org_units_default_administrator;
DROP INDEX uk_org_units_default_administrator ON org_units;
ALTER TABLE org_units DROP COLUMN default_administrator_user_id;
--rollback SELECT 1;

--changeset e-report:2026-08-23-059-drop-default-administrator-pointer-h2 dbms:h2
ALTER TABLE org_units DROP CONSTRAINT IF EXISTS fk_org_units_default_administrator;
DROP INDEX IF EXISTS uk_org_units_default_administrator;
ALTER TABLE org_units DROP COLUMN IF EXISTS default_administrator_user_id;
--rollback SELECT 1;

--changeset e-report:2026-08-23-059-default-administrator-final-assert dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permissions WHERE name = 'organization:default-administrator:update'
SELECT 1;
--rollback SELECT 1;
