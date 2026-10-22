--liquibase formatted sql

--changeset e-report:2026-08-07-012-extend-active-sla-mysql dbms:mysql
UPDATE fault_reports fr
JOIN work_order_sla_policies policy
  ON policy.tenant_id = fr.tenant_id
 AND policy.report_type = fr.report_type
 AND policy.priority = fr.priority
SET fr.sla_due_at = CASE fr.sla_stage
        WHEN 'CLAIM' THEN DATE_ADD(fr.sla_due_at, INTERVAL policy.claim_minutes MINUTE)
        WHEN 'RESPONSE' THEN DATE_ADD(fr.sla_due_at, INTERVAL policy.response_minutes MINUTE)
        WHEN 'VERIFICATION' THEN DATE_ADD(fr.sla_due_at, INTERVAL policy.verification_minutes MINUTE)
        ELSE fr.sla_due_at
    END,
    fr.sla_breached_at = NULL
WHERE fr.status_code NOT IN ('CLOSED', 'CANCELLED')
  AND fr.sla_due_at IS NOT NULL
  AND fr.sla_stage IN ('CLAIM', 'RESPONSE', 'VERIFICATION');

--rollback UPDATE fault_reports fr JOIN work_order_sla_policies policy ON policy.tenant_id = fr.tenant_id AND policy.report_type = fr.report_type AND policy.priority = fr.priority SET fr.sla_due_at = CASE fr.sla_stage WHEN 'CLAIM' THEN DATE_SUB(fr.sla_due_at, INTERVAL policy.claim_minutes MINUTE) WHEN 'RESPONSE' THEN DATE_SUB(fr.sla_due_at, INTERVAL policy.response_minutes MINUTE) WHEN 'VERIFICATION' THEN DATE_SUB(fr.sla_due_at, INTERVAL policy.verification_minutes MINUTE) ELSE fr.sla_due_at END WHERE fr.status_code NOT IN ('CLOSED', 'CANCELLED') AND fr.sla_due_at IS NOT NULL AND fr.sla_stage IN ('CLAIM', 'RESPONSE', 'VERIFICATION');

--changeset e-report:2026-08-07-012-extend-active-sla-h2 dbms:h2
UPDATE fault_reports
SET sla_due_at = CASE sla_stage
        WHEN 'CLAIM' THEN DATEADD('MINUTE', (
            SELECT policy.claim_minutes FROM work_order_sla_policies policy
            WHERE policy.tenant_id = fault_reports.tenant_id
              AND policy.report_type = fault_reports.report_type
              AND policy.priority = fault_reports.priority
        ), sla_due_at)
        WHEN 'RESPONSE' THEN DATEADD('MINUTE', (
            SELECT policy.response_minutes FROM work_order_sla_policies policy
            WHERE policy.tenant_id = fault_reports.tenant_id
              AND policy.report_type = fault_reports.report_type
              AND policy.priority = fault_reports.priority
        ), sla_due_at)
        WHEN 'VERIFICATION' THEN DATEADD('MINUTE', (
            SELECT policy.verification_minutes FROM work_order_sla_policies policy
            WHERE policy.tenant_id = fault_reports.tenant_id
              AND policy.report_type = fault_reports.report_type
              AND policy.priority = fault_reports.priority
        ), sla_due_at)
        ELSE sla_due_at
    END,
    sla_breached_at = NULL
WHERE status_code NOT IN ('CLOSED', 'CANCELLED')
  AND sla_due_at IS NOT NULL
  AND sla_stage IN ('CLAIM', 'RESPONSE', 'VERIFICATION');

--rollback UPDATE fault_reports SET sla_due_at = CASE sla_stage WHEN 'CLAIM' THEN DATEADD('MINUTE', -1 * (SELECT policy.claim_minutes FROM work_order_sla_policies policy WHERE policy.tenant_id = fault_reports.tenant_id AND policy.report_type = fault_reports.report_type AND policy.priority = fault_reports.priority), sla_due_at) WHEN 'RESPONSE' THEN DATEADD('MINUTE', -1 * (SELECT policy.response_minutes FROM work_order_sla_policies policy WHERE policy.tenant_id = fault_reports.tenant_id AND policy.report_type = fault_reports.report_type AND policy.priority = fault_reports.priority), sla_due_at) WHEN 'VERIFICATION' THEN DATEADD('MINUTE', -1 * (SELECT policy.verification_minutes FROM work_order_sla_policies policy WHERE policy.tenant_id = fault_reports.tenant_id AND policy.report_type = fault_reports.report_type AND policy.priority = fault_reports.priority), sla_due_at) ELSE sla_due_at END WHERE status_code NOT IN ('CLOSED', 'CANCELLED') AND sla_due_at IS NOT NULL AND sla_stage IN ('CLAIM', 'RESPONSE', 'VERIFICATION');

--changeset e-report:2026-08-07-012-double-sla-policies-and-chinese-labels dbms:mysql,h2
UPDATE work_order_sla_policies
SET claim_minutes = claim_minutes * 2,
    response_minutes = response_minutes * 2,
    verification_minutes = verification_minutes * 2,
    max_single_hold_minutes = max_single_hold_minutes * 2,
    max_total_hold_minutes = max_total_hold_minutes * 2,
    updated_at = CURRENT_TIMESTAMP;

UPDATE permissions
SET display_name = '故障时限策略查看',
    description = '查看当前市公司的故障时限策略'
WHERE name = 'sla-policy:view';

UPDATE permissions
SET display_name = '故障时限策略修改',
    description = '修改当前市公司的故障时限策略'
WHERE name = 'sla-policy:update';

