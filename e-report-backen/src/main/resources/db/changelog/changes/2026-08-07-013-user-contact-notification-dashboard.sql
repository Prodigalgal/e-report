--liquibase formatted sql

--changeset e-report:2026-08-07-013-user-contact-columns dbms:mysql,h2
ALTER TABLE users ADD COLUMN phone VARCHAR(20);
ALTER TABLE users ADD COLUMN email VARCHAR(254);

--rollback ALTER TABLE users DROP COLUMN email;
--rollback ALTER TABLE users DROP COLUMN phone;

--changeset e-report:2026-08-07-013-user-phone-backfill-mysql dbms:mysql
UPDATE users
SET phone = username
WHERE phone IS NULL
  AND username REGEXP '^1[3-9][0-9]{9}$';

--rollback UPDATE users SET phone = NULL WHERE phone = username AND username REGEXP '^1[3-9][0-9]{9}$';

--changeset e-report:2026-08-07-013-user-phone-backfill-h2 dbms:h2
UPDATE users
SET phone = username
WHERE phone IS NULL
  AND REGEXP_LIKE(username, '^1[3-9][0-9]{9}$');

--rollback UPDATE users SET phone = NULL WHERE phone = username AND REGEXP_LIKE(username, '^1[3-9][0-9]{9}$');

--changeset e-report:2026-08-07-013-retire-static-recipients-disable-chatbots dbms:mysql,h2
DELETE FROM org_app_config WHERE config_key = 'notification.email.recipient.phones';
DELETE FROM tenant_app_config WHERE config_key = 'notification.email.recipient.phones';
DELETE FROM app_config WHERE config_key = 'notification.email.recipient.phones';

UPDATE app_config
SET config_value = 'false'
WHERE config_key IN ('notification.wecom.enabled', 'notification.dingtalk.enabled');
UPDATE tenant_app_config
SET config_value = 'false'
WHERE config_key IN ('notification.wecom.enabled', 'notification.dingtalk.enabled');
UPDATE org_app_config
SET config_value = 'false'
WHERE config_key IN ('notification.wecom.enabled', 'notification.dingtalk.enabled');

UPDATE app_config
SET config_value = ''
WHERE config_key IN ('notification.wecom.webhook-url', 'notification.dingtalk.webhook-url', 'notification.dingtalk.secret');
UPDATE tenant_app_config
SET config_value = ''
WHERE config_key IN ('notification.wecom.webhook-url', 'notification.dingtalk.webhook-url', 'notification.dingtalk.secret');
UPDATE org_app_config
SET config_value = ''
WHERE config_key IN ('notification.wecom.webhook-url', 'notification.dingtalk.webhook-url', 'notification.dingtalk.secret');

--rollback SELECT 1;
