package com.superfriend.superfriend.strategy;

import com.superfriend.superfriend.constant.ChatMode;
import com.superfriend.superfriend.dto.AIChatRequest;
import com.superfriend.superfriend.dto.AIChatResponse;
import com.superfriend.superfriend.dto.UserIntent;
import com.superfriend.superfriend.service.McpHostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Lite 模式聊天策略
 * 简单对话模式，无 Agent 工具调用
 */
@Slf4j
@Component
public class LiteChatStrategy implements ChatModeStrategy {

    @Autowired
    @Lazy
    private McpHostService mcpHostService;

    @Override
    public void executeChat(AIChatRequest request, UserIntent intent,
                            Consumer<AIChatResponse> callback) {
        log.debug("Lite 模式执行普通对话: sessionId={}", request.getSessionId());
        mcpHostService.chatLite(request, intent, callback);
    }

    @Override
    public void executeMultimodalChat(AIChatRequest request, UserIntent intent,
                                       Consumer<AIChatResponse> callback) {
        log.debug("Lite 模式执行多模态对话: sessionId={}", request.getSessionId());
        mcpHostService.chatLiteMultimodal(request, intent, callback);
    }

    @Override
    public void executeFileParsedChat(AIChatRequest request, UserIntent intent,
                                       String enhancedMessage,
                                       Consumer<AIChatResponse> callback) {
        log.debug("Lite 模式执行文件解析后对话: sessionId={}", request.getSessionId());
        // Lite 模式需要构建新的请求对象，替换消息内容
        AIChatRequest enhancedRequest = new AIChatRequest();
        enhancedRequest.setSessionId(request.getSessionId());
        enhancedRequest.setModel(request.getModel());
        enhancedRequest.setUserId(request.getUserId());
        enhancedRequest.setMessage(enhancedMessage);
        enhancedRequest.setHistory(request.getHistory());
        enhancedRequest.setEnableKnowledgeExtraction(request.getEnableKnowledgeExtraction());
        mcpHostService.chatLite(enhancedRequest, intent, callback);
    }

    @Override
    public String getChatMode() {
        return ChatMode.LITE_TASK;
    }
}
