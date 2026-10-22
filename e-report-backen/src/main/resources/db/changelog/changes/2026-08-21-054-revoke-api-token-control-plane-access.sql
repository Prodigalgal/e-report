--liquibase formatted sql

--changeset e-report:2026-08-21-054-revoke-api-token-control-plane-access-mysql dbms:mysql
--comment Revoke legacy API tokens containing permissions outside the V3 machine-safe work-order and dashboard boundary.
UPDATE api_tokens token
SET token.revoked_at = COALESCE(token.revoked_at, CURRENT_TIMESTAMP(6)),
    token.revoke_reason = COALESCE(token.revoke_reason, '3.0.0 安全升级：Token 含管理控制面权限')
WHERE token.revoked_at IS NULL
  AND EXISTS (
      SELECT 1
      FROM api_token_permissions token_permission
      WHERE token_permission.token_id = token.id
        AND token_permission.permission_name <> 'dashboard:statistics:view'
        AND token_permission.permission_name NOT LIKE 'work-order:%'
  );

--rollback empty

--changeset e-report:2026-08-21-054-revoke-api-token-control-plane-access-h2 dbms:h2
--comment H2 equivalent of V3 API token control-plane revocation.
UPDATE api_tokens token
SET token.revoked_at = COALESCE(token.revoked_at, CURRENT_TIMESTAMP),
    token.revoke_reason = COALESCE(token.revoke_reason, '3.0.0 安全升级：Token 含管理控制面权限')
WHERE token.revoked_at IS NULL
  AND EXISTS (
      SELECT 1
      FROM api_token_permissions token_permission
      WHERE token_permission.token_id = token.id
        AND token_permission.permission_name <> 'dashboard:statistics:view'
        AND token_permission.permission_name NOT LIKE 'work-order:%'
  );

--rollback empty
