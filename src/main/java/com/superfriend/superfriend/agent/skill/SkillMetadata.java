package com.superfriend.superfriend.agent.skill;

import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Data
public class SkillMetadata {
    private String name;
    private String description;
    private String license;
    private String compatibility;
    private Map<String, Object> metadata = new HashMap<>();
    private List<String> allowedTools;
    private String category;
    private String version;
    private String author;
    private List<String> tags;
    private Map<String, Object> parameters;
    private String skillFilePath;
    private SkillPriority priority;
    private SkillScope scope;
    private long executionCount;
    private double successRate;
    private long averageExecutionTime;
    private long lastUsedTime;

    public enum SkillPriority {
        LOW(1),
        MEDIUM(5),
        HIGH(8),
        CRITICAL(10);

        private final int value;

        SkillPriority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum SkillScope {
        SYSTEM,
        USER,
        PROJECT
    }

    public SkillMetadata() {
        this.executionCount = 0;
        this.successRate = 1.0;
        this.averageExecutionTime = 0;
        this.lastUsedTime = System.currentTimeMillis();
    }

    public void recordExecution(boolean success, long executionTime) {
        executionCount++;
        if (!success) {
            successRate = (successRate * (executionCount - 1)) / executionCount;
        }
        averageExecutionTime = (averageExecutionTime * (executionCount - 1) + executionTime) / executionCount;
        lastUsedTime = System.currentTimeMillis();
    }

    public String getMetadataString(String key) {
        if (metadata == null) return null;
        Object value = metadata.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    @SuppressWarnings("unchecked")
    public List<String> getMetadataList(String key) {
        if (metadata == null) return new java.util.ArrayList<>();
        Object value = metadata.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return new java.util.ArrayList<>();
    }

    public static SkillMetadata fromConfig(SkillConfig config) {
        SkillMetadata metadata = new SkillMetadata();
        metadata.setName(config.getName());
        metadata.setDescription(config.getDescription());
        metadata.setLicense(config.getLicense());
        metadata.setCompatibility(config.getCompatibility());
        metadata.setMetadata(config.getMetadata());
        metadata.setAllowedTools(config.getAllowedTools());
        metadata.setCategory(config.getCategory());
        metadata.setVersion(config.getVersion());
        metadata.setAuthor(config.getAuthor());
        metadata.setTags(config.getTags());
        metadata.setParameters(config.getParameters());
        metadata.setPriority(config.getPriority());
        return metadata;
    }
}
