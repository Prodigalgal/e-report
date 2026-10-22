package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.service.AppConfigService;
import org.springframework.stereotype.Component;

import static com.pro.ereportbacken.config.NotificationConstant.CENTER_ENABLED_KEY;

@Component
public class NotificationSwitchPolicy {

    private final AppConfigService appConfigService;
    private final NotificationDeliveryGuard deliveryGuard;

    public NotificationSwitchPolicy(AppConfigService appConfigService,
                                    NotificationDeliveryGuard deliveryGuard) {
        this.appConfigService = appConfigService;
        this.deliveryGuard = deliveryGuard;
    }

    public boolean centerEnabled() {
        return deliveryGuard.deliveryEnabled()
                && appConfigService.getConfigValueAsBoolean(CENTER_ENABLED_KEY, false);
    }

    public boolean channelEnabled(NotificationChannelDescriptor descriptor) {
        return appConfigService.getConfigValueAsBoolean(
                descriptor.enabledConfigKey(),
                descriptor.defaultEnabled()
        );
    }
}
