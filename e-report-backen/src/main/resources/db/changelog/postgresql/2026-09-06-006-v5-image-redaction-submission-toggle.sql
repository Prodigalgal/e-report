--liquibase formatted sql

-- business-version: 5.7.0
--changeset e-report:2026-09-06-006-v5.7.0-image-redaction-submission-toggle dbms:postgresql splitStatements:false stripComments:false
ALTER TABLE image_redaction_jobs
    DROP CONSTRAINT IF EXISTS image_redaction_jobs_status_check;
ALTER TABLE image_redaction_jobs
    ADD CONSTRAINT image_redaction_jobs_status_check
    CHECK (status IN ('QUEUED','DEFERRED','PROCESSING','RETRY_PENDING','READY','FAILED','CANCELLED'));

--rollback ALTER TABLE image_redaction_jobs DROP CONSTRAINT IF EXISTS image_redaction_jobs_status_check;
--rollback ALTER TABLE image_redaction_jobs ADD CONSTRAINT image_redaction_jobs_status_check CHECK (status IN ('QUEUED','PROCESSING','RETRY_PENDING','READY','FAILED','CANCELLED'));
