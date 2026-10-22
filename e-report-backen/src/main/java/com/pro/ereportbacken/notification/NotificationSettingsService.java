package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.model.dto.NotificationCenterSettingsDTO;
import com.pro.ereportbacken.model.dto.DailyOutstandingReportSettingsDTO;
import com.pro.ereportbacken.model.dto.NotificationSwitchDTO;
import com.pro.ereportbacken.service.AppConfigService;
import com.pro.ereportbacken.service.ResolvedAppConfig;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static com.pro.ereportbacken.config.NotificationConstant.CENTER_ENABLED_KEY;
import static com.pro.ereportbacken.config.NotificationConstant.DAILY_OUTSTANDING_REPORT_ENABLED_KEY;
import static com.pro.ereportbacken.config.NotificationConstant.DAILY_OUTSTANDING_REPORT_SEND_TIME_KEY;
import static com.pro.ereportbacken.config.NotificationConstant.DAILY_OUTSTANDING_REPORT_SWITCH_NAME;

@Service
public class NotificationSettingsService {

    private static final DateTimeFormatter SEND_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final String DEFAULT_SEND_TIME = "18:00";
    private static final String DAILY_REPORT_SCHEDULE_DESCRIPTION = "每日区县未处理工单邮件汇总发送时间（HH:mm）";

    private static final SettingDescriptor CENTER = new SettingDescriptor(
            "center",
            "通知中心总开关",
            "关闭后，当前作用域内所有通知渠道暂停发送。",
            CENTER_ENABLED_KEY,
            false
    );
    private static final SettingDescriptor DAILY_OUTSTANDING_REPORT = new SettingDescriptor(
            DAILY_OUTSTANDING_REPORT_SWITCH_NAME,
            "每日未处理工单汇总",
            "每天晚上按区县汇总未闭环工单，并将电子表格发送给本区县管理员。",
            DAILY_OUTSTANDING_REPORT_ENABLED_KEY,
            false
    );

    private final AppConfigService appConfigService;
    private final NotificationChannelRegistry channelRegistry;
    private final MailSenderConfigurationService mailSenderConfigurationService;

    public NotificationSettingsService(AppConfigService appConfigService,
                                       NotificationChannelRegistry channelRegistry,
                                       MailSenderConfigurationService mailSenderConfigurationService) {
        this.appConfigService = appConfigService;
        this.channelRegistry = channelRegistry;
        this.mailSenderConfigurationService = mailSenderConfigurationService;
    }

    public NotificationCenterSettingsDTO getSettings(UUID orgUnitId) {
        NotificationSwitchDTO center = resolve(CENTER, orgUnitId);
        var channels = channelRegistry.channels().stream()
                .map(NotificationChannel::descriptor)
                .map(SettingDescriptor::from)
                .map(descriptor -> resolve(descriptor, orgUnitId))
                .toList();
        return new NotificationCenterSettingsDTO(
                center, channels, resolveDailyOutstandingReport(orgUnitId),
                mailSenderConfigurationService.settings(orgUnitId));
    }

    public NotificationSwitchDTO update(String switchName, boolean enabled, UUID orgUnitId) {
        SettingDescriptor descriptor = requireDescriptor(switchName);
        ResolvedAppConfig updated = appConfigService.saveOrUpdateScopedConfig(
                descriptor.configKey(),
                String.valueOf(enabled),
                descriptor.description(),
                orgUnitId
        );
        return toDto(descriptor, updated);
    }

    public DailyOutstandingReportSettingsDTO updateDailyOutstandingReportSchedule(String sendTime, UUID orgUnitId) {
        String normalizedSendTime = normalizeSendTime(sendTime);
        ResolvedAppConfig updatedSchedule = appConfigService.saveOrUpdateScopedConfig(
                DAILY_OUTSTANDING_REPORT_SEND_TIME_KEY,
                normalizedSendTime,
                DAILY_REPORT_SCHEDULE_DESCRIPTION,
                orgUnitId
        );
        return toDailyOutstandingReportSettings(
                resolve(DAILY_OUTSTANDING_REPORT, orgUnitId),
                updatedSchedule
        );
    }

    private DailyOutstandingReportSettingsDTO resolveDailyOutstandingReport(UUID orgUnitId) {
        NotificationSwitchDTO switchSetting = resolve(DAILY_OUTSTANDING_REPORT, orgUnitId);
        return appConfigService.findScopedConfigByKey(DAILY_OUTSTANDING_REPORT_SEND_TIME_KEY, orgUnitId)
                .map(schedule -> toDailyOutstandingReportSettings(switchSetting, schedule))
                .orElseGet(() -> new DailyOutstandingReportSettingsDTO(
                        switchSetting, DEFAULT_SEND_TIME, "UNCONFIGURED", null, null, false));
    }

    private DailyOutstandingReportSettingsDTO toDailyOutstandingReportSettings(
            NotificationSwitchDTO switchSetting,
            ResolvedAppConfig schedule) {
        return new DailyOutstandingReportSettingsDTO(
                switchSetting,
                normalizeSendTime(schedule.config().getConfigValue()),
                schedule.sourceScope().name(),
                schedule.sourceOrgUnitId(),
                schedule.sourceOrgUnitName(),
                schedule.inherited()
        );
    }

    private NotificationSwitchDTO resolve(SettingDescriptor descriptor, UUID orgUnitId) {
        return appConfigService.findScopedConfigByKey(descriptor.configKey(), orgUnitId)
                .map(resolved -> toDto(descriptor, resolved))
                .orElseGet(() -> new NotificationSwitchDTO(
                        descriptor.name(), descriptor.displayName(), descriptor.description(),
                        descriptor.defaultEnabled(), "UNCONFIGURED", null, null, false));
    }

    private SettingDescriptor requireDescriptor(String switchName) {
        String normalizedName = switchName == null ? "" : switchName.trim().toLowerCase(Locale.ROOT);
        if (CENTER.name().equals(normalizedName)) {
            return CENTER;
        }
        if (DAILY_OUTSTANDING_REPORT.name().equals(normalizedName)) {
            return DAILY_OUTSTANDING_REPORT;
        }
        return SettingDescriptor.from(channelRegistry.require(normalizedName).descriptor());
    }

    private String normalizeSendTime(String sendTime) {
        try {
            return LocalTime.parse(sendTime == null ? "" : sendTime.trim(), SEND_TIME_FORMATTER)
                    .format(SEND_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("发送时间必须使用HH:mm格式。", exception);
        }
    }

    private NotificationSwitchDTO toDto(SettingDescriptor descriptor, ResolvedAppConfig resolved) {
        return new NotificationSwitchDTO(
                descriptor.name(),
                descriptor.displayName(),
                descriptor.description(),
                booleanValue(resolved.config().getConfigValue(), descriptor.defaultEnabled()),
                resolved.sourceScope().name(),
                resolved.sourceOrgUnitId(),
                resolved.sourceOrgUnitName(),
                resolved.inherited()
        );
    }

    private boolean booleanValue(String value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value)
                || "1".equals(value)
                || "yes".equalsIgnoreCase(value);
    }

    private record SettingDescriptor(
            String name,
            String displayName,
            String description,
            String configKey,
            boolean defaultEnabled
    ) {
        private static SettingDescriptor from(NotificationChannelDescriptor channel) {
            return new SettingDescriptor(
                    channel.name(),
                    channel.displayName(),
                    channel.description(),
                    channel.enabledConfigKey(),
                    channel.defaultEnabled()
            );
        }
    }
}
