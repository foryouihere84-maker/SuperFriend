package com.superfriend.superfriend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 记忆关联实体
 */
@Data
public class MemoryConnection {

    // ==================== 关联类型 ====================

    public static final String TYPE_RELATED = "RELATED";
    public static final String TYPE_CAUSED_BY = "CAUSED_BY";
    public static final String TYPE_LED_TO = "LED_TO";
    public static final String TYPE_SIMILAR = "SIMILAR";
    public static final String TYPE_CONTRADICTS = "CONTRADICTS";

    // ==================== 字段 ====================

    private Long id;
    private Long userId;
    private Long sourceMemoryId;
    private Long targetMemoryId;
    private String connectionType;
    private BigDecimal strength;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;
}
