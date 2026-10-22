--liquibase formatted sql

--changeset e-report:2026-08-18-047-move-city-notification-config-to-nanjing-mysql dbms:mysql
--comment Move Zhangzhou city-level notification defaults into Nanjing county without overwriting existing county settings, then remove the city defaults.
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM org_units WHERE code = '350627' AND name = '南靖县' AND unit_type = 'COUNTY' AND status = 'ACTIVE'
INSERT INTO org_app_config (
    id, tenant_id, org_unit_id, config_key, config_value, description, last_updated_time
)
SELECT UUID_TO_BIN(UUID(), 1), source.tenant_id, nanjing.id, source.config_key,
       source.config_value, source.description, COALESCE(source.last_updated_time, CURRENT_TIMESTAMP(6))
FROM tenant_app_config source
JOIN org_units nanjing
  ON nanjing.tenant_id = source.tenant_id
 AND nanjing.code = '350627'
 AND nanjing.name = '南靖县'
 AND nanjing.unit_type = 'COUNTY'
 AND nanjing.status = 'ACTIVE'
WHERE (source.config_key LIKE 'notification.%' OR source.config_key LIKE 'spring.mail.%')
  AND NOT EXISTS (
      SELECT 1
      FROM org_app_config existing
      WHERE existing.tenant_id = source.tenant_id
        AND existing.org_unit_id = nanjing.id
        AND existing.config_key = source.config_key
  );

INSERT INTO org_app_config (
    id, tenant_id, org_unit_id, config_key, config_value, description, last_updated_time
)
SELECT UUID_TO_BIN(UUID(), 1), nanjing.tenant_id, nanjing.id, source.config_key,
       source.config_value, source.description, COALESCE(source.last_updated_time, CURRENT_TIMESTAMP(6))
FROM app_config source
JOIN org_units nanjing
  ON nanjing.code = '350627'
 AND nanjing.name = '南靖县'
 AND nanjing.unit_type = 'COUNTY'
 AND nanjing.status = 'ACTIVE'
WHERE (source.config_key LIKE 'notification.%' OR source.config_key LIKE 'spring.mail.%')
  AND NOT EXISTS (
      SELECT 1
      FROM org_app_config existing
      WHERE existing.tenant_id = nanjing.tenant_id
        AND existing.org_unit_id = nanjing.id
        AND existing.config_key = source.config_key
  );

DELETE source
FROM tenant_app_config source
JOIN org_units nanjing
  ON nanjing.tenant_id = source.tenant_id
 AND nanjing.code = '350627'
 AND nanjing.name = '南靖县'
 AND nanjing.unit_type = 'COUNTY'
 AND nanjing.status = 'ACTIVE'
WHERE source.config_key LIKE 'notification.%' OR source.config_key LIKE 'spring.mail.%';

DELETE FROM app_config
WHERE config_key LIKE 'notification.%'
   OR config_key LIKE 'spring.mail.%'
   OR config_key = 'system.fault.notification.recipient.phones';

--changeset e-report:2026-08-18-047-move-city-notification-config-to-nanjing-h2 dbms:h2
--comment H2 equivalent of the Zhangzhou-to-Nanjing notification configuration migration.
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM org_units WHERE code = '350627' AND name = '南靖县' AND unit_type = 'COUNTY' AND status = 'ACTIVE'
INSERT INTO org_app_config (
    id, tenant_id, org_unit_id, config_key, config_value, description, last_updated_time
)
SELECT RANDOM_UUID(), source.tenant_id, nanjing.id, source.config_key,
       source.config_value, source.description, COALESCE(source.last_updated_time, CURRENT_TIMESTAMP)
FROM tenant_app_config source
JOIN org_units nanjing
  ON nanjing.tenant_id = source.tenant_id
 AND nanjing.code = '350627'
 AND nanjing.name = '南靖县'
 AND nanjing.unit_type = 'COUNTY'
 AND nanjing.status = 'ACTIVE'
WHERE (source.config_key LIKE 'notification.%' OR source.config_key LIKE 'spring.mail.%')
  AND NOT EXISTS (
      SELECT 1
      FROM org_app_config existing
      WHERE existing.tenant_id = source.tenant_id
        AND existing.org_unit_id = nanjing.id
        AND existing.config_key = source.config_key
  );

INSERT INTO org_app_config (
    id, tenant_id, org_unit_id, config_key, config_value, description, last_updated_time
)
SELECT RANDOM_UUID(), nanjing.tenant_id, nanjing.id, source.config_key,
       source.config_value, source.description, COALESCE(source.last_updated_time, CURRENT_TIMESTAMP)
FROM app_config source
JOIN org_units nanjing
  ON nanjing.code = '350627'
 AND nanjing.name = '南靖县'
 AND nanjing.unit_type = 'COUNTY'
 AND nanjing.status = 'ACTIVE'
WHERE (source.config_key LIKE 'notification.%' OR source.config_key LIKE 'spring.mail.%')
  AND NOT EXISTS (
      SELECT 1
      FROM org_app_config existing
      WHERE existing.tenant_id = nanjing.tenant_id
        AND existing.org_unit_id = nanjing.id
        AND existing.config_key = source.config_key
  );

DELETE FROM tenant_app_config
WHERE tenant_id = (
    SELECT nanjing.tenant_id
    FROM org_units nanjing
    WHERE nanjing.code = '350627'
      AND nanjing.name = '南靖县'
      AND nanjing.unit_type = 'COUNTY'
      AND nanjing.status = 'ACTIVE'
)
  AND (config_key LIKE 'notification.%' OR config_key LIKE 'spring.mail.%');

DELETE FROM app_config
WHERE config_key LIKE 'notification.%'
   OR config_key LIKE 'spring.mail.%'
   OR config_key = 'system.fault.notification.recipient.phones';
