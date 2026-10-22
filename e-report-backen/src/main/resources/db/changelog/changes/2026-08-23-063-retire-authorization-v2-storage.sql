--liquibase formatted sql

--changeset e-report:2026-08-23-063-collapse-authorization-profile-rules-mysql dbms:mysql
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permission_grants WHERE profile_rule_id IS NOT NULL
CREATE TABLE authorization_v2_profile_rule_collapse_audit_20260823 (
    retired_rule_id BINARY(16) NOT NULL,
    retained_rule_id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    profile_version_id BINARY(16) NOT NULL,
    permission_id BINARY(16) NOT NULL,
    retired_relation_constraint VARCHAR(40) NULL,
    retained_relation_constraint VARCHAR(40) NULL,
    retired_sort_order INT NOT NULL,
    retired_at DATETIME(6) NOT NULL,
    migration_reason VARCHAR(500) NOT NULL,
    PRIMARY KEY (retired_rule_id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
INSERT INTO authorization_v2_profile_rule_collapse_audit_20260823 (
    retired_rule_id,
    retained_rule_id,
    tenant_id,
    profile_version_id,
    permission_id,
    retired_relation_constraint,
    retained_relation_constraint,
    retired_sort_order,
    retired_at,
    migration_reason
)
SELECT ranked.id,
       ranked.retained_rule_id,
       ranked.tenant_id,
       ranked.profile_version_id,
       ranked.permission_id,
       ranked.relation_constraint,
       ranked.retained_relation_constraint,
       ranked.sort_order,
       CURRENT_TIMESTAMP(6),
       '5.0.8 将岗位模板收敛为每个原子权限一条规则，资源关系由组织边界和工单参与关系运行时判定'
FROM (
    SELECT profile_rule.*,
           FIRST_VALUE(profile_rule.id) OVER (
               PARTITION BY profile_rule.profile_version_id, profile_rule.permission_id
               ORDER BY CASE profile_rule.relation_constraint
                            WHEN 'ANY_IN_SCOPE' THEN 0
                            WHEN 'PARTICIPANT' THEN 1
                            WHEN 'REPORTER' THEN 2
                            ELSE 3
                        END,
                        profile_rule.sort_order,
                        profile_rule.id
           ) AS retained_rule_id,
           FIRST_VALUE(profile_rule.relation_constraint) OVER (
               PARTITION BY profile_rule.profile_version_id, profile_rule.permission_id
               ORDER BY CASE profile_rule.relation_constraint
                            WHEN 'ANY_IN_SCOPE' THEN 0
                            WHEN 'PARTICIPANT' THEN 1
                            WHEN 'REPORTER' THEN 2
                            ELSE 3
                        END,
                        profile_rule.sort_order,
                        profile_rule.id
           ) AS retained_relation_constraint,
           ROW_NUMBER() OVER (
               PARTITION BY profile_rule.profile_version_id, profile_rule.permission_id
               ORDER BY CASE profile_rule.relation_constraint
                            WHEN 'ANY_IN_SCOPE' THEN 0
                            WHEN 'PARTICIPANT' THEN 1
                            WHEN 'REPORTER' THEN 2
                            ELSE 3
                        END,
                        profile_rule.sort_order,
                        profile_rule.id
           ) AS rule_rank
    FROM authorization_profile_permission_rules profile_rule
) ranked
WHERE ranked.rule_rank > 1;
DELETE profile_rule
FROM authorization_profile_permission_rules profile_rule
INNER JOIN authorization_v2_profile_rule_collapse_audit_20260823 collapse_audit
        ON collapse_audit.retired_rule_id = profile_rule.id;
--rollback SELECT 1;

--changeset e-report:2026-08-23-063-collapse-authorization-profile-rules-h2 dbms:h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permission_grants WHERE profile_rule_id IS NOT NULL
CREATE TABLE authorization_v2_profile_rule_collapse_audit_20260823 (
    retired_rule_id BINARY(16) NOT NULL PRIMARY KEY,
    retained_rule_id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    profile_version_id BINARY(16) NOT NULL,
    permission_id BINARY(16) NOT NULL,
    retired_relation_constraint VARCHAR(40),
    retained_relation_constraint VARCHAR(40),
    retired_sort_order INT NOT NULL,
    retired_at TIMESTAMP(6) NOT NULL,
    migration_reason VARCHAR(500) NOT NULL
);
INSERT INTO authorization_v2_profile_rule_collapse_audit_20260823 (
    retired_rule_id,
    retained_rule_id,
    tenant_id,
    profile_version_id,
    permission_id,
    retired_relation_constraint,
    retained_relation_constraint,
    retired_sort_order,
    retired_at,
    migration_reason
)
SELECT ranked.id,
       ranked.retained_rule_id,
       ranked.tenant_id,
       ranked.profile_version_id,
       ranked.permission_id,
       ranked.relation_constraint,
       ranked.retained_relation_constraint,
       ranked.sort_order,
       CURRENT_TIMESTAMP,
       '5.0.8 将岗位模板收敛为每个原子权限一条规则，资源关系由组织边界和工单参与关系运行时判定'
FROM (
    SELECT profile_rule.*,
           FIRST_VALUE(profile_rule.id) OVER (
               PARTITION BY profile_rule.profile_version_id, profile_rule.permission_id
               ORDER BY CASE profile_rule.relation_constraint
                            WHEN 'ANY_IN_SCOPE' THEN 0
                            WHEN 'PARTICIPANT' THEN 1
                            WHEN 'REPORTER' THEN 2
                            ELSE 3
                        END,
                        profile_rule.sort_order,
                        profile_rule.id
           ) AS retained_rule_id,
           FIRST_VALUE(profile_rule.relation_constraint) OVER (
               PARTITION BY profile_rule.profile_version_id, profile_rule.permission_id
               ORDER BY CASE profile_rule.relation_constraint
                            WHEN 'ANY_IN_SCOPE' THEN 0
                            WHEN 'PARTICIPANT' THEN 1
                            WHEN 'REPORTER' THEN 2
                            ELSE 3
                        END,
                        profile_rule.sort_order,
                        profile_rule.id
           ) AS retained_relation_constraint,
           ROW_NUMBER() OVER (
               PARTITION BY profile_rule.profile_version_id, profile_rule.permission_id
               ORDER BY CASE profile_rule.relation_constraint
                            WHEN 'ANY_IN_SCOPE' THEN 0
                            WHEN 'PARTICIPANT' THEN 1
                            WHEN 'REPORTER' THEN 2
                            ELSE 3
                        END,
                        profile_rule.sort_order,
                        profile_rule.id
           ) AS rule_rank
    FROM authorization_profile_permission_rules profile_rule
) ranked
WHERE ranked.rule_rank > 1;
DELETE FROM authorization_profile_permission_rules
WHERE id IN (
    SELECT collapse_audit.retired_rule_id
    FROM authorization_v2_profile_rule_collapse_audit_20260823 collapse_audit
);
--rollback SELECT 1;

--changeset e-report:2026-08-23-063-authorization-v2-retirement-audit-mysql dbms:mysql
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permission_grants WHERE assignment_id IS NOT NULL OR profile_rule_id IS NOT NULL OR data_scope_id IS NOT NULL OR relation_constraint IS NOT NULL
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM (SELECT profile_version_id, permission_id FROM authorization_profile_permission_rules GROUP BY profile_version_id, permission_id HAVING COUNT(*) > 1) duplicate_rules
CREATE TABLE authorization_v2_retirement_audit_20260823 (
    source_table VARCHAR(100) NOT NULL,
    retired_row_count BIGINT NOT NULL,
    retired_at DATETIME(6) NOT NULL,
    migration_reason VARCHAR(500) NOT NULL,
    PRIMARY KEY (source_table)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
INSERT INTO authorization_v2_retirement_audit_20260823 VALUES
('authorization_assignments', (SELECT COUNT(*) FROM authorization_assignments), CURRENT_TIMESTAMP(6), '5.0.8 运行时只使用独立原子 Grant'),
('authorization_data_scopes', (SELECT COUNT(*) FROM authorization_data_scopes), CURRENT_TIMESTAMP(6), '5.0.8 数据面由用户所属组织树自然计算'),
('authorization_data_scope_nodes', (SELECT COUNT(*) FROM authorization_data_scope_nodes), CURRENT_TIMESTAMP(6), '5.0.8 不再保存独立数据域节点');
--rollback DROP TABLE authorization_v2_retirement_audit_20260823;

--changeset e-report:2026-08-23-063-authorization-v2-retirement-audit-h2 dbms:h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permission_grants WHERE assignment_id IS NOT NULL OR profile_rule_id IS NOT NULL OR data_scope_id IS NOT NULL OR relation_constraint IS NOT NULL
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM (SELECT profile_version_id, permission_id FROM authorization_profile_permission_rules GROUP BY profile_version_id, permission_id HAVING COUNT(*) > 1) duplicate_rules
CREATE TABLE authorization_v2_retirement_audit_20260823 (
    source_table VARCHAR(100) NOT NULL PRIMARY KEY,
    retired_row_count BIGINT NOT NULL,
    retired_at TIMESTAMP(6) NOT NULL,
    migration_reason VARCHAR(500) NOT NULL
);
INSERT INTO authorization_v2_retirement_audit_20260823 VALUES
('authorization_assignments', (SELECT COUNT(*) FROM authorization_assignments), CURRENT_TIMESTAMP, '5.0.8 运行时只使用独立原子 Grant'),
('authorization_data_scopes', (SELECT COUNT(*) FROM authorization_data_scopes), CURRENT_TIMESTAMP, '5.0.8 数据面由用户所属组织树自然计算'),
('authorization_data_scope_nodes', (SELECT COUNT(*) FROM authorization_data_scope_nodes), CURRENT_TIMESTAMP, '5.0.8 不再保存独立数据域节点');
--rollback DROP TABLE authorization_v2_retirement_audit_20260823;

--changeset e-report:2026-08-23-063-drop-authorization-v2-storage-mysql dbms:mysql
ALTER TABLE permission_grants
    DROP FOREIGN KEY fk_permission_grants_assignment,
    DROP FOREIGN KEY fk_permission_grants_profile_rule,
    DROP FOREIGN KEY fk_permission_grants_scope,
    DROP INDEX idx_permission_grants_assignment,
    DROP COLUMN assignment_id,
    DROP COLUMN profile_rule_id,
    DROP COLUMN data_scope_id,
    DROP COLUMN relation_constraint;
ALTER TABLE authorization_profile_permission_rules
    DROP INDEX uk_authorization_profile_rules_relation,
    DROP COLUMN relation_constraint,
    ADD CONSTRAINT uk_authorization_profile_rules_permission UNIQUE (profile_version_id, permission_id);
ALTER TABLE authorization_profiles DROP COLUMN scope_seed_strategy;
DROP TABLE authorization_data_scope_nodes;
DROP TABLE authorization_data_scopes;
DROP TABLE authorization_assignments;
--rollback SELECT 1;

--changeset e-report:2026-08-23-063-drop-authorization-v2-storage-h2 dbms:h2
ALTER TABLE permission_grants DROP CONSTRAINT fk_permission_grants_assignment;
ALTER TABLE permission_grants DROP CONSTRAINT fk_permission_grants_profile_rule;
ALTER TABLE permission_grants DROP CONSTRAINT fk_permission_grants_scope;
DROP INDEX idx_permission_grants_assignment;
ALTER TABLE permission_grants DROP COLUMN assignment_id;
ALTER TABLE permission_grants DROP COLUMN profile_rule_id;
ALTER TABLE permission_grants DROP COLUMN data_scope_id;
ALTER TABLE permission_grants DROP COLUMN relation_constraint;
ALTER TABLE authorization_profile_permission_rules DROP CONSTRAINT uk_authorization_profile_rules_relation;
ALTER TABLE authorization_profile_permission_rules DROP COLUMN relation_constraint;
ALTER TABLE authorization_profile_permission_rules ADD CONSTRAINT uk_authorization_profile_rules_permission UNIQUE (profile_version_id, permission_id);
ALTER TABLE authorization_profiles DROP COLUMN scope_seed_strategy;
DROP TABLE authorization_data_scope_nodes;
DROP TABLE authorization_data_scopes;
DROP TABLE authorization_assignments;
--rollback SELECT 1;

--changeset e-report:2026-08-23-063-authorization-v2-storage-assert dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_SCHEMA) = LOWER(SCHEMA()) AND LOWER(TABLE_NAME) IN ('authorization_assignments','authorization_data_scopes','authorization_data_scope_nodes')
--precondition-sql-check expectedResult:3 SELECT COUNT(*) FROM authorization_v2_retirement_audit_20260823
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM (SELECT profile_version_id, permission_id FROM authorization_profile_permission_rules GROUP BY profile_version_id, permission_id HAVING COUNT(*) > 1) duplicate_rules
SELECT 1;
--rollback SELECT 1;
