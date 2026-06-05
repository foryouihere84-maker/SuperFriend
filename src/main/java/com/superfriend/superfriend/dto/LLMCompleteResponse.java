package com.superfriend.superfriend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMCompleteResponse {
    private String content;
    private String reasoningContent;
    private List<ToolCall> toolCalls;
    private Map<String, Object> usage;
    private String model;
    @Builder.Default
    private boolean success = true;
    private String error;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;
        private String name;
        private Map<String, Object> arguments;
        private String argumentsStr;
        
        public Map<String, Object> getArguments() {
            if (arguments != null && !arguments.isEmpty()) {
                return arguments;
            }
            if (argumentsStr != null && !argumentsStr.isEmpty()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    return mapper.readValue(argumentsStr, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
<<<<<<< HEAD
                    // 记录解析失败的原因，便于调试
                    System.err.println("[ToolCall] JSON 解析 argumentsStr 失败: " + e.getMessage());
                    System.err.println("[ToolCall] argumentsStr 长度: " + argumentsStr.length());
                    System.err.println("[ToolCall] argumentsStr 前200字符: " +
                        (argumentsStr.length() > 200 ? argumentsStr.substring(0, 200) : argumentsStr));
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                    return new java.util.HashMap<>();
                }
            }
            return new java.util.HashMap<>();
        }
    }
}
