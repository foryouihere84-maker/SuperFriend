package com.superfriend.superfriend.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class SkillDTO {
    private String name;
    private String description;
    private String category;
    private String version;
    private String author;
    private List<String> tags;
    private List<String> allowedTools;
    private String instructions;
    private Map<String, Object> parameters;
    private String priority;
    private String scope;
    private long executionCount;
    private double successRate;
    private long averageExecutionTime;
    private Boolean isSelected;
    private List<Map<String, Object>> scripts;
    private List<Map<String, Object>> resources;
}
