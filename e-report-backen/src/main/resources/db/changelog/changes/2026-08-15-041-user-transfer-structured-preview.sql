--liquibase formatted sql

--changeset e-report:2026-08-15-041-user-transfer-structured-preview-mysql dbms:mysql
--comment Add a masked subject label and an execution lookup index for structured user-transfer previews.
ALTER TABLE user_transfer_job_rows ADD COLUMN subject_display VARCHAR(32) NULL AFTER subject_key_hash;
CREATE INDEX idx_user_transfer_job_rows_subject
    ON user_transfer_job_rows (tenant_id, job_id, subject_key_hash);

--rollback DROP INDEX idx_user_transfer_job_rows_subject ON user_transfer_job_rows;
--rollback ALTER TABLE user_transfer_job_rows DROP COLUMN subject_display;

--changeset e-report:2026-08-15-041-user-transfer-structured-preview-h2 dbms:h2
--comment H2 equivalent for structured user-transfer preview subject labels and lookup index.
ALTER TABLE user_transfer_job_rows ADD COLUMN subject_display VARCHAR(32);
CREATE INDEX idx_user_transfer_job_rows_subject
    ON user_transfer_job_rows (tenant_id, job_id, subject_key_hash);

--rollback DROP INDEX idx_user_transfer_job_rows_subject;
--rollback ALTER TABLE user_transfer_job_rows DROP COLUMN subject_display;
