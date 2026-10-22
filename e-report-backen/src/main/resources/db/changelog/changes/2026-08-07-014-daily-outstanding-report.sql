--liquibase formatted sql

--changeset e-report:2026-08-07-014-daily-outstanding-report-mysql dbms:mysql
CREATE TABLE daily_work_order_report_dispatches (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    org_unit_id BINARY(16) NOT NULL,
    report_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    work_order_count INT NOT NULL DEFAULT 0,
    recipient_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,
    error_summary VARCHAR(1000),
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6),
    CONSTRAINT pk_daily_work_order_report_dispatches PRIMARY KEY (id),
    CONSTRAINT uk_daily_report_dispatch_scope_date UNIQUE (tenant_id, org_unit_id, report_date),
    CONSTRAINT fk_daily_report_dispatch_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_daily_report_dispatch_org FOREIGN KEY (org_unit_id) REFERENCES org_units (id)
);
CREATE INDEX idx_daily_report_dispatch_status
    ON daily_work_order_report_dispatches (tenant_id, status, report_date);
CREATE INDEX idx_fault_reports_tenant_reporting_status
    ON fault_reports (tenant_id, reporting_org_id, status_code, submitted_at);

--rollback DROP INDEX idx_fault_reports_tenant_reporting_status ON fault_reports;
--rollback DROP TABLE daily_work_order_report_dispatches;

--changeset e-report:2026-08-07-014-daily-outstanding-report-h2 dbms:h2
CREATE TABLE daily_work_order_report_dispatches (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    org_unit_id UUID NOT NULL,
    report_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    work_order_count INT NOT NULL DEFAULT 0,
    recipient_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,
    error_summary VARCHAR(1000),
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    CONSTRAINT pk_daily_work_order_report_dispatches PRIMARY KEY (id),
    CONSTRAINT uk_daily_report_dispatch_scope_date UNIQUE (tenant_id, org_unit_id, report_date),
    CONSTRAINT fk_daily_report_dispatch_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_daily_report_dispatch_org FOREIGN KEY (org_unit_id) REFERENCES org_units (id)
);
CREATE INDEX idx_daily_report_dispatch_status
    ON daily_work_order_report_dispatches (tenant_id, status, report_date);
CREATE INDEX idx_fault_reports_tenant_reporting_status
    ON fault_reports (tenant_id, reporting_org_id, status_code, submitted_at);

--rollback DROP INDEX idx_fault_reports_tenant_reporting_status;
--rollback DROP TABLE daily_work_order_report_dispatches;
