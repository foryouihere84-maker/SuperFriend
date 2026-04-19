package com.superfriend.superfriend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.Map;

/**
 * MCP Server 配置类
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpServerConfig {
    /**
     * 启动命令（如：npx, node, python）
     */
    private String command;
    
    /**
     * 命令参数数组
     */
    private String[] args;
    
    /**
     * 环境变量
     */
    private Map<String, String> env;
    
    /**
     * 是否禁用
     */
    private boolean disabled;
    
    /**
     * 超时时间（毫秒）
     */
    private int timeout;
    
    /**
     * 描述信息
     */
    private String description;
}
