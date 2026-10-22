package com.pro.ereportbacken.notification.delivery;

import com.pro.ereportbacken.security.TenantContext;
import static org.mockito.Mockito.mock;

public final class EmailDeliveryTestSupport {
    private EmailDeliveryTestSupport() {
    }

    public static EmailDeliveryTracker tracker() {
        return new EmailDeliveryTracker(mock(EmailDeliveryStore.class), mock(TenantContext.class));
    }
}
