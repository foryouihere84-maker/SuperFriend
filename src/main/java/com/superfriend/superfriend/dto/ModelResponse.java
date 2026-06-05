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
    private String finishReason;  // 完成原因: "stop", "length", "tool_calls", "error" 等

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

    /**
     * 检查输出是否被截断（finish_reason = "length"）
     */
    public boolean isTruncated() {
        return "length".equals(finishReason);
    }
}
