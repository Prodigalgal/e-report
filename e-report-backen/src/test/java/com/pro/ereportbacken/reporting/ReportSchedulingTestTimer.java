package com.pro.ereportbacken.reporting;

import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class ReportSchedulingTestTimer {

    final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final List<Call> calls = new ArrayList<>();
    private Instant now;

    ReportSchedulingTestTimer(Instant now) {
        this.now = now;
        when(scheduler.getClock()).thenAnswer(invocation -> Clock.fixed(this.now, ZoneOffset.UTC));
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            Call call = new Call(invocation.getArgument(0), invocation.getArgument(1));
            calls.add(call);
            return call.future;
        });
    }

    void runDueAt(Instant time) {
        now = time;
        for (int attempt = 0; attempt < 100; attempt++) {
            var pending = calls.stream()
                    .filter(call -> !call.executed && !call.cancelled && !call.at.isAfter(time))
                    .min(Comparator.comparing(call -> call.at));
            if (pending.isEmpty()) {
                return;
            }
            Call call = pending.orElseThrow();
            call.executed = true;
            call.runnable.run();
        }
        throw new AssertionError("定时任务发生立即重入循环");
    }

    List<Instant> pendingTimes() {
        return calls.stream().filter(call -> !call.executed && !call.cancelled).map(call -> call.at).sorted().toList();
    }

    Runnable firstTaskAt(Instant time) {
        return calls.stream().filter(call -> call.at.equals(time)).findFirst().orElseThrow().runnable;
    }

    private static final class Call {
        private final Runnable runnable;
        private final Instant at;
        private final ScheduledFuture<?> future = mock(ScheduledFuture.class);
        private boolean executed;
        private boolean cancelled;

        private Call(Runnable runnable, Instant at) {
            this.runnable = runnable;
            this.at = at;
            when(future.cancel(false)).thenAnswer(invocation -> {
                cancelled = true;
                return true;
            });
        }
    }
}
