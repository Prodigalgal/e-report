package com.pro.ereportbacken.notification.delivery;

import com.pro.ereportbacken.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailDeliveryTrackerTests {
    private final EmailDeliveryStore store = mock(EmailDeliveryStore.class);
    private final EmailDeliveryTracker tracker = new EmailDeliveryTracker(store, mock(TenantContext.class));
    private final UUID id = UUID.randomUUID();
    private final EmailDeliveryContext context = new EmailDeliveryContext(UUID.randomUUID(), UUID.randomUUID(),
            EmailDeliveryCategory.CREATED, UUID.randomUUID(), UUID.randomUUID(), "recipient@example.test", "发送验证");

    @Test
    void recordsStartBeforeSmtpAndAcceptedOnlyAfterTheSingleSend() throws Exception {
        when(store.start(context, "sender@example.test")).thenReturn(id);
        AtomicInteger calls = new AtomicInteger();
        tracker.send(context, "sender@example.test", () -> {
            verify(store).start(context, "sender@example.test");
            verify(store, never()).complete(any(), any(), anyLong(), any());
            calls.incrementAndGet();
        });
        assertThat(calls).hasValue(1);
        verify(store).complete(eq(id), eq(EmailDeliveryStatus.SMTP_ACCEPTED), anyLong(), isNull());
    }

    @Test
    void finalizationFailureDoesNotTurnSmtpAcceptanceIntoARetry() {
        when(store.start(context, null)).thenReturn(id);
        doThrow(new IllegalStateException("database unavailable")).when(store).complete(any(), any(), anyLong(), any());
        AtomicInteger calls = new AtomicInteger();
        assertThatCode(() -> tracker.send(context, null, calls::incrementAndGet)).doesNotThrowAnyException();
        assertThat(calls).hasValue(1);
    }

    @Test
    void authenticationFailureStoresOnlyClassificationAndRethrowsTheOriginal() {
        when(store.start(context, null)).thenReturn(id);
        var failure = new MailAuthenticationException("password=private-value recipient@example.test");
        assertThatThrownBy(() -> tracker.send(context, null, () -> { throw failure; })).isSameAs(failure);
        verify(store).complete(eq(id), eq(EmailDeliveryStatus.FAILED), anyLong(), eq("AUTHENTICATION"));
    }

    @Test
    void timeoutLeavesTheSmtpOutcomeUnconfirmed() {
        when(store.start(context, null)).thenReturn(id);
        var failure = new MailSendException("private transport details", new SocketTimeoutException("read timeout"));
        assertThatThrownBy(() -> tracker.send(context, null, () -> { throw failure; })).isSameAs(failure);
        verify(store).complete(eq(id), eq(EmailDeliveryStatus.UNKNOWN), anyLong(), eq("TIMEOUT"));
    }

    @Test
    void classifiesTimeoutsInsideJavaMailPerMessageFailures() {
        when(store.start(context, null)).thenReturn(id);
        var nested = new jakarta.mail.MessagingException("transport detail", new SocketTimeoutException("timeout"));
        var failure = new MailSendException("send failed", null, Map.of("mail", nested));
        assertThatThrownBy(() -> tracker.send(context, null, () -> { throw failure; })).isSameAs(failure);
        verify(store).complete(eq(id), eq(EmailDeliveryStatus.UNKNOWN), anyLong(), eq("TIMEOUT"));
    }

    @Test
    void unavailableDurableStartPreventsAnUnrecordedSmtpSend() {
        when(store.start(context, null)).thenThrow(new IllegalStateException("database unavailable"));
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> tracker.send(context, null, calls::incrementAndGet)).isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void aLogFailureDoesNotHideTheOriginalSmtpError() {
        when(store.start(context, null)).thenReturn(id);
        doThrow(new IllegalStateException("write failed")).when(store).complete(any(), any(), anyLong(), any());
        var failure = new MailSendException("smtp error");
        assertThatThrownBy(() -> tracker.send(context, null, () -> { throw failure; })).isSameAs(failure);
    }
}
