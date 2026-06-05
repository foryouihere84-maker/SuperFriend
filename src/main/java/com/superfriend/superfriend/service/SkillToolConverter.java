package com.superfriend.superfriend.service;

import com.superfriend.superfriend.agent.skill.Skill;
import com.superfriend.superfriend.dto.McpToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SkillToolConverter {

    private static final String SKILL_TOOL_PREFIX = "skill__";

    public List<McpToolDefinition> convertSkillsToToolDefinitions(List<Skill> skills) {
        List<McpToolDefinition> tools = new ArrayList<>();
        
        if (skills == null || skills.isEmpty()) {
            return tools;
        }
        
        for (Skill skill : skills) {
            try {
                McpToolDefinition tool = convertSkillToToolDefinition(skill);
                if (tool != null) {
                    tools.add(tool);
                    log.debug("转换 Skill 为工具: {} -> {}", skill.getName(), tool.getName());
                }
            } catch (Exception e) {
                log.warn("转换 Skill 失败: {} - {}", skill.getName(), e.getMessage());
            }
        }
        
        log.info("成功转换 {} 个 Skills 为工具定义", tools.size());
        return tools;
    }

    public McpToolDefinition convertSkillToToolDefinition(Skill skill) {
        if (skill == null || skill.getName() == null) {
            return null;
        }
        
        McpToolDefinition tool = new McpToolDefinition();
        tool.setName(SKILL_TOOL_PREFIX + skill.getName());
        tool.setServerName("skills");
        tool.setDescription(buildSkillDescription(skill));
        tool.setInputSchema(buildSkillInputSchema(skill));
        
        return tool;
    }

    private String buildSkillDescription(Skill skill) {
        StringBuilder desc = new StringBuilder();
        
        if (skill.getDescription() != null && !skill.getDescription().isEmpty()) {
            desc.append(skill.getDescription());
        } else {
            desc.append("Execute the '").append(skill.getName()).append("' skill.");
        }
        
        if (skill.getCategory() != null && !skill.getCategory().isEmpty()) {
            desc.append("\n\nCategory: ").append(skill.getCategory());
        }
        
        if (skill.getTags() != null && !skill.getTags().isEmpty()) {
            desc.append("\nTags: ").append(String.join(", ", skill.getTags()));
        }
        
        if (skill.getAllowedTools() != null && !skill.getAllowedTools().isEmpty()) {
            desc.append("\nUses tools: ").append(String.join(", ", skill.getAllowedTools()));
        }
        
        if (skill.getVersion() != null && !skill.getVersion().isEmpty()) {
            desc.append("\nVersion: ").append(skill.getVersion());
        }
        
        return desc.toString();
    }

    private McpToolDefinition.JsonSchema buildSkillInputSchema(Skill skill) {
        McpToolDefinition.JsonSchema schema = new McpToolDefinition.JsonSchema();
        schema.setType("object");
        
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        
        if (skill.getParameters() != null && !skill.getParameters().isEmpty()) {
            for (Map.Entry<String, Object> param : skill.getParameters().entrySet()) {
                String paramName = param.getKey();
                Object paramValue = param.getValue();
                
                Map<String, Object> paramDef = parseParameterDefinition(paramName, paramValue);
                properties.put(paramName, paramDef);
                
                if (isParameterRequired(paramValue)) {
                    required.add(paramName);
                }
            }
        }
        
        if (properties.isEmpty()) {
            Map<String, Object> emptyParam = new HashMap<>();
            emptyParam.put("type", "object");
            emptyParam.put("description", "No parameters required for this skill");
            properties.put("_placeholder", emptyParam);
        }
        
        schema.setProperties(properties);
        if (!required.isEmpty()) {
            schema.setRequired(required.toArray(new String[0]));
        }
        
        return schema;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParameterDefinition(String paramName, Object paramValue) {
        Map<String, Object> paramDef = new HashMap<>();
        
        if (paramValue instanceof Map) {
            Map<String, Object> paramMap = (Map<String, Object>) paramValue;
            
            if (paramMap.containsKey("type")) {
                paramDef.put("type", paramMap.get("type"));
            } else {
                paramDef.put("type", "string");
            }
            
            if (paramMap.containsKey("description")) {
                paramDef.put("description", paramMap.get("description"));
            } else {
                paramDef.put("description", "Parameter: " + paramName);
            }
            
            if (paramMap.containsKey("default")) {
                paramDef.put("default", paramMap.get("default"));
            }
            
            if (paramMap.containsKey("enum")) {
                paramDef.put("enum", paramMap.get("enum"));
            }
            
        } else if (paramValue instanceof String) {
            paramDef.put("type", "string");
            paramDef.put("description", paramValue.toString());
        } else {
            paramDef.put("type", "string");
            paramDef.put("description", "Parameter: " + paramName);
        }
        
        return paramDef;
    }

    @SuppressWarnings("unchecked")
    private boolean isParameterRequired(Object paramValue) {
        if (paramValue instanceof Map) {
            Map<String, Object> paramMap = (Map<String, Object>) paramValue;
            Object required = paramMap.get("required");
            if (required instanceof Boolean) {
                return (Boolean) required;
            }
        }
        return false;
    }

    public static boolean isSkillTool(String toolName) {
        return toolName != null && toolName.startsWith(SKILL_TOOL_PREFIX);
    }

    public static String extractSkillName(String toolName) {
        if (isSkillTool(toolName)) {
            return toolName.substring(SKILL_TOOL_PREFIX.length());
        }
        return toolName;
    }

    public static String getSkillToolPrefix() {
        return SKILL_TOOL_PREFIX;
    }
}
