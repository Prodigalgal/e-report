--liquibase formatted sql

--changeset e-report:2026-08-06-011-legacy-identifiers-swap-mysql dbms:mysql
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:5 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('api_resource_permissions','external_command_records','external_user_bindings','wecom_group_chat_configs','wecom_pending_materials')
--precondition-sql-check expectedResult:0 SELECT SUM(problem_count) FROM (SELECT COUNT(*) AS problem_count FROM api_resource_permissions WHERE id_uuid IS NULL UNION ALL SELECT COUNT(*) AS problem_count FROM external_command_records WHERE id_uuid IS NULL OR (work_order_id IS NOT NULL AND work_order_id_uuid IS NULL) UNION ALL SELECT COUNT(*) AS problem_count FROM external_user_bindings WHERE id_uuid IS NULL OR local_user_id_uuid IS NULL UNION ALL SELECT COUNT(*) AS problem_count FROM wecom_group_chat_configs WHERE id_uuid IS NULL UNION ALL SELECT COUNT(*) AS problem_count FROM wecom_pending_materials WHERE id_uuid IS NULL OR (command_record_id IS NOT NULL AND command_record_id_uuid IS NULL) OR (work_order_id IS NOT NULL AND work_order_id_uuid IS NULL)) legacy_uuid_validation
DROP INDEX idx_external_command_work_order ON external_command_records;
DROP INDEX idx_external_user_binding_user ON external_user_bindings;
DROP INDEX idx_wecom_material_command_record ON wecom_pending_materials;
DROP INDEX idx_wecom_material_work_order ON wecom_pending_materials;

ALTER TABLE api_resource_permissions DROP PRIMARY KEY, DROP COLUMN id, CHANGE COLUMN id_uuid id BINARY(16) NOT NULL, ADD PRIMARY KEY (id);
ALTER TABLE external_command_records DROP PRIMARY KEY, DROP COLUMN id, DROP COLUMN work_order_id, CHANGE COLUMN id_uuid id BINARY(16) NOT NULL, CHANGE COLUMN work_order_id_uuid work_order_id BINARY(16) NULL, ADD PRIMARY KEY (id);
ALTER TABLE external_user_bindings DROP PRIMARY KEY, DROP COLUMN id, DROP COLUMN local_user_id, CHANGE COLUMN id_uuid id BINARY(16) NOT NULL, CHANGE COLUMN local_user_id_uuid local_user_id BINARY(16) NOT NULL, ADD PRIMARY KEY (id);
ALTER TABLE wecom_group_chat_configs DROP PRIMARY KEY, DROP COLUMN id, CHANGE COLUMN id_uuid id BINARY(16) NOT NULL, ADD PRIMARY KEY (id);
ALTER TABLE wecom_pending_materials DROP PRIMARY KEY, DROP COLUMN id, DROP COLUMN command_record_id, DROP COLUMN work_order_id, CHANGE COLUMN id_uuid id BINARY(16) NOT NULL, CHANGE COLUMN command_record_id_uuid command_record_id BINARY(16) NULL, CHANGE COLUMN work_order_id_uuid work_order_id BINARY(16) NULL, ADD PRIMARY KEY (id);

CREATE INDEX idx_external_command_work_order ON external_command_records (work_order_id);
CREATE INDEX idx_external_user_binding_user ON external_user_bindings (local_user_id);
CREATE INDEX idx_wecom_material_command_record ON wecom_pending_materials (command_record_id);
CREATE INDEX idx_wecom_material_work_order ON wecom_pending_materials (work_order_id);
ALTER TABLE external_user_bindings ADD CONSTRAINT fk_external_user_binding_user FOREIGN KEY (local_user_id) REFERENCES users (id);
