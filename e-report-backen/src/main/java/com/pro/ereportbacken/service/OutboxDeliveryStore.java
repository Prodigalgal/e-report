package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import java.util.UUID;

import com.pro.ereportbacken.model.pojo.OutboxEvent;
import com.pro.ereportbacken.repository.OutboxEventRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxDeliveryStore {

    private static final int MAX_ATTEMPTS = 10;
    private final OutboxEventRepository repository;

    public OutboxDeliveryStore(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEvent> claim(int batchSize) {
        LocalDateTime now = UtcTime.now();
        List<OutboxEvent> events = repository.findDeliverable(
                now,
                now.minusMinutes(5),
                PageRequest.of(0, batchSize)
        );
        events.forEach(event -> {
            event.setState("PROCESSING");
            event.setAttempts(event.getAttempts() + 1);
            event.setLockedAt(now);
            event.setLastError(null);
        });
        return repository.saveAll(events);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(UUID eventId) {
        OutboxEvent event = requireEvent(eventId);
        event.setState("COMPLETED");
        event.setProcessedAt(UtcTime.now());
        event.setLockedAt(null);
        repository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID eventId, String errorMessage) {
        OutboxEvent event = requireEvent(eventId);
        event.setLastError(truncate(errorMessage));
        event.setLockedAt(null);
        if (event.getAttempts() >= MAX_ATTEMPTS) {
            event.setState("FAILED");
        } else {
            event.setState("PENDING");
            long retryMinutes = Math.min(60L, 1L << Math.min(6, event.getAttempts()));
            event.setAvailableAt(UtcTime.now().plusMinutes(retryMinutes));
        }
        repository.save(event);
    }

    private OutboxEvent requireEvent(UUID eventId) {
        return repository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Outbox event not found: " + eventId));
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown delivery failure";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}

