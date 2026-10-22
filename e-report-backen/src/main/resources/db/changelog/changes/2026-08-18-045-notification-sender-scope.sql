--liquibase formatted sql

--changeset e-report:2026-08-18-045-notification-sender-scope-mysql dbms:mysql
--comment Move the former platform-wide mail sender into explicit city-company tenant settings and retire platform fallback.
INSERT INTO tenant_app_config (id, tenant_id, config_key, config_value, description, last_updated_time)
SELECT UUID_TO_BIN(UUID(), 1), tenant.id, platform.config_key, platform.config_value,
       platform.description, COALESCE(platform.last_updated_time, CURRENT_TIMESTAMP(6))
FROM tenants tenant
JOIN app_config platform ON platform.config_key IN (
    'spring.mail.host',
    'spring.mail.port',
    'spring.mail.username',
    'spring.mail.password',
    'spring.mail.from',
    'spring.mail.properties.mail.smtp.auth',
    'spring.mail.properties.mail.smtp.ssl.enable',
    'spring.mail.properties.mail.smtp.starttls.enable'
)
WHERE NOT EXISTS (
    SELECT 1
    FROM tenant_app_config scoped
    WHERE scoped.tenant_id = tenant.id
      AND scoped.config_key = platform.config_key
);

DELETE FROM app_config
WHERE config_key IN (
    'spring.mail.host',
    'spring.mail.port',
    'spring.mail.username',
    'spring.mail.password',
    'spring.mail.from',
    'spring.mail.properties.mail.smtp.auth',
    'spring.mail.properties.mail.smtp.ssl.enable',
    'spring.mail.properties.mail.smtp.starttls.enable'
);

--rollback INSERT INTO app_config (id, config_key, config_value, description, last_updated_time) SELECT UUID_TO_BIN(UUID(), 1), scoped.config_key, scoped.config_value, scoped.description, scoped.last_updated_time FROM tenant_app_config scoped WHERE scoped.config_key IN ('spring.mail.host','spring.mail.port','spring.mail.username','spring.mail.password','spring.mail.from','spring.mail.properties.mail.smtp.auth','spring.mail.properties.mail.smtp.ssl.enable','spring.mail.properties.mail.smtp.starttls.enable') AND scoped.tenant_id = (SELECT id FROM tenants ORDER BY created_at ASC LIMIT 1) AND NOT EXISTS (SELECT 1 FROM app_config platform WHERE platform.config_key = scoped.config_key);

--changeset e-report:2026-08-18-045-notification-sender-scope-h2 dbms:h2
--comment H2 equivalent of explicit city-company mail sender migration.
INSERT INTO tenant_app_config (id, tenant_id, config_key, config_value, description, last_updated_time)
SELECT RANDOM_UUID(), tenant.id, platform.config_key, platform.config_value,
       platform.description, COALESCE(platform.last_updated_time, CURRENT_TIMESTAMP)
FROM tenants tenant
JOIN app_config platform ON platform.config_key IN (
    'spring.mail.host',
    'spring.mail.port',
    'spring.mail.username',
    'spring.mail.password',
    'spring.mail.from',
    'spring.mail.properties.mail.smtp.auth',
    'spring.mail.properties.mail.smtp.ssl.enable',
    'spring.mail.properties.mail.smtp.starttls.enable'
)
WHERE NOT EXISTS (
    SELECT 1
    FROM tenant_app_config scoped
    WHERE scoped.tenant_id = tenant.id
      AND scoped.config_key = platform.config_key
);

DELETE FROM app_config
WHERE config_key IN (
    'spring.mail.host',
    'spring.mail.port',
    'spring.mail.username',
    'spring.mail.password',
    'spring.mail.from',
    'spring.mail.properties.mail.smtp.auth',
    'spring.mail.properties.mail.smtp.ssl.enable',
    'spring.mail.properties.mail.smtp.starttls.enable'
);

--rollback INSERT INTO app_config (id, config_key, config_value, description, last_updated_time) SELECT RANDOM_UUID(), scoped.config_key, scoped.config_value, scoped.description, scoped.last_updated_time FROM tenant_app_config scoped WHERE scoped.config_key IN ('spring.mail.host','spring.mail.port','spring.mail.username','spring.mail.password','spring.mail.from','spring.mail.properties.mail.smtp.auth','spring.mail.properties.mail.smtp.ssl.enable','spring.mail.properties.mail.smtp.starttls.enable') AND scoped.tenant_id = (SELECT id FROM tenants ORDER BY created_at ASC FETCH FIRST 1 ROW ONLY) AND NOT EXISTS (SELECT 1 FROM app_config platform WHERE platform.config_key = scoped.config_key);
