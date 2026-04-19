package com.superfriend.superfriend;

import com.superfriend.superfriend.service.McpHostService;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;

/**
 * HarmonyNotes 应用启动类
 */
@Slf4j
@SpringBootApplication
@MapperScan("com.superfriend.superfriend.mapper")
public class SuperFriendApplication {

    @Autowired
    @Lazy
    private McpHostService mcpHostService;

    public static void main(String[] args) {
        SpringApplication.run(SuperFriendApplication.class, args);
    }

    /**
     * 应用启动完成后初始化 MCP Host 并启动选中的服务器
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initMcpHost() {
        log.info("正在初始化 MCP Host...");
        try {
            mcpHostService.initializeServers();
            log.info("MCP Host 初始化完成");
            
            log.info("正在启动 mcp-servers-small-config 中选中的服务器...");
            String result = mcpHostService.startSelectedServers();
            log.info("MCP 服务器启动结果：{}", result);
        } catch (Exception e) {
            log.error("MCP Host 初始化失败：{}", e.getMessage(), e);
        }
    }

}
