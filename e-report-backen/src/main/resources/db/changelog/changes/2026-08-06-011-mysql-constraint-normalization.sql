--liquibase formatted sql

--changeset e-report:2026-08-06-011-normalize-attachments-work-order-fk dbms:mysql
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'attachments' AND CONSTRAINT_NAME = 'FKtaj1eo931bijamyr4owk29c31'
ALTER TABLE attachments DROP FOREIGN KEY FKtaj1eo931bijamyr4owk29c31, ADD CONSTRAINT fk_attachments_work_order FOREIGN KEY (fault_report_id) REFERENCES fault_reports (id);

--changeset e-report:2026-08-06-011-normalize-fault-reporter-fk dbms:mysql
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'fault_reports' AND CONSTRAINT_NAME = 'FK5dcvj47b4geeuyas5wqr1ogx7'
ALTER TABLE fault_reports DROP FOREIGN KEY FK5dcvj47b4geeuyas5wqr1ogx7, ADD CONSTRAINT fk_fault_reports_reporter FOREIGN KEY (reported_by_user_id) REFERENCES users (id);

--changeset e-report:2026-08-06-011-normalize-fault-resolver-fk dbms:mysql
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'fault_reports' AND CONSTRAINT_NAME = 'FKt7rfvuj5cdembtvdnte4q9wql'
ALTER TABLE fault_reports DROP FOREIGN KEY FKt7rfvuj5cdembtvdnte4q9wql, ADD CONSTRAINT fk_fault_reports_resolver FOREIGN KEY (resolved_by_user_id) REFERENCES users (id);

--changeset e-report:2026-08-06-011-normalize-fault-closer-fk dbms:mysql
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'fault_reports' AND CONSTRAINT_NAME = 'FKtd45wjc469hxhsd4m9g8bf4jw'
ALTER TABLE fault_reports DROP FOREIGN KEY FKtd45wjc469hxhsd4m9g8bf4jw, ADD CONSTRAINT fk_fault_reports_closer FOREIGN KEY (closed_by_user_id) REFERENCES users (id);

--changeset e-report:2026-08-06-011-normalize-role-permission-role-fk dbms:mysql
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'role_permissions' AND CONSTRAINT_NAME = 'FKn5fotdgk8d1xvo8nav9uv3muc'
ALTER TABLE role_permissions DROP FOREIGN KEY FKn5fotdgk8d1xvo8nav9uv3muc, ADD CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id);

--changeset e-report:2026-08-06-011-normalize-role-permission-permission-fk dbms:mysql
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'role_permissions' AND CONSTRAINT_NAME = 'FKegdk29eiy7mdtefy5c7eirr6e'
ALTER TABLE role_permissions DROP FOREIGN KEY FKegdk29eiy7mdtefy5c7eirr6e, ADD CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id);

--changeset e-report:2026-08-06-011-normalize-user-role-user-fk dbms:mysql
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'user_roles' AND CONSTRAINT_NAME = 'FKhfh9dx7w3ubf1co1vdev94g3f'
ALTER TABLE user_roles DROP FOREIGN KEY FKhfh9dx7w3ubf1co1vdev94g3f, ADD CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id);

--changeset e-report:2026-08-06-011-normalize-user-role-role-fk dbms:mysql
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'user_roles' AND CONSTRAINT_NAME = 'FKh8ciramu9cc9q3qcqiv4ue8a6'
ALTER TABLE user_roles DROP FOREIGN KEY FKh8ciramu9cc9q3qcqiv4ue8a6, ADD CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id);

--changeset e-report:2026-08-06-011-normalize-material-work-order-fk dbms:mysql
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'work_order_material_usages' AND CONSTRAINT_NAME = 'FKdo5suwws2076ltysdcq78jmia'
ALTER TABLE work_order_material_usages DROP FOREIGN KEY FKdo5suwws2076ltysdcq78jmia, ADD CONSTRAINT fk_material_usage_work_order FOREIGN KEY (fault_report_id) REFERENCES fault_reports (id);

--changeset e-report:2026-08-06-011-normalize-external-binding-user-fk dbms:mysql
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'external_user_bindings' AND CONSTRAINT_NAME = 'FKt76620co02k6ukvp2n9evn5lf'
ALTER TABLE external_user_bindings DROP FOREIGN KEY FKt76620co02k6ukvp2n9evn5lf, ADD CONSTRAINT fk_external_user_binding_user FOREIGN KEY (local_user_id) REFERENCES users (id);
