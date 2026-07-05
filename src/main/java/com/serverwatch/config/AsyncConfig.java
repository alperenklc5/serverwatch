package com.serverwatch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configures the dedicated thread pool used for asynchronous alert notifications.
 *
 * <p>{@code @EnableAsync} activates Spring's proxy-based async method execution.
 * The {@code alertNotificationExecutor} bean is referenced by name in
 * {@link com.serverwatch.alert.AlertEngine} so notifications are dispatched
 * on this pool rather than the shared scheduling pool.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "alertNotificationExecutor")
    public Executor alertNotificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("alert-notify-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
