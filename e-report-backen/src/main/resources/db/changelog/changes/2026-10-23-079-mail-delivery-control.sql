--liquibase formatted sql

--changeset e-report:2026-10-23-079-mail-delivery-control
--comment Belongs to e-report 6.10.0. Durable recipient checkpoints retain every attempt in the delivery ledger.
CREATE TABLE email_dispatches (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    dispatch_key VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(24) NOT NULL CHECK (status IN ('SENDING', 'SMTP_ACCEPTED', 'FAILED')),
    attempts INTEGER NOT NULL,
    retryable BOOLEAN NOT NULL,
    next_attempt_at TIMESTAMP,
    lease_token UUID,
    locked_until TIMESTAMP,
    failure_code VARCHAR(40),
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);
ALTER TABLE email_deliveries ADD COLUMN dispatch_id UUID REFERENCES email_dispatches(id);
CREATE INDEX idx_email_delivery_dispatch ON email_deliveries (dispatch_id);
ALTER TABLE daily_work_order_report_dispatches ADD COLUMN next_attempt_at TIMESTAMP;
--rollback ALTER TABLE daily_work_order_report_dispatches DROP COLUMN next_attempt_at;
--rollback DROP INDEX idx_email_delivery_dispatch;
--rollback ALTER TABLE email_deliveries DROP COLUMN dispatch_id;
--rollback DROP TABLE email_dispatches;
