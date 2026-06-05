package com.superfriend.superfriend.config;

import com.superfriend.superfriend.service.TencentNewsCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
public class TencentNewsSchedulerConfig {

    @Autowired
    private TencentNewsCacheService tencentNewsCacheService;

    @Scheduled(fixedRate = 43200000, initialDelay = 60000)
    public void fetchNewsPeriodically() {
        tencentNewsCacheService.fetchAndCacheNews();
    }
}
