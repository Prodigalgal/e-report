--liquibase formatted sql

--changeset e-report:2026-08-06-002-work-order-lifecycle dbms:mysql,h2
ALTER TABLE fault_reports ADD COLUMN work_order_no VARCHAR(64);
ALTER TABLE fault_reports ADD COLUMN status_code VARCHAR(40);
ALTER TABLE fault_reports ADD COLUMN rework_count INT DEFAULT 0;
ALTER TABLE fault_reports ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE fault_reports ADD COLUMN submitted_at DATETIME(6);
ALTER TABLE fault_reports ADD COLUMN accepted_at DATETIME(6);
ALTER TABLE fault_reports ADD COLUMN closed_at DATETIME(6);
ALTER TABLE fault_reports ADD COLUMN cancelled_at DATETIME(6);

UPDATE fault_reports SET
    work_order_no = CONCAT('LEGACY-', LPAD(id, 12, '0')),
    status_code = CASE
        WHEN status = '已关闭' THEN 'CLOSED'
        WHEN status = '已解决' THEN 'PENDING_VERIFICATION'
        ELSE 'SUBMITTED'
    END,
    rework_count = 0,
    version = 0,
    submitted_at = report_time,
    closed_at = CASE WHEN status = '已关闭' THEN closure_time ELSE NULL END
WHERE work_order_no IS NULL;

ALTER TABLE fault_reports ADD CONSTRAINT uk_fault_reports_tenant_no UNIQUE (tenant_id, work_order_no);
CREATE INDEX idx_fault_reports_tenant_type_status_time ON fault_reports (tenant_id, report_type, status_code, submitted_at);
CREATE INDEX idx_fault_reports_tenant_responsible_status ON fault_reports (tenant_id, responsible_org_id, status_code, submitted_at);
CREATE INDEX idx_fault_reports_tenant_reporter_time ON fault_reports (tenant_id, reported_by_user_id, submitted_at);

CREATE TABLE work_order_assignments (
    id BIGINT AUTO_INCREMENT NOT NULL,
    tenant_id BIGINT NOT NULL,
    work_order_id BIGINT NOT NULL,
    assignee_membership_id BIGINT NOT NULL,
    responsible_org_id BIGINT NOT NULL,
    assigned_by_membership_id BIGINT NOT NULL,
    assignment_type VARCHAR(20) NOT NULL,
    state VARCHAR(30) NOT NULL,
    active_slot VARCHAR(20),
    reason TEXT,
    rejection_reason TEXT,
    assigned_at DATETIME(6) NOT NULL,
    accepted_at DATETIME(6),
    ended_at DATETIME(6),
    CONSTRAINT pk_work_order_assignments PRIMARY KEY (id),
    CONSTRAINT uk_work_order_assignment_slot UNIQUE (tenant_id, work_order_id, active_slot),
    CONSTRAINT fk_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_assignments_work_order FOREIGN KEY (work_order_id) REFERENCES fault_reports (id),
    CONSTRAINT fk_assignments_assignee FOREIGN KEY (assignee_membership_id) REFERENCES users (id),
    CONSTRAINT fk_assignments_assigner FOREIGN KEY (assigned_by_membership_id) REFERENCES users (id),
    CONSTRAINT fk_assignments_org FOREIGN KEY (responsible_org_id) REFERENCES org_units (id)
);
CREATE INDEX idx_assignments_assignee_state ON work_order_assignments (tenant_id, assignee_membership_id, state, assigned_at);

CREATE TABLE work_order_responses (
    id BIGINT AUTO_INCREMENT NOT NULL,
    tenant_id BIGINT NOT NULL,
    work_order_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    submitted_by_membership_id BIGINT NOT NULL,
    resolution_details TEXT NOT NULL,
    resolution_method VARCHAR(255) NOT NULL,
    replacement_completed BOOLEAN,
    replacement_result TEXT,
    submitted_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_work_order_responses PRIMARY KEY (id),
    CONSTRAINT uk_work_order_response_attempt UNIQUE (tenant_id, work_order_id, attempt_no),
    CONSTRAINT fk_responses_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_responses_work_order FOREIGN KEY (work_order_id) REFERENCES fault_reports (id),
    CONSTRAINT fk_responses_assignment FOREIGN KEY (assignment_id) REFERENCES work_order_assignments (id),
    CONSTRAINT fk_responses_submitter FOREIGN KEY (submitted_by_membership_id) REFERENCES users (id)
);

CREATE TABLE work_order_verifications (
    id BIGINT AUTO_INCREMENT NOT NULL,
    tenant_id BIGINT NOT NULL,
    work_order_id BIGINT NOT NULL,
    response_id BIGINT NOT NULL,
    verified_by_membership_id BIGINT NOT NULL,
    decision VARCHAR(20) NOT NULL,
    comment TEXT,
    verification_override BOOLEAN NOT NULL,
    override_reason TEXT,
    verified_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_work_order_verifications PRIMARY KEY (id),
    CONSTRAINT uk_work_order_verification_response UNIQUE (tenant_id, response_id),
    CONSTRAINT fk_verifications_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_verifications_work_order FOREIGN KEY (work_order_id) REFERENCES fault_reports (id),
    CONSTRAINT fk_verifications_response FOREIGN KEY (response_id) REFERENCES work_order_responses (id),
    CONSTRAINT fk_verifications_verifier FOREIGN KEY (verified_by_membership_id) REFERENCES users (id)
);

