package com.pro.ereportbacken.reporting;

import com.pro.ereportbacken.config.CacheNames;
import com.pro.ereportbacken.service.CacheInvalidatedEvent;
import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "app.daily-outstanding-report.scheduler-enabled", havingValue = "true", matchIfMissing = true)
@Log4j2
public class DailyOutstandingReportScheduleSignals implements MessageListener, SubscriptionListener {

    private static final Set<String> SCHEDULE_CACHES = Set.of(CacheNames.APP_CONFIG, CacheNames.ORGANIZATION_DIRECTORY);
    private static final Duration BROADCAST_RETRY_DELAY = Duration.ofSeconds(30);

    private final DailyOutstandingWorkOrderReportScheduler reportScheduler;
    private final TaskScheduler taskScheduler;
    private final StringRedisTemplate redisTemplate;
    private final String channel;
    private final String instanceId = UUID.randomUUID().toString();
    private final AtomicBoolean broadcastQueued = new AtomicBoolean();
    private volatile boolean closed;

    public DailyOutstandingReportScheduleSignals(
            DailyOutstandingWorkOrderReportScheduler reportScheduler,
            TaskScheduler taskScheduler,
            StringRedisTemplate redisTemplate,
            @Value("${DEPLOYMENT_ENVIRONMENT:production}") String environment) {
        this.reportScheduler = reportScheduler;
        this.taskScheduler = taskScheduler;
        this.redisTemplate = redisTemplate;
        this.channel = "e-report:daily-report:schedule-changed:" + environment;
    }

    public String channel() {
        return channel;
    }

    @EventListener
    public void onCacheInvalidated(CacheInvalidatedEvent event) {
        if (!closed && SCHEDULE_CACHES.contains(event.cacheName())) {
            reportScheduler.requestRefresh();
            queueBroadcast(Duration.ZERO);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (!closed && !instanceId.equals(new String(message.getBody(), StandardCharsets.UTF_8))) {
            reportScheduler.requestRefresh();
        }
    }

    @Override
    public void onChannelSubscribed(byte[] channel, long count) {
        if (!closed) {
            reportScheduler.requestRefresh();
        }
    }

    private void queueBroadcast(Duration delay) {
        if (closed || !broadcastQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            taskScheduler.schedule(this::broadcast, taskScheduler.getClock().instant().plus(delay));
        } catch (RuntimeException exception) {
            broadcastQueued.set(false);
            log.warn("日报预约变更广播未能提交。closed={}", closed, exception);
        }
    }

    private void broadcast() {
        broadcastQueued.set(false);
        if (closed) {
            return;
        }
        try {
            redisTemplate.convertAndSend(channel, instanceId);
        } catch (RuntimeException exception) {
            log.warn("日报预约变更广播失败，将重试；本实例已安排重载。", exception);
            queueBroadcast(BROADCAST_RETRY_DELAY);
        }
    }

    @PreDestroy
    public void close() {
        closed = true;
    }
}
