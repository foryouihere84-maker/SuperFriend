package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.superfriend.superfriend.dto.AIChatResponse;
import com.superfriend.superfriend.dto.LLMChunk;
import com.superfriend.superfriend.dto.LLMRequest;
import com.superfriend.superfriend.entity.AIModelConfig;
import com.superfriend.superfriend.entity.Prompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
public class AIService {

    @Autowired
    @Lazy
    private AIModelConfigService modelConfigService;

    @Autowired
    @Lazy
    private LLMClient llmClient;

    @Autowired
    @Lazy
    private SystemContextBuilder systemContextBuilder;
    
    public void chatStream(String message, String sessionId, String model, Consumer<AIChatResponse> onResponse) {
        chatStream(message, sessionId, model, null, null, onResponse);
    }

    public void chatStream(String message, String sessionId, String model, List<Map<String, String>> history, Consumer<AIChatResponse> onResponse) {
        chatStream(message, sessionId, model, history, null, onResponse);
    }

    public void chatStream(String message, String sessionId, String model, List<Map<String, String>> history, Long userId, Consumer<AIChatResponse> onResponse) {
        try {
            AIModelConfig resolvedConfig = modelConfigService.resolveModelConfig(model, userId);
            if (resolvedConfig == null) {
                AIChatResponse errResp = new AIChatResponse();
                errResp.setType("error");
                errResp.setContent("未找到可用的模型配置，请先在模型配置页面添加模型");
                onResponse.accept(errResp);
                return;
            }
            String effectiveModel = resolvedConfig.getModelId();

            List<Map<String, Object>> messages = buildChatMessages(message, sessionId, history, userId);

            LLMRequest request = LLMRequest.fromConfig(effectiveModel, resolvedConfig.getApiUrl(), resolvedConfig.getApiKey())
                .toBuilder()
                .messages(messages)
                .sessionId(sessionId)
                .userId(userId)
                .build();

            log.info("[AIService.chatStream] 调用 LLM: model={}, 消息数={}", effectiveModel, messages.size());

            llmClient.streamChat(request, chunk -> {
                if (chunk.isDone()) {
                    if (chunk.isError()) {
                        AIChatResponse errorResponse = new AIChatResponse();
                        errorResponse.setContent(null);
                        errorResponse.setSessionId(sessionId);
                        errorResponse.setModel(effectiveModel);
                        errorResponse.setError(chunk.getError());
                        errorResponse.setDone(true);
                        onResponse.accept(errorResponse);
                    } else {
                        AIChatResponse doneResponse = new AIChatResponse();
                        doneResponse.setContent(null);
                        doneResponse.setSessionId(sessionId);
                        doneResponse.setModel(effectiveModel);
                        doneResponse.setDone(true);
                        onResponse.accept(doneResponse);
                    }
                } else if (chunk.hasReasoningContent()) {
                    AIChatResponse reasoningResponse = new AIChatResponse();
                    reasoningResponse.setSessionId(sessionId);
                    reasoningResponse.setModel(effectiveModel);
                    reasoningResponse.setDone(false);
                    reasoningResponse.setReasoningContent(chunk.getReasoningContent());
                    reasoningResponse.setType("thinking");
                    onResponse.accept(reasoningResponse);
                } else if (chunk.hasContent() || chunk.hasToolCalls()) {
                    AIChatResponse chatResponse = new AIChatResponse();
                    chatResponse.setSessionId(sessionId);
                    chatResponse.setModel(effectiveModel);
                    chatResponse.setDone(false);
                    chatResponse.setType("result");
                    if (chunk.hasContent()) {
                        chatResponse.setContent(chunk.getContent());
                    }
                    if (chunk.hasToolCalls()) {
                        chatResponse.setToolCalls(chunk.getToolCalls());
                        log.debug("检测到 tool_calls: {}", chunk.getToolCalls().toString());
                    }
                    onResponse.accept(chatResponse);
                }
            });

        } catch (Exception e) {
            log.error("AI 服务异常：{}", e.getMessage(), e);
            AIChatResponse errorResponse = new AIChatResponse();
            errorResponse.setContent(null);
            errorResponse.setSessionId(sessionId);
            errorResponse.setError("AI 服务异常：" + e.getMessage());
            errorResponse.setDone(true);
            onResponse.accept(errorResponse);
        }
    }

