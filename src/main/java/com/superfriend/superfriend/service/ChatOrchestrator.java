package com.superfriend.superfriend.service;

import com.superfriend.superfriend.constant.ChatMode;
import com.superfriend.superfriend.dto.*;
import com.superfriend.superfriend.strategy.ChatModeStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 聊天编排服务
 * 统一意图路由、文件解析、策略分发
 */
@Slf4j
@Service
public class ChatOrchestrator {

    private final Map<String, ChatModeStrategy> strategyMap;

    @Autowired
    @Lazy
    private UserIntentService userIntentService;

    @Autowired
    @Lazy
    private FileParseService fileParseService;

    @Autowired
    @Lazy
    private MultimodalProcessService multimodalProcessService;

    public ChatOrchestrator(List<ChatModeStrategy> strategies) {
        this.strategyMap = new HashMap<>();
        for (ChatModeStrategy strategy : strategies) {
            strategyMap.put(strategy.getChatMode(), strategy);
        }
        log.info("ChatOrchestrator 初始化，注册策略: {}", strategyMap.keySet());
    }

    /**
     * 核心调度方法 - 统一意图路由
     *
     * @param request  AI 请求
     * @param callback 响应回调
     * @param mode     聊天模式
     * @return 多模态上下文（包含生成结果），用于上层追加内容
     */
    public MultimodalContext dispatch(AIChatRequest request, Consumer<AIChatResponse> callback, String mode) {
        return dispatch(request, null, callback, mode);
    }

    /**
     * 核心调度方法 - 带预识别意图
     */
    public MultimodalContext dispatch(AIChatRequest request, UserIntent intent,
                         Consumer<AIChatResponse> callback, String mode) {
        ChatModeStrategy strategy = strategyMap.get(mode);
        if (strategy == null) {
            sendError(callback, request.getSessionId(), request.getModel(),
                    "未找到对应的聊天模式策略: " + mode);
            return null;
        }

        // 意图识别（如果未提供）
        if (intent == null) {
            intent = userIntentService.analyzeIntent(request);
        }
        log.info("意图识别结果: {} (mode={})", intent.getType(), mode);

        // 统一意图路由
        switch (intent.getType()) {
            case GENERATE_IMAGE:
            case GENERATE_AUDIO:
            case GENERATE_VIDEO:
                // 图片/音频/视频生成走多模态处理服务
                return multimodalProcessService.process(request, intent, callback);

            case GENERATE_DOCUMENT:
                // 文档生成走普通对话（由 Agent skill 处理）
                strategy.executeChat(request, intent, callback);
                return null;

            case PARSE_FILE:
            case PARSE_IMAGE:
            case PARSE_AUDIO:
            case PARSE_VIDEO:
                // 文件解析 -> 增强消息 -> 对话
                handleFileParseAndChat(request, intent, strategy, callback);
                return null;

            case MULTIMODAL_CHAT:
                // 多模态对话
                strategy.executeMultimodalChat(request, intent, callback);
                return null;

            default:
                // 普通对话
                strategy.executeChat(request, intent, callback);
                return null;
        }
    }

    /**
     * 处理文件解析后的对话
     * 统一的文件解析逻辑，消除 3 份重复代码
     */
    private void handleFileParseAndChat(AIChatRequest request, UserIntent intent,
                                         ChatModeStrategy strategy,
                                         Consumer<AIChatResponse> callback) {
        log.info("处理文件解析请求: {}", intent.getType());

        // Step 1: 获取文件 URL
        String fileUrl = intent.getFileUrl();
        String mimeType = intent.getMimeType();

        if (fileUrl == null || fileUrl.isEmpty()) {
            List<String[]> files = userIntentService.extractFiles(request);
            if (!files.isEmpty()) {
                fileUrl = files.get(0)[0];
                mimeType = files.get(0)[1];
            }
        }

        if (fileUrl == null) {
            sendError(callback, request.getSessionId(), request.getModel(), "未找到要解析的文件");
            return;
        }

        // Step 2: 发送解析状态
        AIChatResponse parsingStatus = new AIChatResponse();
        parsingStatus.setType("thinking");
        parsingStatus.setContent("正在解析文件...");
        parsingStatus.setSessionId(request.getSessionId());
        parsingStatus.setModel(request.getModel());
        callback.accept(parsingStatus);

        // Step 3: 解析文件
        ParseResult parseResult = fileParseService.parse(fileUrl, mimeType);

        if (!parseResult.isSuccess() || !parseResult.hasContent()) {
            sendError(callback, request.getSessionId(), request.getModel(),
                    "文件解析失败: " + parseResult.getErrorMessage());
            return;
        }

        log.info("文件解析成功，内容长度: {}", parseResult.getTextContent().length());

        // Step 4: 构建增强消息
        String userText = request.getEffectiveText();
        StringBuilder enhancedMessage = new StringBuilder();

        if (userText != null && !userText.isEmpty()) {
            enhancedMessage.append(userText);
        }
        enhancedMessage.append("\n\n--- 文件内容 ---\n");
        enhancedMessage.append(parseResult.getFormattedContentForLLM());

        // Step 5: 调用策略执行
        strategy.executeFileParsedChat(request, intent, enhancedMessage.toString(), callback);
    }

    private void sendError(Consumer<AIChatResponse> callback, String sessionId, String model, String message) {
        AIChatResponse errorResponse = new AIChatResponse();
        errorResponse.setType("error");
        errorResponse.setContent(message);
        errorResponse.setSessionId(sessionId);
        errorResponse.setModel(model);
        errorResponse.setDone(true);
        callback.accept(errorResponse);
    }
}
