--liquibase formatted sql

--changeset e-report:2026-08-19-049-system-app-release-delegation-mysql dbms:mysql
--comment Clarify the audit reason after system-scoped app release permissions became explicitly and transitively delegable under the standard authorization boundary.
UPDATE permission_grants grant_row
JOIN permissions permission_row ON permission_row.id = grant_row.permission_id
SET grant_row.revoke_reason = '2.4.1 退役历史租户发布岗位；系统级发布权限改由授权界面显式委派'
WHERE grant_row.status = 'REVOKED'
  AND permission_row.name LIKE 'app-update:%'
  AND grant_row.revoke_reason = '2.4.0 安装包发布中心调整为超级管理员系统身份专用';

UPDATE authorization_assignments
SET revoke_reason = '2.4.1 退役历史租户发布岗位；系统级发布权限改由授权界面显式委派'
WHERE status = 'REVOKED'
  AND revoke_reason = '2.4.0 退役租户级安装包发布岗位';
--rollback UPDATE permission_grants grant_row JOIN permissions permission_row ON permission_row.id = grant_row.permission_id SET grant_row.revoke_reason = '2.4.0 安装包发布中心调整为超级管理员系统身份专用' WHERE grant_row.status = 'REVOKED' AND permission_row.name LIKE 'app-update:%' AND grant_row.revoke_reason = '2.4.1 退役历史租户发布岗位；系统级发布权限改由授权界面显式委派';

--changeset e-report:2026-08-19-049-system-app-release-delegation-mysql-verify dbms:mysql
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permission_grants grant_row JOIN permissions permission_row ON permission_row.id = grant_row.permission_id WHERE grant_row.status = 'REVOKED' AND permission_row.name LIKE 'app-update:%' AND grant_row.revoke_reason = '2.4.0 安装包发布中心调整为超级管理员系统身份专用'
SELECT 1;
--rollback SELECT 1;

--changeset e-report:2026-08-19-049-system-app-release-delegation-h2 dbms:h2
--comment Clarify the audit reason after system-scoped app release permissions became explicitly and transitively delegable under the standard authorization boundary.
UPDATE permission_grants grant_row
SET revoke_reason = '2.4.1 退役历史租户发布岗位；系统级发布权限改由授权界面显式委派'
WHERE status = 'REVOKED'
  AND revoke_reason = '2.4.0 安装包发布中心调整为超级管理员系统身份专用'
  AND EXISTS (
      SELECT 1 FROM permissions permission_row
      WHERE permission_row.id = grant_row.permission_id
        AND permission_row.name LIKE 'app-update:%'
  );

UPDATE authorization_assignments
SET revoke_reason = '2.4.1 退役历史租户发布岗位；系统级发布权限改由授权界面显式委派'
WHERE status = 'REVOKED'
  AND revoke_reason = '2.4.0 退役租户级安装包发布岗位';
--rollback UPDATE permission_grants grant_row SET revoke_reason = '2.4.0 安装包发布中心调整为超级管理员系统身份专用' WHERE status = 'REVOKED' AND revoke_reason = '2.4.1 退役历史租户发布岗位；系统级发布权限改由授权界面显式委派' AND EXISTS (SELECT 1 FROM permissions permission_row WHERE permission_row.id = grant_row.permission_id AND permission_row.name LIKE 'app-update:%');

--changeset e-report:2026-08-19-049-system-app-release-delegation-h2-verify dbms:h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permission_grants grant_row JOIN permissions permission_row ON permission_row.id = grant_row.permission_id WHERE grant_row.status = 'REVOKED' AND permission_row.name LIKE 'app-update:%' AND grant_row.revoke_reason = '2.4.0 安装包发布中心调整为超级管理员系统身份专用'
SELECT 1;
--rollback SELECT 1;
