--liquibase formatted sql

--changeset e-report:2026-08-10-022-assert-contract-collisions dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM roles legacy_role JOIN roles target_role ON target_role.tenant_id = legacy_role.tenant_id AND target_role.name = CASE WHEN legacy_role.name LIKE 'ROLE_NORMAL_FAULT_%' THEN REPLACE(legacy_role.name, 'ROLE_NORMAL_FAULT_', 'ROLE_NETWORK_FAULT_') WHEN legacy_role.name LIKE 'ROLE_UNCLOSED_BOX_%' THEN REPLACE(legacy_role.name, 'ROLE_UNCLOSED_BOX_', 'ROLE_BOX_DOOR_OPEN_') WHEN legacy_role.name LIKE 'ROLE_BOX_REPLACEMENT_%' THEN REPLACE(legacy_role.name, 'ROLE_BOX_REPLACEMENT_', 'ROLE_DAMAGED_BOX_REPLACEMENT_') END WHERE legacy_role.name LIKE 'ROLE_NORMAL_FAULT_%' OR legacy_role.name LIKE 'ROLE_UNCLOSED_BOX_%' OR legacy_role.name LIKE 'ROLE_BOX_REPLACEMENT_%'
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permissions legacy_permission JOIN permissions target_permission ON target_permission.name = CASE WHEN legacy_permission.name LIKE 'work-order:normal-fault:%' THEN REPLACE(legacy_permission.name, 'work-order:normal-fault:', 'work-order:network-fault:') WHEN legacy_permission.name LIKE 'work-order:unclosed-box:%' THEN REPLACE(legacy_permission.name, 'work-order:unclosed-box:', 'work-order:box-door-open:') END WHERE legacy_permission.name LIKE 'work-order:normal-fault:%' OR legacy_permission.name LIKE 'work-order:unclosed-box:%'
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM fault_reports WHERE report_type IS NULL
--comment Refuse the cutover when a target role or permission already exists, or a work order has no domain type.
SELECT 1;

--rollback SELECT 1;

--changeset e-report:2026-08-10-022-expand-report-type-mysql dbms:mysql
--comment Temporarily allow both old and new report types so existing rows can be converted in place.
ALTER TABLE fault_reports
    MODIFY COLUMN report_type ENUM(
        'DAMAGED_BOX_REPLACEMENT',
        'NETWORK_FAULT',
        'BOX_DOOR_OPEN',
        'NORMAL_FAULT',
        'UNCLOSED_BOX'
    ) NOT NULL DEFAULT 'NETWORK_FAULT';

--rollback ALTER TABLE fault_reports MODIFY COLUMN report_type ENUM('DAMAGED_BOX_REPLACEMENT','NETWORK_FAULT','BOX_DOOR_OPEN','NORMAL_FAULT','UNCLOSED_BOX') NOT NULL DEFAULT 'NORMAL_FAULT';

--changeset e-report:2026-08-10-022-expand-report-type-h2 dbms:h2
--comment H2 uses a bounded VARCHAR during the in-place domain conversion.
ALTER TABLE fault_reports ALTER COLUMN report_type VARCHAR(50);
ALTER TABLE fault_reports ALTER COLUMN report_type SET DEFAULT 'NETWORK_FAULT';
ALTER TABLE fault_reports ALTER COLUMN report_type SET NOT NULL;

--rollback ALTER TABLE fault_reports ALTER COLUMN report_type SET DEFAULT 'NORMAL_FAULT';

--changeset e-report:2026-08-10-022-migrate-domain-contract dbms:mysql,h2
--comment Migrate work orders, SLA policy keys, fixed role codes, permission codes and persisted idempotent responses without changing UUID relationships.
UPDATE fault_reports
SET report_type = CASE report_type
        WHEN 'NORMAL_FAULT' THEN 'NETWORK_FAULT'
        WHEN 'UNCLOSED_BOX' THEN 'BOX_DOOR_OPEN'
        ELSE report_type
    END,
    priority = CASE priority
        WHEN 'NORMAL' THEN 'ROUTINE'
        ELSE priority
    END;

UPDATE work_order_sla_policies
SET report_type = CASE report_type
        WHEN 'NORMAL_FAULT' THEN 'NETWORK_FAULT'
        WHEN 'UNCLOSED_BOX' THEN 'BOX_DOOR_OPEN'
        ELSE report_type
    END,
    priority = CASE priority
        WHEN 'NORMAL' THEN 'ROUTINE'
        ELSE priority
    END;

