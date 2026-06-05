package com.superfriend.superfriend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AgentTaskStep {
    private Long id;
    private String stepId;
    private String planId;
    private Integer stepNumber;
    private String description;
    private String status;
    private String toolName;
    private String toolArguments;
    private String result;
    private String error;
    private Integer executionTimeMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime completedTime;
}
