package com.superfriend.superfriend.service;

import com.superfriend.superfriend.dto.ParseResult;
import com.superfriend.superfriend.parser.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 文件解析服务
 * 协调各种文件解析器，根据文件类型选择合适的解析器
 */
@Slf4j
@Service
public class FileParseService {

    @Autowired
    private ImageParser imageParser;

    @Autowired
    private PdfParser pdfParser;

    @Autowired
    private OfficeParser officeParser;

    @Autowired
    private AudioParser audioParser;

    @Autowired
    private VideoParser videoParser;

    @Autowired
    private TextFileParser textFileParser;

    @Autowired
    private OssService ossService;

    /**
     * 解析器映射表（按 MIME 类型前缀）
     */
    private final Map<String, FileParser> parserMap = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        // 注册解析器（按优先级顺序）
        // 图片类型优先使用 OCR
        parserMap.put("image/", imageParser);

        // 音频类型使用 ASR
        parserMap.put("audio/", audioParser);

        // 视频类型
        parserMap.put("video/", videoParser);

        // PDF
        parserMap.put("application/pdf", pdfParser);

        // Office 文档
        parserMap.put("application/vnd.openxmlformats-officedocument", officeParser);
        parserMap.put("application/vnd.ms-", officeParser);
        parserMap.put("application/msword", officeParser);

        // 文本文件（放在最后作为兜底）
        parserMap.put("text/", textFileParser);
        parserMap.put("application/json", textFileParser);
        parserMap.put("application/xml", textFileParser);
        parserMap.put("application/javascript", textFileParser);

