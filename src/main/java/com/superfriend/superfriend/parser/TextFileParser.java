package com.superfriend.superfriend.parser;

import com.superfriend.superfriend.dto.ParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 文本/代码文件解析器
 * 支持各种文本格式文件：txt, md, json, xml, yaml, html, css, js, py, java, c, cpp 等
 */
@Slf4j
@Component
public class TextFileParser implements FileParser {

    // 支持的文本文件扩展名
    private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<>(Arrays.asList(
            // 纯文本
            "txt", "text", "log",
            // 标记语言
            "md", "markdown", "rst", "adoc",
            // 配置文件
            "json", "xml", "yaml", "yml", "toml", "ini", "properties", "conf", "cfg",
            // Web 前端
            "html", "htm", "css", "scss", "sass", "less", "js", "jsx", "ts", "tsx", "vue", "svelte",
            // 编程语言
            "py", "python", "java", "c", "cpp", "cc", "cxx", "h", "hpp", "cs", "go", "rs", "rb", "php",
            "swift", "kt", "kts", "scala", "clj", "lua", "pl", "pm", "r", "m", "mm",
            // Shell 脚本
            "sh", "bash", "zsh", "fish", "ps1", "bat", "cmd",
            // 数据格式
            "csv", "tsv", "sql", "graphql", "proto",
            // 其他
            "dockerfile", "makefile", "cmake", "gradle", "gitignore", "env"
    ));

    // 特殊文件名（无扩展名）
    private static final Set<String> SPECIAL_FILENAMES = new HashSet<>(Arrays.asList(
            "dockerfile", "makefile", "cmakelists", "readme", "changelog", "license",
            "gitignore", "gitattributes", "editorconfig", "env", ".env"
    ));

    // 代码文件最大行数限制
    private static final int MAX_LINES = 5000;

    // 代码文件最大字符数限制
    private static final int MAX_CHARS = 500000;

    @Override
    public ParseResult parse(String fileUrl, String mimeType) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("开始解析文本/代码文件: {}", fileUrl);

            // 获取文件扩展名
            String extension = getFileExtension(fileUrl);
            String fileName = getFileName(fileUrl);

            // 判断是否支持
            if (!isSupportedFile(extension, fileName)) {
                return ParseResult.unsupported(mimeType != null ? mimeType : "text/plain");
            }

            // 检测编码（默认 UTF-8）
            Charset charset = detectCharset(extension);

            // 读取文件内容
            StringBuilder content = new StringBuilder();
            int lineCount = 0;

