--liquibase formatted sql

--changeset e-report:2026-08-19-048-system-app-release-center-mysql dbms:mysql
--comment Retire tenant-delegated app release grants and profiles; runtime access is derived exclusively from the SUPER_ADMINISTRATOR system identity.
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT CASE WHEN COUNT(*) <= 1 THEN 0 ELSE 1 END FROM users WHERE system_key = 'SUPER_ADMINISTRATOR'
UPDATE users member
SET member.authorization_version = member.authorization_version + 1
WHERE EXISTS (
    SELECT 1
    FROM permission_grants grant_row
    JOIN permissions permission_row ON permission_row.id = grant_row.permission_id
    WHERE grant_row.membership_id = member.id
      AND grant_row.status = 'ACTIVE'
      AND permission_row.name LIKE 'app-update:%'
);

UPDATE permission_grants grant_row
JOIN permissions permission_row ON permission_row.id = grant_row.permission_id
SET grant_row.status = 'REVOKED',
    grant_row.revoked_at = COALESCE(grant_row.revoked_at, CURRENT_TIMESTAMP(6)),
    grant_row.revoke_reason = '2.4.0 安装包发布中心调整为超级管理员系统身份专用'
WHERE grant_row.status = 'ACTIVE'
  AND permission_row.name LIKE 'app-update:%';

UPDATE authorization_assignments assignment_row
SET assignment_row.status = 'REVOKED',
    assignment_row.revoked_at = COALESCE(assignment_row.revoked_at, CURRENT_TIMESTAMP(6)),
    assignment_row.revoke_reason = '2.4.0 退役租户级安装包发布岗位'
WHERE assignment_row.status = 'ACTIVE'
  AND EXISTS (
      SELECT 1
      FROM permission_grants grant_row
      JOIN permissions permission_row ON permission_row.id = grant_row.permission_id
      WHERE grant_row.assignment_id = assignment_row.id
        AND permission_row.name LIKE 'app-update:%'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM permission_grants active_grant
      WHERE active_grant.assignment_id = assignment_row.id
        AND active_grant.status = 'ACTIVE'
  );

UPDATE authorization_profiles
SET active = FALSE,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE system_key IN (
    'PROFILE_RELEASE_VIEWER',
    'PROFILE_RELEASE_PUBLISHER',
    'PROFILE_RELEASE_POLICY_MANAGER',
    'PROFILE_RELEASE_STORAGE_MANAGER'
);
--rollback UPDATE authorization_profiles SET active = TRUE, updated_at = CURRENT_TIMESTAMP(6) WHERE system_key IN ('PROFILE_RELEASE_VIEWER','PROFILE_RELEASE_PUBLISHER','PROFILE_RELEASE_POLICY_MANAGER','PROFILE_RELEASE_STORAGE_MANAGER');

--changeset e-report:2026-08-19-048-system-app-release-center-mysql-verify dbms:mysql
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permission_grants grant_row JOIN permissions permission_row ON permission_row.id = grant_row.permission_id WHERE grant_row.status = 'ACTIVE' AND permission_row.name LIKE 'app-update:%'
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM authorization_profiles WHERE active = TRUE AND system_key IN ('PROFILE_RELEASE_VIEWER','PROFILE_RELEASE_PUBLISHER','PROFILE_RELEASE_POLICY_MANAGER','PROFILE_RELEASE_STORAGE_MANAGER')
SELECT 1;
--rollback SELECT 1;

--changeset e-report:2026-08-19-048-system-app-release-center-h2 dbms:h2
--comment Retire tenant-delegated app release grants and profiles; runtime access is derived exclusively from the SUPER_ADMINISTRATOR system identity.
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT CASE WHEN COUNT(*) <= 1 THEN 0 ELSE 1 END FROM users WHERE system_key = 'SUPER_ADMINISTRATOR'
UPDATE users member
SET authorization_version = authorization_version + 1
WHERE EXISTS (
    SELECT 1
    FROM permission_grants grant_row
    JOIN permissions permission_row ON permission_row.id = grant_row.permission_id
    WHERE grant_row.membership_id = member.id
      AND grant_row.status = 'ACTIVE'
      AND permission_row.name LIKE 'app-update:%'
);

UPDATE permission_grants grant_row
SET status = 'REVOKED',
    revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP),
    revoke_reason = '2.4.0 安装包发布中心调整为超级管理员系统身份专用'
WHERE status = 'ACTIVE'
  AND EXISTS (
      SELECT 1 FROM permissions permission_row
      WHERE permission_row.id = grant_row.permission_id
        AND permission_row.name LIKE 'app-update:%'
  );

UPDATE authorization_assignments assignment_row
SET status = 'REVOKED',
    revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP),
    revoke_reason = '2.4.0 退役租户级安装包发布岗位'
WHERE status = 'ACTIVE'
  AND EXISTS (
      SELECT 1
      FROM permission_grants grant_row
      JOIN permissions permission_row ON permission_row.id = grant_row.permission_id
      WHERE grant_row.assignment_id = assignment_row.id
        AND permission_row.name LIKE 'app-update:%'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM permission_grants active_grant
      WHERE active_grant.assignment_id = assignment_row.id
        AND active_grant.status = 'ACTIVE'
  );

UPDATE authorization_profiles
SET active = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE system_key IN (
    'PROFILE_RELEASE_VIEWER',
    'PROFILE_RELEASE_PUBLISHER',
    'PROFILE_RELEASE_POLICY_MANAGER',
    'PROFILE_RELEASE_STORAGE_MANAGER'
);
--rollback UPDATE authorization_profiles SET active = TRUE, updated_at = CURRENT_TIMESTAMP WHERE system_key IN ('PROFILE_RELEASE_VIEWER','PROFILE_RELEASE_PUBLISHER','PROFILE_RELEASE_POLICY_MANAGER','PROFILE_RELEASE_STORAGE_MANAGER');

--changeset e-report:2026-08-19-048-system-app-release-center-h2-verify dbms:h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permission_grants grant_row JOIN permissions permission_row ON permission_row.id = grant_row.permission_id WHERE grant_row.status = 'ACTIVE' AND permission_row.name LIKE 'app-update:%'
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM authorization_profiles WHERE active = TRUE AND system_key IN ('PROFILE_RELEASE_VIEWER','PROFILE_RELEASE_PUBLISHER','PROFILE_RELEASE_POLICY_MANAGER','PROFILE_RELEASE_STORAGE_MANAGER')
SELECT 1;
--rollback SELECT 1;
