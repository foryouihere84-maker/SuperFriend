package com.superfriend.superfriend.dto;

import lombok.Data;
import java.util.List;

@Data
public class TaskPlanDTO {
    private String planId;
    private String sessionId;
    private Long userId;
    private String originalRequest;
    private String planSummary;
    private String status;
    private Integer totalSteps;
    private Integer completedSteps;
    private List<TaskStepDTO> steps;
}
