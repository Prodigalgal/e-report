--liquibase formatted sql

--changeset e-report:2026-08-30-005-v5-image-redaction-jobs dbms:postgresql splitStatements:false stripComments:false
CREATE TABLE image_redaction_jobs (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    upload_session_id uuid NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('QUEUED','PROCESSING','RETRY_PENDING','READY','FAILED','CANCELLED')),
    source_stored_file_name varchar(255) NOT NULL,
    source_content_type varchar(100) NOT NULL,
    source_file_size bigint NOT NULL,
    output_stored_file_name varchar(255),
    output_original_file_name varchar(255),
    output_content_type varchar(100),
    output_file_size bigint,
    output_sha256 varchar(64),
    attempt_count integer NOT NULL,
    max_attempts integer NOT NULL,
    available_at timestamp(6) NOT NULL,
    lease_owner varchar(100),
    lease_until timestamp(6),
    model_version varchar(100),
    policy_version varchar(50) NOT NULL,
    detected_regions integer,
    processing_duration_ms bigint,
    error_code varchar(80),
    error_message varchar(500),
    completed_at timestamp(6),
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    CONSTRAINT pk_image_redaction_jobs PRIMARY KEY (id),
    CONSTRAINT uk_image_redaction_upload UNIQUE (upload_session_id),
    CONSTRAINT fk_image_redaction_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_image_redaction_upload FOREIGN KEY (upload_session_id) REFERENCES file_transfer_upload_sessions (id)
);

CREATE INDEX idx_image_redaction_ready ON image_redaction_jobs (status, available_at, created_at);
CREATE INDEX idx_image_redaction_lease ON image_redaction_jobs (status, lease_until);
ALTER TABLE attachments ADD COLUMN image_redaction_job_id uuid;
ALTER TABLE attachments ADD CONSTRAINT fk_attachments_image_redaction_job
    FOREIGN KEY (image_redaction_job_id) REFERENCES image_redaction_jobs (id);
CREATE INDEX idx_attachments_image_redaction_job ON attachments (image_redaction_job_id);

ALTER TABLE image_redaction_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE image_redaction_jobs FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON image_redaction_jobs
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

--rollback DROP INDEX idx_attachments_image_redaction_job;
--rollback ALTER TABLE attachments DROP CONSTRAINT fk_attachments_image_redaction_job;
--rollback ALTER TABLE attachments DROP COLUMN image_redaction_job_id;
--rollback DROP TABLE image_redaction_jobs;
