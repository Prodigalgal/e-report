--liquibase formatted sql

--changeset e-report:2026-08-14-039-role-retirement-assertions dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM membership_role_grants legacy_grant LEFT JOIN authorization_assignments assignment_row ON assignment_row.id = legacy_grant.id WHERE assignment_row.id IS NULL
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM user_roles legacy_link LEFT JOIN membership_role_grants legacy_grant ON legacy_grant.membership_id = legacy_link.user_id AND legacy_grant.role_id = legacy_link.role_id WHERE legacy_grant.id IS NULL
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM roles legacy_role LEFT JOIN authorization_profiles profile_row ON profile_row.id = legacy_role.id WHERE profile_row.id IS NULL
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM role_bundles legacy_bundle LEFT JOIN authorization_profiles profile_row ON profile_row.id = legacy_bundle.id WHERE profile_row.id IS NULL
--comment Halt before destructive retirement unless every legacy role association and definition has a deterministic V2 counterpart.
SELECT 1;

--rollback SELECT 1;

--changeset e-report:2026-08-14-039-native-profile-keys-mysql dbms:mysql
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM authorization_profiles source_profile JOIN authorization_profiles conflicting_profile ON conflicting_profile.tenant_id = source_profile.tenant_id AND conflicting_profile.id <> source_profile.id AND conflicting_profile.code = CASE WHEN source_profile.code = 'ROLE_ROLE_VIEWER' THEN 'PROFILE_AUTHORIZATION_PROFILE_VIEWER' WHEN source_profile.code = 'ROLE_ROLE_EDITOR' THEN 'PROFILE_AUTHORIZATION_PROFILE_EDITOR' WHEN source_profile.code = 'ROLE_ORG_VIEWER' THEN 'PROFILE_ORGANIZATION_VIEWER' WHEN source_profile.code = 'ROLE_ORG_EDITOR' THEN 'PROFILE_ORGANIZATION_EDITOR' ELSE CONCAT('PROFILE_', SUBSTRING(source_profile.code, 6)) END WHERE source_profile.built_in = TRUE AND LEFT(source_profile.code, 5) = 'ROLE_'
--comment Rename migrated built-in role-shaped profiles to native authorization profile keys.
UPDATE authorization_profiles
SET code = CASE
        WHEN code = 'ROLE_ROLE_VIEWER' THEN 'PROFILE_AUTHORIZATION_PROFILE_VIEWER'
        WHEN code = 'ROLE_ROLE_EDITOR' THEN 'PROFILE_AUTHORIZATION_PROFILE_EDITOR'
        WHEN code = 'ROLE_ORG_VIEWER' THEN 'PROFILE_ORGANIZATION_VIEWER'
        WHEN code = 'ROLE_ORG_EDITOR' THEN 'PROFILE_ORGANIZATION_EDITOR'
        ELSE CONCAT('PROFILE_', SUBSTRING(code, 6))
    END,
    system_key = CASE
        WHEN system_key = 'ROLE_ROLE_VIEWER' THEN 'PROFILE_AUTHORIZATION_PROFILE_VIEWER'
        WHEN system_key = 'ROLE_ROLE_EDITOR' THEN 'PROFILE_AUTHORIZATION_PROFILE_EDITOR'
        WHEN system_key = 'ROLE_ORG_VIEWER' THEN 'PROFILE_ORGANIZATION_VIEWER'
        WHEN system_key = 'ROLE_ORG_EDITOR' THEN 'PROFILE_ORGANIZATION_EDITOR'
        ELSE CONCAT('PROFILE_', SUBSTRING(system_key, 6))
    END,
    display_name = CASE
        WHEN code = 'ROLE_ROLE_VIEWER' THEN '岗位权限模板查看员'
        WHEN code = 'ROLE_ROLE_EDITOR' THEN '岗位权限模板编辑员'
        ELSE display_name
    END
WHERE built_in = TRUE AND LEFT(code, 5) = 'ROLE_';

UPDATE authorization_assignments assignment_row
JOIN authorization_profile_versions version_row ON version_row.id = assignment_row.profile_version_id
JOIN authorization_profiles profile_row ON profile_row.id = version_row.profile_id
SET assignment_row.source_label = profile_row.display_name
WHERE assignment_row.source_type = 'MIGRATION';

--rollback UPDATE authorization_profiles SET code = CASE WHEN code = 'PROFILE_AUTHORIZATION_PROFILE_VIEWER' THEN 'ROLE_ROLE_VIEWER' WHEN code = 'PROFILE_AUTHORIZATION_PROFILE_EDITOR' THEN 'ROLE_ROLE_EDITOR' WHEN code = 'PROFILE_ORGANIZATION_VIEWER' THEN 'ROLE_ORG_VIEWER' WHEN code = 'PROFILE_ORGANIZATION_EDITOR' THEN 'ROLE_ORG_EDITOR' ELSE CONCAT('ROLE_', SUBSTRING(code, 9)) END, system_key = CASE WHEN system_key = 'PROFILE_AUTHORIZATION_PROFILE_VIEWER' THEN 'ROLE_ROLE_VIEWER' WHEN system_key = 'PROFILE_AUTHORIZATION_PROFILE_EDITOR' THEN 'ROLE_ROLE_EDITOR' WHEN system_key = 'PROFILE_ORGANIZATION_VIEWER' THEN 'ROLE_ORG_VIEWER' WHEN system_key = 'PROFILE_ORGANIZATION_EDITOR' THEN 'ROLE_ORG_EDITOR' ELSE CONCAT('ROLE_', SUBSTRING(system_key, 9)) END WHERE built_in = TRUE AND LEFT(code, 8) = 'PROFILE_';

