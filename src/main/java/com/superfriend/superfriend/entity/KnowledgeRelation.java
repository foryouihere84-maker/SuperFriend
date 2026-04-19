package com.superfriend.superfriend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知识关系实体
 * 表示两个知识节点之间的关系
 *
 * 关系类型现已支持自由定义，使用简洁的中文描述（如：使用、依赖、包含等），最多10个字符
 * 以下常量保留用于兼容历史数据
 */
@Data
public class KnowledgeRelation {

    /**
     * 旧关系类型常量（保留用于兼容历史数据）
     * @deprecated 新关系请使用中文描述
     */
    @Deprecated
    public static final String TYPE_USES = "USES";           // 使用关系
    @Deprecated
    public static final String TYPE_PREFERS = "PREFERS";     // 偏好关系
    @Deprecated
    public static final String TYPE_DEPENDS_ON = "DEPENDS_ON"; // 依赖关系
    @Deprecated
    public static final String TYPE_SOLVES = "SOLVES";       // 解决关系
    @Deprecated
    public static final String TYPE_RELATED_TO = "RELATED_TO"; // 相关关系
    @Deprecated
    public static final String TYPE_PART_OF = "PART_OF";     // 组成关系
    @Deprecated
    public static final String TYPE_CONTRADICTS = "CONTRADICTS"; // 矛盾关系

    private Long id;
    private Long userId;
    private Long sourceNodeId;
    private Long targetNodeId;
    private String relationType;
    private String properties;  // JSON格式
    private BigDecimal weight;
    private String sourceSessionId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;

    // 关联查询字段
    private KnowledgeNode sourceNode;
    private KnowledgeNode targetNode;
}
