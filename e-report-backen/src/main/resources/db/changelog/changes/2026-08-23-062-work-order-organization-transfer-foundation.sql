--liquibase formatted sql

--changeset e-report:2026-08-23-062-work-order-owner-mysql dbms:mysql
ALTER TABLE fault_reports ADD COLUMN owner_org_id BINARY(16) NULL AFTER responsible_org_id;
UPDATE fault_reports work_order
LEFT JOIN users reporter ON reporter.id = work_order.reported_by_user_id
LEFT JOIN tenants tenant ON tenant.id = work_order.tenant_id
SET work_order.owner_org_id = COALESCE(
    work_order.responsible_org_id,
    work_order.reporting_org_id,
    reporter.org_unit_id,
    tenant.root_org_unit_id
);
--rollback ALTER TABLE fault_reports DROP COLUMN owner_org_id;

--changeset e-report:2026-08-23-062-work-order-owner-not-null-mysql dbms:mysql
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM fault_reports WHERE owner_org_id IS NULL
ALTER TABLE fault_reports MODIFY owner_org_id BINARY(16) NOT NULL;
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_owner_org FOREIGN KEY (owner_org_id) REFERENCES org_units(id);
CREATE INDEX idx_fault_reports_tenant_owner_status ON fault_reports (tenant_id, owner_org_id, status_code, submitted_at);
--rollback DROP INDEX idx_fault_reports_tenant_owner_status ON fault_reports;
--rollback ALTER TABLE fault_reports DROP FOREIGN KEY fk_fault_reports_owner_org;

--changeset e-report:2026-08-23-062-work-order-owner-h2 dbms:h2
ALTER TABLE fault_reports ADD COLUMN owner_org_id UUID;
UPDATE fault_reports work_order
SET owner_org_id = COALESCE(
    work_order.responsible_org_id,
    work_order.reporting_org_id,
    (SELECT reporter.org_unit_id FROM users reporter WHERE reporter.id = work_order.reported_by_user_id),
    (SELECT tenant.root_org_unit_id FROM tenants tenant WHERE tenant.id = work_order.tenant_id)
);
--rollback ALTER TABLE fault_reports DROP COLUMN owner_org_id;

--changeset e-report:2026-08-23-062-work-order-owner-not-null-h2 dbms:h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM fault_reports WHERE owner_org_id IS NULL
ALTER TABLE fault_reports ALTER COLUMN owner_org_id SET NOT NULL;
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_owner_org FOREIGN KEY (owner_org_id) REFERENCES org_units(id);
CREATE INDEX idx_fault_reports_tenant_owner_status ON fault_reports (tenant_id, owner_org_id, status_code, submitted_at);
--rollback DROP INDEX idx_fault_reports_tenant_owner_status;
--rollback ALTER TABLE fault_reports DROP CONSTRAINT fk_fault_reports_owner_org;

