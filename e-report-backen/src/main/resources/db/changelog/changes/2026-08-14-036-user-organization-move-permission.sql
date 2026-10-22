--liquibase formatted sql

--changeset e-report:2026-08-14-036-user-organization-move-permission-mysql dbms:mysql
--comment Split user organization migration from ordinary profile editing and preserve historical administrator capability deterministically.
INSERT INTO permissions (id, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), 'user:manage:organization:move', '用户所属组织调整',
       '将用户迁移到另一个授权组织，并显式处理既有授权范围'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'user:manage:organization:move'
);

INSERT INTO permission_grants (
    id, tenant_id, membership_id, assignment_id, profile_rule_id, permission_id,
    data_scope_id, relation_constraint, valid_from, expires_at, status, customized,
    reason, granted_by_membership_id, granted_at, revoked_by_membership_id,
    revoked_at, revoke_reason
)
SELECT UUID_TO_BIN(UUID(), 1), source_grant.tenant_id, source_grant.membership_id,
       source_grant.assignment_id, NULL, target_permission.id, source_grant.data_scope_id,
       source_grant.relation_constraint, source_grant.valid_from, source_grant.expires_at,
       source_grant.status, source_grant.customized,
       '由历史用户资料修改权限拆分：用户所属组织调整',
       source_grant.granted_by_membership_id, source_grant.granted_at,
       source_grant.revoked_by_membership_id, source_grant.revoked_at, source_grant.revoke_reason
FROM permission_grants source_grant
JOIN permissions source_permission ON source_permission.id = source_grant.permission_id
JOIN permissions target_permission ON target_permission.name = 'user:manage:organization:move'
WHERE source_permission.name = 'user:manage:details:update'
  AND NOT EXISTS (
      SELECT 1 FROM permission_grants existing
      WHERE existing.assignment_id = source_grant.assignment_id
        AND existing.permission_id = target_permission.id
        AND existing.data_scope_id = source_grant.data_scope_id
        AND existing.relation_constraint = source_grant.relation_constraint
        AND existing.status = source_grant.status
  );

INSERT INTO authorization_v2_permission_code_map_20260814 (
    source_permission_id, target_permission_id, source_permission_name,
    target_permission_name, migration_reason
)
SELECT source_permission.id, target_permission.id, source_permission.name,
       target_permission.name, '从普通资料修改中拆分用户所属组织调整'
FROM permissions source_permission
JOIN permissions target_permission ON target_permission.name = 'user:manage:organization:move'
WHERE source_permission.name = 'user:manage:details:update'
  AND NOT EXISTS (
      SELECT 1 FROM authorization_v2_permission_code_map_20260814 existing
      WHERE existing.source_permission_id = source_permission.id
        AND existing.target_permission_id = target_permission.id
  );

--rollback DELETE grant_row FROM permission_grants grant_row JOIN permissions permission_row ON permission_row.id = grant_row.permission_id WHERE permission_row.name = 'user:manage:organization:move';
--rollback DELETE map_row FROM authorization_v2_permission_code_map_20260814 map_row JOIN permissions permission_row ON permission_row.id = map_row.target_permission_id WHERE permission_row.name = 'user:manage:organization:move';
--rollback DELETE FROM permissions WHERE name = 'user:manage:organization:move';

--changeset e-report:2026-08-14-036-user-organization-move-permission-h2 dbms:h2
--comment H2 equivalent of the user organization migration atomic permission split.
INSERT INTO permissions (id, name, display_name, description)
SELECT RANDOM_UUID(), 'user:manage:organization:move', '用户所属组织调整',
       '将用户迁移到另一个授权组织，并显式处理既有授权范围'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'user:manage:organization:move'
);

INSERT INTO permission_grants (
    id, tenant_id, membership_id, assignment_id, profile_rule_id, permission_id,
    data_scope_id, relation_constraint, valid_from, expires_at, status, customized,
    reason, granted_by_membership_id, granted_at, revoked_by_membership_id,
    revoked_at, revoke_reason
)
SELECT RANDOM_UUID(), source_grant.tenant_id, source_grant.membership_id,
       source_grant.assignment_id, NULL, target_permission.id, source_grant.data_scope_id,
       source_grant.relation_constraint, source_grant.valid_from, source_grant.expires_at,
       source_grant.status, source_grant.customized,
       '由历史用户资料修改权限拆分：用户所属组织调整',
       source_grant.granted_by_membership_id, source_grant.granted_at,
       source_grant.revoked_by_membership_id, source_grant.revoked_at, source_grant.revoke_reason
FROM permission_grants source_grant
JOIN permissions source_permission ON source_permission.id = source_grant.permission_id
JOIN permissions target_permission ON target_permission.name = 'user:manage:organization:move'
WHERE source_permission.name = 'user:manage:details:update'
  AND NOT EXISTS (
      SELECT 1 FROM permission_grants existing
      WHERE existing.assignment_id = source_grant.assignment_id
        AND existing.permission_id = target_permission.id
        AND existing.data_scope_id = source_grant.data_scope_id
        AND existing.relation_constraint = source_grant.relation_constraint
        AND existing.status = source_grant.status
  );

INSERT INTO authorization_v2_permission_code_map_20260814 (
    source_permission_id, target_permission_id, source_permission_name,
    target_permission_name, migration_reason
)
SELECT source_permission.id, target_permission.id, source_permission.name,
       target_permission.name, '从普通资料修改中拆分用户所属组织调整'
FROM permissions source_permission
JOIN permissions target_permission ON target_permission.name = 'user:manage:organization:move'
WHERE source_permission.name = 'user:manage:details:update'
  AND NOT EXISTS (
      SELECT 1 FROM authorization_v2_permission_code_map_20260814 existing
      WHERE existing.source_permission_id = source_permission.id
        AND existing.target_permission_id = target_permission.id
  );

--rollback DELETE FROM permission_grants WHERE permission_id = (SELECT id FROM permissions WHERE name = 'user:manage:organization:move');
--rollback DELETE FROM authorization_v2_permission_code_map_20260814 WHERE target_permission_name = 'user:manage:organization:move';
--rollback DELETE FROM permissions WHERE name = 'user:manage:organization:move';
