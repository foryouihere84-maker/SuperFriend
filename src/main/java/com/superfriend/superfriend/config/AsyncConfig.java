package com.superfriend.superfriend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

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

    private int parseKeepAlive(String keepAlive) {
        if (keepAlive.endsWith("s")) {
            return Integer.parseInt(keepAlive.substring(0, keepAlive.length() - 1));
        }
        return Integer.parseInt(keepAlive);
    }
}
