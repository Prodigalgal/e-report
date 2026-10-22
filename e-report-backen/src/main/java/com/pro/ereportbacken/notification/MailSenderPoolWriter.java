package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.service.AppConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.pro.ereportbacken.config.EmailConstant.*;

@Service
public class MailSenderPoolWriter {
    static final String POOL_DESCRIPTION = "按配置作用域轮询的SMTP邮件发件池";
    private static final List<String> SINGLE_SENDER_KEYS = MAIL_SENDER_CONFIG_KEYS.stream()
            .filter(key -> !MAIL_SENDER_POOL_KEY.equals(key) && !MAIL_SENDER_STRATEGY_KEY.equals(key))
            .toList();

    private final AppConfigService appConfigService;

    public MailSenderPoolWriter(AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
    }

    @Transactional
    public void replace(String serializedPool, MailSenderPoolStrategy strategy, UUID orgUnitId) {
        if (serializedPool == null) {
            appConfigService.deleteLocalScopedConfigs(List.of(MAIL_SENDER_POOL_KEY), orgUnitId);
        } else {
            appConfigService.saveOrUpdateScopedConfig(
                    MAIL_SENDER_POOL_KEY, serializedPool, POOL_DESCRIPTION, orgUnitId);
        }
        appConfigService.saveOrUpdateScopedConfig(
                MAIL_SENDER_STRATEGY_KEY, strategy.name(), "邮件发件池的组织继承策略", orgUnitId);
        appConfigService.deleteLocalScopedConfigs(SINGLE_SENDER_KEYS, orgUnitId);
    }
}
