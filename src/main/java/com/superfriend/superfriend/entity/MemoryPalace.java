package com.superfriend.superfriend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 记忆宫殿实体
 * 宫殿式记忆系统的核心数据结构
 */
@Data
public class MemoryPalace {

    // ==================== 记忆类型 ====================

    /** 核心记忆 - 永久保留，不衰减 */
    public static final String TYPE_CORE = "CORE";
    /** 重要记忆 - 长期保留，缓慢衰减 */
    public static final String TYPE_IMPORTANT = "IMPORTANT";
    /** 普通记忆 - 中期保留，正常衰减 */
    public static final String TYPE_NORMAL = "NORMAL";
    /** 临时记忆 - 短期保留，快速衰减 */
    public static final String TYPE_EPHEMERAL = "EPHEMERAL";

    // ==================== 分类 ====================

    /** 偏好 */
    public static final String CATEGORY_PREFERENCE = "PREFERENCE";
    /** 技能 */
    public static final String CATEGORY_SKILL = "SKILL";
    /** 事实 */
    public static final String CATEGORY_FACT = "FACT";
    /** 经验 */
    public static final String CATEGORY_EXPERIENCE = "EXPERIENCE";
    /** 关系 */
    public static final String CATEGORY_RELATION = "RELATION";
    /** 决策 */
    public static final String CATEGORY_DECISION = "DECISION";

    // ==================== 状态 ====================

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";
    public static final String STATUS_DELETED = "DELETED";

    // ==================== 字段 ====================

    private Long id;
    private Long userId;

    /** 记忆类型 */
    private String memoryType;
    /** 分类 */
    private String category;
    /** 记忆标题 */
    private String title;
    /** 记忆内容 */
    private String content;
    /** 关键词 */
    private String keywords;

    /** 记忆发生时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime memoryTime;
    /** 来源会话ID */
    private String sessionId;

    /** 重要性 1-10 */
    private Integer importance;
    /** 置信度 */
    private BigDecimal confidence;
    /** 访问次数 */
    private Integer accessCount;
    /** 强化次数 */
    private Integer reinforceCount;

    /** 衰减率 */
    private BigDecimal decayRate;
    /** 最后访问时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastAccessTime;
    /** 有效分数 */
    private BigDecimal effectiveScore;

    /** 关联的记忆ID列表(JSON) */
    private String relatedMemoryIds;
    /** 关联的知识图谱节点ID */
    private Long sourceNodeId;

    /** 情感标签 */
    private String emotionTag;
    /** 上下文标签(JSON) */
    private String contextTags;
    /** 原始对话文本 */
    private String sourceText;

    /** 状态 */
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedTime;

    // ==================== 辅助方法 ====================

    /**
     * 获取默认衰减率
     */
    public static BigDecimal getDefaultDecayRate(String memoryType) {
        switch (memoryType) {
            case TYPE_CORE: return BigDecimal.ZERO;
            case TYPE_IMPORTANT: return new BigDecimal("0.0200");
            case TYPE_NORMAL: return new BigDecimal("0.0500");
            case TYPE_EPHEMERAL: return new BigDecimal("0.1000");
            default: return new BigDecimal("0.0500");
        }
    }

    /**
     * 判断是否为核心记忆
     */
    public boolean isCore() {
        return TYPE_CORE.equals(memoryType);
    }

    /**
     * 判断是否为活跃状态
     */
    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }
}
