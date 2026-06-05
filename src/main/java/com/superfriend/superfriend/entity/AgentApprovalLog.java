package com.superfriend.superfriend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AgentApprovalLog {
    private Long id;
    private Long userId;
    private String sessionId;
    private String toolName;
    private String operation;
    private String arguments;
    private String permissionLevel;
    private String decision;
    private String decidedBy;
    private String reason;
    private Integer responseTimeMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;
}
