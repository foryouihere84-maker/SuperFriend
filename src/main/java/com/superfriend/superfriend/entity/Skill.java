package com.superfriend.superfriend.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class Skill {
    private Long id;
    private String name;
    private String description;
    private String category;
    private String version;
    private String author;
    private String license;
    private String compatibility;
    private Integer priority;
    private Integer scope;
    private String instructions;
    private Map<String, Object> parameters;
    private List<String> allowedTools;
    private List<String> tags;
    private Long timeout;
    private String scriptsJson;
    private String resourcesJson;
    private Integer status;
    private Boolean isSelected;
    private String skillFilePath;
    private Long executionCount;
    private Double successRate;
    private Integer averageExecutionTime;
    private LocalDateTime lastUsedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
