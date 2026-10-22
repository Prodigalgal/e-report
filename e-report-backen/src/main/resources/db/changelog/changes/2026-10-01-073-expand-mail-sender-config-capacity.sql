--liquibase formatted sql

--changeset e-report:2026-10-01-073-expand-mail-sender-config-capacity dbms:mysql
--comment Belongs to e-report 6.2.0. PostgreSQL TEXT and H2 TEXT already support the serialized sender pool size.
ALTER TABLE tenant_app_config MODIFY COLUMN config_value MEDIUMTEXT NULL;
ALTER TABLE org_app_config MODIFY COLUMN config_value MEDIUMTEXT NULL;

--rollback SELECT 1;
