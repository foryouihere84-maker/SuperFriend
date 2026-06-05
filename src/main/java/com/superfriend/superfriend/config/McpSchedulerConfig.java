package com.superfriend.superfriend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import com.superfriend.superfriend.service.McpHostService;

/**
 * MCP Host 定时任务配置
 */
@Slf4j
@Configuration
@EnableScheduling
public class McpSchedulerConfig {
    
    @Autowired
    private McpHostService mcpHostService;
    
    /**
     * 每 1 分钟检查并关闭空闲的 MCP Servers
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupIdleServers() {
        log.debug("执行空闲服务器检查...");
        mcpHostService.checkAndCloseIdleServers();
    }
}
