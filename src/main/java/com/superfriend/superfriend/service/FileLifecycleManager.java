package com.superfriend.superfriend.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 文件生命周期管理器
 * 管理文件的注册、发送标记、过期检测和清理
 */
@Slf4j
@Service
public class FileLifecycleManager {

    /**
     * 文件注册表
     * Key: 文件路径
     * Value: 文件记录
     */
    private final Map<String, FileRecord> fileRegistry = new ConcurrentHashMap<>();

    /**
     * 默认 TTL（24小时）
     */
    private static final long DEFAULT_TTL_MS = 24 * 60 * 60 * 1000;

    /**
     * 文件记录
     */
    @Data
    public static class FileRecord {
        /** 文件路径 */
        private String filePath;
        /** 文件来源（如 "skill:minimax-docx" 或 "sandbox:session_xxx"） */
        private String source;
        /** 会话 ID */
        private String sessionId;
        /** 创建时间 */
        private long createdAt;
        /** 发送时间 */
        private long sentAt;
        /** 是否已发送 */
        private boolean sent;
        /** 文件大小（字节） */
        private long fileSize;
        /** 文件类型 */
        private String fileType;
    }

    /**
     * 注册文件
     *
     * @param filePath 文件路径
     * @param source   文件来源
     * @param sessionId 会话 ID
     * @return 文件记录
     */
    public FileRecord registerFile(String filePath, String source, String sessionId) {
        return registerFile(filePath, source, sessionId, null, null);
    }

