--liquibase formatted sql

--changeset e-report:2026-08-23-057-permission-grant-lineage-mysql dbms:mysql
ALTER TABLE permission_grants
    ADD COLUMN parent_grant_id BINARY(16),
    ADD COLUMN grant_option BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN issuer_type VARCHAR(30) NOT NULL DEFAULT 'MIGRATION',
    ADD COLUMN replaced_by_grant_id BINARY(16),
    ADD COLUMN status_changed_at DATETIME(6);
UPDATE permission_grants
SET status_changed_at = COALESCE(revoked_at, granted_at)
WHERE status_changed_at IS NULL;
UPDATE permission_grants grant_row
SET issuer_type = 'SYSTEM_BASELINE'
WHERE EXISTS (
    SELECT 1
    FROM authorization_assignments assignment_row
    JOIN authorization_profile_versions profile_version ON profile_version.id = assignment_row.profile_version_id
    JOIN authorization_profiles profile_row ON profile_row.id = profile_version.profile_id
    WHERE assignment_row.id = grant_row.assignment_id
      AND profile_row.built_in = TRUE
);
ALTER TABLE permission_grants
    MODIFY status_changed_at DATETIME(6) NOT NULL,
    MODIFY assignment_id BINARY(16) NULL,
    MODIFY data_scope_id BINARY(16) NULL,
    MODIFY relation_constraint VARCHAR(40) NULL,
    ADD CONSTRAINT fk_permission_grants_parent FOREIGN KEY (parent_grant_id) REFERENCES permission_grants (id),
    ADD CONSTRAINT fk_permission_grants_replacement FOREIGN KEY (replaced_by_grant_id) REFERENCES permission_grants (id);
ALTER TABLE authorization_profiles
    MODIFY scope_seed_strategy VARCHAR(30) NULL;
ALTER TABLE authorization_profile_permission_rules
    MODIFY relation_constraint VARCHAR(40) NULL;
CREATE INDEX idx_permission_grants_parent ON permission_grants (parent_grant_id, status);
--rollback DROP INDEX idx_permission_grants_parent ON permission_grants;
--rollback ALTER TABLE permission_grants DROP FOREIGN KEY fk_permission_grants_replacement;
--rollback ALTER TABLE permission_grants DROP FOREIGN KEY fk_permission_grants_parent;
--rollback ALTER TABLE permission_grants DROP COLUMN status_changed_at, DROP COLUMN replaced_by_grant_id, DROP COLUMN issuer_type, DROP COLUMN grant_option, DROP COLUMN parent_grant_id;

