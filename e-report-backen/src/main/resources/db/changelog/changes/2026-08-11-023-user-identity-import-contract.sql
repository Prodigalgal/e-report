--liquibase formatted sql

--changeset e-report:2026-08-11-023-normalize-unique-user-contacts dbms:mysql,h2
--comment Normalize optional contacts before enforcing global uniqueness for user import and profile maintenance.
UPDATE users SET phone = NULL WHERE phone IS NOT NULL AND TRIM(phone) = '';
UPDATE users SET email = NULL WHERE email IS NOT NULL AND TRIM(email) = '';
UPDATE users SET phone = TRIM(phone) WHERE phone IS NOT NULL;
UPDATE users SET email = LOWER(TRIM(email)) WHERE email IS NOT NULL;

--rollback SELECT 1;

--changeset e-report:2026-08-11-023-assert-unique-user-contacts dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM (SELECT phone FROM users WHERE phone IS NOT NULL GROUP BY phone HAVING COUNT(*) > 1) duplicate_phones
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM (SELECT email FROM users WHERE email IS NOT NULL GROUP BY email HAVING COUNT(*) > 1) duplicate_emails
--comment Refuse the release instead of choosing a survivor for duplicated production contacts.
SELECT 1;

--rollback SELECT 1;

--changeset e-report:2026-08-11-023-enforce-unique-user-contacts dbms:mysql,h2
--comment A user can omit a historical contact, but every non-null phone and email is globally unique.
ALTER TABLE users ADD CONSTRAINT uk_users_phone UNIQUE (phone);
ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE (email);

--rollback ALTER TABLE users DROP CONSTRAINT uk_users_email;
--rollback ALTER TABLE users DROP CONSTRAINT uk_users_phone;

--changeset e-report:2026-08-11-023-lowercase-county-default-passwords dbms:mysql,h2
--validCheckSum: 8:28ab4c1ac09544a8f9ab108fbae57abb
--validCheckSum: 9:96ad16f6ce78d71e164c9285f58057e2
--comment Explicitly reset all eleven Zhangzhou county default administrators to the approved lowercase-initial password rule.
UPDATE users
SET password = CASE username
    WHEN 'xiangcheng' THEN '$2b$10$HgrbE4zcI3aHSxBcc5Kh9euUQ1sPP4MXAzlQh5hcFABkO2tJOkFfG'
    WHEN 'longwen' THEN '$2b$10$Wn4jgpstxIvFHP1Sfqu8zOrJm/uRgSCfWfuzbe4XAfLinOCjH35mC'
    WHEN 'longhai' THEN '$2b$10$ZeNRYM.NtB5gD7kYRMB/k..ksyfIfEVQvZMpIUVonjYIJT3mCr2rK'
    WHEN 'changtai' THEN '$2b$10$w.i2eL2Yl0I8n8glZPeeUef7D.qnxLRwCwCgu.yHunEe.TVb0mchC'
    WHEN 'yunxiao' THEN '$2b$10$ty/XyPuodbHBo4a6OaUiRevs4I4a0Qf4B.eq33vuh/SAQPmpHkeHe'
    WHEN 'zhangpu' THEN '$2b$10$yJi4UfaG1HvNX1Nc3fkP8eS8wqh5xsSJ0cT99wpfSoBmKTRbvvLIe'
    WHEN 'zhaoan' THEN '$2b$10$QCOjDkfnC.pECvNdLqnAvOLtjXP1QbUnBwfGcvuysStmpRUf3VPUO'
    WHEN 'dongshan' THEN '$2b$10$cbzLIGDF.FXxeKY77WUeDOmuZq4xZH8/nHLE1M3JVI4e59ATLAjr6'
    WHEN 'nanjing' THEN '$2b$10$qKbGMc6ypBW0kFiTwCU5i.BcC1OibJVFHfHIPbizktBTCWrG9BHoS'
    WHEN 'pinghe' THEN '$2b$10$ob5NjzWFg2rh6Q.KvKMsHePvsqh.5iB0.yPoHUI1NrDPOZf31ygOK'
    WHEN 'huaan' THEN '$2b$10$cg7L9oR9qrkXvTW6qehjkuVaIklfP3yGBYPNypcU9ajQLulvT/CR6'
    ELSE password
END,
authorization_version = authorization_version + 1,
updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = (SELECT id FROM tenants WHERE code = '${defaultTenantCode}')
  AND username IN ('xiangcheng','longwen','longhai','changtai','yunxiao','zhangpu','zhaoan','dongshan','nanjing','pinghe','huaan');

--rollback SELECT 1;

--changeset e-report:2026-08-11-023-notification-recipient-roles-mysql dbms:mysql
--comment Replace empty administrator markers with explicit tenant/county notification recipient capabilities.
INSERT INTO permissions (id, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), seed.name, seed.display_name, seed.description
FROM (
    SELECT 'notification:recipient:county' name, '区县通知接收' display_name,
           '接收所属区县工单通知和未闭环工单日报' description
    UNION ALL
    SELECT 'notification:recipient:tenant', '市公司通知接收',
           '接收无法归属区县的市公司级工单通知'
) seed
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = seed.name);