    /**
     * 注册文件（带文件信息）
     *
     * @param filePath 文件路径
     * @param source   文件来源
     * @param sessionId 会话 ID
     * @param fileSize 文件大小
     * @param fileType 文件类型
     * @return 文件记录
     */
    public FileRecord registerFile(String filePath, String source, String sessionId,
                                   Long fileSize, String fileType) {
        FileRecord record = new FileRecord();
        record.setFilePath(filePath);
        record.setSource(source);
        record.setSessionId(sessionId);
        record.setCreatedAt(System.currentTimeMillis());
        record.setSent(false);

        // 获取文件信息
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                record.setFileSize(fileSize != null ? fileSize : Files.size(path));
                if (fileType == null) {
                    record.setFileType(detectFileType(filePath));
                } else {
                    record.setFileType(fileType);
                }
            }
        } catch (IOException e) {
            log.warn("无法获取文件信息: {} - {}", filePath, e.getMessage());
        }

        fileRegistry.put(filePath, record);
        log.info("注册文件: {} (source={}, size={}, type={})",
            filePath, source, record.getFileSize(), record.getFileType());

        return record;
    }

    /**
     * 标记文件为已发送
     *
     * @param filePath 文件路径
     */
    public void markAsSent(String filePath) {
        FileRecord record = fileRegistry.get(filePath);
        if (record != null) {
            record.setSent(true);
            record.setSentAt(System.currentTimeMillis());
            log.info("文件已发送: {} (TTL={}小时后清理)", filePath,
                DEFAULT_TTL_MS / (60 * 60 * 1000));
        } else {
            log.warn("尝试标记未注册的文件: {}", filePath);
        }
    }

    /**
     * 获取过期文件列表
     *
     * @return 过期文件记录列表
     */
    public List<FileRecord> getExpiredFiles() {
        return getExpiredFiles(DEFAULT_TTL_MS);
    }

    /**
     * 获取过期文件列表
     *
     * @param ttlMs TTL（毫秒）
     * @return 过期文件记录列表
     */
    public List<FileRecord> getExpiredFiles(long ttlMs) {
        long now = System.currentTimeMillis();
        return fileRegistry.values().stream()
            .filter(record -> {
                // 只清理已发送的文件
                if (!record.isSent()) {
                    return false;
                }
                // 检查是否超过 TTL
                return (now - record.getSentAt()) > ttlMs;
            })
            .collect(Collectors.toList());
    }

    /**
     * 清理过期文件
     *
     * @return 清理的文件数量
     */
    public int cleanupExpiredFiles() {
        return cleanupExpiredFiles(DEFAULT_TTL_MS);
    }

    /**
     * 清理过期文件
     *
     * @param ttlMs TTL（毫秒）
     * @return 清理的文件数量
     */
    public int cleanupExpiredFiles(long ttlMs) {
        List<FileRecord> expiredFiles = getExpiredFiles(ttlMs);
        int cleanedCount = 0;

        for (FileRecord record : expiredFiles) {
            try {
                Path path = Paths.get(record.getFilePath());
                if (Files.exists(path)) {
                    Files.delete(path);
                    log.info("已清理过期文件: {} (source={}, age={}小时)",
                        record.getFilePath(),
                        record.getSource(),
                        (System.currentTimeMillis() - record.getSentAt()) / (60 * 60 * 1000));
                }
                fileRegistry.remove(record.getFilePath());
                cleanedCount++;
            } catch (IOException e) {
                log.warn("清理文件失败: {} - {}", record.getFilePath(), e.getMessage());
            }
        }

        return cleanedCount;
    }

    /**
     * 获取文件记录
     *
     * @param filePath 文件路径
     * @return 文件记录，如果不存在返回 null
     */
    public FileRecord getFileRecord(String filePath) {
        return fileRegistry.get(filePath);
    }

    /**
     * 获取所有文件记录
     *
     * @return 文件记录列表
     */
    public List<FileRecord> getAllFileRecords() {
        return new ArrayList<>(fileRegistry.values());
    }

    /**
     * 获取指定会话的文件记录
     *
     * @param sessionId 会话 ID
     * @return 文件记录列表
     */
    public List<FileRecord> getSessionFiles(String sessionId) {
        return fileRegistry.values().stream()
            .filter(record -> sessionId != null && sessionId.equals(record.getSessionId()))
            .collect(Collectors.toList());
    }

    /**
     * 清理会话的所有文件
     *
     * @param sessionId 会话 ID
     * @return 清理的文件数量
     */
    public int cleanupSessionFiles(String sessionId) {
        List<FileRecord> sessionFiles = getSessionFiles(sessionId);
        int cleanedCount = 0;

        for (FileRecord record : sessionFiles) {
            try {
                Path path = Paths.get(record.getFilePath());
                if (Files.exists(path)) {
                    Files.delete(path);
                    log.info("已清理会话文件: {} (sessionId={})", record.getFilePath(), sessionId);
                }
                fileRegistry.remove(record.getFilePath());
                cleanedCount++;
            } catch (IOException e) {
                log.warn("清理会话文件失败: {} - {}", record.getFilePath(), e.getMessage());
            }
        }

        return cleanedCount;
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        long totalFiles = fileRegistry.size();
        long sentFiles = fileRegistry.values().stream().filter(FileRecord::isSent).count();
        long totalSize = 0L;
        for (FileRecord r : fileRegistry.values()) {
            totalSize += r.getFileSize();
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFiles", totalFiles);
        stats.put("sentFiles", sentFiles);
        stats.put("unsentFiles", totalFiles - sentFiles);
        stats.put("totalSizeBytes", totalSize);
        stats.put("totalSizeMB", totalSize / (1024 * 1024));
        return stats;
    }

    /**
     * 检测文件类型
     */
    private String detectFileType(String filePath) {
        String fileName = filePath.toLowerCase();
        if (fileName.endsWith(".docx")) return "document";
        if (fileName.endsWith(".pdf")) return "pdf";
        if (fileName.endsWith(".xlsx")) return "spreadsheet";
        if (fileName.endsWith(".pptx")) return "presentation";
        if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image";
        if (fileName.endsWith(".mp4") || fileName.endsWith(".avi") || fileName.endsWith(".mov")) return "video";
        if (fileName.endsWith(".mp3") || fileName.endsWith(".wav")) return "audio";
        if (fileName.endsWith(".zip") || fileName.endsWith(".rar") || fileName.endsWith(".7z")) return "archive";
        if (fileName.endsWith(".json")) return "json";
        if (fileName.endsWith(".txt") || fileName.endsWith(".md")) return "text";
        return "unknown";
    }

    /**
     * 定时清理过期文件（每小时执行）
     * 清理已发送且超过 TTL 的文件
     */
    @Scheduled(cron = "0 0 * * * *")
    public void scheduledCleanup() {
        log.debug("开始定时清理过期文件...");
        int cleaned = cleanupExpiredFiles();
        if (cleaned > 0) {
            log.info("定时清理完成，共清理 {} 个过期文件", cleaned);
        }
    }

    /**
     * 应用关闭时清理
     */
    @PreDestroy
    public void onShutdown() {
        log.info("应用关闭，清理已发送的过期文件...");
        int cleaned = cleanupExpiredFiles();
        log.info("清理完成，共清理 {} 个文件", cleaned);
    }
}