--changeset e-report:2026-08-14-039-native-profile-keys-h2 dbms:h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM authorization_profiles source_profile JOIN authorization_profiles conflicting_profile ON conflicting_profile.tenant_id = source_profile.tenant_id AND conflicting_profile.id <> source_profile.id AND conflicting_profile.code = CASE WHEN source_profile.code = 'ROLE_ROLE_VIEWER' THEN 'PROFILE_AUTHORIZATION_PROFILE_VIEWER' WHEN source_profile.code = 'ROLE_ROLE_EDITOR' THEN 'PROFILE_AUTHORIZATION_PROFILE_EDITOR' WHEN source_profile.code = 'ROLE_ORG_VIEWER' THEN 'PROFILE_ORGANIZATION_VIEWER' WHEN source_profile.code = 'ROLE_ORG_EDITOR' THEN 'PROFILE_ORGANIZATION_EDITOR' ELSE CONCAT('PROFILE_', SUBSTRING(source_profile.code, 6)) END WHERE source_profile.built_in = TRUE AND LEFT(source_profile.code, 5) = 'ROLE_'
--comment H2 equivalent of native authorization profile key migration.
UPDATE authorization_profiles
SET code = CASE
        WHEN code = 'ROLE_ROLE_VIEWER' THEN 'PROFILE_AUTHORIZATION_PROFILE_VIEWER'
        WHEN code = 'ROLE_ROLE_EDITOR' THEN 'PROFILE_AUTHORIZATION_PROFILE_EDITOR'
        WHEN code = 'ROLE_ORG_VIEWER' THEN 'PROFILE_ORGANIZATION_VIEWER'
        WHEN code = 'ROLE_ORG_EDITOR' THEN 'PROFILE_ORGANIZATION_EDITOR'
        ELSE CONCAT('PROFILE_', SUBSTRING(code, 6))
    END,
    system_key = CASE
        WHEN system_key = 'ROLE_ROLE_VIEWER' THEN 'PROFILE_AUTHORIZATION_PROFILE_VIEWER'
        WHEN system_key = 'ROLE_ROLE_EDITOR' THEN 'PROFILE_AUTHORIZATION_PROFILE_EDITOR'
        WHEN system_key = 'ROLE_ORG_VIEWER' THEN 'PROFILE_ORGANIZATION_VIEWER'
        WHEN system_key = 'ROLE_ORG_EDITOR' THEN 'PROFILE_ORGANIZATION_EDITOR'
        ELSE CONCAT('PROFILE_', SUBSTRING(system_key, 6))
    END,
    display_name = CASE
        WHEN code = 'ROLE_ROLE_VIEWER' THEN '岗位权限模板查看员'
        WHEN code = 'ROLE_ROLE_EDITOR' THEN '岗位权限模板编辑员'
        ELSE display_name
    END
WHERE built_in = TRUE AND LEFT(code, 5) = 'ROLE_';

UPDATE authorization_assignments assignment_row
SET source_label = (
    SELECT profile_row.display_name
    FROM authorization_profile_versions version_row
    JOIN authorization_profiles profile_row ON profile_row.id = version_row.profile_id
    WHERE version_row.id = assignment_row.profile_version_id
)
WHERE assignment_row.source_type = 'MIGRATION';

--rollback UPDATE authorization_profiles SET code = CASE WHEN code = 'PROFILE_AUTHORIZATION_PROFILE_VIEWER' THEN 'ROLE_ROLE_VIEWER' WHEN code = 'PROFILE_AUTHORIZATION_PROFILE_EDITOR' THEN 'ROLE_ROLE_EDITOR' WHEN code = 'PROFILE_ORGANIZATION_VIEWER' THEN 'ROLE_ORG_VIEWER' WHEN code = 'PROFILE_ORGANIZATION_EDITOR' THEN 'ROLE_ORG_EDITOR' ELSE CONCAT('ROLE_', SUBSTRING(code, 9)) END, system_key = CASE WHEN system_key = 'PROFILE_AUTHORIZATION_PROFILE_VIEWER' THEN 'ROLE_ROLE_VIEWER' WHEN system_key = 'PROFILE_AUTHORIZATION_PROFILE_EDITOR' THEN 'ROLE_ROLE_EDITOR' WHEN system_key = 'PROFILE_ORGANIZATION_VIEWER' THEN 'ROLE_ORG_VIEWER' WHEN system_key = 'PROFILE_ORGANIZATION_EDITOR' THEN 'ROLE_ORG_EDITOR' ELSE CONCAT('ROLE_', SUBSTRING(system_key, 9)) END WHERE built_in = TRUE AND LEFT(code, 8) = 'PROFILE_';

--changeset e-report:2026-08-14-039-drop-legacy-role-runtime-mysql dbms:mysql
--comment Destructively retire the V1 role runtime after V2 assertions and key migration. Restore requires the pre-migration database backup.
DROP TABLE membership_role_grants;
DROP TABLE user_roles;
DROP TABLE role_bundle_grant_rules;
DROP TABLE role_bundles;
DROP TABLE role_permissions;
DROP TABLE roles;

--rollback SELECT 1;

--changeset e-report:2026-08-14-039-drop-legacy-role-runtime-h2 dbms:h2
--comment H2 equivalent of destructive V1 role runtime retirement.
DROP TABLE membership_role_grants;
DROP TABLE user_roles;
DROP TABLE role_bundle_grant_rules;
DROP TABLE role_bundles;
DROP TABLE role_permissions;
DROP TABLE roles;

--rollback SELECT 1;