        log.info("文件解析服务初始化完成，注册解析器: {}", parserMap.keySet());
    }

    /**
     * 解析文件
     *
     * @param fileUrl  文件 URL
     * @param mimeType 文件 MIME 类型
     * @return 解析结果
     */
    public ParseResult parse(String fileUrl, String mimeType) {
        log.info("开始解析文件: url={}, mimeType={}", fileUrl, mimeType);

        if (fileUrl == null || fileUrl.isEmpty()) {
            return ParseResult.failed("文件 URL 为空");
        }

        if (mimeType == null || mimeType.isEmpty()) {
            // 尝试从 URL 推断 MIME 类型
            mimeType = inferMimeType(fileUrl);
            log.info("从 URL 推断 MIME 类型: {}", mimeType);
        }

        // 获取合适的解析器
        FileParser parser = getParser(mimeType);

        if (parser == null) {
            log.warn("未找到支持 MIME 类型 {} 的解析器", mimeType);
            return ParseResult.unsupported(mimeType);
        }

        log.info("使用解析器: {}", parser.getName());

        try {
            ParseResult result = parser.parse(fileUrl, mimeType);
            result.setOriginalFileName(extractFileName(fileUrl));

<<<<<<< HEAD
            // 不再自动清理 temp 文件，改为会话结束时统一清理
            // 这样可以支持同一文件被多次引用
            // cleanupTempFileInternal(fileUrl);
=======
            // 解析完成后清理 temp 文件
            cleanupTempFile(fileUrl);
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2

            return result;
        } catch (Exception e) {
            log.error("文件解析异常: {}", e.getMessage(), e);
<<<<<<< HEAD
            // 解析失败也不清理文件，让会话管理器统一处理
            // cleanupTempFileInternal(fileUrl);
=======
            // 解析失败时也尝试清理文件
            cleanupTempFile(fileUrl);
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
            return ParseResult.failed("文件解析异常: " + e.getMessage());
        }
    }

    /**
<<<<<<< HEAD
     * 解析文件（不自动清理 temp 文件）
     * 用于多文件批量解析场景，避免同一文件被多次引用时已被清理
     *
     * @param fileUrl  文件 URL
     * @param mimeType 文件 MIME 类型
     * @return 解析结果
     */
    public ParseResult parseWithoutCleanup(String fileUrl, String mimeType) {
        log.info("开始解析文件（不清理）: url={}, mimeType={}", fileUrl, mimeType);

        if (fileUrl == null || fileUrl.isEmpty()) {
            return ParseResult.failed("文件 URL 为空");
        }

        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = inferMimeType(fileUrl);
            log.info("从 URL 推断 MIME 类型: {}", mimeType);
        }

        FileParser parser = getParser(mimeType);

        if (parser == null) {
            log.warn("未找到支持 MIME 类型 {} 的解析器", mimeType);
            return ParseResult.unsupported(mimeType);
        }

        log.info("使用解析器: {}", parser.getName());

        try {
            ParseResult result = parser.parse(fileUrl, mimeType);
            result.setOriginalFileName(extractFileName(fileUrl));
            return result;
        } catch (Exception e) {
            log.error("文件解析异常: {}", e.getMessage(), e);
            return ParseResult.failed("文件解析异常: " + e.getMessage());
        }
    }

    /**
     * 清理 temp 文件（公开方法，供外部调用）
     */
    public void cleanupTempFile(String fileUrl) {
        cleanupTempFileInternal(fileUrl);
    }

    /**
     * 清理 temp 文件
     * temp://type/fileName -> uploads/temp/type/fileName
     */
    private void cleanupTempFileInternal(String fileUrl) {
=======
     * 清理 temp 文件
     * temp://type/fileName -> uploads/temp/type/fileName
     */
    private void cleanupTempFile(String fileUrl) {
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        if (fileUrl == null || !fileUrl.startsWith("temp://")) {
            return;
        }

        try {
            String path = fileUrl.substring("temp://".length());
            String[] parts = path.split("/", 2);
            if (parts.length != 2) {
                log.warn("无效的 temp:// 文件路径: {}", fileUrl);
                return;
            }
            String type = parts[0];
            String fileName = parts[1];
            Path localPath = Paths.get("uploads", "temp", type, fileName);

            if (Files.exists(localPath)) {
                Files.delete(localPath);
                log.info("已清理 temp 文件: {}", localPath);
            }
        } catch (Exception e) {
            log.warn("清理 temp 文件失败: {}, error: {}", fileUrl, e.getMessage());
        }
    }

    /**
     * 解析 base64 编码的文件
     */
    public ParseResult parseBase64(String base64Data, String mimeType) {
        log.info("开始解析 base64 数据, mimeType={}", mimeType);

        if (base64Data == null || base64Data.isEmpty()) {
            return ParseResult.failed("Base64 数据为空");
        }

        FileParser parser = getParser(mimeType);
        if (parser == null) {
            return ParseResult.unsupported(mimeType);
        }

        return parser.parseBase64(base64Data, mimeType);
    }

    /**
     * 获取合适的解析器
     */
    private FileParser getParser(String mimeType) {
        if (mimeType == null) return null;

        String lowerMimeType = mimeType.toLowerCase();

        // 精确匹配
        for (Map.Entry<String, FileParser> entry : parserMap.entrySet()) {
            if (lowerMimeType.equals(entry.getKey()) ||
                    lowerMimeType.startsWith(entry.getKey())) {
                FileParser parser = entry.getValue();
                if (parser.supports(mimeType)) {
                    return parser;
                }
            }
        }

        // 遍历所有解析器尝试匹配
        for (FileParser parser : Arrays.asList(imageParser, pdfParser, officeParser, audioParser, videoParser, textFileParser)) {
            if (parser.supports(mimeType)) {
                return parser;
            }
        }

        // 最后尝试文本解析器（作为兜底）
        if (textFileParser.supports(mimeType)) {
            return textFileParser;
        }

        return null;
    }

    /**
     * 从 URL 推断 MIME 类型
     */
    private String inferMimeType(String url) {
        if (url == null) return null;

        String extension = getFileExtension(url);
        if (extension == null) return "application/octet-stream";

        switch (extension.toLowerCase()) {
            // 图片
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            case "webp":
                return "image/webp";

            // 文档
            case "pdf":
                return "application/pdf";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc":
                return "application/msword";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls":
                return "application/vnd.ms-excel";

            // 音频
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "m4a":
                return "audio/mp4";
            case "flac":
                return "audio/flac";
            case "aac":
                return "audio/aac";

            // 视频
            case "mp4":
                return "video/mp4";
            case "avi":
                return "video/x-msvideo";
            case "mov":
                return "video/quicktime";
            case "mkv":
                return "video/x-matroska";
            case "webm":
                return "video/webm";

            // 文本/代码文件
            case "txt":
            case "text":
            case "log":
                return "text/plain";
            case "md":
            case "markdown":
                return "text/markdown";
            case "json":
                return "application/json";
            case "xml":
                return "application/xml";
            case "yaml":
            case "yml":
                return "text/yaml";
            case "html":
            case "htm":
                return "text/html";
            case "css":
                return "text/css";
            case "js":
            case "jsx":
                return "application/javascript";
            case "ts":
            case "tsx":
                return "application/typescript";
            case "py":
                return "text/x-python";
            case "java":
                return "text/x-java-source";
            case "c":
            case "h":
                return "text/x-c";
            case "cpp":
            case "hpp":
            case "cc":
            case "cxx":
                return "text/x-c++";
            case "go":
                return "text/x-go";
            case "rs":
                return "text/x-rust";
            case "sql":
                return "application/sql";
            case "csv":
                return "text/csv";
            case "sh":
            case "bash":
                return "text/x-shellscript";

            default:
                // 对于未知扩展名，尝试作为文本处理
                return "text/plain";
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String url) {
        if (url == null) return null;

        // 处理 Data URL
        if (url.startsWith("data:")) {
            int semicolon = url.indexOf(';');
            if (semicolon > 5) {
                String mimeType = url.substring(5, semicolon);
                if (mimeType.contains("/")) {
                    return mimeType.split("/")[1];
                }
            }
            return null;
        }

        int lastDot = url.lastIndexOf('.');
        if (lastDot > 0 && lastDot < url.length() - 1) {
            String ext = url.substring(lastDot + 1);
            // 处理 URL 参数
            int queryIndex = ext.indexOf('?');
            if (queryIndex > 0) {
                ext = ext.substring(0, queryIndex);
            }
            return ext.toLowerCase();
        }
        return null;
    }

    /**
     * 从 URL 提取文件名
     */
    private String extractFileName(String url) {
        if (url == null) return null;

        // 处理 Data URL
        if (url.startsWith("data:")) {
            return "base64_file";
        }

        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            String fileName = url.substring(lastSlash + 1);
            // 移除 URL 参数
            int queryIndex = fileName.indexOf('?');
            if (queryIndex > 0) {
                fileName = fileName.substring(0, queryIndex);
            }
            return fileName;
        }
        return url;
    }

    /**
     * 判断是否支持该 MIME 类型
     */
    public boolean isSupported(String mimeType) {
        return getParser(mimeType) != null;
    }

    /**
     * 获取所有支持的 MIME 类型
     */
    public List<String> getSupportedMimeTypes() {
        List<String> allTypes = new ArrayList<>();
        for (FileParser parser : Arrays.asList(imageParser, pdfParser, officeParser, audioParser, videoParser)) {
            allTypes.addAll(parser.getSupportedMimeTypes());
        }
        return allTypes;
    }

    /**
     * 判断是否为图片类型
     */
    public boolean isImage(String mimeType) {
        return mimeType != null && mimeType.toLowerCase().startsWith("image/");
    }

    /**
     * 判断是否为音频类型
     */
    public boolean isAudio(String mimeType) {
        return mimeType != null && mimeType.toLowerCase().startsWith("audio/");
    }

    /**
     * 判断是否为视频类型
     */
    public boolean isVideo(String mimeType) {
        return mimeType != null && mimeType.toLowerCase().startsWith("video/");
    }

    /**
     * 判断是否为文档类型
     */
    public boolean isDocument(String mimeType) {
        if (mimeType == null) return false;
        String lower = mimeType.toLowerCase();
        return lower.equals("application/pdf") ||
                lower.contains("wordprocessing") ||
                lower.contains("presentation") ||
                lower.contains("spreadsheet") ||
                lower.equals("application/msword") ||
                lower.startsWith("application/vnd.ms-") ||
                lower.startsWith("application/vnd.openxmlformats-officedocument");
    }
}