            // 解析文件 URL，处理 temp:// 协议
            InputStream rawInputStream = resolveFileInputStream(fileUrl);
            if (rawInputStream == null) {
                return ParseResult.failed("无法读取文件: " + fileUrl);
            }

            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(rawInputStream, charset));

                String line;
                while ((line = reader.readLine()) != null && lineCount < MAX_LINES) {
                    content.append(line).append("\n");
                    lineCount++;

                    // 检查字符数限制
                    if (content.length() > MAX_CHARS) {
                        content.append("\n... [文件内容过长，已截断]");
                        break;
                    }
                }
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception e) {
                        log.warn("关闭 reader 失败: {}", e.getMessage());
                    }
                }
            }

            String textContent = content.toString();
            long parseTime = System.currentTimeMillis() - startTime;

            log.info("文本文件解析完成，行数: {}, 字符数: {}, 耗时: {}ms",
                    lineCount, textContent.length(), parseTime);

            // 构建结果
            ParseResult result = ParseResult.success(textContent);
            result.setMimeType(getMimeType(extension));
            result.setParseTimeMs(parseTime);

            // 添加元数据
            result.setMetadata(new HashMap<>());
            result.getMetadata().put("lineCount", lineCount);
            result.getMetadata().put("charCount", textContent.length());
            result.getMetadata().put("extension", extension);
            result.getMetadata().put("fileType", getFileType(extension));
            result.getMetadata().put("language", getLanguageName(extension));

            // 如果是代码文件，添加代码块标记
            if (isCodeFile(extension)) {
                result.getMetadata().put("isCode", true);
                result.getMetadata().put("codeLanguage", getCodeLanguage(extension));
            }

            return result;

        } catch (Exception e) {
            log.error("文本文件解析失败: {}", e.getMessage(), e);
            return ParseResult.failed("文本文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析 base64 编码的文本内容
     */
    @Override
    public ParseResult parseBase64(String base64Data, String mimeType) {
        try {
            // 解码 base64
            byte[] bytes = java.util.Base64.getDecoder().decode(base64Data);
            String content = new String(bytes, StandardCharsets.UTF_8);

            // 检查长度限制
            if (content.length() > MAX_CHARS) {
                content = content.substring(0, MAX_CHARS) + "\n... [内容过长，已截断]";
            }

            ParseResult result = ParseResult.success(content);
            result.setMimeType(mimeType != null ? mimeType : "text/plain");

            result.setMetadata(new HashMap<>());
            result.getMetadata().put("charCount", content.length());

            return result;

        } catch (Exception e) {
            log.error("Base64 文本解析失败: {}", e.getMessage());
            return ParseResult.failed("Base64 文本解析失败: " + e.getMessage());
        }
    }

    /**
     * 判断是否为支持的文件
     */
    private boolean isSupportedFile(String extension, String fileName) {
        if (extension != null && SUPPORTED_EXTENSIONS.contains(extension.toLowerCase())) {
            return true;
        }
        if (fileName != null && SPECIAL_FILENAMES.contains(fileName.toLowerCase())) {
            return true;
        }
        return false;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String url) {
        if (url == null) return null;

        int lastDot = url.lastIndexOf('.');
        if (lastDot > 0 && lastDot < url.length() - 1) {
            String ext = url.substring(lastDot + 1);
            int queryIndex = ext.indexOf('?');
            if (queryIndex > 0) {
                ext = ext.substring(0, queryIndex);
            }
            return ext.toLowerCase();
        }
        return null;
    }

    /**
     * 获取文件名（不含扩展名）
     */
    private String getFileName(String url) {
        if (url == null) return null;

        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            String fullName = url.substring(lastSlash + 1);
            int queryIndex = fullName.indexOf('?');
            if (queryIndex > 0) {
                fullName = fullName.substring(0, queryIndex);
            }
            int dotIndex = fullName.lastIndexOf('.');
            if (dotIndex > 0) {
                return fullName.substring(0, dotIndex);
            }
            return fullName;
        }
        return null;
    }

    /**
     * 检测文件编码
     */
    private Charset detectCharset(String extension) {
        // 默认使用 UTF-8
        // 对于特定文件类型可以考虑其他编码
        return StandardCharsets.UTF_8;
    }

    /**
     * 获取 MIME 类型
     */
    private String getMimeType(String extension) {
        if (extension == null) return "text/plain";

        switch (extension.toLowerCase()) {
            case "json":
                return "application/json";
            case "xml":
                return "application/xml";
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
            case "md":
            case "markdown":
                return "text/markdown";
            case "yaml":
            case "yml":
                return "text/yaml";
            case "sql":
                return "application/sql";
            case "csv":
                return "text/csv";
            default:
                return "text/plain";
        }
    }

    /**
     * 获取文件类型描述
     */
    private String getFileType(String extension) {
        if (extension == null) return "Text";

        switch (extension.toLowerCase()) {
            case "md":
            case "markdown":
                return "Markdown";
            case "json":
                return "JSON";
            case "xml":
                return "XML";
            case "yaml":
            case "yml":
                return "YAML";
            case "html":
            case "htm":
                return "HTML";
            case "css":
            case "scss":
            case "sass":
            case "less":
                return "CSS";
            case "js":
            case "jsx":
            case "ts":
            case "tsx":
                return "JavaScript/TypeScript";
            case "py":
                return "Python";
            case "java":
                return "Java";
            case "c":
            case "h":
            case "cpp":
            case "hpp":
                return "C/C++";
            case "go":
                return "Go";
            case "rs":
                return "Rust";
            case "sql":
                return "SQL";
            case "csv":
                return "CSV";
            case "sh":
            case "bash":
                return "Shell Script";
            default:
                return "Text";
        }
    }

    /**
     * 获取语言名称（用于显示）
     */
    private String getLanguageName(String extension) {
        return getFileType(extension);
    }

    /**
     * 判断是否为代码文件
     */
    private boolean isCodeFile(String extension) {
        if (extension == null) return false;

        Set<String> codeExtensions = new HashSet<>(Arrays.asList(
                "py", "java", "c", "cpp", "cc", "cxx", "h", "hpp", "cs", "go", "rs", "rb", "php",
                "swift", "kt", "kts", "scala", "clj", "lua", "pl", "pm", "r", "m", "mm",
                "js", "jsx", "ts", "tsx", "vue", "sh", "bash", "sql"
        ));

        return codeExtensions.contains(extension.toLowerCase());
    }

    /**
     * 获取代码语言标识（用于语法高亮）
     */
    private String getCodeLanguage(String extension) {
        if (extension == null) return "text";

        switch (extension.toLowerCase()) {
            case "py":
                return "python";
            case "java":
                return "java";
            case "c":
            case "h":
                return "c";
            case "cpp":
            case "hpp":
            case "cc":
            case "cxx":
                return "cpp";
            case "cs":
                return "csharp";
            case "go":
                return "go";
            case "rs":
                return "rust";
            case "rb":
                return "ruby";
            case "php":
                return "php";
            case "swift":
                return "swift";
            case "kt":
            case "kts":
                return "kotlin";
            case "scala":
                return "scala";
            case "js":
            case "jsx":
                return "javascript";
            case "ts":
            case "tsx":
                return "typescript";
            case "vue":
                return "vue";
            case "sh":
            case "bash":
                return "bash";
            case "sql":
                return "sql";
            case "json":
                return "json";
            case "xml":
                return "xml";
            case "yaml":
            case "yml":
                return "yaml";
            case "md":
            case "markdown":
                return "markdown";
            case "html":
            case "htm":
                return "html";
            case "css":
            case "scss":
            case "sass":
            case "less":
                return "css";
            default:
                return "text";
        }
    }

    /**
     * 根据文件 URL 获取输入流
     * 支持 http://, https://, temp:// 协议和本地文件路径
     */
    private InputStream resolveFileInputStream(String fileUrl) {
        try {
            if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
                return new URL(fileUrl).openStream();
            } else if (fileUrl.startsWith("temp://")) {
                // temp://type/fileName -> uploads/temp/type/fileName
                String path = fileUrl.substring("temp://".length());
                String[] parts = path.split("/", 2);
                if (parts.length != 2) {
                    log.error("无效的 temp:// 文件路径: {}", fileUrl);
                    return null;
                }
                String type = parts[0];
                String fileName = parts[1];
                Path localPath = Paths.get("uploads", "temp", type, fileName);
                return new FileInputStream(localPath.toFile());
            } else {
                // 普通本地文件路径
                return new FileInputStream(fileUrl);
            }
        } catch (Exception e) {
            log.error("解析文件 URL 失败: {}, error: {}", fileUrl, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;

        String lower = mimeType.toLowerCase();

        // 支持所有 text/* 类型
        if (lower.startsWith("text/")) {
            return true;
        }

        // 支持常见的应用文本类型
        return lower.equals("application/json") ||
               lower.equals("application/xml") ||
               lower.equals("application/javascript") ||
               lower.equals("application/typescript") ||
               lower.equals("application/sql") ||
               lower.contains("yaml");
    }

    @Override
    public String getName() {
        return "TextFileParser";
    }

    @Override
    public List<String> getSupportedMimeTypes() {
        return Arrays.asList(
                "text/plain", "text/html", "text/css", "text/markdown",
                "application/json", "application/xml", "application/javascript",
                "text/x-python", "text/x-java-source", "text/x-c", "text/x-c++",
                "text/yaml", "application/sql", "text/csv"
        );
    }
}
