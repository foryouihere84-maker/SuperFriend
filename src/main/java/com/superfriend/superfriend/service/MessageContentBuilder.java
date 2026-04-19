package com.superfriend.superfriend.service;

import com.superfriend.superfriend.dto.AIChatRequest;
import com.superfriend.superfriend.dto.ChatMessage;
import com.superfriend.superfriend.dto.ChatMessageContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息内容构建服务
 * 负责将请求转换为 LLM API 兼容的消息格式
 * 支持文本、图片、音频、视频等多模态
 */
@Slf4j
@Service
public class MessageContentBuilder {

    /**
     * 从请求构建用户消息内容
     * 优先级：content > (message + images) > message
     *
     * @param request AI 对话请求
     * @return 消息内容，可能是 String 或 List<Map<String, Object>>
     */
    public Object buildUserMessageContent(AIChatRequest request) {
        // 1. 显式结构化内容优先
        if (request.getContent() != null && !request.getContent().isEmpty()) {
            log.debug("使用结构化 content 字段，共 {} 个部分", request.getContent().size());
            return convertContentToMaps(request.getContent());
        }

        // 2. 文本 + 图片便捷方式
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            log.debug("构建多模态消息：文本 + {} 张图片", request.getImages().size());
            return buildMultimodalContent(request.getMessage(), request.getImages());
        }

