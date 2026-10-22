package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.E2eIsolationStatusDTO;
import com.pro.ereportbacken.security.PermissionCatalog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/e2e-isolation")
@ConditionalOnProperty(name = "app.e2e-isolation.enabled", havingValue = "true")
public class AdminE2eIsolationController {

    private final E2eIsolationStatusDTO status;

    public AdminE2eIsolationController(
            @Value("${notification.delivery-enabled:true}") boolean notificationDeliveryEnabled,
            @Value("${notification.center.enabled:false}") boolean notificationCenterEnabled,
            @Value("${notification.email.enabled:false}") boolean emailEnabled,
            @Value("${notification.wecom.enabled:false}") boolean weComEnabled,
            @Value("${notification.dingtalk.enabled:false}") boolean dingTalkEnabled,
            @Value("${notification.daily-outstanding-report.enabled:false}") boolean dailyOutstandingReportEnabled,
            @Value("${app.outbox.enqueue-enabled:true}") boolean outboxEnqueueEnabled,
            @Value("${app.outbox.enabled:true}") boolean outboxDeliveryEnabled,
            @Value("${app.daily-outstanding-report.scheduler-enabled:true}") boolean dailyOutstandingReportSchedulerEnabled) {
        boolean ready = !notificationDeliveryEnabled
                && !notificationCenterEnabled
                && !emailEnabled
                && !weComEnabled
                && !dingTalkEnabled
                && !dailyOutstandingReportEnabled
                && !outboxEnqueueEnabled
                && !outboxDeliveryEnabled
                && !dailyOutstandingReportSchedulerEnabled;
        this.status = new E2eIsolationStatusDTO(
                ready,
                notificationDeliveryEnabled,
                notificationCenterEnabled,
                emailEnabled,
                weComEnabled,
                dingTalkEnabled,
                dailyOutstandingReportEnabled,
                outboxEnqueueEnabled,
                outboxDeliveryEnabled,
                dailyOutstandingReportSchedulerEnabled
        );
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.OBSERVABILITY_VIEW + "')")
    public E2eIsolationStatusDTO status() {
        return status;
    }
}
