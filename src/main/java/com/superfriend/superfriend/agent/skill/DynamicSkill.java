package com.superfriend.superfriend.agent.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
public class DynamicSkill implements Skill {
    
    private final SkillConfig config;
    private final File skillDirectory;
    private final SkillMetadata metadata;
    private final SkillActivationAnalyzer activationAnalyzer;

    public DynamicSkill(SkillConfig config, File skillDirectory, SkillMetadata.SkillScope scope) {
        this(config, skillDirectory, scope, null);
    }

    public DynamicSkill(SkillConfig config, File skillDirectory, SkillMetadata.SkillScope scope, SkillActivationAnalyzer activationAnalyzer) {
        this.config = config;
        this.skillDirectory = skillDirectory;
        this.metadata = createMetadata(scope);
        this.activationAnalyzer = activationAnalyzer;
    }

    public SkillConfig getConfig() {
        return config;
    }

    private SkillMetadata createMetadata(SkillMetadata.SkillScope scope) {
        SkillMetadata meta = new SkillMetadata();
        meta.setName(config.getName());
        meta.setDescription(config.getDescription());
        meta.setCategory(config.getCategory());
        meta.setVersion(config.getVersion());
        meta.setAuthor(config.getAuthor());
        meta.setTags(config.getTags());
        meta.setAllowedTools(config.getAllowedTools());
        meta.setParameters(config.getParameters());
        meta.setPriority(config.getPriority());
        meta.setScope(scope);
        meta.setSkillFilePath(skillDirectory.getAbsolutePath());
        return meta;
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
        return SkillResult.failure("动态技能需要通过SkillExecutor执行");
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

        if (activationAnalyzer != null) {
            SkillActivationAnalyzer.ActivationResult result = activationAnalyzer.analyzeActivation(this, userRequest);
            return result.isActivated();
        }
        
        String lowerRequest = userRequest.toLowerCase();
        String lowerName = getName().toLowerCase();
        String lowerDesc = getDescription() != null ? getDescription().toLowerCase() : "";
        String lowerCategory = getCategory() != null ? getCategory().toLowerCase() : "";
        
        return lowerRequest.contains(lowerName) || 
               lowerRequest.contains(lowerDesc) || 
               lowerRequest.contains(lowerCategory) ||
               (getTags() != null && getTags().stream().anyMatch(tag -> lowerRequest.contains(tag.toLowerCase())));
    }

    public File getSkillDirectory() {
        return skillDirectory;
    }
}
