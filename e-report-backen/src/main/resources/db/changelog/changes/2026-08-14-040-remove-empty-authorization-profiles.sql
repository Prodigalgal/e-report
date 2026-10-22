--liquibase formatted sql

--changeset e-report:2026-08-14-040-empty-profile-preconditions dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM authorization_profile_permission_rules profile_rule JOIN authorization_profile_versions version_row ON version_row.id = profile_rule.profile_version_id JOIN authorization_profiles profile_row ON profile_row.id = version_row.profile_id WHERE profile_row.code IN ('PROFILE_USER', 'PROFILE_BASIC_USER')
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permission_grants grant_row JOIN authorization_assignments assignment_row ON assignment_row.id = grant_row.assignment_id JOIN authorization_profile_versions version_row ON version_row.id = assignment_row.profile_version_id JOIN authorization_profiles profile_row ON profile_row.id = version_row.profile_id WHERE profile_row.code IN ('PROFILE_USER', 'PROFILE_BASIC_USER')
--comment Empty legacy identity profiles must never carry actual permissions before destructive cleanup.
SELECT 1;

--rollback SELECT 1;

--changeset e-report:2026-08-14-040-native-source-labels-mysql dbms:mysql
--comment Normalize migrated assignment labels after native profile naming so the UI no longer exposes retired role terminology.
UPDATE authorization_profiles
SET display_name = CASE
        WHEN code = 'PROFILE_AUTHORIZATION_PROFILE_VIEWER' THEN '岗位权限模板查看员'
        WHEN code = 'PROFILE_AUTHORIZATION_PROFILE_EDITOR' THEN '岗位权限模板编辑员'
        ELSE display_name
    END
WHERE code IN ('PROFILE_AUTHORIZATION_PROFILE_VIEWER', 'PROFILE_AUTHORIZATION_PROFILE_EDITOR');

UPDATE authorization_assignments assignment_row
JOIN authorization_profile_versions version_row ON version_row.id = assignment_row.profile_version_id
JOIN authorization_profiles profile_row ON profile_row.id = version_row.profile_id
SET assignment_row.source_label = profile_row.display_name
WHERE assignment_row.source_type = 'MIGRATION';

--rollback SELECT 1;

--changeset e-report:2026-08-14-040-native-source-labels-h2 dbms:h2
--comment H2 equivalent of migrated assignment label normalization.
UPDATE authorization_profiles
SET display_name = CASE
        WHEN code = 'PROFILE_AUTHORIZATION_PROFILE_VIEWER' THEN '岗位权限模板查看员'
        WHEN code = 'PROFILE_AUTHORIZATION_PROFILE_EDITOR' THEN '岗位权限模板编辑员'
        ELSE display_name
    END
WHERE code IN ('PROFILE_AUTHORIZATION_PROFILE_VIEWER', 'PROFILE_AUTHORIZATION_PROFILE_EDITOR');

UPDATE authorization_assignments assignment_row
SET source_label = (
    SELECT profile_row.display_name
    FROM authorization_profile_versions version_row
    JOIN authorization_profiles profile_row ON profile_row.id = version_row.profile_id
    WHERE version_row.id = assignment_row.profile_version_id
)
WHERE assignment_row.source_type = 'MIGRATION';

--rollback SELECT 1;

--changeset e-report:2026-08-14-040-remove-empty-profiles-mysql dbms:mysql
--comment Destructively remove permissionless identity markers and their no-op assignments. Restore requires the pre-migration database backup.
DELETE scope_node
FROM authorization_data_scope_nodes scope_node
JOIN authorization_data_scopes scope_row ON scope_row.id = scope_node.scope_id
JOIN authorization_assignments assignment_row ON assignment_row.id = scope_row.id
JOIN authorization_profile_versions version_row ON version_row.id = assignment_row.profile_version_id
JOIN authorization_profiles profile_row ON profile_row.id = version_row.profile_id
WHERE profile_row.code IN ('PROFILE_USER', 'PROFILE_BASIC_USER');

