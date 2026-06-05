package com.superfriend.superfriend.service;

import com.superfriend.superfriend.dto.LLMChunk;
import com.superfriend.superfriend.dto.LLMCompleteResponse;
import com.superfriend.superfriend.dto.LLMRequest;
import com.superfriend.superfriend.dto.ToolDefinitionForLLM;

import java.util.List;
import java.util.function.Consumer;

public interface LLMClient {
    void streamChat(LLMRequest request, Consumer<LLMChunk> onChunk);

    void streamChatWithTools(LLMRequest request, List<ToolDefinitionForLLM> tools,
                              Consumer<LLMChunk> onChunk);

    LLMCompleteResponse chatComplete(LLMRequest request);

    LLMCompleteResponse chatCompleteWithTools(LLMRequest request, List<ToolDefinitionForLLM> tools);
}
