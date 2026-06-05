package com.superfriend.superfriend.service;

import com.superfriend.superfriend.dto.FileIndexEntry;
import com.superfriend.superfriend.dto.ParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 会话文件索引服务
 * 管理每个会话的文件索引，支持 LLM 按需读取文件
 */
@Slf4j
@Service
public class SessionFileIndexService {

    @Autowired
    private FileParseService fileParseService;

    /**
     * 内存缓存：按会话 ID 分组
     */
    private final Map<String, List<FileIndexEntry>> sessionFileIndex = new ConcurrentHashMap<>();

    /**
     * 小文件阈值（10MB）
     * 小于这个阈值的文件会在上传时预解析，内容存储在内存中
     * 大于这个阈值的文件会在需要时按需解析
     */
    private static final int SMALL_FILE_THRESHOLD = 10 * 1024 * 1024;

    /**
     * 摘要最大长度
     */
    private static final int MAX_SUMMARY_LENGTH = 200;

    /**
     * 每会话最大文件数
     */
    private static final int MAX_FILES_PER_SESSION = 20;

    /**
     * 记录过期时间（小时）
     */
    private static final int RECORD_EXPIRY_HOURS = 24;

    /**
     * 注册文件到索引
     *
     * @param sessionId 会话 ID
     * @param fileId    文件 ID
     * @param fileName  文件名
     * @param mimeType  MIME 类型
     * @param fileSize  文件大小
     * @param tempUrl   临时文件 URL
     * @return 文件索引条目
     */
    public FileIndexEntry registerFile(String sessionId, String fileId, String fileName,
                                        String mimeType, Long fileSize, String tempUrl) {
        log.info("[FileIndex] 注册文件: sessionId={}, fileId={}, fileName={}, size={}",
                sessionId, fileId, fileName, fileSize);

        // 创建索引条目
        FileIndexEntry entry = FileIndexEntry.create(fileId, sessionId, fileName, mimeType, fileSize, tempUrl);

        // 小文件预解析（fileSize >= 0 且 < 10MB）
        // fileSize = -1 表示未知大小，按大文件处理（需要时再解析）
        // fileSize = null 也按大文件处理
        boolean isSmallFile = fileSize != null && fileSize >= 0 && fileSize < SMALL_FILE_THRESHOLD;

        if (isSmallFile) {
            try {
                ParseResult result = fileParseService.parseWithoutCleanup(tempUrl, mimeType);
                if (result.isSuccess() && result.hasContent()) {
                    entry.setPreParsed(true);
                    entry.setParsedContent(result.getTextContent());
                    entry.setSummary(generateSummary(result.getTextContent()));

                    // 提取元数据
                    if (result.getMetadata() != null) {
                        if (result.getMetadata().containsKey("pageCount")) {
                            entry.setPageCount((Integer) result.getMetadata().get("pageCount"));
                        }
                        if (result.getMetadata().containsKey("lineCount")) {
                            entry.setLineCount((Integer) result.getMetadata().get("lineCount"));
                        }
                    }

                    // 计算行数（文本文件）
                    if (result.getTextContent() != null && entry.getLineCount() == null) {
                        entry.setLineCount(countLines(result.getTextContent()));
                    }

                    log.info("[FileIndex] 小文件预解析完成: fileId={}, size={}KB, lineCount={}",
                            fileId, fileSize / 1024, entry.getLineCount());
                }
            } catch (Exception e) {
                log.warn("[FileIndex] 小文件预解析失败: fileId={}, error={}", fileId, e.getMessage());
            }
        } else {
            // 大文件或未知大小文件，只生成基本信息摘要，需要时再按需解析
            entry.setSummary(generateBasicSummary(fileName, mimeType, fileSize));
            if (fileSize == null || fileSize < 0) {
                log.info("[FileIndex] 文件大小未知，标记为按需解析: fileId={}", fileId);
            } else {
                log.info("[FileIndex] 大文件不预解析，将在需要时按需读取: fileId={}, size={}MB",
                        fileId, fileSize / (1024 * 1024));
            }
        }

        // 添加到索引
        addToIndex(sessionId, entry);

        return entry;
    }

