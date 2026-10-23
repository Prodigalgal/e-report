package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.model.pojo.WorkOrderType;
import com.pro.ereportbacken.security.ConfigurationScopeContext;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.security.TenantExecutionContext;
import com.pro.ereportbacken.service.AppConfigService;
import com.pro.ereportbacken.service.DefaultTenantService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationCenterServiceTests {

    @Test
    void resolvesSwitchesInsideResponsibleOrganizationContext() throws Exception {
        AppConfigService appConfigService = mock(AppConfigService.class);
        WorkOrderNotificationReader reader = mock(WorkOrderNotificationReader.class);
        NotificationChannel email = channel("email", "notification.email.enabled", true);
        TenantContext tenantContext = tenantContext();
        ConfigurationScopeContext configurationScopeContext = new ConfigurationScopeContext();
        WorkOrderNotification notification = notification(uuid(9), uuid(7));
        when(reader.load(uuid(3), uuid(9), WorkOrderNotificationKind.CREATED)).thenReturn(notification);
        when(appConfigService.getConfigValueAsBoolean("notification.center.enabled", false)).thenAnswer(invocation -> {
            assertThat(configurationScopeContext.currentOrgUnitIdOrNull()).isEqualTo(uuid(7));
            return false;
        });
        NotificationCenterService service = service(
                appConfigService, reader, List.of(email), tenantContext, configurationScopeContext
        );

        service.notifyWorkOrder(uuid(3), uuid(9), WorkOrderNotificationKind.CREATED);

        verify(email, never()).send(notification);
        assertThat(configurationScopeContext.currentOrgUnitIdOrNull()).isNull();
    }

    @Test
    void enabledChannelFailurePropagatesToOutboxProcessor() throws Exception {
        AppConfigService appConfigService = mock(AppConfigService.class);
        WorkOrderNotificationReader reader = mock(WorkOrderNotificationReader.class);
        NotificationChannel channel = channel("failing-channel", "notification.failing.enabled", false);
        TenantContext tenantContext = tenantContext();
        WorkOrderNotification notification = notification(uuid(9), uuid(7));
        when(appConfigService.getConfigValueAsBoolean("notification.center.enabled", false)).thenReturn(true);
        when(appConfigService.getConfigValueAsBoolean("notification.failing.enabled", false)).thenReturn(true);
        when(reader.load(uuid(3), uuid(9), WorkOrderNotificationKind.CREATED)).thenReturn(notification);
        org.mockito.Mockito.doThrow(new IllegalStateException("channel unavailable"))
                .when(channel).send(notification);
        NotificationCenterService service = service(
                appConfigService, reader, List.of(channel), tenantContext, new ConfigurationScopeContext()
        );

        assertThatThrownBy(() -> service.notifyWorkOrder(uuid(3), uuid(9), WorkOrderNotificationKind.CREATED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("channel unavailable");
    }

    @Test
    void targetedDeliveryUsesOnlySelectedEnabledChannel() throws Exception {
        AppConfigService appConfigService = mock(AppConfigService.class);
        WorkOrderNotificationReader reader = mock(WorkOrderNotificationReader.class);
        NotificationChannel email = channel("email", "notification.email.enabled", true);
        NotificationChannel wecom = channel("wecom", "notification.wecom.enabled", false);
        TenantContext tenantContext = tenantContext();
        ConfigurationScopeContext configurationScopeContext = new ConfigurationScopeContext();
        WorkOrderNotification notification = notification(uuid(9), uuid(7));
        when(appConfigService.getConfigValueAsBoolean("notification.center.enabled", false)).thenReturn(true);
        when(appConfigService.getConfigValueAsBoolean("notification.wecom.enabled", false)).thenReturn(true);
        when(reader.load(uuid(3), uuid(9), WorkOrderNotificationKind.CREATED)).thenReturn(notification);
        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(configurationScopeContext.currentOrgUnitIdOrNull()).isEqualTo(uuid(7));
            return null;
        }).when(wecom).send(notification);
        NotificationCenterService service = service(
                appConfigService, reader, List.of(email, wecom), tenantContext, configurationScopeContext
        );

        service.notifyWorkOrder(uuid(3), uuid(9), WorkOrderNotificationKind.CREATED, "wecom");

        verify(wecom).send(notification);
        verify(email, never()).send(notification);
        verify(appConfigService, never()).getConfigValueAsBoolean("notification.email.enabled", true);
        assertThat(configurationScopeContext.currentOrgUnitIdOrNull()).isNull();
    }

    @Test
    void emailCooldownKeepsItsRetryDeadlineWhenOtherChannelsHaveCompleted() throws Exception {
        var configs = mock(AppConfigService.class);
        var reader = mock(WorkOrderNotificationReader.class);
        var email = channel("email", "notification.email.enabled", true);
        var wecom = channel("wecom", "notification.wecom.enabled", false);
        var notification = notification(uuid(9), uuid(7));
        when(configs.getConfigValueAsBoolean("notification.center.enabled", false)).thenReturn(true);
        when(configs.getConfigValueAsBoolean("notification.email.enabled", true)).thenReturn(true);
        when(configs.getConfigValueAsBoolean("notification.wecom.enabled", false)).thenReturn(true);
        when(reader.load(uuid(3), uuid(9), WorkOrderNotificationKind.CREATED)).thenReturn(notification);
        var deferred = new com.pro.ereportbacken.notification.delivery.EmailDispatchDeferredException(java.time.Instant.now().plusSeconds(10));
        org.mockito.Mockito.doThrow(deferred).when(email).send(notification);
        var service = service(configs, reader, List.of(email, wecom), tenantContext(), new ConfigurationScopeContext());
        assertThatThrownBy(() -> service.notifyWorkOrder(uuid(3), uuid(9), WorkOrderNotificationKind.CREATED)).isSameAs(deferred);
        verify(wecom).send(notification);
    }

    @Test
    void disabledTargetedChannelCompletesWithoutCallingTransport() throws Exception {
        AppConfigService appConfigService = mock(AppConfigService.class);
        WorkOrderNotificationReader reader = mock(WorkOrderNotificationReader.class);
        NotificationChannel wecom = channel("wecom", "notification.wecom.enabled", false);
        WorkOrderNotification notification = notification(uuid(9), uuid(7));
        when(appConfigService.getConfigValueAsBoolean("notification.center.enabled", false)).thenReturn(true);
        when(appConfigService.getConfigValueAsBoolean("notification.wecom.enabled", false)).thenReturn(false);
        when(reader.load(uuid(3), uuid(9), WorkOrderNotificationKind.SLA_BREACHED)).thenReturn(notification);
        NotificationCenterService service = service(
                appConfigService,
                reader,
                List.of(wecom),
                tenantContext(),
                new ConfigurationScopeContext()
        );

        service.notifyWorkOrder(uuid(3), uuid(9), WorkOrderNotificationKind.SLA_BREACHED, "wecom");

        verify(wecom, never()).send(notification);
    }

    @Test
    void rejectsUnknownTargetedChannelBeforeLoadingWorkOrder() {
        AppConfigService appConfigService = mock(AppConfigService.class);
        WorkOrderNotificationReader reader = mock(WorkOrderNotificationReader.class);
        NotificationCenterService service = service(
                appConfigService,
                reader,
                List.of(channel("email", "notification.email.enabled", true)),
                tenantContext(),
                new ConfigurationScopeContext()
        );

        assertThatThrownBy(() -> service.notifyWorkOrder(
                uuid(3), uuid(9), WorkOrderNotificationKind.CREATED, "missing"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown notification channel");
        verify(reader, never()).load(uuid(3), uuid(9), WorkOrderNotificationKind.CREATED);
    }

    private NotificationCenterService service(AppConfigService appConfigService,
                                              WorkOrderNotificationReader reader,
                                              List<NotificationChannel> channels,
                                              TenantContext tenantContext,
                                              ConfigurationScopeContext configurationScopeContext) {
        return new NotificationCenterService(
                reader,
                new NotificationChannelRegistry(channels),
                new NotificationSwitchPolicy(appConfigService, new NotificationDeliveryGuard(true)),
                tenantContext,
                configurationScopeContext
        );
    }

    private TenantContext tenantContext() {
        return new TenantContext(mock(DefaultTenantService.class), new TenantExecutionContext());
    }

    private NotificationChannel channel(String name, String enabledKey, boolean defaultEnabled) {
        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.descriptor()).thenReturn(new NotificationChannelDescriptor(
                name, name, name + " notifications", enabledKey, defaultEnabled
        ));
        return channel;
    }

    private WorkOrderNotification notification(UUID workOrderId, UUID responsibleOrgId) {
        return new WorkOrderNotification(
                workOrderId,
                responsibleOrgId,
                responsibleOrgId,
                WorkOrderNotificationKind.CREATED,
                WorkOrderType.NETWORK_FAULT,
                WorkOrderType.NETWORK_FAULT.getDisplayName(),
                "普通",
                "等待接单",
                null,
                null,
                "测试位置",
                "",
                "测试原因",
                "13900000000",
                "测试联系人",
                LocalDateTime.now(),
                List.of(),
                null,
                null,
                false
        );
    }

    private UUID uuid(long seed) {
        return com.pro.ereportbacken.TestIds.uuid(seed);
    }
}