UPDATE roles
SET name = CASE
        WHEN name LIKE 'ROLE_NORMAL_FAULT_%' THEN REPLACE(name, 'ROLE_NORMAL_FAULT_', 'ROLE_NETWORK_FAULT_')
        WHEN name LIKE 'ROLE_UNCLOSED_BOX_%' THEN REPLACE(name, 'ROLE_UNCLOSED_BOX_', 'ROLE_BOX_DOOR_OPEN_')
        WHEN name LIKE 'ROLE_BOX_REPLACEMENT_%' THEN REPLACE(name, 'ROLE_BOX_REPLACEMENT_', 'ROLE_DAMAGED_BOX_REPLACEMENT_')
        ELSE name
    END,
    display_name = REPLACE(
        REPLACE(
            REPLACE(
                REPLACE(display_name, '箱体破损换箱', '破损箱体更换'),
                '破损换箱', '破损箱体更换'
            ),
            '普通故障', '网络故障'
        ),
        '未关箱', '箱门未关'
    ),
    description = REPLACE(
        REPLACE(
            REPLACE(
                REPLACE(description, '箱体破损换箱', '破损箱体更换'),
                '破损换箱', '破损箱体更换'
            ),
            '普通故障', '网络故障'
        ),
        '未关箱', '箱门未关'
    )
WHERE name LIKE 'ROLE_NORMAL_FAULT_%'
   OR name LIKE 'ROLE_UNCLOSED_BOX_%'
   OR name LIKE 'ROLE_BOX_REPLACEMENT_%';

UPDATE permissions
SET name = CASE
        WHEN name LIKE 'work-order:normal-fault:%' THEN REPLACE(name, 'work-order:normal-fault:', 'work-order:network-fault:')
        WHEN name LIKE 'work-order:unclosed-box:%' THEN REPLACE(name, 'work-order:unclosed-box:', 'work-order:box-door-open:')
        ELSE name
    END,
    display_name = REPLACE(
        REPLACE(
            REPLACE(
                REPLACE(display_name, '箱体破损换箱', '破损箱体更换'),
                '破损换箱', '破损箱体更换'
            ),
            '普通故障', '网络故障'
        ),
        '未关箱', '箱门未关'
    ),
    description = REPLACE(
        REPLACE(
            REPLACE(
                REPLACE(description, '箱体破损换箱', '破损箱体更换'),
                '破损换箱', '破损箱体更换'
            ),
            '普通故障', '网络故障'
        ),
        '未关箱', '箱门未关'
    )
WHERE name LIKE 'work-order:normal-fault:%'
   OR name LIKE 'work-order:unclosed-box:%'
   OR name LIKE 'work-order:damaged-box-replacement:%';

UPDATE command_idempotency_records
SET response_body = REPLACE(
        REPLACE(
            REPLACE(response_body, 'NORMAL_FAULT', 'NETWORK_FAULT'),
            'UNCLOSED_BOX', 'BOX_DOOR_OPEN'
        ),
        '"priority":"NORMAL"', '"priority":"ROUTINE"'
    )
WHERE response_body IS NOT NULL
  AND (
      response_body LIKE '%NORMAL_FAULT%'
      OR response_body LIKE '%UNCLOSED_BOX%'
      OR response_body LIKE '%"priority":"NORMAL"%'
  );

UPDATE users
SET authorization_version = authorization_version + 1;

