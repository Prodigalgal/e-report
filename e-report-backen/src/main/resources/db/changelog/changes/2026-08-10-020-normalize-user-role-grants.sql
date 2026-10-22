--liquibase formatted sql

--changeset e-report:2026-08-10-020-normalize-user-role-grants-mysql dbms:mysql
--comment Collapse historical duplicate data scopes to the widest effective grant for each user-role pair.
CREATE TEMPORARY TABLE tmp_v1039_kept_role_grants (
    id BINARY(16) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=MEMORY;

INSERT INTO tmp_v1039_kept_role_grants (id)
SELECT ranked.id
FROM (
    SELECT grant_row.id,
           ROW_NUMBER() OVER (
               PARTITION BY grant_row.tenant_id, grant_row.membership_id, grant_row.role_id
               ORDER BY CASE grant_row.data_scope
                            WHEN 'TENANT_ALL' THEN 1
                            WHEN 'ORG_TREE' THEN 2
                            WHEN 'ORG' THEN 3
                            WHEN 'CUSTOM_ORGS' THEN 4
                            ELSE 5
                        END,
                        grant_row.granted_at DESC,
                        grant_row.id DESC
           ) row_rank
    FROM membership_role_grants grant_row
) ranked
WHERE ranked.row_rank = 1;

DELETE stale_grant
FROM membership_role_grants stale_grant
LEFT JOIN tmp_v1039_kept_role_grants kept_grant ON kept_grant.id = stale_grant.id
WHERE kept_grant.id IS NULL;

DROP TEMPORARY TABLE tmp_v1039_kept_role_grants;

ALTER TABLE membership_role_grants
    DROP INDEX uk_membership_role_grants,
    ADD CONSTRAINT uk_membership_role_grants UNIQUE (tenant_id, membership_id, role_id);

--rollback SELECT 1;

--changeset e-report:2026-08-10-020-normalize-user-role-grants-h2 dbms:h2
--comment H2 equivalent for deterministic single-scope user-role grants.
CREATE TEMPORARY TABLE tmp_v1039_kept_role_grants AS
SELECT ranked.id
FROM (
    SELECT grant_row.id,
           ROW_NUMBER() OVER (
               PARTITION BY grant_row.tenant_id, grant_row.membership_id, grant_row.role_id
               ORDER BY CASE grant_row.data_scope
                            WHEN 'TENANT_ALL' THEN 1
                            WHEN 'ORG_TREE' THEN 2
                            WHEN 'ORG' THEN 3
                            WHEN 'CUSTOM_ORGS' THEN 4
                            ELSE 5
                        END,
                        grant_row.granted_at DESC,
                        grant_row.id DESC
           ) row_rank
    FROM membership_role_grants grant_row
) ranked
WHERE ranked.row_rank = 1;

DELETE FROM membership_role_grants
WHERE id NOT IN (SELECT id FROM tmp_v1039_kept_role_grants);

DROP TABLE tmp_v1039_kept_role_grants;

ALTER TABLE membership_role_grants DROP CONSTRAINT uk_membership_role_grants;
ALTER TABLE membership_role_grants
    ADD CONSTRAINT uk_membership_role_grants UNIQUE (tenant_id, membership_id, role_id);

--rollback SELECT 1;

--changeset e-report:2026-08-10-020-assert-single-user-role-grant dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM (SELECT membership_id, role_id FROM membership_role_grants GROUP BY membership_id, role_id HAVING COUNT(*) > 1) duplicate_grants
SELECT 1;

--rollback SELECT 1;
