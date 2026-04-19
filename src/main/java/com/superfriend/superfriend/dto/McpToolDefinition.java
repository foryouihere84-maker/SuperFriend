package com.superfriend.superfriend.dto;

import lombok.Data;
import java.util.Map;

/**
 * MCP 工具定义
 */
@Data
public class McpToolDefinition {
    /**
     * 工具名称
     */
    private String name;
    
    /**
     * 服务器名称
     */
    private String serverName;
    
    /**
     * 工具描述
     */
    private String description;
    
    /**
     * 输入参数 JSON Schema
     */
    private JsonSchema inputSchema;
    
    /**
     * JSON Schema 定义
     */
    @Data
    public static class JsonSchema {
        /**
         * 参数类型（object, array 等）
         */
        private String type;
        
        /**
         * 参数属性定义
         */
        private Map<String, Object> properties;
        
        /**
         * 必需的参数列表
         */
        private String[] required;
    }
}
