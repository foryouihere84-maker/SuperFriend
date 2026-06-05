package com.superfriend.superfriend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一消息结构
 * 支持 OpenAI API 标准格式，content 可以是 String 或 List<ChatMessageContent>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {

    /**
     * 角色：system | user | assistant
     */
    private String role;

    /**
     * 消息内容
     * - 纯文本消息：String
     * - 多模态消息：List<ChatMessageContent>
     */
    private Object content;

    /**
     * 思考内容（assistant 消息可选，用于 reasoning 模型）
     */
    private String reasoningContent;

    /**
     * 工具调用（assistant 消息可选）
     */
    private Object toolCalls;

    // ============ 静态工厂方法 ============

    /**
     * 创建系统消息
     */
    public static ChatMessage system(String content) {
        ChatMessage message = new ChatMessage();
        message.setRole("system");
        message.setContent(content);
        return message;
    }

    /**
     * 创建用户文本消息
     */
    public static ChatMessage userText(String text) {
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent(text);
        return message;
    }

    /**
     * 创建用户多模态消息（文本 + 图片）
     */
    public static ChatMessage userMultimodal(String text, List<String> imageUrls) {
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent(buildMultimodalContent(text, imageUrls));
        return message;
    }

    /**
     * 创建用户图片消息（仅图片）
     */
    public static ChatMessage userImages(List<String> imageUrls) {
        return userMultimodal(null, imageUrls);
    }

    /**
     * 创建用户消息（使用结构化内容）
     */
    public static ChatMessage userContent(List<ChatMessageContent> contents) {
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent(contents);
        return message;
    }

    /**
     * 创建助手消息
     */
    public static ChatMessage assistant(String content) {
        ChatMessage message = new ChatMessage();
        message.setRole("assistant");
        message.setContent(content);
        return message;
    }

    /**
     * 创建助手消息（带 reasoning）
     */
    public static ChatMessage assistantWithReasoning(String content, String reasoningContent) {
        ChatMessage message = new ChatMessage();
        message.setRole("assistant");
        message.setContent(content);
        message.setReasoningContent(reasoningContent);
        return message;
    }

    // ============ 辅助方法 ============

    /**
     * 构建多模态内容列表
     */
    private static List<ChatMessageContent> buildMultimodalContent(String text, List<String> imageUrls) {
        List<ChatMessageContent> contents = new ArrayList<>();

        // 添加文本
        if (text != null && !text.isEmpty()) {
            contents.add(ChatMessageContent.text(text));
        }

        // 添加图片
        if (imageUrls != null) {
            for (String imageUrl : imageUrls) {
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    contents.add(ChatMessageContent.imageUrl(imageUrl));
                }
            }
        }

        return contents;
    }

    /**
     * 判断内容是否为多模态
     */
    public boolean isMultimodal() {
        return content instanceof List;
    }

    /**
     * 获取文本内容（如果是纯文本消息）
     */
    public String getTextContent() {
        if (content instanceof String) {
            return (String) content;
        }
        return null;
    }

    /**
     * 获取多模态内容列表（如果是多模态消息）
     */
    @SuppressWarnings("unchecked")
    public List<ChatMessageContent> getMultimodalContent() {
        if (content instanceof List) {
            return (List<ChatMessageContent>) content;
        }
        return null;
    }
}
