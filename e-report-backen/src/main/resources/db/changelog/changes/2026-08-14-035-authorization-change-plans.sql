--liquibase formatted sql

--changeset e-report:2026-08-14-035-authorization-change-plans-mysql dbms:mysql
--comment Persist signed authorization change plans for cross-pod preview and idempotent apply.
CREATE TABLE authorization_change_plans (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    target_membership_id BINARY(16) NOT NULL,
    operator_membership_id BINARY(16) NOT NULL,
    expected_authorization_version BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    request_json LONGTEXT NOT NULL,
    preview_json LONGTEXT NOT NULL,
    request_digest VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    applied_at DATETIME(6),
    idempotency_key VARCHAR(120),
    CONSTRAINT pk_authorization_change_plans PRIMARY KEY (id),
    CONSTRAINT fk_authorization_change_plans_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_authorization_change_plans_target FOREIGN KEY (target_membership_id) REFERENCES users (id),
    CONSTRAINT fk_authorization_change_plans_operator FOREIGN KEY (operator_membership_id) REFERENCES users (id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_authorization_plans_target
    ON authorization_change_plans (tenant_id, target_membership_id, status, expires_at);
CREATE INDEX idx_authorization_plans_operator
    ON authorization_change_plans (tenant_id, operator_membership_id, created_at);

--rollback DROP TABLE authorization_change_plans;

--changeset e-report:2026-08-14-035-authorization-change-plans-h2 dbms:h2
--comment H2 equivalent of persisted authorization change plans.
CREATE TABLE authorization_change_plans (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    target_membership_id UUID NOT NULL,
    operator_membership_id UUID NOT NULL,
    expected_authorization_version BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    request_json CLOB NOT NULL,
    preview_json CLOB NOT NULL,
    request_digest VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    applied_at TIMESTAMP(6),
    idempotency_key VARCHAR(120),
    CONSTRAINT fk_authorization_change_plans_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_authorization_change_plans_target FOREIGN KEY (target_membership_id) REFERENCES users (id),
    CONSTRAINT fk_authorization_change_plans_operator FOREIGN KEY (operator_membership_id) REFERENCES users (id)
);
CREATE INDEX idx_authorization_plans_target
    ON authorization_change_plans (tenant_id, target_membership_id, status, expires_at);
CREATE INDEX idx_authorization_plans_operator
    ON authorization_change_plans (tenant_id, operator_membership_id, created_at);

--rollback DROP TABLE authorization_change_plans;
