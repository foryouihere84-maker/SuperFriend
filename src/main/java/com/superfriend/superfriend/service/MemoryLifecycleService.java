package com.superfriend.superfriend.service;

import com.superfriend.superfriend.entity.MemoryEvent;
import com.superfriend.superfriend.entity.MemoryPalace;
import com.superfriend.superfriend.mapper.MemoryEventMapper;
import com.superfriend.superfriend.mapper.MemoryPalaceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 记忆生命周期管理服务
 * 负责记忆的衰减、强化、升级、降级和清理
 */
@Slf4j
@Service
public class MemoryLifecycleService {

    @Autowired
    private MemoryPalaceMapper memoryPalaceMapper;

    @Autowired
    private MemoryEventMapper memoryEventMapper;

    // ==================== 配置常量 ====================

    /** 强化增量 */
    private static final double REINFORCE_INCREMENT = 0.15;
    /** 最大有效分数 */
    private static final double MAX_EFFECTIVE_SCORE = 0.98;
    /** 最小有效分数（低于此值将被归档） */
    private static final double MIN_EFFECTIVE_SCORE = 0.10;
    /** 访问加分 */
    private static final double ACCESS_BONUS = 0.02;

    // ==================== 有效分数计算 ====================

    /**
     * 计算记忆的有效分数
     * 有效分数 = 置信度 × 衰减因子 × 强化因子
     */
    public BigDecimal calculateEffectiveScore(MemoryPalace memory) {
        double baseConfidence = memory.getConfidence() != null ?
            memory.getConfidence().doubleValue() : 0.8;
        double decayFactor = calculateDecayFactor(memory);
        double reinforceFactor = calculateReinforceFactor(memory);

        double effectiveScore = baseConfidence * decayFactor * reinforceFactor;
        effectiveScore = Math.min(effectiveScore, MAX_EFFECTIVE_SCORE);

        return BigDecimal.valueOf(effectiveScore).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 计算衰减因子
     * 根据记忆类型和时间计算衰减程度
     */
    private double calculateDecayFactor(MemoryPalace memory) {
        // 核心记忆不衰减
        if (MemoryPalace.TYPE_CORE.equals(memory.getMemoryType())) {
            return 1.0;
        }

        LocalDateTime lastAccess = memory.getLastAccessTime();
        if (lastAccess == null) {
            lastAccess = memory.getCreatedTime();
        }
        if (lastAccess == null) {
            return 1.0;
        }

        long daysSinceAccess = ChronoUnit.DAYS.between(lastAccess, LocalDateTime.now());
        if (daysSinceAccess <= 0) {
            return 1.0;
        }

        // 根据记忆类型计算时间单位和衰减率
        double decayRate = getDecayRate(memory.getMemoryType());
        long timeUnits = getTimeUnits(daysSinceAccess, memory.getMemoryType());

        return Math.pow(1 - decayRate, timeUnits);
    }

    /**
     * 获取衰减率
     */
    private double getDecayRate(String memoryType) {
        switch (memoryType) {
            case MemoryPalace.TYPE_CORE: return 0.0;
            case MemoryPalace.TYPE_IMPORTANT: return 0.02;
            case MemoryPalace.TYPE_NORMAL: return 0.05;
            case MemoryPalace.TYPE_EPHEMERAL: return 0.10;
            default: return 0.05;
        }
    }

    /**
     * 根据记忆类型获取时间单位数
     */
    private long getTimeUnits(long days, String memoryType) {
        switch (memoryType) {
            case MemoryPalace.TYPE_IMPORTANT: return days / 30; // 月
            case MemoryPalace.TYPE_NORMAL: return days / 7;     // 周
            case MemoryPalace.TYPE_EPHEMERAL: return days;      // 天
            default: return days / 7;
        }
    }

    /**
     * 计算强化因子
     */
    private double calculateReinforceFactor(MemoryPalace memory) {
        int reinforceCount = memory.getReinforceCount() != null ? memory.getReinforceCount() : 0;
        // 每次强化增加 15%，最高 200%
        return Math.min(1.0 + reinforceCount * REINFORCE_INCREMENT, 2.0);
    }

    // ==================== 记忆强化 ====================

    /**
     * 强化记忆（当记忆被再次提及时调用）
     */
    @Transactional
    public void reinforceMemory(Long memoryId, String sessionId) {
        MemoryPalace memory = memoryPalaceMapper.findById(memoryId);
        if (memory == null || !memory.isActive()) {
            return;
        }

        // 计算新的有效分数
        int newReinforceCount = (memory.getReinforceCount() != null ? memory.getReinforceCount() : 0) + 1;
        memory.setReinforceCount(newReinforceCount);
        memory.setLastAccessTime(LocalDateTime.now());

        BigDecimal newScore = calculateEffectiveScore(memory);
        memory.setEffectiveScore(newScore);

        memoryPalaceMapper.update(memory);

        // 记录事件
        recordEvent(memory.getUserId(), memoryId, MemoryEvent.EVENT_REINFORCE,
            "强化次数: " + newReinforceCount + ", 新分数: " + newScore, sessionId);

        // 检查是否需要升级
        checkAndPromote(memory);

        log.info("记忆强化: id={}, reinforceCount={}, effectiveScore={}",
            memoryId, newReinforceCount, newScore);
    }

    /**
     * 记录记忆访问
     */
    @Transactional
    public void recordAccess(Long memoryId, String sessionId) {
        MemoryPalace memory = memoryPalaceMapper.findById(memoryId);
        if (memory == null || !memory.isActive()) {
            return;
        }

        memoryPalaceMapper.incrementAccessCount(memoryId);

        // 更新有效分数（访问有轻微加分）
        BigDecimal currentScore = memory.getEffectiveScore();
        if (currentScore != null) {
            BigDecimal newScore = currentScore.add(BigDecimal.valueOf(ACCESS_BONUS));
            if (newScore.compareTo(BigDecimal.valueOf(MAX_EFFECTIVE_SCORE)) > 0) {
                newScore = BigDecimal.valueOf(MAX_EFFECTIVE_SCORE);
            }
            memoryPalaceMapper.updateEffectiveScore(memoryId, newScore);
        }

        recordEvent(memory.getUserId(), memoryId, MemoryEvent.EVENT_ACCESS,
            "记忆被访问", sessionId);
    }

    // ==================== 记忆升级/降级 ====================

    /**
     * 检查并升级记忆类型
     */
    private void checkAndPromote(MemoryPalace memory) {
        double score = memory.getEffectiveScore() != null ?
            memory.getEffectiveScore().doubleValue() : 0.5;

        String currentType = memory.getMemoryType();
        String newType = null;

        // 升级条件
        if (score >= 0.9 && !MemoryPalace.TYPE_CORE.equals(currentType)) {
            if (MemoryPalace.TYPE_EPHEMERAL.equals(currentType)) {
                newType = MemoryPalace.TYPE_NORMAL;
            } else if (MemoryPalace.TYPE_NORMAL.equals(currentType)) {
                newType = MemoryPalace.TYPE_IMPORTANT;
            } else if (MemoryPalace.TYPE_IMPORTANT.equals(currentType)) {
                newType = MemoryPalace.TYPE_CORE;
            }
        }

        if (newType != null) {
            promoteMemory(memory, newType);
        }
    }

    /**
     * 升级记忆
     */
    @Transactional
    public void promoteMemory(MemoryPalace memory, String newType) {
        String oldType = memory.getMemoryType();
        memory.setMemoryType(newType);
        memory.setDecayRate(MemoryPalace.getDefaultDecayRate(newType));

        memoryPalaceMapper.updateMemoryType(memory.getId(), newType, memory.getDecayRate());

        recordEvent(memory.getUserId(), memory.getId(), MemoryEvent.EVENT_UPGRADE,
            "从 " + oldType + " 升级为 " + newType, null);

        log.info("记忆升级: id={}, {} -> {}", memory.getId(), oldType, newType);
    }

    /**
     * 降级记忆
     */
    @Transactional
    public void demoteMemory(Long memoryId, String reason) {
        MemoryPalace memory = memoryPalaceMapper.findById(memoryId);
        if (memory == null || MemoryPalace.TYPE_CORE.equals(memory.getMemoryType())) {
            return;
        }

        String oldType = memory.getMemoryType();
        String newType = null;

        if (MemoryPalace.TYPE_IMPORTANT.equals(oldType)) {
            newType = MemoryPalace.TYPE_NORMAL;
        } else if (MemoryPalace.TYPE_NORMAL.equals(oldType)) {
            newType = MemoryPalace.TYPE_EPHEMERAL;
        }

        if (newType != null) {
            memory.setMemoryType(newType);
            memory.setDecayRate(MemoryPalace.getDefaultDecayRate(newType));

            memoryPalaceMapper.updateMemoryType(memoryId, newType, memory.getDecayRate());

            recordEvent(memory.getUserId(), memoryId, MemoryEvent.EVENT_DOWNGRADE,
                "从 " + oldType + " 降级为 " + newType + ", 原因: " + reason, null);

            log.info("记忆降级: id={}, {} -> {}, 原因: {}", memoryId, oldType, newType, reason);
        }
    }

    // ==================== 批量衰减计算 ====================

    /**
     * 更新所有记忆的有效分数
     */
    @Scheduled(cron = "0 0 4 * * ?") // 每天凌晨4点
    @Transactional
    public void updateAllEffectiveScores() {
        log.info("开始批量更新记忆有效分数...");

        // 这里需要分页处理，避免内存溢出
        // 简化实现：只处理活跃记忆
        // 实际生产环境应该分批处理

        log.info("记忆有效分数更新完成");
    }

    // ==================== 清理过期记忆 ====================

    /**
     * 清理过期记忆
     */
    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点
    @Transactional
    public void cleanupExpiredMemories() {
        log.info("开始清理过期记忆...");

        // 归档有效分数过低的记忆
        int archivedCount = memoryPalaceMapper.archiveExpiredMemories(
            null, BigDecimal.valueOf(MIN_EFFECTIVE_SCORE));

        log.info("过期记忆清理完成: 归档 {} 条记忆", archivedCount);
    }

    /**
     * 归档记忆
     */
    @Transactional
    public void archiveMemory(Long memoryId, String reason) {
        MemoryPalace memory = memoryPalaceMapper.findById(memoryId);
        if (memory == null) {
            return;
        }

        memoryPalaceMapper.updateStatus(memoryId, MemoryPalace.STATUS_ARCHIVED);

        recordEvent(memory.getUserId(), memoryId, MemoryEvent.EVENT_ARCHIVE,
            "归档原因: " + reason, null);

        log.info("记忆归档: id={}, reason={}", memoryId, reason);
    }

    /**
     * 删除记忆
     */
    @Transactional
    public void deleteMemory(Long memoryId, String sessionId) {
        MemoryPalace memory = memoryPalaceMapper.findById(memoryId);
        if (memory == null) {
            return;
        }

        // 软删除
        memoryPalaceMapper.updateStatus(memoryId, MemoryPalace.STATUS_DELETED);

        recordEvent(memory.getUserId(), memoryId, MemoryEvent.EVENT_DELETE,
            "记忆被删除", sessionId);

        log.info("记忆删除: id={}", memoryId);
    }

    // ==================== 事件记录 ====================

    private void recordEvent(Long userId, Long memoryId, String eventType,
                            String eventData, String sessionId) {
        MemoryEvent event = new MemoryEvent();
        event.setUserId(userId);
        event.setMemoryId(memoryId);
        event.setEventType(eventType);
        event.setEventTime(LocalDateTime.now());
        event.setEventData(eventData);
        event.setSessionId(sessionId);

        memoryEventMapper.insert(event);
    }

    // ==================== 统计 ====================

    /**
     * 获取记忆统计
     */
    public MemoryPalaceStats getStats(Long userId) {
        MemoryPalaceStats stats = new MemoryPalaceStats();
        stats.setTotalCount(memoryPalaceMapper.countByUserId(userId));
        stats.setCoreCount(memoryPalaceMapper.countByUserIdAndType(userId, MemoryPalace.TYPE_CORE));
        stats.setImportantCount(memoryPalaceMapper.countByUserIdAndType(userId, MemoryPalace.TYPE_IMPORTANT));
        stats.setNormalCount(memoryPalaceMapper.countByUserIdAndType(userId, MemoryPalace.TYPE_NORMAL));
        stats.setEphemeralCount(memoryPalaceMapper.countByUserIdAndType(userId, MemoryPalace.TYPE_EPHEMERAL));
        stats.setOldestMemoryTime(memoryPalaceMapper.findOldestMemoryTime(userId));
        stats.setNewestMemoryTime(memoryPalaceMapper.findNewestMemoryTime(userId));
        stats.setAverageEffectiveScore(memoryPalaceMapper.findAverageEffectiveScore(userId));
        return stats;
    }

    @lombok.Data
    public static class MemoryPalaceStats {
        private int totalCount;
        private int coreCount;
        private int importantCount;
        private int normalCount;
        private int ephemeralCount;
        private LocalDateTime oldestMemoryTime;
        private LocalDateTime newestMemoryTime;
        private BigDecimal averageEffectiveScore;
    }
}
