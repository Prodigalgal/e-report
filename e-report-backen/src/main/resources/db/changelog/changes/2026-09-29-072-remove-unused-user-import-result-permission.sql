--liquibase formatted sql

--changeset e-report:2026-09-29-072-remove-unused-user-import-result-permission
DELETE FROM api_token_permissions
WHERE permission_name = 'user:import:result:download';

UPDATE permission_grants
SET parent_grant_id = NULL,
    replaced_by_grant_id = NULL
WHERE permission_id = (
    SELECT id FROM permissions WHERE name = 'user:import:result:download'
);

DELETE FROM permission_grants
WHERE permission_id = (
    SELECT id FROM permissions WHERE name = 'user:import:result:download'
);

DELETE FROM authorization_profile_permission_rules
WHERE permission_id = (
    SELECT id FROM permissions WHERE name = 'user:import:result:download'
);

DELETE FROM permissions
WHERE name = 'user:import:result:download';