CREATE TABLE work_order_events (
    id BIGINT AUTO_INCREMENT NOT NULL,
    tenant_id BIGINT NOT NULL,
    work_order_id BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    previous_status_code VARCHAR(40),
    next_status_code VARCHAR(40),
    assignment_id BIGINT,
    response_id BIGINT,
    verification_id BIGINT,
    operator_membership_id BIGINT,
    operator_name_snapshot VARCHAR(100),
    source VARCHAR(30) NOT NULL,
    reason TEXT,
    metadata_json TEXT,
    request_id VARCHAR(100),
    occurred_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_work_order_events PRIMARY KEY (id),
    CONSTRAINT fk_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_events_work_order FOREIGN KEY (work_order_id) REFERENCES fault_reports (id)
);
CREATE INDEX idx_work_order_events_tenant_order_time ON work_order_events (tenant_id, work_order_id, occurred_at);

ALTER TABLE attachments ADD COLUMN response_id BIGINT;
ALTER TABLE attachments ADD CONSTRAINT fk_attachments_response FOREIGN KEY (response_id) REFERENCES work_order_responses (id);
ALTER TABLE work_order_material_usages ADD COLUMN response_id BIGINT;
ALTER TABLE work_order_material_usages ADD CONSTRAINT fk_material_usage_response FOREIGN KEY (response_id) REFERENCES work_order_responses (id);

ALTER TABLE fault_reports ADD COLUMN current_assignment_id BIGINT;
ALTER TABLE fault_reports ADD COLUMN pending_assignment_id BIGINT;
ALTER TABLE fault_reports ADD COLUMN current_response_id BIGINT;
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_current_assignment FOREIGN KEY (current_assignment_id) REFERENCES work_order_assignments (id);
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_pending_assignment FOREIGN KEY (pending_assignment_id) REFERENCES work_order_assignments (id);
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_current_response FOREIGN KEY (current_response_id) REFERENCES work_order_responses (id);

--rollback ALTER TABLE fault_reports DROP COLUMN current_response_id;
--rollback ALTER TABLE fault_reports DROP COLUMN pending_assignment_id;
--rollback ALTER TABLE fault_reports DROP COLUMN current_assignment_id;
--rollback ALTER TABLE work_order_material_usages DROP COLUMN response_id;
--rollback ALTER TABLE attachments DROP COLUMN response_id;
--rollback DROP TABLE work_order_events;
--rollback DROP TABLE work_order_verifications;
--rollback DROP TABLE work_order_responses;
--rollback DROP TABLE work_order_assignments;
--rollback ALTER TABLE fault_reports DROP COLUMN cancelled_at;
--rollback ALTER TABLE fault_reports DROP COLUMN closed_at;
--rollback ALTER TABLE fault_reports DROP COLUMN accepted_at;
--rollback ALTER TABLE fault_reports DROP COLUMN submitted_at;
--rollback ALTER TABLE fault_reports DROP COLUMN version;
--rollback ALTER TABLE fault_reports DROP COLUMN rework_count;
--rollback ALTER TABLE fault_reports DROP COLUMN status_code;
--rollback ALTER TABLE fault_reports DROP COLUMN work_order_no;

--changeset e-report:2026-08-06-002-lifecycle-constraints-mysql dbms:mysql
ALTER TABLE fault_reports MODIFY COLUMN work_order_no VARCHAR(64) NOT NULL;
ALTER TABLE fault_reports MODIFY COLUMN status_code VARCHAR(40) NOT NULL;
ALTER TABLE fault_reports MODIFY COLUMN rework_count INT NOT NULL;
ALTER TABLE fault_reports MODIFY COLUMN version BIGINT NOT NULL;
ALTER TABLE fault_reports MODIFY COLUMN submitted_at DATETIME(6) NOT NULL;

--rollback ALTER TABLE fault_reports MODIFY COLUMN submitted_at DATETIME(6) NULL;
--rollback ALTER TABLE fault_reports MODIFY COLUMN work_order_no VARCHAR(64) NULL;

--changeset e-report:2026-08-06-002-lifecycle-constraints-h2 dbms:h2
ALTER TABLE fault_reports ALTER COLUMN work_order_no SET NOT NULL;
ALTER TABLE fault_reports ALTER COLUMN status_code SET NOT NULL;
ALTER TABLE fault_reports ALTER COLUMN rework_count SET NOT NULL;
ALTER TABLE fault_reports ALTER COLUMN version SET NOT NULL;
ALTER TABLE fault_reports ALTER COLUMN submitted_at SET NOT NULL;

--rollback ALTER TABLE fault_reports ALTER COLUMN submitted_at DROP NOT NULL;
--rollback ALTER TABLE fault_reports ALTER COLUMN work_order_no DROP NOT NULL;
