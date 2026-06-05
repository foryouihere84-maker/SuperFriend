package com.superfriend.superfriend.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具调用请求
 */
@Data
public class McpToolCallRequest {
    /**
     * MCP Server 名称
     */
    private String serverName;
    
    /**
     * 工具名称
     */
    private String toolName;
    
    /**
     * 调用参数
     */
    private Map<String, Object> arguments;
}
