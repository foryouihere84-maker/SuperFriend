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
 * Medium 模式聊天策略
 * 带技能注入的中等任务对话
 * 与 Lite 模式接口风格一致，唯一区别是 ReAct/Skills/MCP
 */
@Slf4j
@Component
public class MediumChatStrategy implements ChatModeStrategy {

    @Autowired
    @Lazy
    private McpHostService mcpHostService;

    @Override
    public void executeChat(AIChatRequest request, UserIntent intent,
                            Consumer<AIChatResponse> callback) {
        log.debug("Medium 模式执行普通对话: sessionId={}", request.getSessionId());
        // 使用统一接口，与 Lite 模式风格一致
        mcpHostService.chatWithMCPWithSkills(request, intent, callback);
    }

    @Override
    public void executeMultimodalChat(AIChatRequest request, UserIntent intent,
                                       Consumer<AIChatResponse> callback) {
        log.debug("Medium 模式执行多模态对话: sessionId={}", request.getSessionId());
        // 使用统一接口，与 Lite 模式风格一致
        mcpHostService.chatWithMCPWithSkillsMultimodal(request, intent, callback);
    }

    @Override
    public void executeFileParsedChat(AIChatRequest request, UserIntent intent,
                                       String enhancedMessage,
                                       Consumer<AIChatResponse> callback) {
        log.debug("Medium 模式执行文件解析后对话: sessionId={}", request.getSessionId());
        // 构建增强请求，与 Lite 模式风格一致
        AIChatRequest enhancedRequest = new AIChatRequest();
        enhancedRequest.setSessionId(request.getSessionId());
        enhancedRequest.setModel(request.getModel());
        enhancedRequest.setUserId(request.getUserId());
        enhancedRequest.setMessage(enhancedMessage);
        enhancedRequest.setHistory(request.getHistory());
        enhancedRequest.setEnableKnowledgeExtraction(request.getEnableKnowledgeExtraction());
        // 使用统一接口
        mcpHostService.chatWithMCPWithSkills(enhancedRequest, intent, callback);
    }

    @Override
    public String getChatMode() {
        return ChatMode.MEDIUM_TASK;
    }
}
