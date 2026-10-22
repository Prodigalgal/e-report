--liquibase formatted sql

--changeset e-report:2026-08-06-006-validate-global-usernames dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM (SELECT username FROM users GROUP BY username HAVING COUNT(*) > 1) duplicated_usernames
--comment Refuse the global-login cutover when one username resolves to multiple tenant accounts.
SELECT 1;

--rollback SELECT 1;

--changeset e-report:2026-08-06-006-global-username-mysql dbms:mysql
ALTER TABLE users DROP INDEX uk_users_tenant_username;
ALTER TABLE users ADD CONSTRAINT uk_users_username UNIQUE (username);

--rollback ALTER TABLE users DROP INDEX uk_users_username;
--rollback ALTER TABLE users ADD CONSTRAINT uk_users_tenant_username UNIQUE (tenant_id, username);

--changeset e-report:2026-08-06-006-global-username-h2 dbms:h2
ALTER TABLE users DROP CONSTRAINT uk_users_tenant_username;
ALTER TABLE users ADD CONSTRAINT uk_users_username UNIQUE (username);

--rollback ALTER TABLE users DROP CONSTRAINT uk_users_username;
--rollback ALTER TABLE users ADD CONSTRAINT uk_users_tenant_username UNIQUE (tenant_id, username);

--changeset e-report:2026-08-06-006-nanjing-ownership dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM org_units o JOIN tenants t ON t.id = o.tenant_id WHERE t.code = '${defaultTenantCode}' AND o.code = '350627'
--comment Move legacy ordinary accounts and all pre-cutover work orders into the Nanjing county organization.
UPDATE membership_role_grants
SET scope_org_id = (
    SELECT o.id
    FROM org_units o
    JOIN tenants t ON t.id = o.tenant_id
    WHERE t.code = '${defaultTenantCode}' AND o.code = '350627'
)
WHERE data_scope IN ('ORG', 'ORG_TREE', 'CUSTOM_ORGS')
  AND membership_id IN (
      SELECT u.id
      FROM users u
      JOIN tenants t ON t.id = u.tenant_id
      WHERE t.code = '${defaultTenantCode}'
        AND u.username NOT IN (
            'admin', 'xiangcheng', 'longwen', 'longhai', 'changtai', 'yunxiao', 'zhangpu',
            'zhaoan', 'dongshan', 'nanjing', 'pinghe', 'huaan'
        )
  );

UPDATE users
SET org_unit_id = (
    SELECT o.id
    FROM org_units o
    JOIN tenants t ON t.id = o.tenant_id
    WHERE t.code = '${defaultTenantCode}' AND o.code = '350627'
)
WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}')
  AND username NOT IN (
      'admin', 'xiangcheng', 'longwen', 'longhai', 'changtai', 'yunxiao', 'zhangpu',
      'zhaoan', 'dongshan', 'nanjing', 'pinghe', 'huaan'
  );

UPDATE work_order_assignments
SET responsible_org_id = (
    SELECT o.id
    FROM org_units o
    JOIN tenants t ON t.id = o.tenant_id
    WHERE t.code = '${defaultTenantCode}' AND o.code = '350627'
)
WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}');

UPDATE fault_reports
SET reporting_org_id = (
        SELECT o.id
        FROM org_units o
        JOIN tenants t ON t.id = o.tenant_id
        WHERE t.code = '${defaultTenantCode}' AND o.code = '350627'
    ),
    responsible_org_id = (
        SELECT o.id
        FROM org_units o
        JOIN tenants t ON t.id = o.tenant_id
        WHERE t.code = '${defaultTenantCode}' AND o.code = '350627'
    )
WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}');

--rollback UPDATE fault_reports SET reporting_org_id = (SELECT o.id FROM org_units o JOIN tenants t ON t.id = o.tenant_id WHERE t.code = '${defaultTenantCode}' AND o.code = 'ROOT'), responsible_org_id = (SELECT o.id FROM org_units o JOIN tenants t ON t.id = o.tenant_id WHERE t.code = '${defaultTenantCode}' AND o.code = 'ROOT') WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}');
--rollback UPDATE work_order_assignments SET responsible_org_id = (SELECT o.id FROM org_units o JOIN tenants t ON t.id = o.tenant_id WHERE t.code = '${defaultTenantCode}' AND o.code = 'ROOT') WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}');
--rollback UPDATE users SET org_unit_id = (SELECT o.id FROM org_units o JOIN tenants t ON t.id = o.tenant_id WHERE t.code = '${defaultTenantCode}' AND o.code = 'ROOT') WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}') AND username NOT IN ('admin', 'xiangcheng', 'longwen', 'longhai', 'changtai', 'yunxiao', 'zhangpu', 'zhaoan', 'dongshan', 'nanjing', 'pinghe', 'huaan');
--rollback UPDATE membership_role_grants SET scope_org_id = (SELECT o.id FROM org_units o JOIN tenants t ON t.id = o.tenant_id WHERE t.code = '${defaultTenantCode}' AND o.code = 'ROOT') WHERE data_scope IN ('ORG', 'ORG_TREE', 'CUSTOM_ORGS') AND membership_id IN (SELECT u.id FROM users u JOIN tenants t ON t.id = u.tenant_id WHERE t.code = '${defaultTenantCode}' AND u.username NOT IN ('admin', 'xiangcheng', 'longwen', 'longhai', 'changtai', 'yunxiao', 'zhangpu', 'zhaoan', 'dongshan', 'nanjing', 'pinghe', 'huaan'));
