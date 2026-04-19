package com.superfriend.superfriend.service;

import com.superfriend.superfriend.config.KnowledgeExtractionProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 延迟知识提取队列服务
 *
 * 功能：
 * 1. 当用户关闭/切换对话时，将知识提取任务加入延迟队列
 * 2. 同一 sessionId 的新任务会覆盖旧任务（去重）
 * 3. 延迟执行，给用户返回对话的机会
 * 4. 手动调用时立即执行，不走延迟队列
 */
@Slf4j
@Service
public class DelayedExtractionQueueService {

    @Autowired
    @Lazy
    private KnowledgeExtractorService knowledgeExtractorService;

    @Autowired
    @Lazy
    private ContextMangerService contextMangerService;

    @Autowired
    private KnowledgeExtractionProperties properties;

    /**
     * 延迟任务存储：sessionId -> DelayedTask
     */
    private final ConcurrentHashMap<String, DelayedTask> delayedTasks = new ConcurrentHashMap<>();

    /**
     * 定时执行器
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * 延迟任务信息
     */
    @Data
    public static class DelayedTask {
        private final String sessionId;
        private final Long userId;
        private final String model;
        private final long createdAt;
        private final long executeAt;
        private volatile boolean cancelled = false;
        private java.util.concurrent.ScheduledFuture<?> future;

        public DelayedTask(String sessionId, Long userId, String model, int delaySeconds) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.model = model;
            this.createdAt = System.currentTimeMillis();
            this.executeAt = createdAt + (delaySeconds * 1000L);
        }

        public void cancel() {
            this.cancelled = true;
            if (future != null) {
                future.cancel(false);
            }
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= executeAt;
        }

