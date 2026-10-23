package com.pro.ereportbacken.notification.delivery;

import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.notification.ConfiguredMailSenderFactory;
import com.pro.ereportbacken.notification.MailSenderConfigurationService;
import com.pro.ereportbacken.notification.MailSenderRotationService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

public final class EmailDeliveryTestSupport {
    private EmailDeliveryTestSupport() {
    }

    public static EmailDeliveryTracker tracker() {
        TenantContext tenant = mock(TenantContext.class);
        when(tenant.currentTenantIdOrDefault()).thenReturn(UUID.randomUUID());
        return new EmailDeliveryTracker(mock(EmailDeliveryStore.class), tenant);
    }

    public static EmailDispatchService dispatcher(ConfiguredMailSenderFactory factory) {
        MailAccountGate gate = mock(MailAccountGate.class);
        EmailDispatchStore store = mock(EmailDispatchStore.class);
        when(gate.acquire(any())).thenReturn(new MailAccountGate.Reservation(
                new MailAccountGate.Lease("test-account", UUID.randomUUID(), Duration.ofMinutes(5), 10), null));
        when(gate.release(any(), any())).thenReturn(Instant.now().plusSeconds(60));
        when(store.claim(any(), any(), any())).thenAnswer(invocation ->
                new EmailDispatchStore.Claim(UUID.randomUUID(), UUID.randomUUID(), null));
        when(store.complete(any(), any(), nullable(MailFailurePolicy.class), any())).thenAnswer(invocation -> {
            MailFailurePolicy failure = invocation.getArgument(2);
            return failure == null ? EmailDispatchResult.accepted() : EmailDispatchResult.failed(failure.failureCode());
        });
        return new EmailDispatchService(mock(MailSenderConfigurationService.class),
                mock(MailSenderRotationService.class), factory, gate, store, tracker());
    }
}
