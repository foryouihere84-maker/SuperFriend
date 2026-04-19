package com.superfriend.superfriend.strategy;

import com.superfriend.superfriend.constant.ChatMode;
import com.superfriend.superfriend.dto.AIChatRequest;
import com.superfriend.superfriend.dto.AIChatResponse;
import com.superfriend.superfriend.dto.UserIntent;

import java.util.function.Consumer;

/**
 * 聊天模式策略接口
 * 不同模式（Lite/Medium/Complex）的执行策略
 */
public interface ChatModeStrategy {

    /**
     * 执行普通对话
     * 用于 CHAT、GENERATE_DOCUMENT 等场景
     *
     * @param request  AI 请求
     * @param intent   用户意图
     * @param callback 响应回调
     */
    void executeChat(AIChatRequest request, UserIntent intent,
                     Consumer<AIChatResponse> callback);

    /**
     * 执行多模态对话
     * 用于 MULTIMODAL_CHAT 场景（图片/音频/视频输入）
     *
     * @param request  AI 请求
     * @param intent   用户意图
     * @param callback 响应回调
     */
    void executeMultimodalChat(AIChatRequest request, UserIntent intent,
                                Consumer<AIChatResponse> callback);

    /**
     * 执行文件解析后的增强对话
     * 用于 PARSE_FILE/PARSE_IMAGE/PARSE_AUDIO/PARSE_VIDEO 场景
     *
     * @param request        AI 请求
     * @param intent         用户意图
     * @param enhancedMessage 增强后的消息（包含解析的文件内容）
     * @param callback       响应回调
     */
    void executeFileParsedChat(AIChatRequest request, UserIntent intent,
                                String enhancedMessage,
                                Consumer<AIChatResponse> callback);

    /**
     * 获取对应的聊天模式
     */
    String getChatMode();
}