DELETE scope_row
FROM authorization_data_scopes scope_row
JOIN authorization_assignments assignment_row ON assignment_row.id = scope_row.id
JOIN authorization_profile_versions version_row ON version_row.id = assignment_row.profile_version_id
JOIN authorization_profiles profile_row ON profile_row.id = version_row.profile_id
WHERE profile_row.code IN ('PROFILE_USER', 'PROFILE_BASIC_USER');

DELETE assignment_row
FROM authorization_assignments assignment_row
JOIN authorization_profile_versions version_row ON version_row.id = assignment_row.profile_version_id
JOIN authorization_profiles profile_row ON profile_row.id = version_row.profile_id
WHERE profile_row.code IN ('PROFILE_USER', 'PROFILE_BASIC_USER');

UPDATE authorization_profiles
SET current_version_id = NULL
WHERE code IN ('PROFILE_USER', 'PROFILE_BASIC_USER');

DELETE version_row
FROM authorization_profile_versions version_row
JOIN authorization_profiles profile_row ON profile_row.id = version_row.profile_id
WHERE profile_row.code IN ('PROFILE_USER', 'PROFILE_BASIC_USER');

DELETE FROM authorization_profiles
WHERE code IN ('PROFILE_USER', 'PROFILE_BASIC_USER');

--rollback SELECT 1;

--changeset e-report:2026-08-14-040-remove-empty-profiles-h2 dbms:h2
--comment H2 equivalent of permissionless identity cleanup.
DELETE FROM authorization_data_scope_nodes scope_node
WHERE EXISTS (
    SELECT 1
    FROM authorization_data_scopes scope_row
    JOIN authorization_assignments assignment_row ON assignment_row.id = scope_row.id
    JOIN authorization_profile_versions version_row ON version_row.id = assignment_row.profile_version_id
    JOIN authorization_profiles profile_row ON profile_row.id = version_row.profile_id
    WHERE scope_row.id = scope_node.scope_id
      AND profile_row.code IN ('PROFILE_USER', 'PROFILE_BASIC_USER')
);

DELETE FROM authorization_data_scopes scope_row
WHERE EXISTS (
    SELECT 1
    FROM authorization_assignments assignment_row
    JOIN authorization_profile_versions version_row ON version_row.id = assignment_row.profile_version_id
    JOIN authorization_profiles profile_row ON profile_row.id = version_row.profile_id
    WHERE assignment_row.id = scope_row.id
      AND profile_row.code IN ('PROFILE_USER', 'PROFILE_BASIC_USER')
);

DELETE FROM authorization_assignments assignment_row
WHERE EXISTS (
    SELECT 1
    FROM authorization_profile_versions version_row
    JOIN authorization_profiles profile_row ON profile_row.id = version_row.profile_id
    WHERE version_row.id = assignment_row.profile_version_id
      AND profile_row.code IN ('PROFILE_USER', 'PROFILE_BASIC_USER')
);

UPDATE authorization_profiles
SET current_version_id = NULL
WHERE code IN ('PROFILE_USER', 'PROFILE_BASIC_USER');

DELETE FROM authorization_profile_versions version_row
WHERE EXISTS (
    SELECT 1 FROM authorization_profiles profile_row
    WHERE profile_row.id = version_row.profile_id
      AND profile_row.code IN ('PROFILE_USER', 'PROFILE_BASIC_USER')
);

DELETE FROM authorization_profiles
WHERE code IN ('PROFILE_USER', 'PROFILE_BASIC_USER');

--rollback SELECT 1;

--changeset e-report:2026-08-14-040-empty-profile-assertions dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM authorization_profiles WHERE code IN ('PROFILE_USER', 'PROFILE_BASIC_USER')
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM authorization_assignments WHERE source_type = 'MIGRATION' AND source_label IN ('角色查看员', '角色编辑员')
--comment Empty profiles and retired role labels must be fully absent after cleanup.
SELECT 1;

--rollback SELECT 1;
