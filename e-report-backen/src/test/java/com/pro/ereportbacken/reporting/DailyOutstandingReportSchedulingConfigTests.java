package com.pro.ereportbacken.reporting;

import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.security.ConfigurationScopeContext;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.service.OrganizationHierarchyService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyOutstandingReportSchedulingConfigTests {

    private final RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
            .withUserConfiguration(SchedulingContext.class)
            .withBean(TenantRepository.class, () -> mock(TenantRepository.class))
            .withBean(OrganizationHierarchyService.class, () -> mock(OrganizationHierarchyService.class))
            .withBean(TenantContext.class, () -> mock(TenantContext.class))
            .withBean(ConfigurationScopeContext.class, ConfigurationScopeContext::new)
            .withBean(DailyOutstandingWorkOrderReportService.class, () -> mock(DailyOutstandingWorkOrderReportService.class))
            .withBean(RedisConnectionFactory.class, () -> connectionFactory)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));

    @Test
    void springWiresTheTimerAndDedicatedWorkerEvenWhenRedisIsUnavailable() {
        when(connectionFactory.getConnection()).thenThrow(new RedisConnectionFailureException("redis unavailable"));

        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(TaskScheduler.class);
            assertThat(context).hasSingleBean(DailyOutstandingWorkOrderReportScheduler.class);
            assertThat(context.getBean("dailyOutstandingReportScheduleListener", RedisMessageListenerContainer.class).isRunning())
                    .isFalse();
            CountDownLatch completed = new CountDownLatch(1);
            context.getBean("dailyOutstandingReportTaskExecutor", Executor.class).execute(completed::countDown);
            assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
        });
    }

    @Test
    void disablingSchedulingDoesNotCreateListenersOrWorkers() {
        runner.withPropertyValues("app.daily-outstanding-report.scheduler-enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DailyOutstandingWorkOrderReportScheduler.class);
            assertThat(context).doesNotHaveBean(DailyOutstandingReportScheduleSignals.class);
            assertThat(context).doesNotHaveBean("dailyOutstandingReportTaskExecutor");
            assertThat(context).doesNotHaveBean("dailyOutstandingReportScheduleListener");
        });
    }

    @Test
    void failedSubscriptionRetriesAndShutdownCancelsTheRetry() throws Exception {
        Instant now = Instant.parse("2026-10-20T01:00:00Z");
        ReportSchedulingTestTimer timer = new ReportSchedulingTestTimer(now);
        DailyOutstandingReportScheduleSignals signals = mock(DailyOutstandingReportScheduleSignals.class);
        when(signals.channel()).thenReturn("test-daily-report");
        when(connectionFactory.getConnection()).thenThrow(new RedisConnectionFailureException("redis unavailable"));
        RedisMessageListenerContainer listener = new DailyOutstandingReportSchedulingConfig()
                .dailyOutstandingReportScheduleListener(connectionFactory, signals, timer.scheduler);
        listener.afterPropertiesSet();
        try {
            listener.start();

            assertThat(timer.pendingTimes()).containsExactly(now.plusSeconds(30));
            timer.runDueAt(now.plusSeconds(30));
            verify(connectionFactory, times(2)).getConnection();
        } finally {
            listener.destroy();
        }
        assertThat(timer.pendingTimes()).isEmpty();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @Import({DailyOutstandingReportSchedulingConfig.class, DailyOutstandingWorkOrderReportScheduler.class,
            DailyOutstandingReportScheduleSignals.class})
    static class SchedulingContext {
    }
}
