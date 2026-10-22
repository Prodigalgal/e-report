package com.pro.ereportbacken.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
public class AuthenticationHashWorkloadGuard {

    private final Semaphore admissionSlots;
    private final Semaphore hashSlots;
    private final int maximumAdmissions;
    private final int maximumConcurrent;
    private final Duration waitTimeout;
    private final Duration retryAfter;

    public AuthenticationHashWorkloadGuard(int maximumConcurrent,
                                           int maximumQueued,
                                           Duration waitTimeout,
                                           Duration retryAfter) {
        if (maximumConcurrent <= 0 || maximumQueued < 0) {
            throw new IllegalArgumentException("Authentication workload capacities are invalid.");
        }
        this.maximumAdmissions = maximumConcurrent + maximumQueued;
        this.maximumConcurrent = maximumConcurrent;
        this.admissionSlots = new Semaphore(maximumAdmissions, true);
        this.hashSlots = new Semaphore(maximumConcurrent, true);
        this.waitTimeout = requirePositive(waitTimeout, "waitTimeout");
        this.retryAfter = requirePositive(retryAfter, "retryAfter");
    }

    public Permit acquire() {
        if (!admissionSlots.tryAcquire()) {
            throw unavailable("Authentication hash queue is full.");
        }
        boolean hashSlotAcquired = false;
        try {
            hashSlotAcquired = hashSlots.tryAcquire(waitTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!hashSlotAcquired) {
                throw unavailable("Authentication hash wait timed out.");
            }
            return new Permit(admissionSlots, hashSlots);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("Authentication hash wait was interrupted.");
        } finally {
            if (!hashSlotAcquired) {
                admissionSlots.release();
            }
        }
    }

    private ResponseStatusException unavailable(String reason) {
        log.warn("{} active={}, admitted={}", reason,
                maximumConcurrent - hashSlots.availablePermits(),
                maximumAdmissions - admissionSlots.availablePermits());
        return new AuthenticationCapacityException(retryAfter);
    }

    private Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("Authentication workload " + name + " must be positive.");
        }
        return value;
    }

    public static final class Permit implements AutoCloseable {

        private final Semaphore admissionSlots;
        private final Semaphore hashSlots;
        private final AtomicBoolean released = new AtomicBoolean();

        private Permit(Semaphore admissionSlots, Semaphore hashSlots) {
            this.admissionSlots = admissionSlots;
            this.hashSlots = hashSlots;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                hashSlots.release();
                admissionSlots.release();
            }
        }
    }

    private static final class AuthenticationCapacityException extends ResponseStatusException {

        private final HttpHeaders headers = new HttpHeaders();

        private AuthenticationCapacityException(Duration retryAfter) {
            super(HttpStatus.SERVICE_UNAVAILABLE, "当前登录人数较多，请稍后自动重试。");
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter.toSeconds()));
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
