package com.superfriend.superfriend.agent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SkillManager {
    
    @Autowired
    private SkillRegistry skillRegistry;

    @Autowired
    private SkillExecutor skillExecutor;

    @Autowired
    private com.superfriend.superfriend.service.SkillService skillService;

    @Autowired
    private SkillActivationAnalyzer activationAnalyzer;

    @Value("${skills.system.path:skills/system}")
    private String systemSkillsPath;

    @Value("${skills.project.path:skills/project}")
    private String projectSkillsPath;

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @PostConstruct
    public void initialize() {
        log.info("开始初始化Skills系统...");
        loadSystemSkills();
        loadProjectSkills();
        loadDatabaseSkills();
        syncSelectionStatusFromDatabase();
        log.info("Skills系统初始化完成，共加载 {} 个技能，其中 {} 个被选中", 
            skillRegistry.getSkillCount(), skillRegistry.getSelectedSkillNames().size());
    }

    private void loadDatabaseSkills() {
        try {
            List<com.superfriend.superfriend.entity.Skill> systemAndProjectSkills = skillService.findByScopeIn(Arrays.asList(1, 2));
            
            if (systemAndProjectSkills != null && !systemAndProjectSkills.isEmpty()) {
                int loadedCount = 0;
                for (com.superfriend.superfriend.entity.Skill dbSkill : systemAndProjectSkills) {
                    if (!skillRegistry.hasSkill(dbSkill.getName())) {
                        Skill agentSkill = new DatabaseSkill(dbSkill);
                        skillRegistry.register(agentSkill);
                        loadedCount++;
                    }
                }
                log.info("从数据库加载了 {} 个系统/项目技能", loadedCount);
            }
        } catch (Exception e) {
            log.warn("从数据库加载技能失败: {}", e.getMessage());
        }
    }

    private void syncSelectionStatusFromDatabase() {
        try {
            List<com.superfriend.superfriend.entity.Skill> selectedSkills = skillService.findSelectedSkills();
            if (selectedSkills != null && !selectedSkills.isEmpty()) {
                for (com.superfriend.superfriend.entity.Skill dbSkill : selectedSkills) {
                    if (skillRegistry.hasSkill(dbSkill.getName())) {
                        skillRegistry.updateSelectionStatus(dbSkill.getName(), true);
                    }
                }
                log.info("从数据库同步了 {} 个选中技能", selectedSkills.size());
            }
        } catch (Exception e) {
            log.warn("从数据库同步选择状态失败: {}", e.getMessage());
        }
    }
    
    public void syncUserSelectionStatusFromDatabase(Long userId) {
        if (userId == null) {
            return;
        }
        
        try {
            List<com.superfriend.superfriend.entity.UserSkill> userSelectedSkills = 
                skillService.findSelectedUserSkillsByUserId(userId);
            
            if (userSelectedSkills != null && !userSelectedSkills.isEmpty()) {
                for (com.superfriend.superfriend.entity.UserSkill us : userSelectedSkills) {
                    com.superfriend.superfriend.entity.Skill dbSkill = skillService.findById(us.getSkillId());
                    if (dbSkill != null && skillRegistry.hasSkill(dbSkill.getName())) {
                        skillRegistry.updateUserSelectionStatus(userId, dbSkill.getName(), true);
                    }
                }
                log.info("为用户 {} 同步了 {} 个选中技能", userId, userSelectedSkills.size());
            }
        } catch (Exception e) {
            log.warn("同步用户选择状态失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    public void updateSkillsPaths(String systemPath, String projectPath) {
        this.systemSkillsPath = systemPath;
        this.projectSkillsPath = projectPath;
        log.info("更新 Skills 路径: system={}, project={}", 
            systemPath, projectPath);
    }

    private void loadSystemSkills() {
        try {
            File skillsDir = new File(systemSkillsPath);
            if (skillsDir.exists() && skillsDir.isDirectory()) {
                loadSkillsFromDirectory(skillsDir, SkillMetadata.SkillScope.SYSTEM);
                log.info("已从文件系统加载系统技能: {}", skillsDir.getAbsolutePath());
            } else {
                Resource resource = new ClassPathResource(systemSkillsPath);
                if (resource.exists()) {
                    skillsDir = resource.getFile();
                    loadSkillsFromDirectory(skillsDir, SkillMetadata.SkillScope.SYSTEM);
                    log.info("已从classpath加载系统技能");
                } else {
                    log.warn("系统技能目录不存在: {} (绝对路径: {})", systemSkillsPath, skillsDir.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            log.warn("加载系统技能失败: {}", e.getMessage());
        }
    }

    private void loadProjectSkills() {
        try {
            Path projectSkillsDir = Paths.get(projectSkillsPath);
            if (Files.exists(projectSkillsDir)) {
                loadSkillsFromDirectory(projectSkillsDir.toFile(), SkillMetadata.SkillScope.PROJECT);
                log.info("已加载项目技能");
            }
        } catch (Exception e) {
            log.warn("加载项目技能失败: {}", e.getMessage());
        }
    }

    private void loadSkillsFromDirectory(File directory, SkillMetadata.SkillScope scope) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] skillDirs = directory.listFiles(File::isDirectory);
        if (skillDirs == null) {
            return;
        }

        for (File skillDir : skillDirs) {
            try {
                loadSkill(skillDir, scope);
            } catch (Exception e) {
                log.error("加载技能失败: {} - {}", skillDir.getName(), e.getMessage());
            }
        }
    }

    private void loadSkill(File skillDir, SkillMetadata.SkillScope scope) throws IOException {
        File skillFile = findSkillFile(skillDir);
        if (skillFile == null) {
            log.warn("技能目录中未找到SKILL.md或skill.yaml: {}", skillDir.getName());
            return;
        }

        SkillConfig config = parseSkillFile(skillFile);
        if (config == null) {
            return;
        }

        File scriptsDir = new File(skillDir, "scripts");
        if (scriptsDir.exists() && scriptsDir.isDirectory()) {
            config.setHasScripts(true);
            
            File mainScript = findMainScript(scriptsDir);
            if (mainScript != null) {
                config.setMainScript(mainScript.getName());
            }
            
            log.debug("检测到技能 {} 包含脚本目录", config.getName());
        }

        DynamicSkill skill = new DynamicSkill(config, skillDir, scope, activationAnalyzer);
        skillRegistry.register(skill);
        skillRegistry.registerSkillBasePath(config.getName(), skillDir.getAbsolutePath());

        log.info("成功加载技能: {} ({})", config.getName(), scope);
    }

    private File findMainScript(File scriptsDir) {
        String[] mainScriptNames = {"main.py", "main.sh", "main.js", "index.js", "run.py"};
        
        for (String name : mainScriptNames) {
            File script = new File(scriptsDir, name);
            if (script.exists() && script.isFile()) {
                return script;
            }
        }

        File[] scripts = scriptsDir.listFiles((dir, name) -> 
            name.endsWith(".py") || name.endsWith(".sh") || name.endsWith(".js")
        );
        
        return (scripts != null && scripts.length > 0) ? scripts[0] : null;
    }

    private File findSkillFile(File skillDir) {
        File mdFile = new File(skillDir, "SKILL.md");
        if (mdFile.exists()) {
            return mdFile;
        }

        File yamlFile = new File(skillDir, "skill.yaml");
        if (yamlFile.exists()) {
            return yamlFile;
        }

        File ymlFile = new File(skillDir, "skill.yml");
        if (ymlFile.exists()) {
            return ymlFile;
        }

        return null;
    }

    private SkillConfig parseSkillFile(File skillFile) throws IOException {
        String fileName = skillFile.getName().toLowerCase();

        if (fileName.endsWith(".md")) {
            return parseMarkdownSkill(skillFile);
        } else if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
            return parseYamlSkill(skillFile);
        }

        return null;
    }

    private SkillConfig parseMarkdownSkill(File mdFile) throws IOException {
        String content = new String(Files.readAllBytes(mdFile.toPath()));
        SkillConfig config = new SkillConfig();

        if (content.startsWith("---")) {
            int endIndex = content.indexOf("---", 3);
            if (endIndex != -1) {
                String yamlContent = content.substring(3, endIndex).trim();
                String remainingContent = content.substring(endIndex + 3).trim();
                
                try {
                    Map<String, Object> yamlData = yamlMapper.readValue(yamlContent, Map.class);
                    
                    if (yamlData.containsKey("name")) {
                        config.setName((String) yamlData.get("name"));
                    }
                    if (yamlData.containsKey("description")) {
                        config.setDescription((String) yamlData.get("description"));
                    }
                    if (yamlData.containsKey("license")) {
                        config.setLicense((String) yamlData.get("license"));
                    }
                    if (yamlData.containsKey("compatibility")) {
                        config.setCompatibility((String) yamlData.get("compatibility"));
                    }
                    
                    if (yamlData.containsKey("metadata")) {
                        Object metadataObj = yamlData.get("metadata");
                        if (metadataObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> metadata = (Map<String, Object>) metadataObj;
                            config.setMetadata(metadata);
                            
                            if (metadata.containsKey("category")) {
                                config.setCategory(String.valueOf(metadata.get("category")));
                            }
                            if (metadata.containsKey("version")) {
                                config.setVersion(String.valueOf(metadata.get("version")));
                            }
                            if (metadata.containsKey("author")) {
                                config.setAuthor(String.valueOf(metadata.get("author")));
                            }
                            if (metadata.containsKey("priority")) {
                                String priorityStr = String.valueOf(metadata.get("priority")).toUpperCase();
                                try {
                                    config.setPriority(SkillMetadata.SkillPriority.valueOf(priorityStr));
                                } catch (IllegalArgumentException e) {
                                    config.setPriority(SkillMetadata.SkillPriority.MEDIUM);
                                }
                            }
                            if (metadata.containsKey("timeout")) {
                                try {
                                    config.setTimeout(Long.parseLong(String.valueOf(metadata.get("timeout"))));
                                } catch (NumberFormatException e) {
                                    log.warn("Invalid timeout value: {}", metadata.get("timeout"));
                                }
                            }
                            if (metadata.containsKey("tags")) {
                                Object tagsObj = metadata.get("tags");
                                if (tagsObj instanceof List) {
                                    @SuppressWarnings("unchecked")
                                    List<String> tags = (List<String>) tagsObj;
                                    config.setTags(tags);
                                }
                            }
                        }
                    }
                    
                    if (yamlData.containsKey("allowed-tools")) {
                        String allowedToolsStr = (String) yamlData.get("allowed-tools");
                        if (allowedToolsStr != null && !allowedToolsStr.isEmpty()) {
                            List<String> tools = Arrays.asList(allowedToolsStr.split("\\s+"));
                            config.setAllowedTools(tools);
                        }
                    }
                    
                    if (yamlData.containsKey("category")) {
                        config.setCategory((String) yamlData.get("category"));
                    }
                    if (yamlData.containsKey("version")) {
                        config.setVersion((String) yamlData.get("version"));
                    }
                    if (yamlData.containsKey("author")) {
                        config.setAuthor((String) yamlData.get("author"));
                    }
                    if (yamlData.containsKey("tags")) {
                        Object tagsObj = yamlData.get("tags");
                        if (tagsObj instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<String> tags = (List<String>) tagsObj;
                            config.setTags(tags);
                        }
                    }
                    if (yamlData.containsKey("priority")) {
                        String priorityStr = ((String) yamlData.get("priority")).toUpperCase();
                        try {
                            config.setPriority(SkillMetadata.SkillPriority.valueOf(priorityStr));
                        } catch (IllegalArgumentException e) {
                            config.setPriority(SkillMetadata.SkillPriority.MEDIUM);
                        }
                    }
                    if (yamlData.containsKey("timeout")) {
                        try {
                            config.setTimeout(Long.parseLong(String.valueOf(yamlData.get("timeout"))));
                        } catch (NumberFormatException e) {
                            log.warn("Invalid timeout value: {}", yamlData.get("timeout"));
                        }
                    }
                    
                    if (!remainingContent.isEmpty()) {
                        config.setInstructions(remainingContent);
                    }
                    
                    if (config.getCategory() == null || config.getCategory().isEmpty()) {
                        config.setCategory("general");
                    }
                    
                    return config;
                } catch (Exception e) {
                    log.warn("解析YAML front matter失败: {} - {}", mdFile.getName(), e.getMessage());
                }
            }
        }

        String[] lines = content.split("\n");
        StringBuilder currentSection = new StringBuilder();
        String currentSectionName = null;

        for (String line : lines) {
            if (line.startsWith("# ")) {
                config.setName(line.substring(2).trim());
            } else if (line.startsWith("## ")) {
                if (currentSectionName != null) {
                    processSection(config, currentSectionName, currentSection.toString().trim());
                }
                currentSectionName = line.substring(3).trim().toLowerCase();
                currentSection = new StringBuilder();
            } else {
                currentSection.append(line).append("\n");
            }
        }
        
        if (currentSectionName != null) {
            processSection(config, currentSectionName, currentSection.toString().trim());
        }
        
        if (config.getCategory() == null || config.getCategory().isEmpty()) {
            config.setCategory("general");
        }

        return config;
    }

    private void processSection(SkillConfig config, String sectionName, String content) {
        switch (sectionName) {
            case "description":
                config.setDescription(content);
                break;
            case "instructions":
            case "instruction":
                config.setInstructions(content);
                break;
            case "metadata":
                parseMetadataSection(config, content);
                break;
            case "parameters":
                break;
            case "output":
                break;
        }
    }

    private void parseMetadataSection(SkillConfig config, String content) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("**Category:**") || line.startsWith("**Category**:")) {
                config.setCategory(line.replaceAll("\\*\\*Category:\\*\\*\\s*", "").trim());
            } else if (line.startsWith("**Tags:**") || line.startsWith("**Tags**:")) {
                String tagsStr = line.replaceAll("\\*\\*Tags:\\*\\*\\s*", "").trim();
                config.setTags(Arrays.asList(tagsStr.split(",\\s*")));
            } else if (line.startsWith("**Allowed Tools:**") || line.startsWith("**Allowed Tools**:")) {
                String toolsStr = line.replaceAll("\\*\\*Allowed Tools:\\*\\*\\s*", "").trim();
                config.setAllowedTools(Arrays.asList(toolsStr.split(",\\s*")));
            } else if (line.startsWith("**Priority:**") || line.startsWith("**Priority**:")) {
                String priorityStr = line.replaceAll("\\*\\*Priority:\\*\\*\\s*", "").trim().toUpperCase();
                try {
                    config.setPriority(SkillMetadata.SkillPriority.valueOf(priorityStr));
                } catch (IllegalArgumentException e) {
                    config.setPriority(SkillMetadata.SkillPriority.MEDIUM);
                }
            } else if (line.startsWith("**Version:**") || line.startsWith("**Version**:")) {
                config.setVersion(line.replaceAll("\\*\\*Version:\\*\\*\\s*", "").trim());
            } else if (line.startsWith("**Author:**") || line.startsWith("**Author**:")) {
                config.setAuthor(line.replaceAll("\\*\\*Author:\\*\\*\\s*", "").trim());
            }
        }
    }

    private SkillConfig parseYamlSkill(File yamlFile) throws IOException {
        return yamlMapper.readValue(yamlFile, SkillConfig.class);
    }

    public SkillResult executeSkill(String skillName, SkillContext context) {
        return skillExecutor.execute(skillName, context);
    }

    public List<Skill> suggestSkills(String userRequest) {
        return skillRegistry.getApplicableSkills(userRequest);
    }

    public void reloadSkills() {
        log.info("重新加载所有技能...");
        skillRegistry.getAllSkills().forEach(skill -> 
            skillRegistry.unregister(skill.getName())
        );
        initialize();
    }

    public void createSkill(String skillName, SkillConfig config, SkillMetadata.SkillScope scope) throws IOException {
        String basePath = getBasePathForScope(scope);
        Path skillDir = Paths.get(basePath, skillName);
        Files.createDirectories(skillDir);

        File skillFile = new File(skillDir.toFile(), "skill.yaml");
        yamlMapper.writeValue(skillFile, config);

        loadSkill(skillDir.toFile(), scope);
        log.info("成功创建技能: {} ({})", skillName, scope);
    }

    public void deleteSkill(String skillName) throws IOException {
        Skill skill = skillRegistry.getSkill(skillName);
        if (skill == null) {
            throw new IllegalArgumentException("技能不存在: " + skillName);
        }

        SkillMetadata metadata = skill.getMetadata();
        Path skillDir = Paths.get(metadata.getSkillFilePath()).getParent();

        skillRegistry.unregister(skillName);

        deleteDirectory(skillDir.toFile());
        log.info("成功删除技能: {}", skillName);
    }
    
    public void deleteSkillFiles(String skillName) throws IOException {
        Skill skill = skillRegistry.getSkill(skillName);
        if (skill == null) {
            log.warn("技能在注册表中不存在，跳过文件删除: {}", skillName);
            return;
        }

        SkillMetadata metadata = skill.getMetadata();
        if (metadata.getSkillFilePath() == null) {
            log.warn("技能文件路径为空，跳过文件删除: {}", skillName);
            return;
        }

        Path skillDir = Paths.get(metadata.getSkillFilePath()).getParent();
        if (Files.exists(skillDir)) {
            deleteDirectory(skillDir.toFile());
            log.info("成功删除技能文件: {}", skillName);
        }
    }

    private void deleteDirectory(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    Files.delete(file.toPath());
                }
            }
        }
        Files.delete(directory.toPath());
    }

    private String getBasePathForScope(SkillMetadata.SkillScope scope) {
        switch (scope) {
            case PROJECT:
                return projectSkillsPath;
            default:
                return systemSkillsPath;
        }
    }
}