--rollback UPDATE users SET authorization_version = authorization_version - 1 WHERE authorization_version > 0;
--rollback UPDATE command_idempotency_records SET response_body = REPLACE(REPLACE(REPLACE(response_body, 'NETWORK_FAULT', 'NORMAL_FAULT'), 'BOX_DOOR_OPEN', 'UNCLOSED_BOX'), '"priority":"ROUTINE"', '"priority":"NORMAL"') WHERE response_body IS NOT NULL;
--rollback UPDATE permissions SET name = CASE WHEN name LIKE 'work-order:network-fault:%' THEN REPLACE(name, 'work-order:network-fault:', 'work-order:normal-fault:') WHEN name LIKE 'work-order:box-door-open:%' THEN REPLACE(name, 'work-order:box-door-open:', 'work-order:unclosed-box:') ELSE name END WHERE name LIKE 'work-order:network-fault:%' OR name LIKE 'work-order:box-door-open:%';
--rollback UPDATE roles SET name = CASE WHEN name LIKE 'ROLE_NETWORK_FAULT_%' THEN REPLACE(name, 'ROLE_NETWORK_FAULT_', 'ROLE_NORMAL_FAULT_') WHEN name LIKE 'ROLE_BOX_DOOR_OPEN_%' THEN REPLACE(name, 'ROLE_BOX_DOOR_OPEN_', 'ROLE_UNCLOSED_BOX_') WHEN name LIKE 'ROLE_DAMAGED_BOX_REPLACEMENT_%' THEN REPLACE(name, 'ROLE_DAMAGED_BOX_REPLACEMENT_', 'ROLE_BOX_REPLACEMENT_') ELSE name END WHERE name LIKE 'ROLE_NETWORK_FAULT_%' OR name LIKE 'ROLE_BOX_DOOR_OPEN_%' OR name LIKE 'ROLE_DAMAGED_BOX_REPLACEMENT_%';
--rollback UPDATE work_order_sla_policies SET report_type = CASE report_type WHEN 'NETWORK_FAULT' THEN 'NORMAL_FAULT' WHEN 'BOX_DOOR_OPEN' THEN 'UNCLOSED_BOX' ELSE report_type END, priority = CASE priority WHEN 'ROUTINE' THEN 'NORMAL' ELSE priority END;
--rollback UPDATE fault_reports SET report_type = CASE report_type WHEN 'NETWORK_FAULT' THEN 'NORMAL_FAULT' WHEN 'BOX_DOOR_OPEN' THEN 'UNCLOSED_BOX' ELSE report_type END, priority = CASE priority WHEN 'ROUTINE' THEN 'NORMAL' ELSE priority END;

--changeset e-report:2026-08-10-022-assert-new-contract dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM fault_reports WHERE report_type IN ('NORMAL_FAULT', 'UNCLOSED_BOX') OR priority = 'NORMAL'
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM work_order_sla_policies WHERE report_type IN ('NORMAL_FAULT', 'UNCLOSED_BOX') OR priority = 'NORMAL'
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM roles WHERE name LIKE 'ROLE_NORMAL_FAULT_%' OR name LIKE 'ROLE_UNCLOSED_BOX_%' OR name LIKE 'ROLE_BOX_REPLACEMENT_%'
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM permissions WHERE name LIKE 'work-order:normal-fault:%' OR name LIKE 'work-order:unclosed-box:%'
--comment Halt before contracting the schema unless every persisted runtime identifier uses the new domain contract.
SELECT 1;

--rollback SELECT 1;

--changeset e-report:2026-08-10-022-contract-report-type-mysql dbms:mysql
--comment Remove old report types from the production schema contract.
ALTER TABLE fault_reports
    MODIFY COLUMN report_type ENUM(
        'DAMAGED_BOX_REPLACEMENT',
        'NETWORK_FAULT',
        'BOX_DOOR_OPEN'
    ) NOT NULL DEFAULT 'NETWORK_FAULT';

ALTER TABLE fault_reports
    MODIFY COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'ROUTINE';

--rollback ALTER TABLE fault_reports MODIFY COLUMN report_type ENUM('DAMAGED_BOX_REPLACEMENT','NETWORK_FAULT','BOX_DOOR_OPEN','NORMAL_FAULT','UNCLOSED_BOX') NOT NULL DEFAULT 'NETWORK_FAULT';
--rollback ALTER TABLE fault_reports MODIFY COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'ROUTINE';

--changeset e-report:2026-08-10-022-contract-report-type-h2 dbms:h2
--comment H2 equivalent for the final non-null default contract.
ALTER TABLE fault_reports ALTER COLUMN report_type SET DEFAULT 'NETWORK_FAULT';
ALTER TABLE fault_reports ALTER COLUMN report_type SET NOT NULL;
ALTER TABLE fault_reports ALTER COLUMN priority SET DEFAULT 'ROUTINE';
ALTER TABLE fault_reports ALTER COLUMN priority SET NOT NULL;

--rollback ALTER TABLE fault_reports ALTER COLUMN report_type SET DEFAULT 'NETWORK_FAULT';
--rollback ALTER TABLE fault_reports ALTER COLUMN priority SET DEFAULT 'ROUTINE';
