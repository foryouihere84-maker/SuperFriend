package com.superfriend.superfriend.dto;

import lombok.Data;
import java.util.List;

/**
 * MCP 工具调用响应
 */
@Data
public class McpToolCallResponse {
    /**
     * MCP Server 名称
     */
    private String serverName;
    
    /**
     * 工具名称
     */
    private String toolName;
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 返回内容列表
     */
    private List<ContentItem> content;
    
    /**
     * 错误信息（失败时）
     */
    private String error;
    
    /**
     * 内容项
     */
    @Data
    public static class ContentItem {
        /**
         * 内容类型（text, image, resource）
         */
        private String type;
        
        /**
         * 文本内容
         */
        private String text;
        
        /**
         * 其他数据（用于非文本类型）
         */
        private Object data;
    }
}