INSERT INTO roles (id, tenant_id, built_in, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), tenant.id, TRUE, seed.name, seed.display_name, seed.description
FROM tenants tenant
CROSS JOIN (
    SELECT 'ROLE_COUNTY_NOTIFICATION_RECIPIENT' name, '区县通知收件人' display_name,
           '接收所属区县工单通知和未闭环工单日报。' description
    UNION ALL
    SELECT 'ROLE_TENANT_NOTIFICATION_RECIPIENT', '市公司通知收件人',
           '接收无法归属区县的市公司级工单通知。'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM roles existing WHERE existing.tenant_id = tenant.id AND existing.name = seed.name
);

INSERT INTO role_permissions (role_id, permission_id, scope_profile)
SELECT role_row.id, permission_row.id, 'GRANT_SCOPE'
FROM roles role_row
JOIN permissions permission_row
  ON (role_row.name = 'ROLE_COUNTY_NOTIFICATION_RECIPIENT' AND permission_row.name = 'notification:recipient:county')
  OR (role_row.name = 'ROLE_TENANT_NOTIFICATION_RECIPIENT' AND permission_row.name = 'notification:recipient:tenant')
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions existing
    WHERE existing.role_id = role_row.id AND existing.permission_id = permission_row.id
);

INSERT INTO user_roles (user_id, role_id)
SELECT DISTINCT assignment.user_id, target_role.id
FROM user_roles assignment
JOIN roles marker ON marker.id = assignment.role_id
JOIN roles target_role
  ON target_role.tenant_id = marker.tenant_id
 AND target_role.name = CASE marker.name
     WHEN 'ROLE_COUNTY_ADMIN' THEN 'ROLE_COUNTY_NOTIFICATION_RECIPIENT'
     WHEN 'ROLE_ADMIN' THEN 'ROLE_TENANT_NOTIFICATION_RECIPIENT'
 END
WHERE marker.name IN ('ROLE_COUNTY_ADMIN', 'ROLE_ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM user_roles existing
      WHERE existing.user_id = assignment.user_id AND existing.role_id = target_role.id
  );

INSERT INTO membership_role_grants (
    id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at
)
SELECT UUID_TO_BIN(UUID(), 1), source_grant.tenant_id, source_grant.membership_id,
       target_role.id, source_grant.data_scope, source_grant.scope_org_id,
       source_grant.granted_at, source_grant.expires_at
FROM membership_role_grants source_grant
JOIN roles marker ON marker.id = source_grant.role_id
JOIN roles target_role
  ON target_role.tenant_id = marker.tenant_id
 AND target_role.name = CASE marker.name
     WHEN 'ROLE_COUNTY_ADMIN' THEN 'ROLE_COUNTY_NOTIFICATION_RECIPIENT'
     WHEN 'ROLE_ADMIN' THEN 'ROLE_TENANT_NOTIFICATION_RECIPIENT'
 END
WHERE marker.name IN ('ROLE_COUNTY_ADMIN', 'ROLE_ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM membership_role_grants existing
      WHERE existing.tenant_id = source_grant.tenant_id
        AND existing.membership_id = source_grant.membership_id
        AND existing.role_id = target_role.id
  );

--rollback SELECT 1;

--changeset e-report:2026-08-11-023-notification-recipient-roles-h2 dbms:h2
--comment H2 equivalent for explicit tenant/county notification recipient capabilities.
INSERT INTO permissions (id, name, display_name, description)
SELECT RANDOM_UUID(), seed.name, seed.display_name, seed.description
FROM (
    SELECT 'notification:recipient:county' name, '区县通知接收' display_name,
           '接收所属区县工单通知和未闭环工单日报' description
    UNION ALL
    SELECT 'notification:recipient:tenant', '市公司通知接收',
           '接收无法归属区县的市公司级工单通知'
) seed
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = seed.name);

INSERT INTO roles (id, tenant_id, built_in, name, display_name, description)
SELECT RANDOM_UUID(), tenant.id, TRUE, seed.name, seed.display_name, seed.description
FROM tenants tenant
CROSS JOIN (
    SELECT 'ROLE_COUNTY_NOTIFICATION_RECIPIENT' name, '区县通知收件人' display_name,
           '接收所属区县工单通知和未闭环工单日报。' description
    UNION ALL
    SELECT 'ROLE_TENANT_NOTIFICATION_RECIPIENT', '市公司通知收件人',
           '接收无法归属区县的市公司级工单通知。'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM roles existing WHERE existing.tenant_id = tenant.id AND existing.name = seed.name
);

