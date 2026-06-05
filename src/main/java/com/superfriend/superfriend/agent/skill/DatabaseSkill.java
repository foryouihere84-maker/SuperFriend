package com.superfriend.superfriend.agent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.entity.SkillScript;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Slf4j
public class DatabaseSkill implements Skill {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillConfig config;
    private final SkillMetadata metadata;
    private final Long skillId;
    private final List<SkillScript> scripts;

    public DatabaseSkill(com.superfriend.superfriend.entity.Skill entity) {
        this.skillId = entity.getId();
        this.scripts = parseScriptsFromJson(entity.getScriptsJson());
        this.config = createConfig(entity);
        this.metadata = createMetadata(entity);
        
        if (!this.scripts.isEmpty()) {
            this.config.setHasScripts(true);
            for (SkillScript script : this.scripts) {
                if (script.getIsMain() != null && script.getIsMain() == 1) {
                    this.config.setMainScript(script.getScriptName());
                    break;
                }
            }
        }
    }

    public DatabaseSkill(com.superfriend.superfriend.entity.Skill entity, List<SkillScript> scripts) {
        this.skillId = entity.getId();
        this.scripts = scripts != null ? scripts : parseScriptsFromJson(entity.getScriptsJson());
        this.config = createConfig(entity);
        this.metadata = createMetadata(entity);
        
        if (!this.scripts.isEmpty()) {
            this.config.setHasScripts(true);
            for (SkillScript script : this.scripts) {
                if (script.getIsMain() != null && script.getIsMain() == 1) {
                    this.config.setMainScript(script.getScriptName());
                    break;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<SkillScript> parseScriptsFromJson(String scriptsJson) {
        List<SkillScript> result = new ArrayList<>();
        if (scriptsJson == null || scriptsJson.isEmpty()) {
            return result;
        }
        
        try {
            List<Map<String, Object>> scriptsList = objectMapper.readValue(scriptsJson, List.class);
            for (Map<String, Object> scriptMap : scriptsList) {
                SkillScript script = new SkillScript();
                script.setScriptType((String) scriptMap.get("scriptType"));
                script.setScriptName((String) scriptMap.get("scriptName"));
                script.setScriptContent((String) scriptMap.get("scriptContent"));
                if (scriptMap.get("isMain") != null) {
                    script.setIsMain(((Boolean) scriptMap.get("isMain")) ? 1 : 0);
                }
                if (scriptMap.get("executionOrder") != null) {
                    script.setExecutionOrder(((Number) scriptMap.get("executionOrder")).intValue());
                }
                result.add(script);
            }
            log.debug("从 scripts_json 解析出 {} 个脚本", result.size());
        } catch (Exception e) {
            log.warn("解析 scripts_json 失败: {}", e.getMessage());
        }
        return result;
    }

    private SkillConfig createConfig(com.superfriend.superfriend.entity.Skill entity) {
        SkillConfig config = new SkillConfig();
        config.setName(entity.getName());
        config.setDescription(entity.getDescription());
        config.setCategory(entity.getCategory());
        config.setVersion(entity.getVersion() != null ? entity.getVersion() : "1.0.0");
        config.setAuthor(entity.getAuthor());
        config.setTags(entity.getTags() != null ? entity.getTags() : new java.util.ArrayList<>());
        config.setAllowedTools(entity.getAllowedTools() != null ? entity.getAllowedTools() : new java.util.ArrayList<>());
        config.setInstructions(entity.getInstructions());
        config.setParameters(entity.getParameters() != null ? entity.getParameters() : new java.util.HashMap<>());
        config.setHasScripts(false);
        
        if (entity.getPriority() != null) {
            switch (entity.getPriority()) {
                case 1: config.setPriority(SkillMetadata.SkillPriority.LOW); break;
                case 5: config.setPriority(SkillMetadata.SkillPriority.MEDIUM); break;
                case 8: config.setPriority(SkillMetadata.SkillPriority.HIGH); break;
                case 10: config.setPriority(SkillMetadata.SkillPriority.CRITICAL); break;
                default: config.setPriority(SkillMetadata.SkillPriority.MEDIUM);
            }
        }
        
        return config;
    }

    private SkillMetadata createMetadata(com.superfriend.superfriend.entity.Skill entity) {
        SkillMetadata meta = new SkillMetadata();
        meta.setName(entity.getName());
        meta.setDescription(entity.getDescription());
        meta.setCategory(entity.getCategory());
        meta.setVersion(entity.getVersion() != null ? entity.getVersion() : "1.0.0");
        meta.setAuthor(entity.getAuthor());
        meta.setTags(entity.getTags() != null ? entity.getTags() : new java.util.ArrayList<>());
        meta.setAllowedTools(entity.getAllowedTools() != null ? entity.getAllowedTools() : new java.util.ArrayList<>());
        meta.setParameters(entity.getParameters() != null ? entity.getParameters() : new java.util.HashMap<>());
        
        if (entity.getPriority() != null) {
            switch (entity.getPriority()) {
                case 1: meta.setPriority(SkillMetadata.SkillPriority.LOW); break;
                case 5: meta.setPriority(SkillMetadata.SkillPriority.MEDIUM); break;
                case 8: meta.setPriority(SkillMetadata.SkillPriority.HIGH); break;
                case 10: meta.setPriority(SkillMetadata.SkillPriority.CRITICAL); break;
                default: meta.setPriority(SkillMetadata.SkillPriority.MEDIUM);
            }
        } else {
            meta.setPriority(SkillMetadata.SkillPriority.MEDIUM);
        }
        
        meta.setScope(SkillMetadata.SkillScope.USER);
        meta.setExecutionCount(entity.getExecutionCount() != null ? entity.getExecutionCount() : 0L);
        meta.setSuccessRate(entity.getSuccessRate() != null ? entity.getSuccessRate() : 1.0);
        meta.setAverageExecutionTime(entity.getAverageExecutionTime() != null ? entity.getAverageExecutionTime() : 0L);
        
        return meta;
    }

    public Long getSkillId() {
        return skillId;
    }

    public SkillConfig getConfig() {
        return config;
    }

    public List<SkillScript> getScripts() {
        return scripts;
    }

    public SkillScript getMainScript() {
        for (SkillScript script : scripts) {
            if (script.getIsMain() != null && script.getIsMain() == 1) {
                return script;
            }
        }
        return scripts.isEmpty() ? null : scripts.get(0);
    }

    @Override
    public String getName() {
        return config.getName();
    }

    @Override
    public String getDescription() {
        return config.getDescription();
    }

    @Override
    public String getCategory() {
        return config.getCategory();
    }

    @Override
    public String getVersion() {
        return config.getVersion();
    }

    @Override
    public List<String> getAllowedTools() {
        return config.getAllowedTools();
    }

    @Override
    public List<String> getTags() {
        return config.getTags();
    }

    @Override
    public String getInstructions() {
        return config.getInstructions();
    }

    @Override
    public Map<String, Object> getParameters() {
        return config.getParameters();
    }

    @Override
    public SkillMetadata getMetadata() {
        return metadata;
    }

    @Override
    public SkillResult execute(SkillContext context) {
        return SkillResult.failure("数据库技能需要通过SkillExecutor执行");
    }

    @Override
    public boolean validateContext(SkillContext context) {
        if (context.getUserRequest() == null || context.getUserRequest().isEmpty()) {
            log.warn("技能 {} 验证失败: 用户请求为空", getName());
            return false;
        }

        if (context.getSessionId() == null || context.getSessionId().isEmpty()) {
            log.warn("技能 {} 验证失败: Session ID为空", getName());
            return false;
        }

        return true;
    }

    @Override
    public boolean isApplicable(String userRequest) {
        if (userRequest == null || userRequest.isEmpty()) {
            return false;
        }
        
        String lowerRequest = userRequest.toLowerCase();
        String lowerName = getName().toLowerCase();
        String lowerDesc = getDescription() != null ? getDescription().toLowerCase() : "";
        
        return lowerRequest.contains(lowerName) || lowerRequest.contains(lowerDesc);
    }
}
