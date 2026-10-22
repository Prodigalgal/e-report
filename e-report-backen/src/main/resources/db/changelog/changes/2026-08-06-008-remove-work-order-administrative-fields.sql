--liquibase formatted sql

--changeset e-report:2026-08-06-008-remove-work-order-administrative-fields dbms:mysql,h2
--comment Destructive cleanup explicitly approved: work orders retain only detailed location and GPS coordinates.
ALTER TABLE fault_reports DROP COLUMN township;
ALTER TABLE fault_reports DROP COLUMN village_or_street;

--rollback ALTER TABLE fault_reports ADD COLUMN township VARCHAR(100);
--rollback ALTER TABLE fault_reports ADD COLUMN village_or_street VARCHAR(100);
