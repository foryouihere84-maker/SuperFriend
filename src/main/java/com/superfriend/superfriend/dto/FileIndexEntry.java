package com.superfriend.superfriend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件索引条目
 * 用于会话级别的文件索引管理，支持 LLM 按需读取文件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileIndexEntry {

    /**
     * 文件唯一 ID（UUID）
     */
    private String fileId;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * MIME 类型
     */
    private String mimeType;

    /**
     * 文件类型分类
     */
    public enum FileType {
        DOCUMENT,    // 文档：PDF, Word, TXT, Markdown
        SPREADSHEET, // 表格：Excel, CSV
        IMAGE,       // 图片：PNG, JPG, GIF
        CODE,        // 代码：Java, Python, JS
        TEXT,        // 纯文本
        OTHER        // 其他
    }

    /**
     * 文件类型分类
     */
    private FileType fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件摘要（自动生成，限制长度）
     */
    private String summary;

    /**
     * 行数（文本/代码文件）
     */
    private Integer lineCount;

    /**
     * 页数（PDF 文件）
     */
    private Integer pageCount;

    /**
     * 临时文件 URL（temp://xxx）
     */
    private String tempUrl;

    /**
     * 上传时间
     */
    private LocalDateTime uploadTime;

    /**
     * 是否已预解析（小文件 < 50KB）
     */
    private Boolean preParsed;

    /**
     * 预解析内容（仅小文件有）
     */
    private String parsedContent;

    // ============ 静态工厂方法 ============

    /**
     * 创建文件索引条目
     */
    public static FileIndexEntry create(String fileId, String sessionId, String fileName,
                                         String mimeType, Long fileSize, String tempUrl) {
        return FileIndexEntry.builder()
                .fileId(fileId)
                .sessionId(sessionId)
                .fileName(fileName)
                .mimeType(mimeType)
                .fileType(determineFileType(mimeType, fileName))
                .fileSize(fileSize)
                .tempUrl(tempUrl)
                .uploadTime(LocalDateTime.now())
                .preParsed(false)
                .build();
    }

    /**
     * 根据 MIME 类型和文件名确定文件类型
     */
    public static FileType determineFileType(String mimeType, String fileName) {
        if (mimeType == null) {
            return FileType.OTHER;
        }

        String lowerMime = mimeType.toLowerCase();
        String lowerName = fileName != null ? fileName.toLowerCase() : "";

        // 图片
        if (lowerMime.startsWith("image/")) {
            return FileType.IMAGE;
        }

        // 表格
        if (lowerMime.contains("spreadsheet") || lowerMime.contains("excel") ||
            lowerMime.contains("csv") || lowerName.endsWith(".csv") ||
            lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
            return FileType.SPREADSHEET;
        }

        // 代码
        if (lowerMime.contains("javascript") || lowerMime.contains("python") ||
            lowerMime.contains("java") || lowerMime.contains("typescript") ||
            lowerMime.contains("json") || lowerMime.contains("xml") ||
            lowerName.endsWith(".java") || lowerName.endsWith(".py") ||
            lowerName.endsWith(".js") || lowerName.endsWith(".ts") ||
            lowerName.endsWith(".json") || lowerName.endsWith(".xml") ||
            lowerName.endsWith(".html") || lowerName.endsWith(".css")) {
            return FileType.CODE;
        }

        // 文档
        if (lowerMime.contains("pdf") || lowerMime.contains("word") ||
            lowerMime.contains("document") || lowerMime.contains("msword") ||
            lowerName.endsWith(".pdf") || lowerName.endsWith(".doc") ||
            lowerName.endsWith(".docx")) {
            return FileType.DOCUMENT;
        }

        // 纯文本
        if (lowerMime.startsWith("text/") || lowerName.endsWith(".txt") ||
            lowerName.endsWith(".md") || lowerName.endsWith(".markdown")) {
            return FileType.TEXT;
        }

        return FileType.OTHER;
    }

    // ============ 便捷方法 ============

    /**
     * 是否为小文件（可预解析）
     */
    public boolean isSmallFile() {
        // 小于 50KB 的文件视为小文件
        return fileSize != null && fileSize < 50 * 1024;
    }

    /**
     * 获取格式化的文件大小
     */
    public String getFormattedSize() {
        if (fileSize == null) {
            return "未知";
        }
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", fileSize / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * 获取文件类型的中文名称
     */
    public String getFileTypeDisplayName() {
        if (fileType == null) {
            return "其他";
        }
        switch (fileType) {
            case DOCUMENT:
                return "文档";
            case SPREADSHEET:
                return "表格";
            case IMAGE:
                return "图片";
            case CODE:
                return "代码";
            case TEXT:
                return "文本";
            default:
                return "其他";
        }
    }
}