        // 3. 纯文本（向后兼容）
        return request.getMessage();
    }

    /**
     * 从 ChatMessage 构建消息内容
     *
     * @param message 聊天消息
     * @return 消息内容
     */
    public Object buildMessageContent(ChatMessage message) {
        if (message == null) {
            return null;
        }

        Object content = message.getContent();
        if (content instanceof String) {
            return content;
        } else if (content instanceof List) {
            @SuppressWarnings("unchecked")
            List<ChatMessageContent> contents = (List<ChatMessageContent>) content;
            return convertContentToMaps(contents);
        }

        return content;
    }

    /**
     * 构建历史消息列表
     * 优先使用新的 historyMessages 字段，兼容旧的 history 字段
     *
     * @param request AI 对话请求
     * @return LLM API 格式的消息列表
     */
    public List<Map<String, Object>> buildHistoryMessages(AIChatRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // 优先使用新格式
        if (request.getHistoryMessages() != null && !request.getHistoryMessages().isEmpty()) {
            for (ChatMessage msg : request.getHistoryMessages()) {
                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("role", msg.getRole());
                messageMap.put("content", buildMessageContent(msg));
                messages.add(messageMap);
            }
            log.debug("使用 historyMessages 字段，共 {} 条历史消息", messages.size());
            return messages;
        }

        // 兼容旧格式
        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            for (Map<String, String> histMsg : request.getHistory()) {
                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("role", histMsg.get("role"));
                messageMap.put("content", histMsg.get("content"));
                messages.add(messageMap);
            }
            log.debug("使用 history 字段，共 {} 条历史消息", messages.size());
        }

        return messages;
    }

    /**
     * 构建完整的消息列表（包含系统提示词、历史、当前用户消息）
     *
     * @param systemPrompt 系统提示词
     * @param request      AI 对话请求
     * @return 完整的消息列表
     */
    public List<Map<String, Object>> buildCompleteMessages(String systemPrompt, AIChatRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // 1. 系统消息
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.add(systemMessage);
        }

        // 2. 历史消息
        messages.addAll(buildHistoryMessages(request));

        // 3. 用户消息
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", buildUserMessageContent(request));
        messages.add(userMessage);

        return messages;
    }

    /**
     * 构建多模态内容（文本 + 图片）
     *
     * @param text      文本内容（可为 null）
     * @param imageUrls 图片 URL 列表
     * @return 多模态内容列表
     */
    private List<Map<String, Object>> buildMultimodalContent(String text, List<String> imageUrls) {
        List<Map<String, Object>> contents = new ArrayList<>();

        // 添加文本
        if (text != null && !text.isEmpty()) {
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", text);
            contents.add(textPart);
        }

        // 添加图片
        if (imageUrls != null) {
            for (String imageUrl : imageUrls) {
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    Map<String, Object> imagePart = new HashMap<>();
                    imagePart.put("type", "image_url");

                    Map<String, Object> urlWrapper = new HashMap<>();
                    urlWrapper.put("url", imageUrl);
                    imagePart.put("image_url", urlWrapper);
                    contents.add(imagePart);
                }
            }
        }

        return contents;
    }

    /**
     * 将 ChatMessageContent 列表转换为 Map 列表
     * 支持文本、图片、音频、视频
     *
     * @param contents ChatMessageContent 列表
     * @return Map 列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertContentToMaps(List<ChatMessageContent> contents) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (ChatMessageContent content : contents) {
            Map<String, Object> contentMap = new HashMap<>();
            String type = content.getType();
            contentMap.put("type", type);

            switch (type) {
                case "text":
                    contentMap.put("text", content.getText());
                    break;

                case "image_url":
                    if (content.getImageUrl() != null) {
                        Map<String, Object> imageUrlMap = new HashMap<>();
                        imageUrlMap.put("url", content.getImageUrl().getUrl());
                        if (content.getImageUrl().getDetail() != null) {
                            imageUrlMap.put("detail", content.getImageUrl().getDetail());
                        }
                        contentMap.put("image_url", imageUrlMap);
                    }
                    break;

                case "input_audio":
                    // OpenAI Realtime API 格式
                    if (content.getInputAudio() != null) {
                        Map<String, Object> audioMap = new HashMap<>();
                        audioMap.put("data", content.getInputAudio().getData());
                        audioMap.put("format", content.getInputAudio().getFormat());
                        contentMap.put("input_audio", audioMap);
                    }
                    break;

                case "audio_url":
                    // 通用音频 URL 格式
                    if (content.getAudioUrl() != null) {
                        Map<String, Object> audioUrlMap = new HashMap<>();
                        audioUrlMap.put("url", content.getAudioUrl().getUrl());
                        if (content.getAudioUrl().getFormat() != null) {
                            audioUrlMap.put("format", content.getAudioUrl().getFormat());
                        }
                        if (content.getAudioUrl().getMimeType() != null) {
                            audioUrlMap.put("mime_type", content.getAudioUrl().getMimeType());
                        }
                        contentMap.put("audio_url", audioUrlMap);
                    }
                    break;

                case "video_url":
                    // 视频 URL 格式
                    if (content.getVideoUrl() != null) {
                        Map<String, Object> videoUrlMap = new HashMap<>();
                        videoUrlMap.put("url", content.getVideoUrl().getUrl());
                        if (content.getVideoUrl().getMimeType() != null) {
                            videoUrlMap.put("mime_type", content.getVideoUrl().getMimeType());
                        }
                        contentMap.put("video_url", videoUrlMap);
                    }
                    break;

                default:
                    log.warn("未知的内容类型: {}", type);
            }

            result.add(contentMap);
        }

        return result;
    }

    /**
     * 验证图片 URL 格式
     *
     * @param url 图片 URL
     * @return 是否有效
     */
    public boolean isValidImageUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        // 支持 HTTP/HTTPS URL
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return true;
        }

        // 支持 Data URL (base64)
        if (url.startsWith("data:image/")) {
            return url.contains(";base64,");
        }

        return false;
    }

    /**
     * 验证音频 URL 格式
     *
     * @param url 音频 URL
     * @return 是否有效
     */
    public boolean isValidAudioUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        // 支持 HTTP/HTTPS URL
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return true;
        }

        // 支持 Data URL (base64)
        if (url.startsWith("data:audio/")) {
            return url.contains(";base64,");
        }

        return false;
    }

    /**
     * 验证视频 URL 格式
     *
     * @param url 视频 URL
     * @return 是否有效
     */
    public boolean isValidVideoUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        // 支持 HTTP/HTTPS URL
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return true;
        }

        // 支持 Data URL (base64)
        if (url.startsWith("data:video/")) {
            return url.contains(";base64,");
        }

        return false;
    }

    /**
     * 从 base64 数据构建 Data URL
     *
     * @param mediaType 媒体类型 (image/jpeg, audio/mp3, video/mp4, etc.)
     * @param base64Data base64 编码数据
     * @return Data URL
     */
    public String buildDataUrl(String mediaType, String base64Data) {
        return "data:" + mediaType + ";base64," + base64Data;
    }

    /**
     * 检测消息内容中的模态类型
     *
     * @param request AI 对话请求
     * @return 模态类型列表
     */
    public List<String> detectModalities(AIChatRequest request) {
        List<String> modalities = new ArrayList<>();
        modalities.add("text"); // 文本始终存在

        // 检查便捷方式的图片
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            modalities.add("image");
        }

        // 检查结构化内容
        if (request.getContent() != null) {
            for (ChatMessageContent content : request.getContent()) {
                if (content.isImage() && !modalities.contains("image")) {
                    modalities.add("image");
                } else if (content.isAudio() && !modalities.contains("audio")) {
                    modalities.add("audio");
                } else if (content.isVideo() && !modalities.contains("video")) {
                    modalities.add("video");
                }
            }
        }

        return modalities;
    }
}
