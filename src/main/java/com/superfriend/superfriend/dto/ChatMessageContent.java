package com.superfriend.superfriend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多模态消息内容
 * 支持 OpenAI API 标准格式，兼容文本、图片、音频、视频
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessageContent {

    /**
     * 内容类型：
     * - text: 文本
     * - image_url: 图片 URL
     * - input_audio: 音频输入（OpenAI Realtime API 格式）
     * - audio_url: 音频 URL（通用格式）
     * - video_url: 视频 URL
     */
    private String type;

    /**
     * 文本内容（type=text 时使用）
     */
    private String text;

    /**
     * 图片 URL（type=image_url 时使用）
     */
    @JsonProperty("image_url")
    private MediaUrl imageUrl;

    /**
     * 音频输入（type=input_audio 时使用，OpenAI 格式）
     */
    @JsonProperty("input_audio")
    private InputAudio inputAudio;

    /**
     * 音频 URL（type=audio_url 时使用，通用格式）
     */
    @JsonProperty("audio_url")
    private MediaUrl audioUrl;

    /**
     * 视频 URL（type=video_url 时使用）
     */
    @JsonProperty("video_url")
    private MediaUrl videoUrl;

    /**
     * 媒体 URL 结构（通用）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MediaUrl {
        /**
         * 媒体 URL，可以是：
         * 1. HTTP/HTTPS URL
         * 2. Data URL: data:{mediaType};base64,{base64_data}
         */
        private String url;

        /**
         * 细节级别（可选）：low | high | auto
         */
        private String detail;

        /**
         * 媒体类型（可选）：image/jpeg, audio/mp3, video/mp4 等
         */
        private String mimeType;

        /**
         * 格式（可选，用于音频）：wav, mp3, pcm16 等
         */
        private String format;
    }

    /**
     * 音频输入结构（OpenAI Realtime API 格式）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InputAudio {
        /**
         * 音频数据（base64 编码）
         */
        private String data;

        /**
         * 音频格式：wav, mp3, pcm16, g711_ulaw, g711_alaw
         */
        private String format;
    }

    // ============ 静态工厂方法 ============

    /**
     * 创建文本内容
     */
    public static ChatMessageContent text(String text) {
        ChatMessageContent content = new ChatMessageContent();
        content.setType("text");
        content.setText(text);
        return content;
    }

    /**
     * 创建图片内容（默认 auto 细节级别）
     */
    public static ChatMessageContent imageUrl(String url) {
        return imageUrl(url, "auto", null);
    }

    /**
     * 创建图片内容（指定细节级别）
     */
    public static ChatMessageContent imageUrl(String url, String detail) {
        return imageUrl(url, detail, null);
    }

    /**
     * 创建图片内容（完整参数）
     */
    public static ChatMessageContent imageUrl(String url, String detail, String mimeType) {
        ChatMessageContent content = new ChatMessageContent();
        content.setType("image_url");
        MediaUrl mediaUrl = new MediaUrl();
        mediaUrl.setUrl(url);
        if (detail != null && !detail.isEmpty()) {
            mediaUrl.setDetail(detail);
        }
        if (mimeType != null && !mimeType.isEmpty()) {
            mediaUrl.setMimeType(mimeType);
        }
        content.setImageUrl(mediaUrl);
        return content;
    }

    /**
     * 创建 base64 编码的图片内容
     */
    public static ChatMessageContent imageBase64(String mediaType, String base64Data) {
        String dataUrl = "data:" + mediaType + ";base64," + base64Data;
        return imageUrl(dataUrl, "auto", mediaType);
    }

    /**
     * 创建音频内容（URL 格式）
     */
    public static ChatMessageContent audioUrl(String url) {
        return audioUrl(url, null, null);
    }

    /**
     * 创建音频内容（指定格式）
     */
    public static ChatMessageContent audioUrl(String url, String format, String mimeType) {
        ChatMessageContent content = new ChatMessageContent();
        content.setType("audio_url");
        MediaUrl mediaUrl = new MediaUrl();
        mediaUrl.setUrl(url);
        if (format != null && !format.isEmpty()) {
            mediaUrl.setFormat(format);
        }
        if (mimeType != null && !mimeType.isEmpty()) {
            mediaUrl.setMimeType(mimeType);
        }
        content.setAudioUrl(mediaUrl);
        return content;
    }

    /**
     * 创建音频内容（OpenAI input_audio 格式，base64 编码）
     */
    public static ChatMessageContent inputAudio(String base64Data, String format) {
        ChatMessageContent content = new ChatMessageContent();
        content.setType("input_audio");
        InputAudio inputAudio = new InputAudio();
        inputAudio.setData(base64Data);
        inputAudio.setFormat(format != null ? format : "wav");
        content.setInputAudio(inputAudio);
        return content;
    }

    /**
     * 创建 base64 编码的音频内容
     */
    public static ChatMessageContent audioBase64(String mediaType, String base64Data) {
        String dataUrl = "data:" + mediaType + ";base64," + base64Data;
        String format = extractFormatFromMimeType(mediaType);
        return audioUrl(dataUrl, format, mediaType);
    }

    /**
     * 创建视频内容（URL 格式）
     */
    public static ChatMessageContent videoUrl(String url) {
        return videoUrl(url, null);
    }

    /**
     * 创建视频内容（指定 MIME 类型）
     */
    public static ChatMessageContent videoUrl(String url, String mimeType) {
        ChatMessageContent content = new ChatMessageContent();
        content.setType("video_url");
        MediaUrl mediaUrl = new MediaUrl();
        mediaUrl.setUrl(url);
        if (mimeType != null && !mimeType.isEmpty()) {
            mediaUrl.setMimeType(mimeType);
        }
        content.setVideoUrl(mediaUrl);
        return content;
    }

    /**
     * 创建 base64 编码的视频内容
     */
    public static ChatMessageContent videoBase64(String mediaType, String base64Data) {
        String dataUrl = "data:" + mediaType + ";base64," + base64Data;
        return videoUrl(dataUrl, mediaType);
    }

    /**
     * 从 MIME 类型提取格式
     */
    private static String extractFormatFromMimeType(String mimeType) {
        if (mimeType == null) return null;
        // audio/mp3 -> mp3, audio/wav -> wav
        int slashIndex = mimeType.indexOf('/');
        if (slashIndex > 0 && slashIndex < mimeType.length() - 1) {
            return mimeType.substring(slashIndex + 1);
        }
        return null;
    }

    // ============ 类型判断方法 ============

    /**
     * 是否为文本类型
     */
    public boolean isText() {
        return "text".equals(type);
    }

    /**
     * 是否为图片类型
     */
    public boolean isImage() {
        return "image_url".equals(type);
    }

    /**
     * 是否为音频类型
     */
    public boolean isAudio() {
        return "audio_url".equals(type) || "input_audio".equals(type);
    }

    /**
     * 是否为视频类型
     */
    public boolean isVideo() {
        return "video_url".equals(type);
    }

    /**
     * 是否为多模态类型（非文本）
     */
    public boolean isMultimodal() {
        return isImage() || isAudio() || isVideo();
    }
}
