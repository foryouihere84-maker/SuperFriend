package com.superfriend.superfriend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SkillExecution {
    private Long id;
    private Long skillId;
    private String sessionId;
    private Long userId;
    private String userRequest;
    private String result;
    private Integer success;
    private Integer executionTime;
    private String errorMessage;
    private LocalDateTime createdTime;
}
