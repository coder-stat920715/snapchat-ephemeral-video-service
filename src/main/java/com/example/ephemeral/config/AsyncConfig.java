package com.example.ephemeral.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    public static final String STORAGE_CLEANUP_EXECUTOR = "storageCleanupExecutor";

    @Bean(name = STORAGE_CLEANUP_EXECUTOR)
    public ThreadPoolTaskExecutor storageCleanupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("storage-cleanup-");
        // Never run S3 work on the HTTP thread: if saturated, drop and let the scheduled sweeper catch up.
        executor.setRejectedExecutionHandler((runnable, pool) ->
                log.warn("Storage cleanup queue full; task dropped, sweeper will retry"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return storageCleanupExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("Uncaught async exception in {}", method.getName(), ex);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
