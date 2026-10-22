package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.model.pojo.AppConfig;
import com.pro.ereportbacken.model.dto.MailSenderSettingsDTO;
import com.pro.ereportbacken.model.dto.MailSenderAccountDTO;
import com.pro.ereportbacken.service.AppConfigService;
import com.pro.ereportbacken.service.ResolvedAppConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationSettingsServiceTests {

    @Test
    void returnsRegistryDrivenSettingsForRequestedOrganization() {
        UUID orgUnitId = uuid(7);
        AppConfigService appConfigService = mock(AppConfigService.class);
        MailSenderConfigurationService mailSenderService = senderService();
        Map<String, ResolvedAppConfig> configs = Map.of(
                "notification.center.enabled", resolved("notification.center.enabled", "true", orgUnitId, "南靖县", false),
                "notification.email.enabled", resolved("notification.email.enabled", "false", orgUnitId, "南靖县", false),
                "notification.wecom.enabled", resolved("notification.wecom.enabled", "YES", null, null, true),
                "notification.daily-outstanding-report.enabled", resolved(
                        "notification.daily-outstanding-report.enabled", "true", null, null, true),
                "notification.daily-outstanding-report.send-time", resolved(
                        "notification.daily-outstanding-report.send-time", "18:00", orgUnitId, "南靖县", false)
        );
        configs.forEach((key, value) -> when(appConfigService.findScopedConfigByKey(key, orgUnitId))
                .thenReturn(Optional.of(value)));
        NotificationSettingsService service = new NotificationSettingsService(
                appConfigService,
                new NotificationChannelRegistry(List.of(
                        channel("wecom", "企业微信", "notification.wecom.enabled", false),
                        channel("email", "邮件", "notification.email.enabled", true)
                )),
                mailSenderService
        );

        var settings = service.getSettings(orgUnitId);

        assertThat(settings.center().enabled()).isTrue();
        assertThat(settings.channels()).extracting("name").containsExactly("email", "wecom");
        assertThat(settings.channels().getFirst().enabled()).isFalse();
        assertThat(settings.channels().getFirst().sourceOrgUnitName()).isEqualTo("南靖县");
        assertThat(settings.channels().getLast().enabled()).isTrue();
        assertThat(settings.channels().getLast().inherited()).isTrue();
        assertThat(settings.dailyOutstandingReport().switchSetting().enabled()).isTrue();
        assertThat(settings.dailyOutstandingReport().sendTime()).isEqualTo("18:00");
        assertThat(settings.dailyOutstandingReport().sourceOrgUnitName()).isEqualTo("南靖县");
        assertThat(settings.mailSender().configured()).isTrue();
    }

    @Test
    void returnsFailClosedUnconfiguredSettingsWhenCityAndOrganizationHaveNoOverrides() {
        UUID orgUnitId = uuid(7);
        AppConfigService appConfigService = mock(AppConfigService.class);
        NotificationSettingsService service = new NotificationSettingsService(
                appConfigService,
                new NotificationChannelRegistry(List.of(
                        channel("email", "邮件", "notification.email.enabled", false),
                        channel("wecom", "企业微信", "notification.wecom.enabled", false)
                )),
                senderService()
        );

        var settings = service.getSettings(orgUnitId);

        assertThat(settings.center().enabled()).isFalse();
        assertThat(settings.center().sourceScope()).isEqualTo("UNCONFIGURED");
        assertThat(settings.channels()).allSatisfy(channel -> {
            assertThat(channel.enabled()).isFalse();
            assertThat(channel.sourceScope()).isEqualTo("UNCONFIGURED");
        });
        assertThat(settings.dailyOutstandingReport().switchSetting().enabled()).isFalse();
        assertThat(settings.dailyOutstandingReport().sendTime()).isEqualTo("18:00");
        assertThat(settings.dailyOutstandingReport().sourceScope()).isEqualTo("UNCONFIGURED");
    }

    @Test
    void updatesDailyReportScheduleWithNormalizedTime() {
        UUID orgUnitId = uuid(7);
        AppConfigService appConfigService = mock(AppConfigService.class);
        ResolvedAppConfig schedule = resolved(
                "notification.daily-outstanding-report.send-time", "18:00", orgUnitId, "南靖县", false);
        ResolvedAppConfig updatedSchedule = resolved(
                "notification.daily-outstanding-report.send-time", "18:05", orgUnitId, "南靖县", false);
        ResolvedAppConfig enabled = resolved(
                "notification.daily-outstanding-report.enabled", "true", null, null, true);
        when(appConfigService.findScopedConfigByKey(
                "notification.daily-outstanding-report.send-time", orgUnitId))
                .thenReturn(Optional.of(schedule), Optional.of(updatedSchedule));
        when(appConfigService.findScopedConfigByKey(
                "notification.daily-outstanding-report.enabled", orgUnitId)).thenReturn(Optional.of(enabled));
        when(appConfigService.saveOrUpdateScopedConfig(
                "notification.daily-outstanding-report.send-time", "18:05",
                "每日区县未处理工单邮件汇总发送时间（HH:mm）", orgUnitId
        )).thenReturn(resolved(
                "notification.daily-outstanding-report.send-time", "18:05", orgUnitId, "南靖县", false));
        NotificationSettingsService service = new NotificationSettingsService(
                appConfigService, new NotificationChannelRegistry(List.of(
                        channel("email", "邮件", "notification.email.enabled", true))), senderService());

        var result = service.updateDailyOutstandingReportSchedule("18:05", orgUnitId);

        assertThat(result.sendTime()).isEqualTo("18:05");
        verify(appConfigService).saveOrUpdateScopedConfig(
                "notification.daily-outstanding-report.send-time", "18:05",
                "每日区县未处理工单邮件汇总发送时间（HH:mm）", orgUnitId);
        assertThatThrownBy(() -> service.updateDailyOutstandingReportSchedule("25:00", orgUnitId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HH:mm");
    }

    @Test
    void updatesOnlyRegisteredSwitchAndPreservesDescription() {
        UUID orgUnitId = uuid(7);
        AppConfigService appConfigService = mock(AppConfigService.class);
        ResolvedAppConfig inherited = resolved("notification.wecom.enabled", "false", null, null, true);
        ResolvedAppConfig updated = resolved("notification.wecom.enabled", "true", orgUnitId, "南靖县", false);
        when(appConfigService.findScopedConfigByKey("notification.wecom.enabled", orgUnitId))
                .thenReturn(Optional.of(inherited));
        when(appConfigService.saveOrUpdateScopedConfig(
                "notification.wecom.enabled", "true", "企业微信通知说明", orgUnitId
        )).thenReturn(updated);
        NotificationSettingsService service = new NotificationSettingsService(
                appConfigService,
                new NotificationChannelRegistry(List.of(
                        channel("wecom", "企业微信", "notification.wecom.enabled", false)
                )), senderService()
        );

        var result = service.update(" WECOM ", true, orgUnitId);

        assertThat(result.enabled()).isTrue();
        assertThat(result.inherited()).isFalse();
        verify(appConfigService).saveOrUpdateScopedConfig(
                "notification.wecom.enabled", "true", "企业微信通知说明", orgUnitId
        );
        assertThatThrownBy(() -> service.update("unregistered", true, orgUnitId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown notification channel");
    }

    private NotificationChannel channel(String name,
                                        String displayName,
                                        String configKey,
                                        boolean defaultEnabled) {
        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.descriptor()).thenReturn(new NotificationChannelDescriptor(
                name,
                displayName,
                displayName + "通知说明",
                configKey,
                defaultEnabled
        ));
        return channel;
    }

    private MailSenderConfigurationService senderService() {
        MailSenderConfigurationService service = mock(MailSenderConfigurationService.class);
        when(service.settings(org.mockito.ArgumentMatchers.any())).thenReturn(new MailSenderSettingsDTO(
                true, false,
                List.of(new MailSenderAccountDTO(
                        uuid(20), "smtp.example.com", 465, "sender@example.com", "sender@example.com",
                        true, true, false, true, "CUSTOM", "PASSWORD", "AUTO", false,
                        null, null, null, "TLSv1.2 TLSv1.3", 10000, 30000, 30000,
                        null, null, null, false, false, "TENANT_OVERRIDE", null, null)),
                "TENANT_OVERRIDE", null, null, true, null, false, MailSenderPoolStrategy.INHERIT, List.of(), List.of(), null));
        return service;
    }

    private ResolvedAppConfig resolved(String key,
                                       String value,
                                       UUID sourceOrgUnitId,
                                       String sourceOrgUnitName,
                                       boolean inherited) {
        AppConfig config = new AppConfig(key, value, "测试说明");
        ResolvedAppConfig.SourceScope sourceScope = sourceOrgUnitId == null
                ? ResolvedAppConfig.SourceScope.TENANT_OVERRIDE
                : ResolvedAppConfig.SourceScope.ORGANIZATION_OVERRIDE;
        return new ResolvedAppConfig(
                config,
                uuid(7),
                "南靖县",
                sourceScope,
                sourceOrgUnitId,
                sourceOrgUnitName,
                inherited
        );
    }

    private UUID uuid(long seed) {
        return com.pro.ereportbacken.TestIds.uuid(seed);
    }
}
