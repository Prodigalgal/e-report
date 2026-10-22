package com.pro.ereportbacken.reporting;

import com.pro.ereportbacken.config.CacheNames;
import com.pro.ereportbacken.service.CacheInvalidatedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DailyOutstandingReportScheduleSignalsTests {

    private final Instant now = Instant.parse("2026-10-20T01:00:00Z");
    private final ReportSchedulingTestTimer timer = new ReportSchedulingTestTimer(now);
    private final DailyOutstandingWorkOrderReportScheduler scheduler = mock(DailyOutstandingWorkOrderReportScheduler.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final DailyOutstandingReportScheduleSignals signals =
            new DailyOutstandingReportScheduleSignals(scheduler, timer.scheduler, redis, "test");

    @Test
    void configurationAndOrganizationChangesRefreshLocallyAndBroadcastOnce() {
        signals.onCacheInvalidated(new CacheInvalidatedEvent(CacheNames.APP_CONFIG));
        signals.onCacheInvalidated(new CacheInvalidatedEvent(CacheNames.ORGANIZATION_DIRECTORY));

        timer.runDueAt(now);

        verify(scheduler, times(2)).requestRefresh();
        verify(redis).convertAndSend(eq("e-report:daily-report:schedule-changed:test"), anyString());
    }

    @Test
    void unrelatedCacheChangesDoNotRefreshSchedules() {
        signals.onCacheInvalidated(new CacheInvalidatedEvent(CacheNames.AUTHORIZATION_SNAPSHOT));

        assertThat(timer.pendingTimes()).isEmpty();
        verifyNoInteractions(scheduler, redis);
    }

    @Test
    void remoteChangesAndSubscriptionRecoveryReloadWithoutBroadcastLoops() {
        signals.onMessage(message("another-instance"), null);
        signals.onChannelSubscribed(signals.channel().getBytes(StandardCharsets.UTF_8), 1);

        verify(scheduler, times(2)).requestRefresh();
        verifyNoInteractions(redis);
    }

    @Test
    void doesNotReloadForItsOwnBroadcast() {
        signals.onCacheInvalidated(new CacheInvalidatedEvent(CacheNames.APP_CONFIG));
        timer.runDueAt(now);
        ArgumentCaptor<String> sender = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(signals.channel()), sender.capture());

        signals.onMessage(message(sender.getValue()), null);

        verify(scheduler).requestRefresh();
    }

    @Test
    void failedBroadcastRetriesWithoutPollingReports() {
        when(redis.convertAndSend(eq(signals.channel()), anyString()))
                .thenThrow(new IllegalStateException("redis unavailable")).thenReturn(1L);
        signals.onCacheInvalidated(new CacheInvalidatedEvent(CacheNames.APP_CONFIG));

        timer.runDueAt(now);
        assertThat(timer.pendingTimes()).containsExactly(now.plusSeconds(30));
        timer.runDueAt(now.plusSeconds(30));

        verify(redis, times(2)).convertAndSend(eq(signals.channel()), anyString());
        verify(scheduler).requestRefresh();
        assertThat(timer.pendingTimes()).isEmpty();
    }

    @Test
    void shutdownStopsPendingBroadcasts() {
        signals.onCacheInvalidated(new CacheInvalidatedEvent(CacheNames.APP_CONFIG));
        signals.close();
        timer.runDueAt(now);

        verify(redis, never()).convertAndSend(anyString(), anyString());
    }

    private Message message(String sender) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(sender.getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
