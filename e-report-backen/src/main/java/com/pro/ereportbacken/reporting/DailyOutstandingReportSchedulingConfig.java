package com.pro.ereportbacken.reporting;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.daily-outstanding-report.scheduler-enabled", havingValue = "true", matchIfMissing = true)
@Log4j2
public class DailyOutstandingReportSchedulingConfig {

    @Bean
    public Executor dailyOutstandingReportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("daily-report-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean
    public RedisMessageListenerContainer dailyOutstandingReportScheduleListener(
            RedisConnectionFactory connectionFactory, DailyOutstandingReportScheduleSignals signals,
            TaskScheduler taskScheduler) {
        RedisMessageListenerContainer container = new RecoveringScheduleListener(taskScheduler);
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(signals, new ChannelTopic(signals.channel()));
        return container;
    }

    private static final class RecoveringScheduleListener extends RedisMessageListenerContainer {

        private static final Duration RETRY_DELAY = Duration.ofSeconds(30);
        private final TaskScheduler taskScheduler;
        private ScheduledFuture<?> retry;
        private boolean destroyed;

        private RecoveringScheduleListener(TaskScheduler taskScheduler) {
            this.taskScheduler = taskScheduler;
        }

        @Override
        public synchronized void start() {
            if (destroyed) {
                return;
            }
            try {
                super.start();
            } catch (RuntimeException exception) {
                super.stop();
                log.warn("日报配置变更订阅暂不可用，将重试连接；本实例的定时预约继续工作。", exception);
                retry = taskScheduler.schedule(this::start, taskScheduler.getClock().instant().plus(RETRY_DELAY));
            }
        }

        @Override
        public synchronized void stop() {
            cancelRetry();
            super.stop();
        }

        @Override
        public synchronized void destroy() throws Exception {
            destroyed = true;
            cancelRetry();
            super.destroy();
        }

        private void cancelRetry() {
            if (retry != null) {
                retry.cancel(false);
                retry = null;
            }
        }
    }
}
