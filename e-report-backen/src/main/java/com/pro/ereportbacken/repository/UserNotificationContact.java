package com.pro.ereportbacken.repository;

import java.util.UUID;

public interface UserNotificationContact {
    UUID getUserId();

    String getPhone();

    String getEmail();

    boolean getEmailWorkOrderCreated();

    boolean getEmailWorkOrderSlaBreached();

    boolean getEmailWorkOrderAssigned();

    boolean getEmailDailyOutstandingReport();
}
