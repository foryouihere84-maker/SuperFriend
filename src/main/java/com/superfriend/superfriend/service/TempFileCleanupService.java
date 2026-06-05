package com.superfriend.superfriend.service;

import com.superfriend.superfriend.config.FilePathConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 临时文件清理服务
 * 定时清理过期的临时文件，包括：
 * 1. 已发送超过 TTL 的文件
 * 2. uploads/temp 目录中的过期文件
 * 3. traces 目录中的过期文件
 * 4. 沙箱导出目录中的过期文件
 */
@Slf4j
@Service
public class TempFileCleanupService {

    private final FilePathConfig filePathConfig;
    private final FileLifecycleManager fileLifecycleManager;

    /**
     * 清理统计
     */
    private final AtomicInteger totalCleanedFiles = new AtomicInteger(0);
    private final AtomicInteger totalCleanedBytes = new AtomicInteger(0);

    @Autowired
    public TempFileCleanupService(FilePathConfig filePathConfig,
                                   FileLifecycleManager fileLifecycleManager) {
        this.filePathConfig = filePathConfig;
        this.fileLifecycleManager = fileLifecycleManager;
    }

    /**
     * 定时清理任务 - 每小时执行
     */
    @Scheduled(cron = "0 0 * * * *")
    public void scheduledCleanup() {
        log.info("开始定时清理过期临时文件...");
        CleanupResult result = cleanupExpiredFiles();
        log.info("定时清理完成: 清理 {} 个文件，释放 {} MB", result.filesCleaned, result.bytesFreed / (1024 * 1024));
    }

    /**
     * 清理过期文件
     */
    public CleanupResult cleanupExpiredFiles() {
        CleanupResult result = new CleanupResult();

        // 1. 清理已发送超过 TTL 的文件（通过 FileLifecycleManager）
        int registeredCleaned = fileLifecycleManager.cleanupExpiredFiles();
        result.filesCleaned += registeredCleaned;
        log.info("清理已注册文件: {} 个", registeredCleaned);

        // 2. 清理 uploads/temp 目录
        CleanupResult tempResult = cleanupDirectory(
            Paths.get(filePathConfig.getTempDir()),
            filePathConfig.getTempFileTtlMs(),
            "uploads/temp"
        );
        result.filesCleaned += tempResult.filesCleaned;
        result.bytesFreed += tempResult.bytesFreed;

        // 3. 清理 traces 目录
        CleanupResult traceResult = cleanupDirectory(
            Paths.get(filePathConfig.getTraceDir()),
            filePathConfig.getTempFileTtlMs(),
            "traces"
        );
        result.filesCleaned += traceResult.filesCleaned;
        result.bytesFreed += traceResult.bytesFreed;

        // 4. 清理沙箱导出目录
        CleanupResult exportResult = cleanupDirectory(
            Paths.get(filePathConfig.getExportDir()),
            filePathConfig.getTempFileTtlMs(),
            "exports"
        );
        result.filesCleaned += exportResult.filesCleaned;
        result.bytesFreed += exportResult.bytesFreed;

        // 更新统计
        totalCleanedFiles.addAndGet(result.filesCleaned);
        totalCleanedBytes.addAndGet((int) result.bytesFreed);

        return result;
    }

    /**
     * 清理指定目录中的过期文件
     *
     * @param directory 目录路径
     * @param ttlMs     TTL（毫秒）
     * @param label     标签（用于日志）
     */
    private CleanupResult cleanupDirectory(Path directory, long ttlMs, String label) {
        CleanupResult result = new CleanupResult();

        if (!Files.exists(directory)) {
            log.debug("目录不存在，跳过清理: {}", directory);
            return result;
        }

        try {
            Instant expiryTime = Instant.now().minusMillis(ttlMs);

            Files.walk(directory)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                        Instant lastModified = attrs.lastModifiedTime().toInstant();

                        if (lastModified.isBefore(expiryTime)) {
                            long fileSize = attrs.size();
                            Files.delete(file);
                            result.filesCleaned++;
                            result.bytesFreed += fileSize;
                            log.debug("清理过期文件: {} ({} 小时前修改)",
                                file, ChronoUnit.HOURS.between(lastModified, Instant.now()));
                        }
                    } catch (IOException e) {
                        log.warn("清理文件失败: {} - {}", file, e.getMessage());
                    }
                });

            // 清理空目录
            Files.walk(directory)
                .filter(Files::isDirectory)
                .filter(dir -> !dir.equals(directory))
                .forEach(dir -> {
                    try {
                        if (!Files.list(dir).findAny().isPresent()) {
                            Files.delete(dir);
                            log.debug("清理空目录: {}", dir);
                        }
                    } catch (IOException e) {
                        // 忽略
                    }
                });

            if (result.filesCleaned > 0) {
                log.info("清理 {} 目录: {} 个文件，{} MB",
                    label, result.filesCleaned, result.bytesFreed / (1024 * 1024));
            }

        } catch (IOException e) {
            log.error("清理目录失败: {} - {}", directory, e.getMessage());
        }

        return result;
    }

    /**
     * 清理指定会话的所有文件
     *
     * @param sessionId 会话 ID
     */
    public int cleanupSessionFiles(String sessionId) {
        return fileLifecycleManager.cleanupSessionFiles(sessionId);
    }

    /**
     * 获取清理统计
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = fileLifecycleManager.getStatistics();
        stats.put("totalCleanedFiles", totalCleanedFiles.get());
        stats.put("totalCleanedBytes", totalCleanedBytes.get());
        stats.put("totalCleanedMB", totalCleanedBytes.get() / (1024 * 1024));
        return stats;
    }

    /**
     * 应用关闭时清理
     */
    @PreDestroy
    public void onShutdown() {
        log.info("应用关闭，执行最终清理...");
        CleanupResult result = cleanupExpiredFiles();
        log.info("最终清理完成: 清理 {} 个文件，释放 {} MB",
            result.filesCleaned, result.bytesFreed / (1024 * 1024));
    }

    /**
     * 清理结果
     */
    public static class CleanupResult {
        public int filesCleaned = 0;
        public long bytesFreed = 0;
    }
}
