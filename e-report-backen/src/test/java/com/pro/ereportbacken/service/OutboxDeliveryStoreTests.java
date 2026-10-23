package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.OutboxEvent;
import com.pro.ereportbacken.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OutboxDeliveryStoreTests {
    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final OutboxDeliveryStore store = new OutboxDeliveryStore(repository);

    @Test
    void repeatedCooldownDeferralsNeverExhaustFailureAttemptsAndRetainTheRetryDeadline() {
        var event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAttempts(0);
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        for (int cycle = 0; cycle < 15; cycle++) {
            event.setState("PROCESSING"); event.setAttempts(event.getAttempts() + 1);
            event.setLockedAt(LocalDateTime.now());
            Instant retryAt = Instant.now().plusSeconds(10);
            store.defer(event.getId(), retryAt);
            assertThat(event.getState()).isEqualTo("PENDING");
            assertThat(event.getAttempts()).isZero();
            assertThat(event.getAvailableAt()).isEqualTo(LocalDateTime.ofInstant(retryAt, ZoneOffset.UTC));
            assertThat(event.getLockedAt()).isNull();
        }
    }

    @Test
    void terminalRecipientFailureStopsAutomaticEventRetry() {
        var event = new OutboxEvent();
        event.setId(UUID.randomUUID()); event.setState("PROCESSING"); event.setAttempts(1);
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        store.markTerminalFailure(event.getId(), "未取得明确提交结果，停止自动重发。");
        assertThat(event.getState()).isEqualTo("FAILED");
        assertThat(event.getLastError()).contains("停止自动重发");
        assertThat(event.getAttempts()).isEqualTo(1);
    }
}
