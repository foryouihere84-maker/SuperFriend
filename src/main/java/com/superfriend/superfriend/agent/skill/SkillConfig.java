package com.superfriend.superfriend.agent.skill;

import lombok.Data;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

@Data
public class SkillConfig {
    private String name;
    private String description;
    private String license;
    private String compatibility;
    private Map<String, Object> metadata = new HashMap<>();
    private List<String> allowedTools = new ArrayList<>();
    private String instructions;
    private String category;
    private String version = "1.0.0";
    private String author;
    private List<String> tags = new ArrayList<>();
    private Map<String, Object> parameters = new HashMap<>();
    private SkillMetadata.SkillPriority priority = SkillMetadata.SkillPriority.MEDIUM;
    private long timeout = 60000L;
    private boolean hasScripts = false;
    private String mainScript;
    private List<Map<String, Object>> scripts = new ArrayList<>();
    private List<Map<String, Object>> resources = new ArrayList<>();

    public SkillConfig() {
    }

    public SkillConfig(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getMetadataString(String key) {
        Object value = metadata.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    public void setMetadataString(String key, String value) {
        metadata.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public List<String> getMetadataList(String key) {
        Object value = metadata.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return new ArrayList<>();
    }

    public boolean hasAllowedTools() {
        return allowedTools != null && !allowedTools.isEmpty();
    }

    public boolean isToolAllowed(String toolName) {
        if (!hasAllowedTools()) {
            return true;
        }
        for (String allowed : allowedTools) {
            if (allowed.equals(toolName) || allowed.startsWith(toolName + "(") || allowed.equals("*")) {
                return true;
            }
        }
        return false;
    }
}
