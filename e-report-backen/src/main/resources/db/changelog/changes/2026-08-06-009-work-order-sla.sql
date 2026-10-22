--liquibase formatted sql

--changeset e-report:2026-08-06-009-work-order-sla-structure dbms:mysql,h2
CREATE TABLE work_order_sla_policies (
    id BIGINT AUTO_INCREMENT NOT NULL,
    tenant_id BIGINT NOT NULL,
    report_type VARCHAR(50) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    claim_minutes INT NOT NULL,
    response_minutes INT NOT NULL,
    verification_minutes INT NOT NULL,
    pause_on_hold BOOLEAN NOT NULL,
    max_single_hold_minutes INT NOT NULL,
    max_total_hold_minutes INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_work_order_sla_policies PRIMARY KEY (id),
    CONSTRAINT uk_sla_policy_tenant_type_priority UNIQUE (tenant_id, report_type, priority),
    CONSTRAINT fk_sla_policy_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX idx_sla_policy_tenant_type ON work_order_sla_policies (tenant_id, report_type);

ALTER TABLE fault_reports ADD COLUMN priority VARCHAR(20) DEFAULT 'NORMAL';
ALTER TABLE fault_reports ADD COLUMN sla_stage VARCHAR(30) DEFAULT 'CLAIM';
ALTER TABLE fault_reports ADD COLUMN sla_due_at DATETIME(6);
ALTER TABLE fault_reports ADD COLUMN sla_paused_at DATETIME(6);
ALTER TABLE fault_reports ADD COLUMN total_hold_seconds BIGINT DEFAULT 0;
ALTER TABLE fault_reports ADD COLUMN sla_breached_at DATETIME(6);
ALTER TABLE fault_reports ADD COLUMN sla_ever_breached BOOLEAN DEFAULT FALSE;

INSERT INTO work_order_sla_policies (
    tenant_id, report_type, priority, claim_minutes, response_minutes, verification_minutes,
    pause_on_hold, max_single_hold_minutes, max_total_hold_minutes, created_at, updated_at
)
SELECT t.id, report_types.report_type, priorities.priority,
       priorities.claim_minutes, priorities.response_minutes, priorities.verification_minutes,
       TRUE, 1440, 4320, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants t
CROSS JOIN (
    SELECT 'NORMAL_FAULT' AS report_type
    UNION ALL SELECT 'UNCLOSED_BOX'
    UNION ALL SELECT 'DAMAGED_BOX_REPLACEMENT'
) report_types
CROSS JOIN (
    SELECT 'URGENT' AS priority, 15 AS claim_minutes, 120 AS response_minutes, 60 AS verification_minutes
    UNION ALL SELECT 'HIGH', 30, 240, 120
    UNION ALL SELECT 'NORMAL', 120, 1440, 480
    UNION ALL SELECT 'LOW', 240, 2880, 1440
) priorities;

UPDATE fault_reports
SET priority = 'NORMAL',
    sla_stage = CASE status_code
        WHEN 'SUBMITTED' THEN 'CLAIM'
        WHEN 'PENDING_VERIFICATION' THEN 'VERIFICATION'
        WHEN 'CLOSED' THEN 'COMPLETED'
        WHEN 'CANCELLED' THEN 'COMPLETED'
        ELSE 'RESPONSE'
    END,
    sla_paused_at = CASE WHEN status_code = 'ON_HOLD' THEN COALESCE(last_updated_time, submitted_at) ELSE NULL END,
    total_hold_seconds = 0,
    sla_breached_at = NULL,
    sla_ever_breached = FALSE;

CREATE INDEX idx_fault_reports_tenant_sla_due ON fault_reports (tenant_id, sla_stage, sla_due_at);
CREATE INDEX idx_fault_reports_tenant_sla_breach ON fault_reports (tenant_id, sla_ever_breached, status_code);

--rollback DROP INDEX idx_fault_reports_tenant_sla_breach ON fault_reports;
--rollback DROP INDEX idx_fault_reports_tenant_sla_due ON fault_reports;
--rollback ALTER TABLE fault_reports DROP COLUMN sla_ever_breached;
--rollback ALTER TABLE fault_reports DROP COLUMN sla_breached_at;
--rollback ALTER TABLE fault_reports DROP COLUMN total_hold_seconds;
--rollback ALTER TABLE fault_reports DROP COLUMN sla_paused_at;
--rollback ALTER TABLE fault_reports DROP COLUMN sla_due_at;
--rollback ALTER TABLE fault_reports DROP COLUMN sla_stage;
--rollback ALTER TABLE fault_reports DROP COLUMN priority;
--rollback DROP TABLE work_order_sla_policies;

--changeset e-report:2026-08-06-009-work-order-sla-backfill-mysql dbms:mysql
UPDATE fault_reports
SET sla_due_at = CASE sla_stage
    WHEN 'CLAIM' THEN DATE_ADD(submitted_at, INTERVAL 120 MINUTE)
    WHEN 'RESPONSE' THEN DATE_ADD(COALESCE(accepted_at, submitted_at), INTERVAL 1440 MINUTE)
    WHEN 'VERIFICATION' THEN DATE_ADD(COALESCE(resolution_time, submitted_at), INTERVAL 480 MINUTE)
    ELSE NULL
END;
ALTER TABLE fault_reports MODIFY COLUMN priority VARCHAR(20) NOT NULL;
ALTER TABLE fault_reports MODIFY COLUMN sla_stage VARCHAR(30) NOT NULL;
ALTER TABLE fault_reports MODIFY COLUMN total_hold_seconds BIGINT NOT NULL;
ALTER TABLE fault_reports MODIFY COLUMN sla_ever_breached BOOLEAN NOT NULL;

--rollback SELECT 1;

--changeset e-report:2026-08-06-009-work-order-sla-backfill-h2 dbms:h2
UPDATE fault_reports
SET sla_due_at = CASE sla_stage
    WHEN 'CLAIM' THEN DATEADD('MINUTE', 120, submitted_at)
    WHEN 'RESPONSE' THEN DATEADD('MINUTE', 1440, COALESCE(accepted_at, submitted_at))
    WHEN 'VERIFICATION' THEN DATEADD('MINUTE', 480, COALESCE(resolution_time, submitted_at))
    ELSE NULL
END;
ALTER TABLE fault_reports ALTER COLUMN priority SET NOT NULL;
ALTER TABLE fault_reports ALTER COLUMN sla_stage SET NOT NULL;
ALTER TABLE fault_reports ALTER COLUMN total_hold_seconds SET NOT NULL;
ALTER TABLE fault_reports ALTER COLUMN sla_ever_breached SET NOT NULL;

--rollback SELECT 1;
