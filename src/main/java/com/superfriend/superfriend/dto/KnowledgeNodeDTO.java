package com.superfriend.superfriend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知识节点DTO
 */
@Data
public class KnowledgeNodeDTO {
    private Long id;
    private Long userId;
    private String nodeType;
    private String name;
    private String description;        // 简短描述
    private String avatar;             // 头像URL或emoji
    private String image;              // 图片URL，点击头像展示
    private String detailedDescription; // 详细描述，点击头像展示
    private String keywords;           // 关键词标签
    private Integer importance;        // 重要性 1-10
    private String properties;
    private String sourceSessionId;
    private BigDecimal confidence;
    private Integer accessCount;
    private LocalDateTime lastAccessedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    /**
     * 节点类型的中文名称
     */
    public String getNodeTypeName() {
        if (nodeType == null) return "其他";
        switch (nodeType) {
            case "USER": return "用户";
            case "PROJECT": return "项目";
            case "TECHNOLOGY": return "技术栈";
            case "CONCEPT": return "概念";
            case "TASK": return "任务";
            case "ERROR": return "错误";
            case "SOLUTION": return "解决方案";
            case "PREFERENCE": return "偏好";
            default: return "其他";
        }
    }

    /**
     * 获取默认头像（根据节点类型）
     */
    public String getDefaultAvatar() {
        if (nodeType == null) return "📌";
        switch (nodeType) {
            case "USER": return "👤";
            case "PROJECT": return "📁";
            case "TECHNOLOGY": return "⚙️";
            case "CONCEPT": return "💡";
            case "TASK": return "📋";
            case "ERROR": return "❌";
            case "SOLUTION": return "✅";
            case "PREFERENCE": return "⭐";
            default: return "📌";
        }
    }

    /**
     * 获取有效头像（自定义头像或默认头像）
     */
    public String getEffectiveAvatar() {
        return avatar != null && !avatar.isEmpty() ? avatar : getDefaultAvatar();
    }
}
