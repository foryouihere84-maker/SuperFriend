package com.superfriend.superfriend.dto;

import lombok.Data;

@Data
public class TaskStepDTO {
    private String stepId;
    private Integer stepNumber;
    private String description;
    private String status;
    private String toolName;
    private String toolArguments;
    private String result;
    private String error;
    private Integer executionTimeMs;
}
