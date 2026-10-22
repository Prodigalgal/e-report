--liquibase formatted sql

--changeset e-report:2026-09-12-069-work-order-organization-processing dbms:postgresql
ALTER TABLE work_order_organization_participations
    DROP CONSTRAINT IF EXISTS ck_work_order_org_relation;
ALTER TABLE work_order_organization_participations
    DROP CONSTRAINT IF EXISTS work_order_organization_participations_relation_type_check;
ALTER TABLE work_order_organization_participations
    ADD CONSTRAINT ck_work_order_org_relation
        CHECK (relation_type IN ('REPORTING','OWNER','PREVIOUS_OWNER','PROCESSING','PENDING_TRANSFER_TARGET'));

--rollback ALTER TABLE work_order_organization_participations DROP CONSTRAINT IF EXISTS ck_work_order_org_relation;
--rollback ALTER TABLE work_order_organization_participations ADD CONSTRAINT ck_work_order_org_relation CHECK (relation_type IN ('REPORTING','OWNER','PREVIOUS_OWNER','PENDING_TRANSFER_TARGET'));

--changeset e-report:2026-09-12-069-work-order-organization-processing-h2 dbms:h2
ALTER TABLE work_order_organization_participations
    DROP CONSTRAINT IF EXISTS ck_work_order_org_relation;
ALTER TABLE work_order_organization_participations
    DROP CONSTRAINT IF EXISTS work_order_organization_participations_relation_type_check;
ALTER TABLE work_order_organization_participations
    ADD CONSTRAINT ck_work_order_org_relation
        CHECK (relation_type IN ('REPORTING','OWNER','PREVIOUS_OWNER','PROCESSING','PENDING_TRANSFER_TARGET'));

--rollback ALTER TABLE work_order_organization_participations DROP CONSTRAINT IF EXISTS ck_work_order_org_relation;
--rollback ALTER TABLE work_order_organization_participations ADD CONSTRAINT ck_work_order_org_relation CHECK (relation_type IN ('REPORTING','OWNER','PREVIOUS_OWNER','PENDING_TRANSFER_TARGET'));
