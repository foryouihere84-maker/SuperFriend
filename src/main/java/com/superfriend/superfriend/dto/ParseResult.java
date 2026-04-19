package com.superfriend.superfriend.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 文件解析结果
 * 统一的文件解析输出格式
 */
@Data
public class ParseResult {

    /**
     * 解析状态
     */
    public enum Status {
        /** 成功 */
        SUCCESS,
        /** 部分成功 */
        PARTIAL,
        /** 失败 */
        FAILED,
        /** 不支持的文件类型 */
        UNSUPPORTED
    }

    /**
     * 解析状态
     */
    private Status status;

    /**
     * 解析出的文本内容
     */
    private String textContent;

    /**
     * 解析出的结构化内容（如表格数据）
     */
    private List<Map<String, Object>> structuredContent;

    /**
     * 提取的图片 URL 列表（从文档中提取的图片）
     */
    private List<String> extractedImages;

    /**
     * 元数据（页数、作者、创建时间等）
     */
    private Map<String, Object> metadata;

    /**
     * 错误信息（当状态为 FAILED 时）
     */
    private String errorMessage;

    /**
     * 原始文件名
     */
    private String originalFileName;

    /**
     * 原始 MIME 类型
     */
    private String mimeType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 解析耗时（毫秒）
     */
    private Long parseTimeMs;

    // ============ 静态工厂方法 ============

    public static ParseResult success(String textContent) {
        ParseResult result = new ParseResult();
        result.setStatus(Status.SUCCESS);
        result.setTextContent(textContent);
        return result;
    }

    public static ParseResult success(String textContent, List<Map<String, Object>> structuredContent) {
        ParseResult result = new ParseResult();
        result.setStatus(Status.SUCCESS);
        result.setTextContent(textContent);
        result.setStructuredContent(structuredContent);
        return result;
    }

    public static ParseResult partial(String textContent, String errorMessage) {
        ParseResult result = new ParseResult();
        result.setStatus(Status.PARTIAL);
        result.setTextContent(textContent);
        result.setErrorMessage(errorMessage);
        return result;
    }

    public static ParseResult failed(String errorMessage) {
        ParseResult result = new ParseResult();
        result.setStatus(Status.FAILED);
        result.setErrorMessage(errorMessage);
        return result;
    }

    public static ParseResult unsupported(String mimeType) {
        ParseResult result = new ParseResult();
        result.setStatus(Status.UNSUPPORTED);
        result.setMimeType(mimeType);
        result.setErrorMessage("不支持的文件类型: " + mimeType);
        return result;
    }

    // ============ 便捷方法 ============

    public boolean isSuccess() {
        return status == Status.SUCCESS || status == Status.PARTIAL;
    }

    public boolean hasContent() {
        return textContent != null && !textContent.isEmpty();
    }

    public boolean hasStructuredContent() {
        return structuredContent != null && !structuredContent.isEmpty();
    }

    public boolean hasImages() {
        return extractedImages != null && !extractedImages.isEmpty();
    }

    /**
     * 获取用于 LLM 的格式化内容
     */
    public String getFormattedContentForLLM() {
        StringBuilder sb = new StringBuilder();

        if (textContent != null && !textContent.isEmpty()) {
            sb.append(textContent);
        }

        if (structuredContent != null && !structuredContent.isEmpty()) {
            sb.append("\n\n--- 结构化数据 ---\n");
            for (int i = 0; i < structuredContent.size(); i++) {
                Map<String, Object> row = structuredContent.get(i);
                sb.append("[").append(i + 1).append("] ").append(row.toString()).append("\n");
            }
        }

        return sb.toString();
    }
}
