--liquibase formatted sql

--changeset e-report:2026-08-12-029-web-session-environment dbms:mysql,h2
--comment Persist the versioned WEB risk environment with its authenticated session.
ALTER TABLE auth_sessions ADD COLUMN web_fingerprint_hash VARCHAR(64) NULL;
ALTER TABLE auth_sessions ADD COLUMN client_name VARCHAR(120) NULL;
ALTER TABLE auth_sessions ADD COLUMN client_device_name VARCHAR(120) NULL;
ALTER TABLE auth_sessions ADD COLUMN client_os_version VARCHAR(120) NULL;
ALTER TABLE auth_sessions ADD COLUMN client_app_version VARCHAR(50) NULL;
ALTER TABLE auth_sessions ADD COLUMN client_security_signals VARCHAR(1000) NULL;
ALTER TABLE auth_sessions ADD COLUMN web_environment_json TEXT NULL;

--rollback ALTER TABLE auth_sessions DROP COLUMN web_environment_json;
--rollback ALTER TABLE auth_sessions DROP COLUMN client_security_signals;
--rollback ALTER TABLE auth_sessions DROP COLUMN client_app_version;
--rollback ALTER TABLE auth_sessions DROP COLUMN client_os_version;
--rollback ALTER TABLE auth_sessions DROP COLUMN client_device_name;
--rollback ALTER TABLE auth_sessions DROP COLUMN client_name;
--rollback ALTER TABLE auth_sessions DROP COLUMN web_fingerprint_hash;

--changeset e-report:2026-08-12-029-android-device-environment dbms:mysql,h2
--comment Extend Android trusted-device context without introducing permanent hardware identifiers.
ALTER TABLE trusted_devices ADD COLUMN environment_fingerprint_hash VARCHAR(64) NULL;
ALTER TABLE trusted_devices ADD COLUMN environment_schema_version INTEGER NULL;
ALTER TABLE trusted_devices ADD COLUMN soc_manufacturer VARCHAR(80) NULL;
ALTER TABLE trusted_devices ADD COLUMN soc_model VARCHAR(120) NULL;
ALTER TABLE trusted_devices ADD COLUMN supported_32_bit_abis VARCHAR(255) NULL;
ALTER TABLE trusted_devices ADD COLUMN supported_64_bit_abis VARCHAR(255) NULL;
ALTER TABLE trusted_devices ADD COLUMN cpu_core_count INTEGER NULL;
ALTER TABLE trusted_devices ADD COLUMN low_ram_device BOOLEAN NULL;

--rollback ALTER TABLE trusted_devices DROP COLUMN low_ram_device;
--rollback ALTER TABLE trusted_devices DROP COLUMN cpu_core_count;
--rollback ALTER TABLE trusted_devices DROP COLUMN supported_64_bit_abis;
--rollback ALTER TABLE trusted_devices DROP COLUMN supported_32_bit_abis;
--rollback ALTER TABLE trusted_devices DROP COLUMN soc_model;
--rollback ALTER TABLE trusted_devices DROP COLUMN soc_manufacturer;
--rollback ALTER TABLE trusted_devices DROP COLUMN environment_schema_version;
--rollback ALTER TABLE trusted_devices DROP COLUMN environment_fingerprint_hash;