    /**
     * 获取会话的文件索引列表
     *
     * @param sessionId 会话 ID
     * @return 文件索引列表
     */
    public List<FileIndexEntry> getFileIndex(String sessionId) {
        List<FileIndexEntry> entries = sessionFileIndex.get(sessionId);
        if (entries == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(entries);
    }

    /**
     * 检查会话是否已有注册的文件
     *
     * @param sessionId 会话 ID
     * @return 是否有文件
     */
    public boolean hasRegisteredFiles(String sessionId) {
        List<FileIndexEntry> entries = sessionFileIndex.get(sessionId);
        return entries != null && !entries.isEmpty();
    }

    /**
     * 检查会话中是否存在指定文件名的文件
     *
     * @param sessionId 会话 ID
     * @param fileName  文件名
     * @return 是否存在
     */
    public boolean hasFileWithName(String sessionId, String fileName) {
        if (sessionId == null || fileName == null) {
            return false;
        }
        List<FileIndexEntry> entries = sessionFileIndex.get(sessionId);
        if (entries == null || entries.isEmpty()) {
            return false;
        }
        return entries.stream()
                .anyMatch(e -> fileName.equals(e.getFileName()) ||
                        (e.getFileName() != null && e.getFileName().contains(fileName)));
    }

    /**
     * 获取文件详细信息
     *
     * @param fileId    文件 ID
     * @param sessionId 会话 ID
     * @return 文件索引条目，不存在则返回 null
     */
    public FileIndexEntry getFileInfo(String fileId, String sessionId) {
        List<FileIndexEntry> entries = sessionFileIndex.get(sessionId);
        if (entries == null) {
            return null;
        }
        return entries.stream()
                .filter(e -> e.getFileId().equals(fileId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 读取文件内容
     * - 小文件（已预解析）：直接从内存返回
     * - 大文件（未预解析）：按需解析文件并返回内容
     *
     * @param fileId    文件 ID
     * @param sessionId 会话 ID
     * @return 文件内容，失败返回 null
     */
    public String getFileContent(String fileId, String sessionId) {
        FileIndexEntry entry = getFileInfo(fileId, sessionId);
        if (entry == null) {
            log.warn("[FileIndex] 文件不存在: fileId={}, sessionId={}", fileId, sessionId);
            return null;
        }

        // 如果是预解析的小文件，直接返回内容
        if (entry.getPreParsed() && entry.getParsedContent() != null) {
            log.debug("[FileIndex] 返回预解析内容: fileId={}, size={}KB", fileId, entry.getParsedContent().length() / 1024);
            return entry.getParsedContent();
        }

        // 大文件按需解析
        log.info("[FileIndex] 按需解析大文件: fileId={}, fileName={}, tempUrl={}",
                fileId, entry.getFileName(), entry.getTempUrl());
        try {
            long startTime = System.currentTimeMillis();
            ParseResult result = fileParseService.parseWithoutCleanup(entry.getTempUrl(), entry.getMimeType());
            long parseTime = System.currentTimeMillis() - startTime;

            if (result.isSuccess() && result.hasContent()) {
                log.info("[FileIndex] 大文件解析完成: fileId={}, contentSize={}KB, parseTime={}ms",
                        fileId, result.getTextContent().length() / 1024, parseTime);
                return result.getTextContent();
            } else {
                log.warn("[FileIndex] 大文件解析失败: fileId={}, status={}",
                        fileId, result.getStatus());
            }
        } catch (Exception e) {
            log.error("[FileIndex] 解析文件失败: fileId={}, error={}", fileId, e.getMessage(), e);
        }

        return null;
    }

    /**
     * 读取文件内容（支持分段）
     *
     * @param fileId    文件 ID
     * @param sessionId 会话 ID
     * @param startLine 起始行号（从 1 开始）
     * @param endLine   结束行号
     * @return 文件内容片段
     */
    public String getFileContentRange(String fileId, String sessionId, Integer startLine, Integer endLine) {
        String content = getFileContent(fileId, sessionId);
        if (content == null) {
            return null;
        }

        if (startLine == null && endLine == null) {
            return content;
        }

        String[] lines = content.split("\n");
        int start = startLine != null ? Math.max(1, startLine) - 1 : 0;
        int end = endLine != null ? Math.min(lines.length, endLine) : lines.length;

        if (start >= lines.length) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            sb.append(lines[i]).append("\n");
        }

        return sb.toString();
    }

    /**
     * 在文件中搜索关键词
     *
     * @param sessionId 会话 ID
     * @param keyword   关键词
     * @param fileId    文件 ID（可选，null 表示搜索所有文件）
     * @return 搜索结果
     */
    public List<FileSearchResult> searchInFiles(String sessionId, String keyword, String fileId) {
        List<FileIndexEntry> entries;
        if (fileId != null) {
            FileIndexEntry entry = getFileInfo(fileId, sessionId);
            entries = new ArrayList<>();
            if (entry != null) {
                entries.add(entry);
            }
        } else {
            entries = getFileIndex(sessionId);
        }

        List<FileSearchResult> results = new ArrayList<>();
        String lowerKeyword = keyword.toLowerCase();

        for (FileIndexEntry entry : entries) {
            String content = getFileContent(entry.getFileId(), sessionId);
            if (content == null) {
                continue;
            }

            List<SearchMatch> matches = new ArrayList<>();
            String[] lines = content.split("\n");

            for (int i = 0; i < lines.length; i++) {
                if (lines[i].toLowerCase().contains(lowerKeyword)) {
                    matches.add(new SearchMatch(i + 1, lines[i].trim()));
                }
            }

            if (!matches.isEmpty()) {
                results.add(new FileSearchResult(entry.getFileId(), entry.getFileName(), matches));
            }
        }

        return results;
    }

    /**
     * 清除会话的文件索引
     *
     * @param sessionId 会话 ID
     */
    public void clearSessionFiles(String sessionId) {
        List<FileIndexEntry> entries = sessionFileIndex.remove(sessionId);
        if (entries != null) {
            // 清理临时文件
            for (FileIndexEntry entry : entries) {
                try {
                    fileParseService.cleanupTempFile(entry.getTempUrl());
                } catch (Exception e) {
                    log.warn("[FileIndex] 清理临时文件失败: {}", entry.getTempUrl());
                }
            }
            log.info("[FileIndex] 清除会话文件索引: sessionId={}, count={}", sessionId, entries.size());
        }
    }

    /**
     * 定时清理过期记录（每小时执行）
     */
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredRecords() {
        LocalDateTime expiryTime = LocalDateTime.now().minusHours(RECORD_EXPIRY_HOURS);
        int removedSessions = 0;

        Iterator<Map.Entry<String, List<FileIndexEntry>>> iterator = sessionFileIndex.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<FileIndexEntry>> entry = iterator.next();
            List<FileIndexEntry> files = entry.getValue();

            // 检查是否有过期文件
            boolean hasExpired = files.stream()
                    .anyMatch(f -> f.getUploadTime() != null && f.getUploadTime().isBefore(expiryTime));

            if (hasExpired) {
                // 清理临时文件
                for (FileIndexEntry file : files) {
                    try {
                        fileParseService.cleanupTempFile(file.getTempUrl());
                    } catch (Exception ignored) {
                    }
                }
                iterator.remove();
                removedSessions++;
            }
        }

        if (removedSessions > 0) {
            log.info("[FileIndex] 清理过期会话文件索引: {} 个会话", removedSessions);
        }
    }

    // ========== 私有方法 ==========

    private void addToIndex(String sessionId, FileIndexEntry entry) {
        sessionFileIndex.computeIfAbsent(sessionId, k -> new ArrayList<>())
                .add(entry);

        // 限制每会话文件数
        List<FileIndexEntry> entries = sessionFileIndex.get(sessionId);
        while (entries.size() > MAX_FILES_PER_SESSION) {
            FileIndexEntry removed = entries.remove(0);
            // 清理最旧文件的临时文件
            try {
                fileParseService.cleanupTempFile(removed.getTempUrl());
            } catch (Exception e) {
                log.warn("[FileIndex] 清理旧文件失败: {}", removed.getTempUrl());
            }
            log.info("[FileIndex] 移除最旧文件: fileId={}", removed.getFileId());
        }
    }

    /**
     * 生成文件摘要
     */
    private String generateSummary(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        // 移除多余空白
        String cleaned = content.trim().replaceAll("\\s+", " ");

        if (cleaned.length() <= MAX_SUMMARY_LENGTH) {
            return cleaned;
        }

        return cleaned.substring(0, MAX_SUMMARY_LENGTH) + "...";
    }

    /**
     * 生成基本摘要（用于大文件）
     */
    private String generateBasicSummary(String fileName, String mimeType, Long fileSize) {
        StringBuilder sb = new StringBuilder();

        FileIndexEntry.FileType fileType = FileIndexEntry.determineFileType(mimeType, fileName);
        sb.append(fileType != null ? fileType.name() : "未知").append("类型文件");

        if (fileName != null) {
            sb.append("：").append(fileName);
        }

        return sb.toString();
    }

    /**
     * 计算行数
     */
    private int countLines(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return content.split("\n").length;
    }

    // ========== 内部类 ==========

    /**
     * 文件搜索结果
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class FileSearchResult {
        private String fileId;
        private String fileName;
        private List<SearchMatch> matches;
    }

    /**
     * 搜索匹配项
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SearchMatch {
        private int lineNumber;
        private String lineContent;
    }
}