--changeset e-report:2026-08-23-062-work-order-transfer-tables-mysql dbms:mysql
CREATE TABLE work_order_organization_participations (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    work_order_id BINARY(16) NOT NULL,
    org_unit_id BINARY(16) NOT NULL,
    organization_name_snapshot VARCHAR(100) NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    created_by_membership_id BINARY(16) NULL,
    reason VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_work_order_org_participation_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_work_order_org_participation_order FOREIGN KEY (work_order_id) REFERENCES fault_reports(id),
    CONSTRAINT fk_work_order_org_participation_org FOREIGN KEY (org_unit_id) REFERENCES org_units(id),
    CONSTRAINT fk_work_order_org_participation_creator FOREIGN KEY (created_by_membership_id) REFERENCES users(id),
    CONSTRAINT ck_work_order_org_relation CHECK (relation_type IN ('REPORTING','OWNER','PREVIOUS_OWNER','PENDING_TRANSFER_TARGET'))
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_work_order_org_participation_order
    ON work_order_organization_participations (tenant_id, work_order_id, relation_type, active);
CREATE INDEX idx_work_order_org_participation_org
    ON work_order_organization_participations (tenant_id, org_unit_id, active);

INSERT INTO work_order_organization_participations
(id, tenant_id, work_order_id, org_unit_id, organization_name_snapshot,
 relation_type, active, started_at, ended_at, created_by_membership_id, reason)
SELECT UUID_TO_BIN(UUID(), 1), work_order.tenant_id, work_order.id, work_order.reporting_org_id,
       reporting_org.name, 'REPORTING', TRUE, work_order.submitted_at, NULL,
       work_order.reported_by_user_id, '5.0.6 迁移：提单组织'
FROM fault_reports work_order
JOIN org_units reporting_org ON reporting_org.id = work_order.reporting_org_id
WHERE work_order.reporting_org_id IS NOT NULL;
INSERT INTO work_order_organization_participations
(id, tenant_id, work_order_id, org_unit_id, organization_name_snapshot,
 relation_type, active, started_at, ended_at, created_by_membership_id, reason)
SELECT UUID_TO_BIN(UUID(), 1), work_order.tenant_id, work_order.id, work_order.owner_org_id,
       owner_org.name, 'OWNER', TRUE, work_order.submitted_at, NULL,
       work_order.reported_by_user_id, '5.0.6 迁移：当前归属组织'
FROM fault_reports work_order
JOIN org_units owner_org ON owner_org.id = work_order.owner_org_id;

CREATE TABLE work_order_organization_transfers (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    work_order_id BINARY(16) NOT NULL,
    source_org_unit_id BINARY(16) NOT NULL,
    target_org_unit_id BINARY(16) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expected_work_order_version BIGINT NOT NULL,
    requested_by_membership_id BINARY(16) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    request_reason VARCHAR(2000) NOT NULL,
    decided_by_membership_id BINARY(16) NULL,
    decided_at DATETIME(6) NULL,
    decision_comment VARCHAR(2000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_work_order_transfer_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_work_order_transfer_order FOREIGN KEY (work_order_id) REFERENCES fault_reports(id),
    CONSTRAINT fk_work_order_transfer_source_org FOREIGN KEY (source_org_unit_id) REFERENCES org_units(id),
    CONSTRAINT fk_work_order_transfer_target_org FOREIGN KEY (target_org_unit_id) REFERENCES org_units(id),
    CONSTRAINT fk_work_order_transfer_requester FOREIGN KEY (requested_by_membership_id) REFERENCES users(id),
    CONSTRAINT fk_work_order_transfer_decider FOREIGN KEY (decided_by_membership_id) REFERENCES users(id),
    CONSTRAINT ck_work_order_transfer_status CHECK (status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED'))
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_work_order_transfers_order_status
    ON work_order_organization_transfers (tenant_id, work_order_id, status, requested_at);
CREATE INDEX idx_work_order_transfers_target_status
    ON work_order_organization_transfers (tenant_id, target_org_unit_id, status, requested_at);
--rollback DROP TABLE work_order_organization_transfers;
--rollback DROP TABLE work_order_organization_participations;

--changeset e-report:2026-08-23-062-work-order-transfer-tables-h2 dbms:h2
CREATE TABLE work_order_organization_participations (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    work_order_id UUID NOT NULL,
    org_unit_id UUID NOT NULL,
    organization_name_snapshot VARCHAR(100) NOT NULL,
    relation_type VARCHAR(32) NOT NULL CHECK (relation_type IN ('REPORTING','OWNER','PREVIOUS_OWNER','PENDING_TRANSFER_TARGET')),
    active BOOLEAN NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    ended_at TIMESTAMP(6),
    created_by_membership_id UUID,
    reason VARCHAR(1000),
    CONSTRAINT fk_work_order_org_participation_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_work_order_org_participation_order FOREIGN KEY (work_order_id) REFERENCES fault_reports(id),
    CONSTRAINT fk_work_order_org_participation_org FOREIGN KEY (org_unit_id) REFERENCES org_units(id),
    CONSTRAINT fk_work_order_org_participation_creator FOREIGN KEY (created_by_membership_id) REFERENCES users(id)
);
CREATE INDEX idx_work_order_org_participation_order
    ON work_order_organization_participations (tenant_id, work_order_id, relation_type, active);
CREATE INDEX idx_work_order_org_participation_org
    ON work_order_organization_participations (tenant_id, org_unit_id, active);

INSERT INTO work_order_organization_participations
(id, tenant_id, work_order_id, org_unit_id, organization_name_snapshot,
 relation_type, active, started_at, ended_at, created_by_membership_id, reason)
SELECT RANDOM_UUID(), work_order.tenant_id, work_order.id, work_order.reporting_org_id,
       reporting_org.name, 'REPORTING', TRUE, work_order.submitted_at, NULL,
       work_order.reported_by_user_id, '5.0.6 迁移：提单组织'
FROM fault_reports work_order
JOIN org_units reporting_org ON reporting_org.id = work_order.reporting_org_id
WHERE work_order.reporting_org_id IS NOT NULL;
INSERT INTO work_order_organization_participations
(id, tenant_id, work_order_id, org_unit_id, organization_name_snapshot,
 relation_type, active, started_at, ended_at, created_by_membership_id, reason)
SELECT RANDOM_UUID(), work_order.tenant_id, work_order.id, work_order.owner_org_id,
       owner_org.name, 'OWNER', TRUE, work_order.submitted_at, NULL,
       work_order.reported_by_user_id, '5.0.6 迁移：当前归属组织'
FROM fault_reports work_order
JOIN org_units owner_org ON owner_org.id = work_order.owner_org_id;

CREATE TABLE work_order_organization_transfers (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    work_order_id UUID NOT NULL,
    source_org_unit_id UUID NOT NULL,
    target_org_unit_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED')),
    expected_work_order_version BIGINT NOT NULL,
    requested_by_membership_id UUID NOT NULL,
    requested_at TIMESTAMP(6) NOT NULL,
    request_reason VARCHAR(2000) NOT NULL,
    decided_by_membership_id UUID,
    decided_at TIMESTAMP(6),
    decision_comment VARCHAR(2000),
    CONSTRAINT fk_work_order_transfer_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_work_order_transfer_order FOREIGN KEY (work_order_id) REFERENCES fault_reports(id),
    CONSTRAINT fk_work_order_transfer_source_org FOREIGN KEY (source_org_unit_id) REFERENCES org_units(id),
    CONSTRAINT fk_work_order_transfer_target_org FOREIGN KEY (target_org_unit_id) REFERENCES org_units(id),
    CONSTRAINT fk_work_order_transfer_requester FOREIGN KEY (requested_by_membership_id) REFERENCES users(id),
    CONSTRAINT fk_work_order_transfer_decider FOREIGN KEY (decided_by_membership_id) REFERENCES users(id)
);
CREATE INDEX idx_work_order_transfers_order_status
    ON work_order_organization_transfers (tenant_id, work_order_id, status, requested_at);
CREATE INDEX idx_work_order_transfers_target_status
    ON work_order_organization_transfers (tenant_id, target_org_unit_id, status, requested_at);
--rollback DROP TABLE work_order_organization_transfers;
--rollback DROP TABLE work_order_organization_participations;

--changeset e-report:2026-08-23-062-work-order-transfer-schema-assert dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:2 SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_SCHEMA) = LOWER(SCHEMA()) AND LOWER(TABLE_NAME) IN ('work_order_organization_participations','work_order_organization_transfers')
SELECT 1;
--rollback SELECT 1;
