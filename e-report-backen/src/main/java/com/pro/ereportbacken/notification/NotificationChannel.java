package com.pro.ereportbacken.notification;

public interface NotificationChannel {
    NotificationChannelDescriptor descriptor();

    void send(WorkOrderNotification notification) throws Exception;
}
