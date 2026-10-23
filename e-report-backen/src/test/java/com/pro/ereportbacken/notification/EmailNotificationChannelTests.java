package com.pro.ereportbacken.notification;

import static com.pro.ereportbacken.notification.delivery.EmailDeliveryTestSupport.tracker;

import com.pro.ereportbacken.service.FileStorageService;
import com.pro.ereportbacken.service.MapKeyPoolService;
import com.pro.ereportbacken.service.MapService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class EmailNotificationChannelTests {
    @Test
    void optOutCompletesNormallyWithoutUsingSmtpOrBuildingAttachments() {
        var factory = mock(com.pro.ereportbacken.notification.delivery.EmailDispatchService.class);
        var files = mock(FileStorageService.class);
        var map = mock(MapService.class);
        var keys = mock(MapKeyPoolService.class);
        var resolver = mock(EmailNotificationRecipientResolver.class);
        var notification = mock(WorkOrderNotification.class);
        when(notification.id()).thenReturn(UUID.randomUUID());
        when(resolver.resolve(notification)).thenReturn(
                new EmailNotificationRecipientResolver.RecipientSelection(List.of(), 0, 2));

        new EmailNotificationChannel(factory, files, map, keys, resolver, tracker()).send(notification);

        verifyNoInteractions(factory, files, map, keys);
    }

    @Test
    void missingContactDetailsStillFailsInsteadOfPretendingTheUserOptedOut() {
        var factory = mock(com.pro.ereportbacken.notification.delivery.EmailDispatchService.class);
        var resolver = mock(EmailNotificationRecipientResolver.class);
        var notification = mock(WorkOrderNotification.class);
        when(resolver.resolve(notification)).thenReturn(
                new EmailNotificationRecipientResolver.RecipientSelection(List.of(), 1, 0));
        var channel = new EmailNotificationChannel(factory, mock(FileStorageService.class),
                mock(MapService.class), mock(MapKeyPoolService.class), resolver, tracker());

        assertThatThrownBy(() -> channel.send(notification)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有可用的收件地址");
        verifyNoInteractions(factory);
    }
}
