--liquibase formatted sql

--changeset e-report:2026-08-20-052-trusted-device-reauthentication-mysql dbms:mysql
--comment Record why device trust was withdrawn and support the reauthentication-required state after password changes.
ALTER TABLE trusted_devices
    MODIFY COLUMN status VARCHAR(32) NOT NULL,
    ADD COLUMN revoke_reason VARCHAR(255) NULL;

--rollback UPDATE trusted_devices SET status = 'REVOKED' WHERE status = 'REAUTHENTICATION_REQUIRED';
--rollback ALTER TABLE trusted_devices DROP COLUMN revoke_reason, MODIFY COLUMN status VARCHAR(20) NOT NULL;

--changeset e-report:2026-08-20-052-trusted-device-reauthentication-h2 dbms:h2
--comment H2 equivalent for password-change trusted-device revocation verification.
ALTER TABLE trusted_devices ALTER COLUMN status VARCHAR(32) NOT NULL;
ALTER TABLE trusted_devices ADD COLUMN revoke_reason VARCHAR(255) NULL;

--rollback UPDATE trusted_devices SET status = 'REVOKED' WHERE status = 'REAUTHENTICATION_REQUIRED';
--rollback ALTER TABLE trusted_devices DROP COLUMN revoke_reason;
--rollback ALTER TABLE trusted_devices ALTER COLUMN status VARCHAR(20) NOT NULL;

--changeset e-report:2026-08-20-052-trusted-device-reauthentication-assert dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE LOWER(TABLE_NAME) = 'trusted_devices' AND LOWER(COLUMN_NAME) = 'revoke_reason'
--comment Refuse application startup when the trusted-device revocation audit column is unavailable.
SELECT 1;

--rollback SELECT 1;