UPDATE roles
SET display_name = '时限管理员',
    description = '负责查看和维护当前市公司的故障时限策略。'
WHERE name = 'ROLE_SLA_MANAGER';

UPDATE permissions
SET description = '查看安卓应用安装包发布记录'
WHERE name = 'app-update:view';

UPDATE permissions
SET description = '登记并发布安卓应用安装包'
WHERE name = 'app-update:publish';

UPDATE roles
SET description = '负责安卓应用安装包的查看、发布和存储维护。'
WHERE name = 'ROLE_RELEASE_MANAGER';

UPDATE roles
SET description = '管理授权区县组织树内的工单、用户、组织及独立配置，并查看全市故障时限标准。'
WHERE name = 'ROLE_COUNTY_ADMIN';

UPDATE app_config
SET description = CASE config_key
    WHEN 'spring.mail.host' THEN '邮件服务器地址'
    WHEN 'spring.mail.port' THEN '邮件服务器端口'
    WHEN 'spring.mail.username' THEN '邮件服务器用户名'
    WHEN 'spring.mail.password' THEN '邮件服务器密码或授权码'
    WHEN 'spring.mail.from' THEN '默认发件地址'
    WHEN 'spring.mail.properties.mail.smtp.auth' THEN '是否启用邮件服务器身份认证'
    WHEN 'spring.mail.properties.mail.smtp.ssl.enable' THEN '是否启用邮件加密连接'
    WHEN 'notification.email.recipient.phones' THEN '邮件通知接收管理员手机号列表（逗号分隔）'
    WHEN 'notification.wecom.webhook-url' THEN '企业微信机器人回调地址'
    WHEN 'notification.dingtalk.webhook-url' THEN '钉钉机器人回调地址'
    WHEN 'amap.webservice.apikey' THEN '高德网页服务接口密钥（用于静态地图等）'
    WHEN 'map.amap.webservice.api-key-pool' THEN '高德网页服务接口密钥池，支持逗号、分号、换行分隔'
    ELSE description
END
WHERE config_key IN (
    'spring.mail.host',
    'spring.mail.port',
    'spring.mail.username',
    'spring.mail.password',
    'spring.mail.from',
    'spring.mail.properties.mail.smtp.auth',
    'spring.mail.properties.mail.smtp.ssl.enable',
    'notification.email.recipient.phones',
    'notification.wecom.webhook-url',
    'notification.dingtalk.webhook-url',
    'amap.webservice.apikey',
    'map.amap.webservice.api-key-pool'
);

--rollback UPDATE app_config SET description = CASE config_key WHEN 'spring.mail.host' THEN 'SMTP Mail Host' WHEN 'spring.mail.port' THEN 'SMTP Mail Port' WHEN 'spring.mail.username' THEN 'SMTP Mail Username' WHEN 'spring.mail.password' THEN 'SMTP Mail Password/Auth Code' WHEN 'spring.mail.from' THEN 'Default Mail From Address' WHEN 'spring.mail.properties.mail.smtp.auth' THEN 'SMTP Auth Enabled (true/false)' WHEN 'spring.mail.properties.mail.smtp.ssl.enable' THEN 'SMTP SSL Enabled (true/false)' WHEN 'notification.email.recipient.phones' THEN '邮件通知接收管理员手机号列表 (逗号分隔)' WHEN 'notification.wecom.webhook-url' THEN '企业微信机器人 Webhook URL' WHEN 'notification.dingtalk.webhook-url' THEN '钉钉机器人 Webhook URL' WHEN 'amap.webservice.apikey' THEN '高德Web服务API Key (用于静态地图等)' WHEN 'map.amap.webservice.api-key-pool' THEN '高德Web服务API Key池，支持逗号、分号、换行分隔' ELSE description END WHERE config_key IN ('spring.mail.host', 'spring.mail.port', 'spring.mail.username', 'spring.mail.password', 'spring.mail.from', 'spring.mail.properties.mail.smtp.auth', 'spring.mail.properties.mail.smtp.ssl.enable', 'notification.email.recipient.phones', 'notification.wecom.webhook-url', 'notification.dingtalk.webhook-url', 'amap.webservice.apikey', 'map.amap.webservice.api-key-pool');
--rollback UPDATE roles SET description = '管理授权区县组织树内的工单、用户、组织及独立配置，并查看全市 SLA 标准。' WHERE name = 'ROLE_COUNTY_ADMIN';
--rollback UPDATE roles SET description = '负责 Android 安装包的查看、发布和存储维护。' WHERE name = 'ROLE_RELEASE_MANAGER';
--rollback UPDATE permissions SET description = '登记并发布 Android 安装包' WHERE name = 'app-update:publish';
--rollback UPDATE permissions SET description = '查看 Android 安装包发布记录' WHERE name = 'app-update:view';
--rollback UPDATE roles SET display_name = 'SLA管理员', description = '负责查看和维护当前市公司的故障 SLA 策略。' WHERE name = 'ROLE_SLA_MANAGER';
--rollback UPDATE permissions SET display_name = 'SLA策略修改', description = '修改当前市公司的故障 SLA 策略' WHERE name = 'sla-policy:update';
--rollback UPDATE permissions SET display_name = 'SLA策略查看', description = '查看当前市公司的故障 SLA 策略' WHERE name = 'sla-policy:view';
--rollback UPDATE work_order_sla_policies SET claim_minutes = claim_minutes / 2, response_minutes = response_minutes / 2, verification_minutes = verification_minutes / 2, max_single_hold_minutes = max_single_hold_minutes / 2, max_total_hold_minutes = max_total_hold_minutes / 2, updated_at = CURRENT_TIMESTAMP;
