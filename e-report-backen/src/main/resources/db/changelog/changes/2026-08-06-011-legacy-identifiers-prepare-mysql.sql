--liquibase formatted sql

--changeset e-report:2026-08-06-011-legacy-identifiers-prepare-mysql dbms:mysql
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:5 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('api_resource_permissions','external_command_records','external_user_bindings','wecom_group_chat_configs','wecom_pending_materials')
ALTER TABLE api_resource_permissions ADD COLUMN id_uuid BINARY(16);
ALTER TABLE external_command_records ADD COLUMN id_uuid BINARY(16), ADD COLUMN work_order_id_uuid BINARY(16);
ALTER TABLE external_user_bindings ADD COLUMN id_uuid BINARY(16), ADD COLUMN local_user_id_uuid BINARY(16);
ALTER TABLE wecom_group_chat_configs ADD COLUMN id_uuid BINARY(16);
ALTER TABLE wecom_pending_materials ADD COLUMN id_uuid BINARY(16), ADD COLUMN command_record_id_uuid BINARY(16), ADD COLUMN work_order_id_uuid BINARY(16);

UPDATE api_resource_permissions SET id_uuid = UUID_TO_BIN(UUID());
UPDATE external_command_records SET id_uuid = UUID_TO_BIN(UUID());
UPDATE external_user_bindings SET id_uuid = UUID_TO_BIN(UUID());
UPDATE wecom_group_chat_configs SET id_uuid = UUID_TO_BIN(UUID());
UPDATE wecom_pending_materials SET id_uuid = UUID_TO_BIN(UUID());

UPDATE external_command_records child JOIN fault_reports parent ON parent.id = child.work_order_id SET child.work_order_id_uuid = parent.id_uuid WHERE child.work_order_id IS NOT NULL;
UPDATE external_user_bindings child JOIN users parent ON parent.id = child.local_user_id SET child.local_user_id_uuid = parent.id_uuid;
UPDATE wecom_pending_materials child JOIN external_command_records parent ON parent.id = child.command_record_id SET child.command_record_id_uuid = parent.id_uuid WHERE child.command_record_id IS NOT NULL;
UPDATE wecom_pending_materials child JOIN fault_reports parent ON parent.id = child.work_order_id SET child.work_order_id_uuid = parent.id_uuid WHERE child.work_order_id IS NOT NULL;

ALTER TABLE external_user_bindings DROP FOREIGN KEY fk_external_user_binding_user;
