--liquibase formatted sql

--changeset e-report:2026-08-14-037-self-verification-permissions-mysql dbms:mysql
--comment Add explicit self-verification capabilities and migrate historical controller grants without role-name runtime checks.
INSERT INTO permissions (id, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), seed.name, seed.display_name, seed.description
FROM (
    SELECT CONCAT('work-order:', type_seed.slug, ':verification:self-approve') name,
           CONCAT(type_seed.label, '本人回单验收通过') display_name,
           CONCAT('允许管控人员验收通过本人提交的', type_seed.label, '回单') description
    FROM (
        SELECT 'network-fault' slug, '网络故障工单' label
        UNION ALL SELECT 'box-door-open', '箱门未关工单'
        UNION ALL SELECT 'damaged-box-replacement', '破损箱体更换工单'
    ) type_seed
    UNION ALL
    SELECT CONCAT('work-order:', type_seed.slug, ':verification:self-reject'),
           CONCAT(type_seed.label, '本人回单验收驳回'),
           CONCAT('允许管控人员验收驳回本人提交的', type_seed.label, '回单')
    FROM (
        SELECT 'network-fault' slug, '网络故障工单' label
        UNION ALL SELECT 'box-door-open', '箱门未关工单'
        UNION ALL SELECT 'damaged-box-replacement', '破损箱体更换工单'
    ) type_seed
) seed
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = seed.name);

INSERT INTO authorization_v2_permission_code_map_20260814 (
    source_permission_id, target_permission_id, source_permission_name,
    target_permission_name, migration_reason
)
SELECT source_permission.id, target_permission.id, source_permission.name,
       target_permission.name, '历史管控取消能力拆分出本人回单验收能力'
FROM permissions source_permission
JOIN permissions target_permission ON target_permission.name IN (
    REPLACE(source_permission.name, ':cancel', ':verification:self-approve'),
    REPLACE(source_permission.name, ':cancel', ':verification:self-reject')
)
WHERE source_permission.name LIKE 'work-order:%:cancel'
  AND NOT EXISTS (
      SELECT 1 FROM authorization_v2_permission_code_map_20260814 existing
      WHERE existing.source_permission_id = source_permission.id
        AND existing.target_permission_id = target_permission.id
  );

INSERT INTO permission_grants (
    id, tenant_id, membership_id, assignment_id, profile_rule_id, permission_id,
    data_scope_id, relation_constraint, valid_from, expires_at, status, customized,
    reason, granted_by_membership_id, granted_at, revoked_by_membership_id,
    revoked_at, revoke_reason
)
SELECT UUID_TO_BIN(UUID(), 1), source_grant.tenant_id, source_grant.membership_id,
       source_grant.assignment_id, NULL, target_permission.id, source_grant.data_scope_id,
       'RESPONSE_SUBMITTER', source_grant.valid_from, source_grant.expires_at,
       source_grant.status, source_grant.customized,
       '由历史工单管控权限拆分：本人回单验收',
       source_grant.granted_by_membership_id, source_grant.granted_at,
       source_grant.revoked_by_membership_id, source_grant.revoked_at, source_grant.revoke_reason
FROM permission_grants source_grant
JOIN permissions source_permission ON source_permission.id = source_grant.permission_id
JOIN permissions target_permission ON target_permission.name IN (
    REPLACE(source_permission.name, ':cancel', ':verification:self-approve'),
    REPLACE(source_permission.name, ':cancel', ':verification:self-reject')
)
WHERE source_permission.name LIKE 'work-order:%:cancel'
  AND NOT EXISTS (
      SELECT 1 FROM permission_grants existing
      WHERE existing.assignment_id = source_grant.assignment_id
        AND existing.permission_id = target_permission.id
        AND existing.status = source_grant.status
  );

--rollback DELETE grant_row FROM permission_grants grant_row JOIN permissions permission_row ON permission_row.id = grant_row.permission_id WHERE permission_row.name LIKE 'work-order:%:verification:self-%';
--rollback DELETE FROM authorization_v2_permission_code_map_20260814 WHERE target_permission_name LIKE 'work-order:%:verification:self-%';
--rollback DELETE FROM permissions WHERE name LIKE 'work-order:%:verification:self-%';

