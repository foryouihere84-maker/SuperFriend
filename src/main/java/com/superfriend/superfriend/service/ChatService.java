package com.superfriend.superfriend.service;

import com.superfriend.superfriend.constant.ChatMode;
import com.superfriend.superfriend.dto.AIChatRequest;
import com.superfriend.superfriend.dto.AIChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Slf4j
@Service
public class ChatService {

    @Autowired
    @Lazy
    private McpHostService mcpHostService;

    @Autowired
    @Lazy
    private ContextMangerService contextMangerService;

    public void chat(AIChatRequest request, Consumer<AIChatResponse> onResponse) {
        log.info("收到对话请求：sessionId={}, model={}, messageLength={}, 历史条数={}",
            request.getSessionId(),
            request.getModel(),
            request.getMessage() != null ? request.getMessage().length() : 0,
            request.getHistory() != null ? request.getHistory().size() : 0);

        StringBuilder assistantContent = new StringBuilder();

        Consumer<AIChatResponse> wrappedOnResponse = aiResponse -> {
            try {
                onResponse.accept(aiResponse);
                if (aiResponse.getContent() != null) {
                    assistantContent.append(aiResponse.getContent());
                }
            } catch (Exception e) {
                log.error("处理响应回调失败：{}", e.getMessage());
            }
        };

        mcpHostService.chatWithMCP(
            request.getMessage(),
            request.getSessionId(),
            request.getModel(),
            request.getHistory(),
            request.getUserId(),
            wrappedOnResponse
        );

        contextMangerService.saveContext(
            request.getSessionId(),
            request.getUserId(),
            request.getModel(),
            ChatMode.MCP,
            request.getMessage(),
            request.getHistory(),
            assistantContent.toString()
        );

        log.info("对话完成，sessionId={}", request.getSessionId());
    }
}
