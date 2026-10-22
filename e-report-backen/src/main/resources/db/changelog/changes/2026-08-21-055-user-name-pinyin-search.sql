--liquibase formatted sql

--changeset e-report:2026-08-21-055-user-name-pinyin-search-mysql dbms:mysql
--comment Add server-derived phonetic search keys; historical rows are backfilled in bounded application batches.
ALTER TABLE users
    ADD COLUMN name_pinyin VARCHAR(640) CHARACTER SET ascii COLLATE ascii_general_ci NULL AFTER name,
    ADD COLUMN name_pinyin_initials VARCHAR(160) CHARACTER SET ascii COLLATE ascii_general_ci NULL AFTER name_pinyin;
CREATE INDEX idx_users_tenant_name_pinyin ON users (tenant_id, name_pinyin);
CREATE INDEX idx_users_tenant_name_pinyin_initials ON users (tenant_id, name_pinyin_initials);
--rollback DROP INDEX idx_users_tenant_name_pinyin_initials ON users;
--rollback DROP INDEX idx_users_tenant_name_pinyin ON users;
--rollback ALTER TABLE users DROP COLUMN name_pinyin_initials;
--rollback ALTER TABLE users DROP COLUMN name_pinyin;

--changeset e-report:2026-08-21-055-user-name-pinyin-search-h2 dbms:h2
--comment H2 equivalent for user phonetic search keys.
ALTER TABLE users ADD COLUMN name_pinyin VARCHAR(640);
ALTER TABLE users ADD COLUMN name_pinyin_initials VARCHAR(160);
CREATE INDEX idx_users_tenant_name_pinyin ON users (tenant_id, name_pinyin);
CREATE INDEX idx_users_tenant_name_pinyin_initials ON users (tenant_id, name_pinyin_initials);
--rollback DROP INDEX idx_users_tenant_name_pinyin_initials;
--rollback DROP INDEX idx_users_tenant_name_pinyin;
--rollback ALTER TABLE users DROP COLUMN name_pinyin_initials;
--rollback ALTER TABLE users DROP COLUMN name_pinyin;
