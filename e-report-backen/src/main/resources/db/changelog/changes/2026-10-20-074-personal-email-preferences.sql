--liquibase formatted sql

--changeset e-report:2026-10-20-074-personal-email-preferences
--comment Belongs to e-report 6.4.0. Preserve current delivery behavior for all existing accounts.
ALTER TABLE users ADD COLUMN email_work_order_created BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE users ADD COLUMN email_work_order_sla_breached BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE users ADD COLUMN email_work_order_assigned BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE users ADD COLUMN email_daily_outstanding_report BOOLEAN DEFAULT TRUE NOT NULL;
--rollback ALTER TABLE users DROP COLUMN email_daily_outstanding_report;
--rollback ALTER TABLE users DROP COLUMN email_work_order_assigned;
--rollback ALTER TABLE users DROP COLUMN email_work_order_sla_breached;
--rollback ALTER TABLE users DROP COLUMN email_work_order_created;
