--liquibase formatted sql

--changeset e-report:2026-09-01-068-work-order-department-processing dbms:postgresql
ALTER TABLE work_order_department_participations
    DROP CONSTRAINT IF EXISTS ck_work_order_dept_relation;
ALTER TABLE work_order_department_participations
    DROP CONSTRAINT IF EXISTS work_order_department_participations_relation_type_check;
ALTER TABLE work_order_department_participations
    ADD CONSTRAINT ck_work_order_dept_relation
        CHECK (relation_type IN ('REPORTING','RESPONSIBLE','PROCESSING','VERIFICATION','COPIED'));

--rollback ALTER TABLE work_order_department_participations DROP CONSTRAINT IF EXISTS ck_work_order_dept_relation;
--rollback ALTER TABLE work_order_department_participations ADD CONSTRAINT ck_work_order_dept_relation CHECK (relation_type IN ('REPORTING','RESPONSIBLE','VERIFICATION','COPIED'));

--changeset e-report:2026-09-01-068-work-order-department-processing-h2 dbms:h2
ALTER TABLE work_order_department_participations
    DROP CONSTRAINT IF EXISTS ck_work_order_dept_relation;
ALTER TABLE work_order_department_participations
    DROP CONSTRAINT IF EXISTS work_order_department_participations_relation_type_check;
ALTER TABLE work_order_department_participations
    ADD CONSTRAINT ck_work_order_dept_relation
        CHECK (relation_type IN ('REPORTING','RESPONSIBLE','PROCESSING','VERIFICATION','COPIED'));

--rollback ALTER TABLE work_order_department_participations DROP CONSTRAINT IF EXISTS ck_work_order_dept_relation;
--rollback ALTER TABLE work_order_department_participations ADD CONSTRAINT ck_work_order_dept_relation CHECK (relation_type IN ('REPORTING','RESPONSIBLE','VERIFICATION','COPIED'));
