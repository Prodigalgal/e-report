--liquibase formatted sql

--changeset e-report:2026-08-16-042-user-transfer-error-purpose-mysql dbms:mysql
--comment Add a dedicated generated-file purpose for downloadable user-import failure workbooks.
ALTER TABLE file_transfer_upload_sessions
    MODIFY COLUMN purpose ENUM(
        'APP_RELEASE',
        'WORK_ORDER_ATTACHMENT',
        'USER_IMPORT',
        'USER_IMPORT_TEMPLATE',
        'USER_IMPORT_ERROR',
        'USER_EXPORT'
    ) NOT NULL;

--rollback DELETE FROM file_transfer_upload_sessions WHERE purpose = 'USER_IMPORT_ERROR';
--rollback ALTER TABLE file_transfer_upload_sessions MODIFY COLUMN purpose ENUM('APP_RELEASE','WORK_ORDER_ATTACHMENT','USER_IMPORT','USER_IMPORT_TEMPLATE','USER_EXPORT') NOT NULL;

--changeset e-report:2026-08-16-042-user-transfer-error-purpose-h2 dbms:h2
--comment H2 equivalent of the generated user-import failure workbook purpose.
ALTER TABLE file_transfer_upload_sessions
    ALTER COLUMN purpose ENUM(
        'APP_RELEASE',
        'WORK_ORDER_ATTACHMENT',
        'USER_IMPORT',
        'USER_IMPORT_TEMPLATE',
        'USER_IMPORT_ERROR',
        'USER_EXPORT'
    ) NOT NULL;

--rollback DELETE FROM file_transfer_upload_sessions WHERE purpose = 'USER_IMPORT_ERROR';
--rollback ALTER TABLE file_transfer_upload_sessions ALTER COLUMN purpose ENUM('APP_RELEASE','WORK_ORDER_ATTACHMENT','USER_IMPORT','USER_IMPORT_TEMPLATE','USER_EXPORT') NOT NULL;
