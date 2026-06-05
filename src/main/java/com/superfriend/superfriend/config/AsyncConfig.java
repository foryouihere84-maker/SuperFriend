package com.superfriend.superfriend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${spring.task.execution.pool.core-size:4}")
    private int corePoolSize;

    @Value("${spring.task.execution.pool.max-size:8}")
    private int maxPoolSize;

    @Value("${spring.task.execution.pool.queue-capacity:20}")
    private int queueCapacity;

    @Value("${spring.task.execution.pool.keep-alive:60s}")
    private String keepAlive;

    @Value("${spring.task.execution.thread-name-prefix:sf-async-}")
    private String threadNamePrefix;

    @Value("${app.executor.parallel.core-size:6}")
    private int parallelCoreSize;

    @Value("${app.executor.script.core-size:4}")
    private int scriptCoreSize;

    @Value("${app.executor.script.max-size:8}")
    private int scriptMaxSize;

    @Value("${app.executor.script.queue-capacity:50}")
    private int scriptQueueCapacity;

    @Bean(name = "asyncTaskExecutor")
    public TaskExecutor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setKeepAliveSeconds(parseKeepAlive(keepAlive));
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "parallelToolExecutor")
    public ExecutorService parallelToolExecutor() {
        return new ThreadPoolExecutor(
            parallelCoreSize,
            parallelCoreSize,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r, "sf-parallel-tool-" + System.currentTimeMillis() % 10000);
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Bean(name = "scriptExecutorPool")
    public ExecutorService scriptExecutorPool() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            scriptCoreSize,
            scriptMaxSize,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(scriptQueueCapacity),
            r -> {
                Thread t = new Thread(r, "sf-script-" + System.currentTimeMillis() % 10000);
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private int parseKeepAlive(String keepAlive) {
        if (keepAlive.endsWith("s")) {
            return Integer.parseInt(keepAlive.substring(0, keepAlive.length() - 1));
        }
        return Integer.parseInt(keepAlive);
    }
}
