package com.superfriend.superfriend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知识节点实体
 * 表示用户知识图谱中的一个实体节点
 */
@Data
public class KnowledgeNode {

    /**
     * 节点类型枚举
     */
    public static final String TYPE_USER = "USER";
    public static final String TYPE_PROJECT = "PROJECT";
    public static final String TYPE_TECHNOLOGY = "TECHNOLOGY";
    public static final String TYPE_CONCEPT = "CONCEPT";
    public static final String TYPE_TASK = "TASK";
    public static final String TYPE_ERROR = "ERROR";
    public static final String TYPE_SOLUTION = "SOLUTION";
    public static final String TYPE_PREFERENCE = "PREFERENCE";
    public static final String TYPE_CHARACTER = "CHARACTER";  // 角色/人物
    public static final String TYPE_WORK = "WORK";            // 作品（小说、游戏、电影等）

    /**
     * 图谱范围枚举
     */
    public static final String SCOPE_CONVERSATION = "CONVERSATION";  // 对话级图谱
    public static final String SCOPE_GLOBAL = "GLOBAL";              // 全局图谱

    private Long id;
    private Long userId;
    private String nodeType;

    /**
     * 图谱范围: CONVERSATION（对话级）/ GLOBAL（全局）
     * 默认为 GLOBAL
     */
    private String scope;
    private String name;
    private String description;  // 简短描述
    private String avatar;       // 头像URL或emoji，支持自定义
    private String image;        // 图片URL，点击头像展示
    private String detailedDescription;  // 详细描述，点击头像展示
    private String keywords;     // 关键词标签（逗号分隔）
    private Integer importance;  // 重要性 1-10
    private String properties;   // JSON格式
    private String sourceSessionId;
    private BigDecimal confidence;
    private Integer accessCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastAccessedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedTime;
}
