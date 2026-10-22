--liquibase formatted sql

--changeset e-report:2026-08-14-038-organization-move-audit-mysql dbms:mysql
--comment Persist auditable organization subtree moves.
CREATE TABLE organization_move_events (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    org_unit_id BINARY(16) NOT NULL,
    old_parent_id BINARY(16) NOT NULL,
    new_parent_id BINARY(16) NOT NULL,
    old_path VARCHAR(500) NOT NULL,
    new_path VARCHAR(500) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    operator_membership_id BINARY(16),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_organization_move_events PRIMARY KEY (id),
    CONSTRAINT fk_organization_move_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_organization_move_events_org FOREIGN KEY (org_unit_id) REFERENCES org_units (id),
    CONSTRAINT fk_organization_move_events_old_parent FOREIGN KEY (old_parent_id) REFERENCES org_units (id),
    CONSTRAINT fk_organization_move_events_new_parent FOREIGN KEY (new_parent_id) REFERENCES org_units (id),
    CONSTRAINT fk_organization_move_events_operator FOREIGN KEY (operator_membership_id) REFERENCES users (id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_organization_move_events_org
    ON organization_move_events (tenant_id, org_unit_id, created_at);

--rollback DROP TABLE organization_move_events;

--changeset e-report:2026-08-14-038-organization-move-audit-h2 dbms:h2
--comment H2 equivalent of auditable organization subtree moves.
CREATE TABLE organization_move_events (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    org_unit_id UUID NOT NULL,
    old_parent_id UUID NOT NULL,
    new_parent_id UUID NOT NULL,
    old_path VARCHAR(500) NOT NULL,
    new_path VARCHAR(500) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    operator_membership_id UUID,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_organization_move_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_organization_move_events_org FOREIGN KEY (org_unit_id) REFERENCES org_units (id),
    CONSTRAINT fk_organization_move_events_old_parent FOREIGN KEY (old_parent_id) REFERENCES org_units (id),
    CONSTRAINT fk_organization_move_events_new_parent FOREIGN KEY (new_parent_id) REFERENCES org_units (id),
    CONSTRAINT fk_organization_move_events_operator FOREIGN KEY (operator_membership_id) REFERENCES users (id)
);
CREATE INDEX idx_organization_move_events_org
    ON organization_move_events (tenant_id, org_unit_id, created_at);

--rollback DROP TABLE organization_move_events;
