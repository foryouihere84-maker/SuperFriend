package com.superfriend.superfriend.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ModelResponse {
    private String content;
    private String reasoningContent;
    private List<ToolCall> toolCalls;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private Double cost;
    private Long latencyMs;
    
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
    
    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }
    
    public boolean hasReasoningContent() {
        return reasoningContent != null && !reasoningContent.isEmpty();
    }
    
    public boolean hasUsage() {
        return inputTokens != null || outputTokens != null;
    }
}
