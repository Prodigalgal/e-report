--liquibase formatted sql

--changeset e-report:2026-09-12-070-h2-organization-processing-constraint dbms:h2
-- H2's baseline check constraint was unnamed and receives a generated name;
-- remove that legacy constraint after 069 adds the named replacement.
ALTER TABLE work_order_organization_participations
    DROP CONSTRAINT IF EXISTS CONSTRAINT_E13;

--rollback ALTER TABLE work_order_organization_participations DROP CONSTRAINT IF EXISTS ck_work_order_org_relation;
