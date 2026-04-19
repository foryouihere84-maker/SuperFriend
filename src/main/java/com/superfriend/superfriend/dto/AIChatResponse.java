package com.superfriend.superfriend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AI 对话响应
 * 支持文本和多模态返回
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AIChatResponse {

    // ============ 文本内容（向后兼容） ============

    /**
     * 文本内容
     */
    private String content;

    /**
     * 推理/思考内容
     */
    private String reasoningContent;

    /**
     * 思考步骤
     */
    private String thought;

    // ============ 多模态内容 ============

    /**
     * 多模态内容列表
     * 当 AI 返回图片、音频等时使用
     */
    private List<ResponseContent> multimodalContent;

    // ============ 元数据 ============

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 使用的模型
     */
    private String model;

    /**
     * 实际使用的模型（可能与请求不同，自动切换时）
     */
    private String actualModel;

    /**
     * 是否完成
     */
    private boolean done;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 错误类型代码
     */
    private String errorType;

    /**
     * 错误标题
     */
    private String errorTitle;

    /**
     * 错误建议
     */
    private String errorSuggestion;

    /**
     * 是否可重试
     */
    private Boolean retryable;

    /**
     * 工具调用
     */
    @JsonProperty("tool_calls")
    private Object toolCalls;

    /**
     * 消息类型
     */
    private String type;

    /**
     * 资源 ID（用于图片、文件等，标识 IndexedDB 中的资源）
     */
    private String resourceId;

    /**
     * 文件名（文件类型消息时使用）
     */
    private String fileName;

    /**
     * 文件类型 (pdf, docx, xlsx, pptx)
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 模型切换信息
     */
    private ModelSwitchInfo modelSwitch;

    // ============ 成本追踪 ============

    /**
     * 本次调用成本（美元）
     */
    private Double cost;

    /**
     * 输入 Token 数量
     */
    private Long inputTokens;

    /**
     * 输出 Token 数量
     */
    private Long outputTokens;

    /**
     * 累计成本（当前会话）
     */
    private Double totalCost;

    /**
     * 累计 Token（当前会话）
     */
    private Long totalTokens;

    // ============ 生成元数据 ============

    /**
     * 生成耗时（毫秒）
     */
    private Long generationTimeMs;

    /**
     * 额外元数据
     */
    private Map<String, Object> metadata;

    // ============ 便捷方法 ============

    /**
     * 判断是否为多模态响应
     */
    public boolean isMultimodal() {
        return multimodalContent != null && !multimodalContent.isEmpty();
    }

    /**
     * 判断是否有错误
     */
    public boolean hasError() {
        return error != null && !error.isEmpty();
    }

    /**
     * 获取文本内容（兼容方法）
     */
    public String getTextContent() {
        if (content != null) {
            return content;
        }
        if (multimodalContent != null) {
            StringBuilder sb = new StringBuilder();
            for (ResponseContent rc : multimodalContent) {
                if ("text".equals(rc.getType()) && rc.getText() != null) {
                    sb.append(rc.getText());
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        }
        return null;
    }

    // ============ 静态工厂方法 ============

    /**
     * 创建文本响应
     */
    public static AIChatResponse text(String content, String sessionId, String model) {
        AIChatResponse response = new AIChatResponse();
        response.setContent(content);
        response.setSessionId(sessionId);
        response.setModel(model);
        response.setType("result");
        return response;
    }

    /**
     * 创建多模态响应
     */
    public static AIChatResponse multimodal(List<ResponseContent> contents, String sessionId, String model) {
        AIChatResponse response = new AIChatResponse();
        response.setMultimodalContent(contents);
        response.setSessionId(sessionId);
        response.setModel(model);
        response.setType("result");
        return response;
    }

    /**
     * 创建错误响应
     */
    public static AIChatResponse error(String error, String sessionId, String model) {
        AIChatResponse response = new AIChatResponse();
        response.setError(error);
        response.setSessionId(sessionId);
        response.setModel(model);
        response.setType("error");
        response.setDone(true);
        return response;
    }

    /**
     * 创建思考响应
     */
    public static AIChatResponse thinking(String thought, String sessionId, String model) {
        AIChatResponse response = new AIChatResponse();
        response.setThought(thought);
        response.setSessionId(sessionId);
        response.setModel(model);
        response.setType("thinking");
        return response;
    }

    /**
     * 响应内容（多模态）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseContent {

        /**
         * 内容类型：text, image, audio, video, file
         */
        private String type;

        /**
         * 文本内容
         */
        private String text;

        /**
         * 图片 URL
         */
        @JsonProperty("image_url")
        private MediaData imageUrl;

        /**
         * 音频 URL
         */
        @JsonProperty("audio_url")
        private MediaData audioUrl;

        /**
         * 视频 URL
         */
        @JsonProperty("video_url")
        private MediaData videoUrl;

        /**
         * 文件名
         */
        private String fileName;

        /**
         * 文件类型 (pdf, docx, xlsx, pptx)
         */
        private String fileType;

        /**
         * 文件大小
         */
        private Long fileSize;

        // ============ 静态工厂方法 ============

        public static ResponseContent text(String text) {
            ResponseContent content = new ResponseContent();
            content.setType("text");
            content.setText(text);
            return content;
        }

        public static ResponseContent image(String url) {
            ResponseContent content = new ResponseContent();
            content.setType("image");
            MediaData data = new MediaData();
            data.setUrl(url);
            content.setImageUrl(data);
            return content;
        }

        public static ResponseContent image(String url, String mimeType) {
            ResponseContent content = image(url);
            content.getImageUrl().setMimeType(mimeType);
            return content;
        }

        public static ResponseContent audio(String url) {
            ResponseContent content = new ResponseContent();
            content.setType("audio");
            MediaData data = new MediaData();
            data.setUrl(url);
            content.setAudioUrl(data);
            return content;
        }

        public static ResponseContent audio(String url, String mimeType) {
            ResponseContent content = audio(url);
            content.getAudioUrl().setMimeType(mimeType);
            return content;
        }

        public static ResponseContent video(String url) {
            ResponseContent content = new ResponseContent();
            content.setType("video");
            MediaData data = new MediaData();
            data.setUrl(url);
            content.setVideoUrl(data);
            return content;
        }

        public static ResponseContent video(String url, String mimeType) {
            ResponseContent content = video(url);
            content.getVideoUrl().setMimeType(mimeType);
            return content;
        }

        public static ResponseContent file(String url, String fileName, String fileType, Long fileSize) {
            ResponseContent content = new ResponseContent();
            content.setType("file");
            content.setText(url);
            content.setFileName(fileName);
            content.setFileType(fileType);
            content.setFileSize(fileSize);
            return content;
        }
    }

    /**
     * 媒体数据
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MediaData {

        /**
         * 媒体 URL
         */
        private String url;

        /**
         * MIME 类型
         */
        private String mimeType;

        /**
         * 描述
         */
        private String description;

        /**
         * 额外参数
         */
        private Map<String, Object> metadata;
    }

    /**
     * 模型切换信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ModelSwitchInfo {

        /**
         * 原始请求的模型
         */
        private String originalModel;

        /**
         * 实际使用的模型
         */
        private String actualModel;

        /**
         * 切换原因
         */
        private String reason;

        /**
         * 所需模态
         */
        private List<String> requiredModalities;
    }
}
