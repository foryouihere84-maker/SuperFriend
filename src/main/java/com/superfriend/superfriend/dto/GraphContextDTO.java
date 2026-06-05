package com.superfriend.superfriend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 图谱上下文DTO
 * 用于返回完整的图谱数据给前端可视化
 */
@Data
public class GraphContextDTO {

    /**
     * 节点列表
     */
    private List<NodeDTO> nodes;

    /**
     * 关系列表
     */
    private List<RelationDTO> relations;

    /**
     * 生成的文本上下文（用于注入LLM）
     */
    private String textContext;

    /**
     * 统计信息
     */
    private GraphStats stats;

    @Data
    public static class NodeDTO {
        private Long id;
        private String type;
        private String typeName;
        private String name;
        private String description;         // 简短描述
        private String avatar;              // 头像URL或emoji
        private String image;               // 图片URL，点击头像展示
        private String detailedDescription; // 详细描述，点击头像展示
        private String keywords;            // 关键词标签
        private Integer importance;         // 重要性 1-10
        private String properties;
        private BigDecimal confidence;
        private Integer accessCount;

        /**
         * 获取默认头像（根据节点类型）
         */
        public String getDefaultAvatar() {
            if (type == null) return "📌";
            switch (type) {
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

    @Data
    public static class RelationDTO {
        private Long id;
        private Long sourceId;
        private Long targetId;
        private String type;
        private String typeName;
        private BigDecimal weight;
        private String sourceName;
        private String targetName;
    }

    @Data
    public static class GraphStats {
        private int totalNodes;
        private int totalRelations;
        private int nodeTypeCount;
        private int relationTypeCount;
    }

    /**
     * 关系类型的中文名称
     * 支持动态关系类型：如果已经是中文则直接返回
     * 保留旧类型映射以兼容历史数据
     */
    public static String getRelationTypeName(String relationType) {
        if (relationType == null || relationType.trim().isEmpty()) {
            return "相关";
        }

        // 检查是否包含中文字符，如果是则直接返回
        if (relationType.matches(".*[\\u4e00-\\u9fa5].*")) {
            return relationType;
        }

        // 旧类型映射（兼容历史数据）
        switch (relationType) {
            case "USES": return "使用";
            case "PREFERS": return "偏好";
            case "DEPENDS_ON": return "依赖";
            case "SOLVES": return "解决";
            case "RELATED_TO": return "相关";
            case "PART_OF": return "组成";
            case "CONTRADICTS": return "矛盾";
            default: return relationType; // 返回原值，避免丢失信息
        }
    }
}
