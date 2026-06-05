package com.superfriend.superfriend.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 对话请求
 * 支持纯文本和多模态消息
 */
@Data
public class AIChatRequest {

    // ============ 文本消息（向后兼容） ============

    /**
     * 用户消息文本（简单场景）
     * 如果使用多模态，此字段作为文本部分
     */
    @Size(max = 32000, message = "消息内容过长，最大支持32000字符")
    private String message;

    // ============ 多模态支持 ============

    /**
     * 结构化消息内容（多模态场景）
     * 与 message 互斥，优先使用此字段
     */
    private List<ChatMessageContent> content;

    /**
     * 图片 URL 列表（便捷方式）
     * 与 message 配合使用，自动构建多模态消息
     * 支持：HTTP URL 或 data:image/xxx;base64,xxx 格式
     */
    private List<String> images;

    /**
     * 文档文件 URL 列表
     * 支持：PDF、DOCX、DOC、PPTX、PPT、XLSX、XLS
     * 可以是 HTTP URL 或 OSS objectKey
     */
    private List<String> documents;

    /**
     * 音频文件 URL 列表
     * 支持：MP3、WAV、M4A、FLAC 等
     */
    private List<String> audios;

    /**
     * 视频文件 URL 列表
     * 支持：MP4、AVI、MOV、MKV 等
     */
    private List<String> videos;

    // ============ 图片生成参数 ============

    /**
     * 图片生成尺寸
     * 支持格式: "1024x1024", "768x1344", "1344x768" 等
     * CogView-4 支持: 长宽 512-2048px，需被 16 整除
     */
    private String imageSize;

    // ============ 会话信息 ============

    /**
     * 会话 ID（必填）
     */
    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

    /**
     * 模型 ID（可选，使用默认模型）
     */
    private String model;

    /**
     * 处理类型
     */
    private String processType;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 关联笔记
     */
    private String relatedNotes;

    /**
     * 是否保存历史
     */
    private Boolean saveHistory;

    /**
     * 是否启用知识提取
     * 用户主动开启后，对话结束时会触发知识图谱节点提取
     * 默认为 false（不自动提取）
     */
    private Boolean enableKnowledgeExtraction;

    // ============ 历史消息 ============

    /**
     * 历史消息（旧格式，向后兼容）
     * Map 格式：{"role": "user/assistant", "content": "text"}
     */
    private List<Map<String, String>> history;

    /**
     * 历史消息（新格式，支持多模态）
     * 推荐使用此字段
     */
    private List<ChatMessage> historyMessages;

    // ============ 便捷方法 ============

    /**
     * 判断是否为多模态请求
     * 检查当前消息和历史消息中的多模态内容
     */
    public boolean isMultimodal() {
        // 1. 检查便捷方式的图片
        if (images != null && !images.isEmpty()) {
            return true;
        }

        // 2. 检查文档文件
        if (documents != null && !documents.isEmpty()) {
            return true;
        }

        // 3. 检查音频文件
        if (audios != null && !audios.isEmpty()) {
            return true;
        }

        // 4. 检查视频文件
        if (videos != null && !videos.isEmpty()) {
            return true;
        }

        // 5. 检查结构化内容中的非文本类型
        if (content != null && !content.isEmpty()) {
            for (ChatMessageContent c : content) {
                if (c.isMultimodal()) { // isImage() || isAudio() || isVideo()
                    return true;
                }
            }
        }

        // 6. 检查历史消息中的多模态内容
        if (historyMessages != null && !historyMessages.isEmpty()) {
            for (ChatMessage msg : historyMessages) {
                Object msgContent = msg.getContent();
                if (msgContent instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<ChatMessageContent> contents = (List<ChatMessageContent>) msgContent;
                    for (ChatMessageContent c : contents) {
                        if (c.isMultimodal()) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * 判断是否有文件需要解析
     * 包括图片、文档、音频、视频
     */
    public boolean hasFiles() {
        return (images != null && !images.isEmpty()) ||
               (documents != null && !documents.isEmpty()) ||
               (audios != null && !audios.isEmpty()) ||
               (videos != null && !videos.isEmpty()) ||
               (content != null && content.stream().anyMatch(ChatMessageContent::isMultimodal));
    }

    /**
     * 获取多模态类型列表
     * 返回请求中包含的所有模态类型
     */
    public List<String> getModalityTypes() {
        List<String> types = new ArrayList<>();
        types.add("text"); // 文本始终存在

        // 检查便捷方式的图片
        if (images != null && !images.isEmpty()) {
            types.add("image");
        }

        // 检查文档文件
        if (documents != null && !documents.isEmpty()) {
            types.add("document");
        }

        // 检查音频文件
        if (audios != null && !audios.isEmpty()) {
            types.add("audio");
        }

        // 检查视频文件
        if (videos != null && !videos.isEmpty()) {
            types.add("video");
        }

        // 检查结构化内容
        if (content != null) {
            for (ChatMessageContent c : content) {
                if (c.isImage() && !types.contains("image")) {
                    types.add("image");
                } else if (c.isAudio() && !types.contains("audio")) {
                    types.add("audio");
                } else if (c.isVideo() && !types.contains("video")) {
                    types.add("video");
                }
            }
        }

        return types;
    }

    /**
     * 获取有效的文本内容
     * 优先返回 message 字段
     */
    public String getEffectiveText() {
        return message;
    }
}