    public void funcStream(String message, String sessionId, String model, Long userId, Consumer<AIChatResponse> onResponse) {
        try {
            AIModelConfig resolvedConfig = modelConfigService.resolveModelConfig(model, userId);
            if (resolvedConfig == null) {
                AIChatResponse errResp = new AIChatResponse();
                errResp.setType("error");
                errResp.setContent("未找到可用的模型配置，请先在模型配置页面添加模型");
                onResponse.accept(errResp);
                return;
            }
            String effectiveModel = resolvedConfig.getModelId();

            List<Map<String, Object>> messages = new ArrayList<>();

            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", new Prompt().getSystemPrompt());
            messages.add(systemMessage);

            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", message);
            messages.add(userMessage);

            LLMRequest request = LLMRequest.fromConfig(effectiveModel, resolvedConfig.getApiUrl(), resolvedConfig.getApiKey())
                .toBuilder()
                .messages(messages)
                .temperature(0.7)
                .sessionId(sessionId)
                .userId(userId)
                .build();

            log.info("[AIService.funcStream] 调用 LLM: model={}, 消息长度={}", effectiveModel,
                message != null ? message.length() : 0);

            llmClient.streamChat(request, chunk -> {
                if (chunk.isDone()) {
                    if (chunk.isError()) {
                        AIChatResponse errorResponse = new AIChatResponse();
                        errorResponse.setContent(null);
                        errorResponse.setSessionId(sessionId);
                        errorResponse.setModel(effectiveModel);
                        errorResponse.setError(chunk.getError());
                        errorResponse.setDone(true);
                        onResponse.accept(errorResponse);
                    } else {
                        AIChatResponse doneResponse = new AIChatResponse();
                        doneResponse.setContent(null);
                        doneResponse.setSessionId(sessionId);
                        doneResponse.setModel(effectiveModel);
                        doneResponse.setDone(true);
                        onResponse.accept(doneResponse);
                    }
                } else if (chunk.hasReasoningContent()) {
                    AIChatResponse reasoningResponse = new AIChatResponse();
                    reasoningResponse.setSessionId(sessionId);
                    reasoningResponse.setModel(effectiveModel);
                    reasoningResponse.setDone(false);
                    reasoningResponse.setReasoningContent(chunk.getReasoningContent());
                    reasoningResponse.setType("thinking");
                    onResponse.accept(reasoningResponse);
                } else if (chunk.hasContent() || chunk.hasToolCalls()) {
                    AIChatResponse chatResponse = new AIChatResponse();
                    chatResponse.setSessionId(sessionId);
                    chatResponse.setModel(effectiveModel);
                    chatResponse.setDone(false);
                    chatResponse.setType("result");
                    if (chunk.hasContent()) {
                        chatResponse.setContent(chunk.getContent());
                    }
                    if (chunk.hasToolCalls()) {
                        chatResponse.setToolCalls(chunk.getToolCalls());
                        log.debug("func 检测到 tool_calls: {}", chunk.getToolCalls().toString());
                    }
                    onResponse.accept(chatResponse);
                }
            });

        } catch (Exception e) {
            log.error("AI 服务 (func) 异常：{}", e.getMessage(), e);
            AIChatResponse errorResponse = new AIChatResponse();
            errorResponse.setContent(null);
            errorResponse.setSessionId(sessionId);
            errorResponse.setError("AI 服务异常：" + e.getMessage());
            errorResponse.setDone(true);
            onResponse.accept(errorResponse);
        }
    }

    private List<Map<String, Object>> buildChatMessages(String message, String sessionId, List<Map<String, String>> history, Long userId) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // 使用 SystemContextBuilder 统一构建系统提示词
        String systemPrompt = systemContextBuilder.buildChatPrompt(userId, sessionId, message);

        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        messages.add(systemMessage);

        if (history != null && !history.isEmpty()) {
            for (Map<String, String> msg : history) {
                if (msg.containsKey("content") && msg.get("content") != null && !msg.get("content").isEmpty()) {
                    Map<String, Object> msgCopy = new HashMap<>();
                    msgCopy.putAll(msg);
                    messages.add(msgCopy);
                }
            }
            log.info("已添加 {} 条对话历史", messages.size() - 1);
        }

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", message);
        messages.add(userMessage);

        return messages;
    }
}
