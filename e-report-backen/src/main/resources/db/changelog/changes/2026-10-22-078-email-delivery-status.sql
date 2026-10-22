--liquibase formatted sql

--changeset e-report:2026-10-22-078-email-delivery-status
--comment Belongs to e-report 6.9.3. Normalize unsuccessful attempts without inferring recipient delivery.
UPDATE email_deliveries
SET status = 'FAILED', failure_code = COALESCE(failure_code, 'INCOMPLETE')
WHERE status = 'UNKNOWN';

ALTER TABLE email_deliveries ADD CONSTRAINT chk_email_delivery_status
    CHECK (status IN ('SENDING', 'SMTP_ACCEPTED', 'FAILED'));
--rollback ALTER TABLE email_deliveries DROP CONSTRAINT chk_email_delivery_status;
