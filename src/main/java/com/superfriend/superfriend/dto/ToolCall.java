package com.superfriend.superfriend.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ToolCall {
    private String id;
    private String serverName;
    private String toolName;
    private Map<String, Object> arguments;
    
    public String getFullName() {
        if (serverName != null && !serverName.isEmpty() && toolName != null) {
            return serverName + "__" + toolName;
        }
        return toolName;
    }
}
