package com.superfriend.superfriend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 记忆事件日志实体
 */
@Data
public class MemoryEvent {

    // ==================== 事件类型 ====================

    public static final String EVENT_CREATE = "CREATE";
    public static final String EVENT_ACCESS = "ACCESS";
    public static final String EVENT_REINFORCE = "REINFORCE";
    public static final String EVENT_DECAY = "DECAY";
    public static final String EVENT_ARCHIVE = "ARCHIVE";
    public static final String EVENT_DELETE = "DELETE";
    public static final String EVENT_UPGRADE = "UPGRADE";
    public static final String EVENT_DOWNGRADE = "DOWNGRADE";

    // ==================== 字段 ====================

    private Long id;
    private Long userId;
    private Long memoryId;
    private String eventType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime eventTime;
    private String eventData;
    private String sessionId;
}
