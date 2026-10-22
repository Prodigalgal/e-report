--liquibase formatted sql

--changeset e-report:2026-08-18-046-complete-legacy-mail-transport-flags-mysql dbms:mysql
--comment Complete only existing legacy sender groups with explicit false transport flags; do not create sender defaults for unconfigured tenants.
INSERT INTO tenant_app_config (id, tenant_id, config_key, config_value, description, last_updated_time)
SELECT UUID_TO_BIN(UUID(), 1), configured.tenant_id,
       'spring.mail.properties.mail.smtp.auth', 'false',
       'Legacy mail transport flag normalized by Liquibase', CURRENT_TIMESTAMP(6)
FROM (
    SELECT DISTINCT tenant_id
    FROM tenant_app_config
    WHERE config_key IN ('spring.mail.host', 'spring.mail.username', 'spring.mail.password', 'spring.mail.from')
) configured
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_app_config existing
    WHERE existing.tenant_id = configured.tenant_id
      AND existing.config_key = 'spring.mail.properties.mail.smtp.auth'
);

INSERT INTO tenant_app_config (id, tenant_id, config_key, config_value, description, last_updated_time)
SELECT UUID_TO_BIN(UUID(), 1), configured.tenant_id,
       'spring.mail.properties.mail.smtp.ssl.enable', 'false',
       'Legacy mail transport flag normalized by Liquibase', CURRENT_TIMESTAMP(6)
FROM (
    SELECT DISTINCT tenant_id
    FROM tenant_app_config
    WHERE config_key IN ('spring.mail.host', 'spring.mail.username', 'spring.mail.password', 'spring.mail.from')
) configured
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_app_config existing
    WHERE existing.tenant_id = configured.tenant_id
      AND existing.config_key = 'spring.mail.properties.mail.smtp.ssl.enable'
);

INSERT INTO tenant_app_config (id, tenant_id, config_key, config_value, description, last_updated_time)
SELECT UUID_TO_BIN(UUID(), 1), configured.tenant_id,
       'spring.mail.properties.mail.smtp.starttls.enable', 'false',
       'Legacy mail transport flag normalized by Liquibase', CURRENT_TIMESTAMP(6)
FROM (
    SELECT DISTINCT tenant_id
    FROM tenant_app_config
    WHERE config_key IN ('spring.mail.host', 'spring.mail.username', 'spring.mail.password', 'spring.mail.from')
) configured
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_app_config existing
    WHERE existing.tenant_id = configured.tenant_id
      AND existing.config_key = 'spring.mail.properties.mail.smtp.starttls.enable'
);

--rollback DELETE FROM tenant_app_config WHERE description = 'Legacy mail transport flag normalized by Liquibase';

--changeset e-report:2026-08-18-046-complete-legacy-mail-transport-flags-h2 dbms:h2
--comment H2 equivalent of explicit legacy sender transport flag normalization.
INSERT INTO tenant_app_config (id, tenant_id, config_key, config_value, description, last_updated_time)
SELECT RANDOM_UUID(), configured.tenant_id,
       'spring.mail.properties.mail.smtp.auth', 'false',
       'Legacy mail transport flag normalized by Liquibase', CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT tenant_id
    FROM tenant_app_config
    WHERE config_key IN ('spring.mail.host', 'spring.mail.username', 'spring.mail.password', 'spring.mail.from')
) configured
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_app_config existing
    WHERE existing.tenant_id = configured.tenant_id
      AND existing.config_key = 'spring.mail.properties.mail.smtp.auth'
);

INSERT INTO tenant_app_config (id, tenant_id, config_key, config_value, description, last_updated_time)
SELECT RANDOM_UUID(), configured.tenant_id,
       'spring.mail.properties.mail.smtp.ssl.enable', 'false',
       'Legacy mail transport flag normalized by Liquibase', CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT tenant_id
    FROM tenant_app_config
    WHERE config_key IN ('spring.mail.host', 'spring.mail.username', 'spring.mail.password', 'spring.mail.from')
) configured
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_app_config existing
    WHERE existing.tenant_id = configured.tenant_id
      AND existing.config_key = 'spring.mail.properties.mail.smtp.ssl.enable'
);

INSERT INTO tenant_app_config (id, tenant_id, config_key, config_value, description, last_updated_time)
SELECT RANDOM_UUID(), configured.tenant_id,
       'spring.mail.properties.mail.smtp.starttls.enable', 'false',
       'Legacy mail transport flag normalized by Liquibase', CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT tenant_id
    FROM tenant_app_config
    WHERE config_key IN ('spring.mail.host', 'spring.mail.username', 'spring.mail.password', 'spring.mail.from')
) configured
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_app_config existing
    WHERE existing.tenant_id = configured.tenant_id
      AND existing.config_key = 'spring.mail.properties.mail.smtp.starttls.enable'
);

--rollback DELETE FROM tenant_app_config WHERE description = 'Legacy mail transport flag normalized by Liquibase';
