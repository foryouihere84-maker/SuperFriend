package com.superfriend.superfriend.dto;

import com.fasterxml.jackson.databind.JsonNode;
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
public class LLMChunk {
    private String content;
    private String reasoningContent;
    private JsonNode toolCalls;
    private String finishReason;
    @Builder.Default
    private boolean done = false;
    private JsonNode usage;
    private String error;
    private List<LLMCompleteResponse.ToolCall> accumulatedToolCalls;

    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }

    public boolean hasReasoningContent() {
        return reasoningContent != null && !reasoningContent.isEmpty();
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isMissingNode() && toolCalls.isArray() && toolCalls.size() > 0;
    }
    
    public boolean hasAccumulatedToolCalls() {
        return accumulatedToolCalls != null && !accumulatedToolCalls.isEmpty();
    }

    public boolean isError() {
        return error != null && !error.isEmpty();
    }
}
