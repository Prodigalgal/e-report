package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.security.ConfigurationScopeContext;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.notification.delivery.EmailDispatchDeferredException;
import com.pro.ereportbacken.notification.delivery.EmailDispatchFailedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Log4j2
public class NotificationCenterService {

    private final WorkOrderNotificationReader notificationReader;
    private final NotificationChannelRegistry channelRegistry;
    private final NotificationSwitchPolicy switchPolicy;
    private final TenantContext tenantContext;
    private final ConfigurationScopeContext configurationScopeContext;

    public NotificationCenterService(WorkOrderNotificationReader notificationReader,
                                     NotificationChannelRegistry channelRegistry,
                                     NotificationSwitchPolicy switchPolicy,
                                     TenantContext tenantContext,
                                     ConfigurationScopeContext configurationScopeContext) {
        this.notificationReader = notificationReader;
        this.channelRegistry = channelRegistry;
        this.switchPolicy = switchPolicy;
        this.tenantContext = tenantContext;
        this.configurationScopeContext = configurationScopeContext;
    }

    public void notifyWorkOrder(UUID tenantId,
                                UUID workOrderId,
                                WorkOrderNotificationKind kind) {
        notifyWorkOrder(tenantId, workOrderId, kind, null);
    }

    public void notifyWorkOrder(UUID tenantId,
                                UUID workOrderId,
                                WorkOrderNotificationKind kind,
                                String channelName) {
        List<NotificationChannel> selectedChannels = channelName == null
                ? channelRegistry.channels()
                : List.of(channelRegistry.require(channelName));
        tenantContext.runInTenant(tenantId, () -> {
            WorkOrderNotification notification = notificationReader.load(tenantId, workOrderId, kind);
            configurationScopeContext.runInOrgUnit(notification.reportingOrgUnitId(), () -> {
                deliver(notification, selectedChannels);
                return null;
            });
        });
    }

    public void notifyAssignmentReceived(UUID tenantId, UUID workOrderId, UUID assignmentId) {
        NotificationChannel emailChannel = channelRegistry.require(EmailNotificationChannel.CHANNEL_NAME);
        tenantContext.runInTenant(tenantId, () -> {
            Optional<WorkOrderNotification> notification = notificationReader.loadAssignmentReceived(
                    tenantId, workOrderId, assignmentId);
            if (notification.isEmpty()) {
                log.info("派单邮件事件已过时，接收人已不再是当前处理人或待接收人。workOrderId={}, assignmentId={}",
                        workOrderId, assignmentId);
                return;
            }
            WorkOrderNotification current = notification.get();
            configurationScopeContext.runInOrgUnit(current.reportingOrgUnitId(), () -> {
                deliver(current, List.of(emailChannel));
                return null;
            });
        });
    }

    private void deliver(WorkOrderNotification notification, List<NotificationChannel> selectedChannels) {
        UUID workOrderId = notification.id();
        if (!switchPolicy.centerEnabled()) {
            log.info("Notification center is disabled. Skip work order {}.", workOrderId);
            return;
        }

        int enabledChannelCount = 0;
        List<Exception> failures = new ArrayList<>();
        for (NotificationChannel channel : selectedChannels) {
            NotificationChannelDescriptor descriptor = channel.descriptor();
            if (!switchPolicy.channelEnabled(descriptor)) {
                log.debug("Notification channel '{}' is disabled. Skip work order {}.", descriptor.name(), workOrderId);
                continue;
            }
            enabledChannelCount++;
            try {
                channel.send(notification);
            } catch (EmailDispatchDeferredException deferred) {
                failures.add(deferred);
                log.debug("邮件通知已延后。workOrderId={}, retryAt={}", workOrderId, deferred.retryAt());
            } catch (EmailDispatchFailedException failure) {
                failures.add(failure);
                log.warn("邮件通知已停止自动重试。workOrderId={}, reason={}", workOrderId, failure.getMessage());
            } catch (Exception exception) {
                failures.add(exception);
                log.error("Notification channel '{}' failed for work order {}: {}",
                        descriptor.name(), workOrderId, exception.getMessage(), exception);
            }
        }

        if (enabledChannelCount == 0) {
            log.info("Notification center is enabled but no selected channel is enabled. Work order {} has no outbound notification.",
                    workOrderId);
        }
        propagateDeliveryFailures(workOrderId, selectedChannels, failures);
    }

    private void propagateDeliveryFailures(UUID workOrderId,
                                           List<NotificationChannel> selectedChannels,
                                           List<Exception> failures) {
        if (failures.isEmpty()) {
            return;
        }
        if (failures.size() == 1 && (failures.getFirst() instanceof EmailDispatchDeferredException
                || failures.getFirst() instanceof EmailDispatchFailedException)) {
            throw (RuntimeException) failures.getFirst();
        }
        if (selectedChannels.size() == 1 && failures.size() == 1) {
            Exception failure = failures.getFirst();
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(
                    "Notification channel '" + selectedChannels.getFirst().descriptor().name()
                            + "' failed for work order " + workOrderId + ".",
                    failure
            );
        }
        IllegalStateException deliveryFailure = new IllegalStateException(
                "Notification delivery failed for " + failures.size() + " enabled channel(s)."
        );
        failures.forEach(deliveryFailure::addSuppressed);
        throw deliveryFailure;
    }
}