        public long getRemainingDelayMs() {
            return Math.max(0, executeAt - System.currentTimeMillis());
        }
    }

    /**
     * 添加延迟知识提取任务
     * 如果已存在同一 sessionId 的任务，会取消旧任务并创建新任务
     *
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param model 模型名称
     */
    public void scheduleDelayedExtraction(String sessionId, Long userId, String model) {
        if (!properties.isEnabled()) {
            log.debug("知识提取已禁用，跳过延迟任务: sessionId={}", sessionId);
            return;
        }

        if (sessionId == null || sessionId.isEmpty()) {
            log.warn("sessionId 为空，跳过延迟任务");
            return;
        }

        int delaySeconds = properties.getDelaySeconds();

        // 取消已存在的任务
        cancelDelayedExtraction(sessionId);

        // 创建新任务
        DelayedTask task = new DelayedTask(sessionId, userId, model, delaySeconds);

        // 调度延迟执行
        java.util.concurrent.ScheduledFuture<?> future = scheduler.schedule(() -> {
            executeDelayedTask(task);
        }, delaySeconds, TimeUnit.SECONDS);

        task.setFuture(future);
        delayedTasks.put(sessionId, task);

        log.info("已安排延迟知识提取任务: sessionId={}, userId={}, delay={}秒, 队列大小={}",
                sessionId, userId, delaySeconds, delayedTasks.size());
    }

    /**
     * 取消延迟任务
     *
     * @param sessionId 会话ID
     * @return 是否成功取消（true=取消了一个任务，false=没有找到任务）
     */
    public boolean cancelDelayedExtraction(String sessionId) {
        DelayedTask task = delayedTasks.remove(sessionId);
        if (task != null) {
            task.cancel();
            log.info("已取消延迟知识提取任务: sessionId={}", sessionId);
            return true;
        }
        return false;
    }

    /**
     * 检查是否存在延迟任务
     */
    public boolean hasDelayedTask(String sessionId) {
        return delayedTasks.containsKey(sessionId);
    }

    /**
     * 获取延迟任务信息
     */
    public DelayedTask getDelayedTask(String sessionId) {
        return delayedTasks.get(sessionId);
    }

    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return delayedTasks.size();
    }

    /**
     * 立即执行知识提取（不走延迟队列）
     * 用于前端手动调用，无条件执行，忽略消息数量和文本长度限制
     * 前端直接传入对话内容，不从数据库获取
     *
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param model 模型名称
     * @param messages 对话消息列表（前端传入）
     * @return 执行结果：success/extracting/failed
     */
    public ExtractionResult executeImmediate(String sessionId, Long userId, String model,
                                              List<Map<String, Object>> messages) {
        log.info("[手动提取] 开始处理: sessionId={}, userId={}, model={}, messageCount={}",
            sessionId, userId, model, messages != null ? messages.size() : 0);

        // 先取消可能存在的延迟任务
        boolean hadDelayedTask = cancelDelayedExtraction(sessionId);
        if (hadDelayedTask) {
            log.info("[手动提取] 已取消该会话的延迟任务: sessionId={}", sessionId);
        }

        // 检查是否正在提取中（防止重复执行）
        if (knowledgeExtractorService.isProcessing(sessionId)) {
            log.warn("[手动提取] 知识提取正在进行中，跳过: sessionId={}", sessionId);
            return ExtractionResult.EXTRACTING;
        }

        // 验证消息列表
        if (messages == null || messages.isEmpty()) {
            log.warn("[手动提取] 消息列表为空，无法提取: sessionId={}", sessionId);
            return ExtractionResult.FAILED;
        }

        // 手动提取：清除已完成标记，允许重新提取
        knowledgeExtractorService.clearExtractionStatus(sessionId);
        log.info("[手动提取] 已清除提取状态，准备开始提取: sessionId={}", sessionId);

        // 记录消息详情
        int totalLength = 0;
        for (Map<String, Object> msg : messages) {
            String content = (String) msg.get("content");
            if (content != null) {
                totalLength += content.length();
            }
        }
        log.info("[手动提取] 对话统计: sessionId={}, 消息数={}, 总字符数={}",
            sessionId, messages.size(), totalLength);

        // 直接执行异步提取（无条件，使用前端传入的消息）
        knowledgeExtractorService.extractAsync(userId, sessionId, messages, model);

        log.info("[手动提取] 已触发异步提取任务: sessionId={}", sessionId);
        return ExtractionResult.SUCCESS;
    }

    /**
     * 获取提取状态
     */
    public ExtractionStatus getExtractionStatus(String sessionId) {
        if (knowledgeExtractorService.isProcessing(sessionId)) {
            return new ExtractionStatus("EXTRACTING", "正在提取中", true);
        }
        if (knowledgeExtractorService.isExtractionCompleted(sessionId)) {
            return new ExtractionStatus("COMPLETED", "提取已完成", false);
        }
        if (hasDelayedTask(sessionId)) {
            DelayedTask task = getDelayedTask(sessionId);
            int remainingSeconds = (int) (task.getRemainingDelayMs() / 1000);
            return new ExtractionStatus("PENDING", "等待提取中", false, remainingSeconds);
        }
        return new ExtractionStatus("NONE", "未提取", false);
    }

    /**
     * 提取结果枚举
     */
    public enum ExtractionResult {
        SUCCESS,      // 成功触发提取
        EXTRACTING,   // 正在提取中
        COMPLETED,    // 已完成提取
        FAILED        // 提取失败（无消息等）
    }

    /**
     * 提取状态
     */
    @Data
    public static class ExtractionStatus {
        private final String status;
        private final String message;
        private final boolean inProgress;
        private final Integer remainingSeconds;

        public ExtractionStatus(String status, String message, boolean inProgress) {
            this(status, message, inProgress, null);
        }

        public ExtractionStatus(String status, String message, boolean inProgress, Integer remainingSeconds) {
            this.status = status;
            this.message = message;
            this.inProgress = inProgress;
            this.remainingSeconds = remainingSeconds;
        }
    }

    /**
     * 执行延迟任务
     */
    private void executeDelayedTask(DelayedTask task) {
        if (task.isCancelled()) {
            log.debug("任务已取消，跳过执行: sessionId={}", task.getSessionId());
            return;
        }

        // 从队列中移除
        delayedTasks.remove(task.getSessionId());

        log.info("执行延迟知识提取任务: sessionId={}, userId={}, 等待时间={}秒",
                task.getSessionId(), task.getUserId(),
                (System.currentTimeMillis() - task.getCreatedAt()) / 1000);

        executeExtraction(task.getSessionId(), task.getUserId(), task.getModel());
    }

    /**
     * 执行知识提取（仅用于延迟队列，自动触发已禁用）
     * 延迟队列保留但实际只在 finalize 时使用
     * 正常的知识提取只通过手动触发 (executeImmediate)
     */
    private void executeExtraction(String sessionId, Long userId, String model) {
        if (userId == null || sessionId == null) {
            log.warn("参数无效，跳过知识提取: sessionId={}, userId={}", sessionId, userId);
            return;
        }

        try {
            // 检查是否正在提取中
            if (knowledgeExtractorService.isProcessing(sessionId)) {
                log.info("Session {} 正在提取中，跳过延迟提取", sessionId);
                return;
            }

            // 检查是否已完成提取
            if (knowledgeExtractorService.isExtractionCompleted(sessionId)) {
                log.info("Session {} 已完成知识提取，跳过延迟提取", sessionId);
                return;
            }

            // 获取对话消息
            List<Map<String, Object>> messages = contextMangerService.getMessagesForExtraction(sessionId);
            if (messages == null || messages.isEmpty()) {
                log.info("Session {} 没有消息，跳过知识提取", sessionId);
                return;
            }

            // 延迟队列也走无条件提取（不检查 shouldTriggerExtraction）
            log.info("[延迟提取] 执行提取: sessionId={}, messageCount={}", sessionId, messages.size());
            knowledgeExtractorService.extractAsync(userId, sessionId, messages, model);

        } catch (Exception e) {
            log.error("知识提取失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 定时清理过期的延迟任务（每5分钟检查一次）
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredTasks() {
        long now = System.currentTimeMillis();
        int cleaned = 0;

        for (Map.Entry<String, DelayedTask> entry : delayedTasks.entrySet()) {
            DelayedTask task = entry.getValue();
            // 如果任务创建超过 10 分钟仍未执行，可能是异常，清理掉
            if (now - task.getCreatedAt() > 600000) {
                task.cancel();
                delayedTasks.remove(entry.getKey());
                cleaned++;
                log.warn("清理过期延迟任务: sessionId={}", entry.getKey());
            }
        }

        if (cleaned > 0) {
            log.info("清理了 {} 个过期的延迟任务，当前队列大小={}", cleaned, delayedTasks.size());
        }
    }

    /**
     * 服务关闭时清理资源
     */
    @PreDestroy
    public void shutdown() {
        log.info("关闭延迟任务队列，当前队列大小={}", delayedTasks.size());
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
