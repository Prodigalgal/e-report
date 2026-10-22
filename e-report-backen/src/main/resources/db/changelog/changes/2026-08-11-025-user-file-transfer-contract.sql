--liquibase formatted sql

--changeset e-report:2026-08-11-025-user-file-transfer-contract-mysql dbms:mysql
--comment Expand transfer purposes and processing state for resumable user import and immutable spreadsheet downloads.
ALTER TABLE file_transfer_upload_sessions
    MODIFY COLUMN purpose ENUM(
        'APP_RELEASE',
        'WORK_ORDER_ATTACHMENT',
        'USER_IMPORT',
        'USER_IMPORT_TEMPLATE',
        'USER_EXPORT'
    ) NOT NULL;

ALTER TABLE file_transfer_upload_sessions
    MODIFY COLUMN status ENUM(
        'ABORTED',
        'CLAIMED',
        'COMPLETED',
        'INITIATED',
        'PROCESSING'
    ) NOT NULL;

--rollback DELETE FROM file_transfer_upload_sessions WHERE purpose IN ('USER_IMPORT','USER_IMPORT_TEMPLATE','USER_EXPORT') OR status = 'PROCESSING';
--rollback ALTER TABLE file_transfer_upload_sessions MODIFY COLUMN status ENUM('ABORTED','CLAIMED','COMPLETED','INITIATED') NOT NULL;
--rollback ALTER TABLE file_transfer_upload_sessions MODIFY COLUMN purpose ENUM('APP_RELEASE','WORK_ORDER_ATTACHMENT') NOT NULL;

--changeset e-report:2026-08-11-025-user-file-transfer-contract-h2 dbms:h2
--comment H2 equivalent for the user file-transfer purpose and processing-state contract.
ALTER TABLE file_transfer_upload_sessions
    ALTER COLUMN purpose ENUM(
        'APP_RELEASE',
        'WORK_ORDER_ATTACHMENT',
        'USER_IMPORT',
        'USER_IMPORT_TEMPLATE',
        'USER_EXPORT'
    ) NOT NULL;

ALTER TABLE file_transfer_upload_sessions
    ALTER COLUMN status ENUM(
        'ABORTED',
        'CLAIMED',
        'COMPLETED',
        'INITIATED',
        'PROCESSING'
    ) NOT NULL;

--rollback DELETE FROM file_transfer_upload_sessions WHERE purpose IN ('USER_IMPORT','USER_IMPORT_TEMPLATE','USER_EXPORT') OR status = 'PROCESSING';
--rollback ALTER TABLE file_transfer_upload_sessions ALTER COLUMN status ENUM('ABORTED','CLAIMED','COMPLETED','INITIATED') NOT NULL;
--rollback ALTER TABLE file_transfer_upload_sessions ALTER COLUMN purpose ENUM('APP_RELEASE','WORK_ORDER_ATTACHMENT') NOT NULL;