INSERT INTO role_permissions (role_id, permission_id, scope_profile)
SELECT role_row.id, permission_row.id, 'GRANT_SCOPE'
FROM roles role_row
JOIN permissions permission_row
  ON (role_row.name = 'ROLE_COUNTY_NOTIFICATION_RECIPIENT' AND permission_row.name = 'notification:recipient:county')
  OR (role_row.name = 'ROLE_TENANT_NOTIFICATION_RECIPIENT' AND permission_row.name = 'notification:recipient:tenant')
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions existing
    WHERE existing.role_id = role_row.id AND existing.permission_id = permission_row.id
);

INSERT INTO user_roles (user_id, role_id)
SELECT DISTINCT assignment.user_id, target_role.id
FROM user_roles assignment
JOIN roles marker ON marker.id = assignment.role_id
JOIN roles target_role
  ON target_role.tenant_id = marker.tenant_id
 AND target_role.name = CASE marker.name
     WHEN 'ROLE_COUNTY_ADMIN' THEN 'ROLE_COUNTY_NOTIFICATION_RECIPIENT'
     WHEN 'ROLE_ADMIN' THEN 'ROLE_TENANT_NOTIFICATION_RECIPIENT'
 END
WHERE marker.name IN ('ROLE_COUNTY_ADMIN', 'ROLE_ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM user_roles existing
      WHERE existing.user_id = assignment.user_id AND existing.role_id = target_role.id
  );

INSERT INTO membership_role_grants (
    id, tenant_id, membership_id, role_id, data_scope, scope_org_id, granted_at, expires_at
)
SELECT RANDOM_UUID(), source_grant.tenant_id, source_grant.membership_id,
       target_role.id, source_grant.data_scope, source_grant.scope_org_id,
       source_grant.granted_at, source_grant.expires_at
FROM membership_role_grants source_grant
JOIN roles marker ON marker.id = source_grant.role_id
JOIN roles target_role
  ON target_role.tenant_id = marker.tenant_id
 AND target_role.name = CASE marker.name
     WHEN 'ROLE_COUNTY_ADMIN' THEN 'ROLE_COUNTY_NOTIFICATION_RECIPIENT'
     WHEN 'ROLE_ADMIN' THEN 'ROLE_TENANT_NOTIFICATION_RECIPIENT'
 END
WHERE marker.name IN ('ROLE_COUNTY_ADMIN', 'ROLE_ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM membership_role_grants existing
      WHERE existing.tenant_id = source_grant.tenant_id
        AND existing.membership_id = source_grant.membership_id
        AND existing.role_id = target_role.id
  );

--rollback SELECT 1;

--changeset e-report:2026-08-11-023-retire-empty-identity-roles-mysql dbms:mysql
--comment Invalidate affected sessions and permanently remove the three permissionless administrator identity roles.
UPDATE users member
JOIN user_roles assignment ON assignment.user_id = member.id
JOIN roles marker ON marker.id = assignment.role_id
SET member.authorization_version = member.authorization_version + 1
WHERE marker.name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN');

DELETE bundle_role FROM role_bundle_roles bundle_role
JOIN roles marker ON marker.id = bundle_role.role_id
WHERE marker.name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN');
DELETE grant_row FROM membership_role_grants grant_row
JOIN roles marker ON marker.id = grant_row.role_id
WHERE marker.name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN');
DELETE assignment FROM user_roles assignment
JOIN roles marker ON marker.id = assignment.role_id
WHERE marker.name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN');
DELETE rule_row FROM role_permissions rule_row
JOIN roles marker ON marker.id = rule_row.role_id
WHERE marker.name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN');
DELETE FROM roles WHERE name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN');

--rollback SELECT 1;

--changeset e-report:2026-08-11-023-retire-empty-identity-roles-h2 dbms:h2
--comment H2 equivalent for permanently removing permissionless administrator identity roles.
UPDATE users member
SET authorization_version = authorization_version + 1
WHERE EXISTS (
    SELECT 1 FROM user_roles assignment
    JOIN roles marker ON marker.id = assignment.role_id
    WHERE assignment.user_id = member.id
      AND marker.name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN')
);
DELETE FROM role_bundle_roles WHERE role_id IN (SELECT id FROM roles WHERE name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN'));
DELETE FROM membership_role_grants WHERE role_id IN (SELECT id FROM roles WHERE name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN'));
DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN'));
DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN'));
DELETE FROM roles WHERE name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN');

--rollback SELECT 1;

--changeset e-report:2026-08-11-023-assert-role-retirement dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM roles WHERE name IN ('ROLE_ADMIN', 'ROLE_COUNTY_ADMIN', 'ROLE_PLATFORM_ADMIN')
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM users u JOIN tenants t ON t.id = u.tenant_id WHERE t.code = '${defaultTenantCode}' AND u.username IN ('xiangcheng','longwen','longhai','changtai','yunxiao','zhangpu','zhaoan','dongshan','nanjing','pinghe','huaan') AND u.password IS NULL
--comment Refuse application startup when identity role cleanup or password reset did not complete.
SELECT 1;

--rollback SELECT 1;
