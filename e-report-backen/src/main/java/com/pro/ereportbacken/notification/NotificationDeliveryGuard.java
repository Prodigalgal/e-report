package com.pro.ereportbacken.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationDeliveryGuard {

    private final boolean deliveryEnabled;

    public NotificationDeliveryGuard(@Value("${notification.delivery-enabled:true}") boolean deliveryEnabled) {
        this.deliveryEnabled = deliveryEnabled;
    }

    public boolean deliveryEnabled() {
        return deliveryEnabled;
    }
}
