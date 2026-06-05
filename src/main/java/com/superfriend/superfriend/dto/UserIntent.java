package com.superfriend.superfriend.dto;

import lombok.Data;

/**
 * 用户意图
 * 用于识别用户请求的类型，决定后续处理流程
 */
@Data
public class UserIntent {

    /**
     * 意图类型枚举
     */
    public enum Type {
        /** 纯文本对话 */
        CHAT,
        /** 多模态对话（包含图片/音频/视频输入） */
        MULTIMODAL_CHAT,
        /** 生成图片 */
        GENERATE_IMAGE,
        /** 生成音频 */
        GENERATE_AUDIO,
        /** 生成视频 */
        GENERATE_VIDEO,
        /** 生成文档（DOCX/XLSX/PPTX等） */
        GENERATE_DOCUMENT,
        /** 解析文件（PDF/DOCX/PPTX等） */
        PARSE_FILE,
        /** 解析图片（OCR） */
        PARSE_IMAGE,
        /** 解析音频（ASR） */
        PARSE_AUDIO,
        /** 解析视频 */
        PARSE_VIDEO,
        /** 未知意图 */
        UNKNOWN
    }

    /**
     * 意图类型
     */
    private Type type;

    /**
     * 置信度 (0-1)
     */
    private double confidence;

    /**
     * 文件 URL（当类型为 PARSE_* 时）
     */
    private String fileUrl;

    /**
     * 文件 MIME 类型
     */
    private String mimeType;

    /**
     * 文件扩展名
     */
    private String fileExtension;

    /**
     * 原始用户文本
     */
    private String userText;

    /**
     * 提取的关键词
     */
    private java.util.List<String> keywords;

    /**
     * 生成提示词（当类型为 GENERATE_* 时）
     */
    private String generationPrompt;

    /**
     * 图片尺寸（当类型为 GENERATE_IMAGE 时）
     * 格式: "1024x1024", "768x1344" 等
     */
    private String imageSize;

    /**
     * 意图判断理由（LLM 分类时提供）
     */
    private String reason;

    /**
     * 是否需要联网搜索
     */
    private boolean needsSearch;

    /**
     * 优化后的提示词（LLM优化后的用户意图描述）
     */
    private String optimizedPrompt;

    // ============ 静态工厂方法 ============

    public static UserIntent chat() {
        UserIntent intent = new UserIntent();
        intent.setType(Type.CHAT);
        intent.setConfidence(1.0);
        return intent;
    }

    public static UserIntent multimodalChat() {
        UserIntent intent = new UserIntent();
        intent.setType(Type.MULTIMODAL_CHAT);
        intent.setConfidence(1.0);
        return intent;
    }

    public static UserIntent generateImage(String prompt) {
        UserIntent intent = new UserIntent();
        intent.setType(Type.GENERATE_IMAGE);
        intent.setGenerationPrompt(prompt);
        intent.setConfidence(1.0);
        return intent;
    }

    public static UserIntent parseFile(String fileUrl, String mimeType, String extension) {
        UserIntent intent = new UserIntent();
        intent.setType(Type.PARSE_FILE);
        intent.setFileUrl(fileUrl);
        intent.setMimeType(mimeType);
        intent.setFileExtension(extension);
        intent.setConfidence(1.0);
        return intent;
    }

    public static UserIntent parseImage(String fileUrl) {
        UserIntent intent = new UserIntent();
        intent.setType(Type.PARSE_IMAGE);
        intent.setFileUrl(fileUrl);
        intent.setMimeType("image/*");
        intent.setConfidence(1.0);
        return intent;
    }

    public static UserIntent parseAudio(String fileUrl, String mimeType) {
        UserIntent intent = new UserIntent();
        intent.setType(Type.PARSE_AUDIO);
        intent.setFileUrl(fileUrl);
        intent.setMimeType(mimeType);
        intent.setConfidence(1.0);
        return intent;
    }

    public static UserIntent parseVideo(String fileUrl, String mimeType) {
        UserIntent intent = new UserIntent();
        intent.setType(Type.PARSE_VIDEO);
        intent.setFileUrl(fileUrl);
        intent.setMimeType(mimeType);
        intent.setConfidence(1.0);
        return intent;
    }

    public static UserIntent generateDocument(String prompt) {
        UserIntent intent = new UserIntent();
        intent.setType(Type.GENERATE_DOCUMENT);
        intent.setGenerationPrompt(prompt);
        intent.setConfidence(1.0);
        return intent;
    }

    public static UserIntent unknown() {
        UserIntent intent = new UserIntent();
        intent.setType(Type.UNKNOWN);
        intent.setConfidence(0.0);
        return intent;
    }

    // ============ 便捷方法 ============

    public boolean isGeneration() {
        return type == Type.GENERATE_IMAGE ||
               type == Type.GENERATE_AUDIO ||
               type == Type.GENERATE_VIDEO ||
               type == Type.GENERATE_DOCUMENT;
    }

    public boolean isFileParsing() {
        return type == Type.PARSE_FILE ||
               type == Type.PARSE_IMAGE ||
               type == Type.PARSE_AUDIO ||
               type == Type.PARSE_VIDEO;
    }

    public boolean isMultimodal() {
        return type == Type.MULTIMODAL_CHAT ||
               type == Type.PARSE_IMAGE ||
               type == Type.PARSE_AUDIO ||
               type == Type.PARSE_VIDEO;
    }

    public boolean requiresImageOutput() {
        return type == Type.GENERATE_IMAGE;
    }

    public boolean requiresAudioOutput() {
        return type == Type.GENERATE_AUDIO;
    }

    public boolean requiresDocumentOutput() {
        return type == Type.GENERATE_DOCUMENT;
    }
}