--changeset e-report:2026-08-23-057-permission-grant-lineage-h2 dbms:h2
ALTER TABLE permission_grants ADD COLUMN parent_grant_id UUID;
ALTER TABLE permission_grants ADD COLUMN grant_option BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE permission_grants ADD COLUMN issuer_type VARCHAR(30) DEFAULT 'MIGRATION' NOT NULL;
ALTER TABLE permission_grants ADD COLUMN replaced_by_grant_id UUID;
ALTER TABLE permission_grants ADD COLUMN status_changed_at TIMESTAMP(6);
UPDATE permission_grants SET status_changed_at = COALESCE(revoked_at, granted_at) WHERE status_changed_at IS NULL;
UPDATE permission_grants grant_row
SET issuer_type = 'SYSTEM_BASELINE'
WHERE EXISTS (
    SELECT 1
    FROM authorization_assignments assignment_row
    JOIN authorization_profile_versions profile_version ON profile_version.id = assignment_row.profile_version_id
    JOIN authorization_profiles profile_row ON profile_row.id = profile_version.profile_id
    WHERE assignment_row.id = grant_row.assignment_id
      AND profile_row.built_in = TRUE
);
ALTER TABLE permission_grants ALTER COLUMN status_changed_at SET NOT NULL;
ALTER TABLE permission_grants ALTER COLUMN assignment_id DROP NOT NULL;
ALTER TABLE permission_grants ALTER COLUMN data_scope_id DROP NOT NULL;
ALTER TABLE permission_grants ALTER COLUMN relation_constraint DROP NOT NULL;
ALTER TABLE authorization_profiles ALTER COLUMN scope_seed_strategy DROP NOT NULL;
ALTER TABLE authorization_profile_permission_rules ALTER COLUMN relation_constraint DROP NOT NULL;
ALTER TABLE permission_grants ADD CONSTRAINT fk_permission_grants_parent FOREIGN KEY (parent_grant_id) REFERENCES permission_grants (id);
ALTER TABLE permission_grants ADD CONSTRAINT fk_permission_grants_replacement FOREIGN KEY (replaced_by_grant_id) REFERENCES permission_grants (id);
CREATE INDEX idx_permission_grants_parent ON permission_grants (parent_grant_id, status);
--rollback DROP INDEX idx_permission_grants_parent;
--rollback ALTER TABLE permission_grants DROP CONSTRAINT fk_permission_grants_replacement;
--rollback ALTER TABLE permission_grants DROP CONSTRAINT fk_permission_grants_parent;
--rollback ALTER TABLE permission_grants DROP COLUMN status_changed_at;
--rollback ALTER TABLE permission_grants DROP COLUMN replaced_by_grant_id;
--rollback ALTER TABLE permission_grants DROP COLUMN issuer_type;
--rollback ALTER TABLE permission_grants DROP COLUMN grant_option;
--rollback ALTER TABLE permission_grants DROP COLUMN parent_grant_id;

--changeset e-report:2026-08-23-057-v4-atomic-permission-manifest dbms:mysql,h2
CREATE TABLE permission_catalog_additions_20260823 (
    permission_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (permission_name)
);
INSERT INTO permission_catalog_additions_20260823 (permission_name) VALUES
('self-profile:view'),
('self-profile:update'),
('self-password:change'),
('self-session:view'),
('self-session:revoke'),
('user:manage:contact:update'),
('user:manage:department:move'),
('user:manage:session:view'),
('user:manage:session:revoke'),
('user:manage:trusted-device:view'),
('user:manage:trusted-device:revoke'),
('user:authorization:reattach'),
('user:authorization:grant-option:issue'),
('user:import:task:view'),
('user:import:result:download'),
('user:import:error-report:download'),
('user:export:task:view'),
('user:export:cancel'),
('authorization-template:update'),
('authorization-template:view'),
('authorization-template:create'),
('authorization-template:clone'),
('authorization-template:enable'),
('authorization-template:disable'),
('authorization-template:delete'),
('authorization-template:apply'),
('organization:manage:enable'),
('organization:manage:disable'),
('organization:manage:merge'),
('organization:manage:delete'),
('department:view'),
('department:create'),
('department:update'),
('department:move'),
('department:enable'),
('department:disable'),
('department:merge'),
('department:delete');
--rollback DROP TABLE permission_catalog_additions_20260823;

--changeset e-report:2026-08-23-057-v4-atomic-permissions-mysql dbms:mysql
INSERT INTO permissions (id, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), candidate.permission_name, NULL, NULL
FROM permission_catalog_additions_20260823 candidate
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = candidate.permission_name);
--rollback SELECT 1;

--changeset e-report:2026-08-23-057-v4-atomic-permissions-h2 dbms:h2
INSERT INTO permissions (id, name, display_name, description)
SELECT RANDOM_UUID(), candidate.permission_name, NULL, NULL
FROM permission_catalog_additions_20260823 candidate
WHERE NOT EXISTS (SELECT 1 FROM permissions existing WHERE existing.name = candidate.permission_name);
--rollback SELECT 1;

--changeset e-report:2026-08-23-057-v4-atomic-permissions-assert dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:38 SELECT COUNT(*) FROM permissions WHERE name IN (SELECT permission_name FROM permission_catalog_additions_20260823)
SELECT 1;
--rollback SELECT 1;