--changeset e-report:2026-08-14-037-self-verification-permissions-h2 dbms:h2
--comment H2 equivalent of explicit self-verification capability migration.
INSERT INTO permissions (id, name, display_name, description)
SELECT RANDOM_UUID(), seed.name, seed.display_name, seed.description
FROM (
    SELECT CONCAT('work-order:', type_seed.slug, ':verification:self-approve') name,
           CONCAT(type_seed.label, '本人回单验收通过') display_name,
           CONCAT('允许管控人员验收通过本人提交的', type_seed.label, '回单') description
    FROM (
        SELECT 'network-fault' slug, '网络故障工单' label
        UNION ALL SELECT 'box-door-open', '箱门未关工单'
        UNION ALL SELECT 'damaged-box-replacement', '破损箱体更换工单'
    ) type_seed
    UNION ALL
    SELECT CONCAT('work-order:', type_seed.slug, ':verification:self-reject'),
           CONCAT(type_seed.label, '本人回单验收驳回'),
           CONCAT('允许管控人员验收驳回本人提交的', type_seed.label, '回单')
    FROM (
        SELECT 'network-fault' slug, '网络故障工单' label
        UNION ALL SELECT 'box-door-open', '箱门未关工单'
        UNION ALL SELECT 'damaged-box-replacement', '破损箱体更换工单'
    ) type_seed
) seed
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = seed.name);

INSERT INTO authorization_v2_permission_code_map_20260814 (
    source_permission_id, target_permission_id, source_permission_name,
    target_permission_name, migration_reason
)
SELECT source_permission.id, target_permission.id, source_permission.name,
       target_permission.name, '历史管控取消能力拆分出本人回单验收能力'
FROM permissions source_permission
JOIN permissions target_permission ON target_permission.name IN (
    REPLACE(source_permission.name, ':cancel', ':verification:self-approve'),
    REPLACE(source_permission.name, ':cancel', ':verification:self-reject')
)
WHERE source_permission.name LIKE 'work-order:%:cancel'
  AND NOT EXISTS (
      SELECT 1 FROM authorization_v2_permission_code_map_20260814 existing
      WHERE existing.source_permission_id = source_permission.id
        AND existing.target_permission_id = target_permission.id
  );

INSERT INTO permission_grants (
    id, tenant_id, membership_id, assignment_id, profile_rule_id, permission_id,
    data_scope_id, relation_constraint, valid_from, expires_at, status, customized,
    reason, granted_by_membership_id, granted_at, revoked_by_membership_id,
    revoked_at, revoke_reason
)
SELECT RANDOM_UUID(), source_grant.tenant_id, source_grant.membership_id,
       source_grant.assignment_id, NULL, target_permission.id, source_grant.data_scope_id,
       'RESPONSE_SUBMITTER', source_grant.valid_from, source_grant.expires_at,
       source_grant.status, source_grant.customized,
       '由历史工单管控权限拆分：本人回单验收',
       source_grant.granted_by_membership_id, source_grant.granted_at,
       source_grant.revoked_by_membership_id, source_grant.revoked_at, source_grant.revoke_reason
FROM permission_grants source_grant
JOIN permissions source_permission ON source_permission.id = source_grant.permission_id
JOIN permissions target_permission ON target_permission.name IN (
    REPLACE(source_permission.name, ':cancel', ':verification:self-approve'),
    REPLACE(source_permission.name, ':cancel', ':verification:self-reject')
)
WHERE source_permission.name LIKE 'work-order:%:cancel'
  AND NOT EXISTS (
      SELECT 1 FROM permission_grants existing
      WHERE existing.assignment_id = source_grant.assignment_id
        AND existing.permission_id = target_permission.id
        AND existing.status = source_grant.status
  );

--rollback DELETE FROM permission_grants WHERE permission_id IN (SELECT id FROM permissions WHERE name LIKE 'work-order:%:verification:self-%');
--rollback DELETE FROM authorization_v2_permission_code_map_20260814 WHERE target_permission_name LIKE 'work-order:%:verification:self-%';
--rollback DELETE FROM permissions WHERE name LIKE 'work-order:%:verification:self-%';
