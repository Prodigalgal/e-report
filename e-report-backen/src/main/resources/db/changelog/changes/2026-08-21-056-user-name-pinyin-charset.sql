--liquibase formatted sql

--changeset e-report:2026-08-21-056-user-name-pinyin-charset-mysql dbms:mysql
--comment Keep phonetic keys valid for keyboard pinyin and non-Chinese names without sacrificing indexed prefix search.
ALTER TABLE users
    MODIFY COLUMN name_pinyin VARCHAR(640) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
    MODIFY COLUMN name_pinyin_initials VARCHAR(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL;
--rollback ALTER TABLE users MODIFY COLUMN name_pinyin VARCHAR(640) CHARACTER SET ascii COLLATE ascii_general_ci NULL;
--rollback ALTER TABLE users MODIFY COLUMN name_pinyin_initials VARCHAR(160) CHARACTER SET ascii COLLATE ascii_general_ci NULL;

--changeset e-report:2026-08-21-056-user-name-pinyin-charset-h2 dbms:h2
--comment H2 stores the same Unicode-derived search key contract without a charset conversion.
SELECT 1;
--rollback SELECT 1;
