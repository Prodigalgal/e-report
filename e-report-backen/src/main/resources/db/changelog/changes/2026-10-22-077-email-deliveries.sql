--liquibase formatted sql

--changeset e-report:2026-10-22-077-email-deliveries
--comment Belongs to e-report 6.9.0. Only masked addresses and classified failures are retained.
CREATE TABLE email_deliveries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    org_unit_id UUID,
    category VARCHAR(40) NOT NULL,
    status VARCHAR(24) NOT NULL,
    related_id UUID,
    recipient_user_id UUID,
    recipient_address VARCHAR(320) NOT NULL,
    sender_address VARCHAR(320),
    subject VARCHAR(255) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    failure_code VARCHAR(40)
);
CREATE INDEX idx_email_delivery_scope_time ON email_deliveries (tenant_id, org_unit_id, started_at, id);
CREATE INDEX idx_email_delivery_time ON email_deliveries (tenant_id, started_at, id);
CREATE INDEX idx_email_delivery_status_time ON email_deliveries (tenant_id, status, started_at, id);
--rollback DROP TABLE email_deliveries;

--changeset e-report:2026-10-22-077-email-delivery-permission
INSERT INTO permissions (id, name, display_name, description)
SELECT CAST('01a168d0-2d20-7000-8000-000000000077' AS UUID), 'notification:email-delivery:view',
       '邮件发送台账查看', '查看职责组织范围内的邮件发送结果和脱敏详情，默认仅超级管理员持有'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'notification:email-delivery:view');
--rollback SELECT 1;
