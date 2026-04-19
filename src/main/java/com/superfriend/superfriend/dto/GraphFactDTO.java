package com.superfriend.superfriend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 图谱事实 DTO
 * 用于表示从对话中提取的事实信息
 */
@Data
public class GraphFactDTO {

    /**
     * 事实类型: INTEREST/TRAIT/PREFERENCE/SKILL
     */
    private String factType;

    /**
     * 事实键（名称）
     */
    private String factKey;

    /**
     * 事实值
     */
    private String value;

    /**
     * 置信度 0-1
     */
    private BigDecimal confidence;

    /**
     * 来源会话ID
     */
    private String sourceSessionId;

    /**
     * 上下文标签
     */
    private List<String> contexts;

    /**
     * 分类
     */
    private String category;

    /**
     * 级别（用于技能）
     */
    private String level;

    /**
     * 提及次数
     */
    private Integer mentionCount;
}
