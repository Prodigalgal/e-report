--liquibase formatted sql

--changeset e-report:2026-08-20-051-api-token-management-permissions-mysql dbms:mysql
--comment Register explicit self-service API Token management permissions before built-in profile synchronization.
INSERT INTO permissions (id, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), candidate.name, candidate.display_name, candidate.description
FROM (
    SELECT 'api-token:view' AS name, 'API Token 查看' AS display_name,
           '查看本人创建的 API Token 及其状态' AS description
    UNION ALL
    SELECT 'api-token:create', 'API Token 创建',
           '为本人创建不超过当前有效权限的 API Token'
    UNION ALL
    SELECT 'api-token:revoke', 'API Token 撤销',
           '撤销本人创建的 API Token'
) candidate
WHERE NOT EXISTS (
    SELECT 1 FROM permissions existing WHERE existing.name = candidate.name
);

--rollback SELECT 1;

--changeset e-report:2026-08-20-051-api-token-management-permissions-h2 dbms:h2
--comment H2 equivalent for API Token management permission migration verification.
INSERT INTO permissions (id, name, display_name, description)
SELECT RANDOM_UUID(), candidate.name, candidate.display_name, candidate.description
FROM (
    SELECT 'api-token:view' AS name, 'API Token 查看' AS display_name,
           '查看本人创建的 API Token 及其状态' AS description
    UNION ALL
    SELECT 'api-token:create', 'API Token 创建',
           '为本人创建不超过当前有效权限的 API Token'
    UNION ALL
    SELECT 'api-token:revoke', 'API Token 撤销',
           '撤销本人创建的 API Token'
) candidate
WHERE NOT EXISTS (
    SELECT 1 FROM permissions existing WHERE existing.name = candidate.name
);

--rollback SELECT 1;

--changeset e-report:2026-08-20-051-api-token-management-permissions-assert dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:3 SELECT COUNT(*) FROM permissions WHERE name IN ('api-token:view', 'api-token:create', 'api-token:revoke')
--comment Refuse application startup when any API Token management permission is missing.
SELECT 1;

--rollback SELECT 1;
