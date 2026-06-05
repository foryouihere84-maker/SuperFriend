package com.superfriend.superfriend.agent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.entity.UserSkill;
import com.superfriend.superfriend.event.UserSkillChangedEvent;
import com.superfriend.superfriend.service.SkillService;
import com.superfriend.superfriend.service.SkillEnvironmentManager;
import com.superfriend.superfriend.service.SkillEnvironmentManager.EnvironmentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SkillRegistry {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Skill> skills = new ConcurrentHashMap<>();
    private final Set<String> selectedSkills = ConcurrentHashMap.newKeySet();
    private final Map<Long, Set<String>> userSelectedSkills = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> categoryIndex = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> tagIndex = new ConcurrentHashMap<>();
    private final Map<String, SkillMetadata> metadataMap = new ConcurrentHashMap<>();
    private final Map<String, List<String>> toolDependencyIndex = new ConcurrentHashMap<>();
    private final Map<String, String> skillBasePaths = new ConcurrentHashMap<>();

    // 用户Skills缓存：userId -> (skillName -> Skill对象)
    private final Map<Long, Map<String, Skill>> userSkillsCache = new ConcurrentHashMap<>();
    // 用户Skills缓存时间戳：用于过期检查
    private final Map<Long, Long> userSkillsCacheTimestamp = new ConcurrentHashMap<>();
    // 缓存过期时间（毫秒）
    private static final long CACHE_EXPIRE_MS = 30 * 60 * 1000; // 30分钟
    
    @Autowired
    @Lazy
    private ScriptExecutor scriptExecutor;

    @Autowired
    @Lazy
    private SkillScriptRunner skillScriptRunner;
    
    @Autowired
    @Lazy
    private SkillService skillService;
    
    @Autowired
    @Lazy
    private com.superfriend.superfriend.service.OssService ossService;

    @Autowired
    @Lazy
    private SkillEnvironmentManager environmentManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // 环境检查开关
    private boolean autoEnvCheck = true;

    public void register(Skill skill) {
        register(skill, false);
    }

    public void register(Skill skill, boolean isSelected) {
        String skillName = skill.getName();
        skills.put(skillName, skill);

        if (isSelected || isSystemSkill(skill)) {
            selectedSkills.add(skillName);
            log.info("已选中技能: {}", skillName);
        }

        String category = skill.getCategory();
        if (category != null && !category.isEmpty()) {
            categoryIndex.computeIfAbsent(category, k -> ConcurrentHashMap.newKeySet()).add(skillName);
        }

        List<String> tags = skill.getTags();
        if (tags != null) {
            for (String tag : tags) {
                if (tag != null && !tag.isEmpty()) {
                    tagIndex.computeIfAbsent(tag.toLowerCase(), k -> ConcurrentHashMap.newKeySet()).add(skillName);
                }
            }
        }

        metadataMap.put(skillName, skill.getMetadata());

        List<String> allowedTools = skill.getAllowedTools();
        if (allowedTools != null) {
            for (String tool : allowedTools) {
                if (tool != null && !tool.isEmpty()) {
                    toolDependencyIndex.computeIfAbsent(tool, k -> new ArrayList<>()).add(skillName);
                }
            }
        }

        log.info("已注册技能: {} (分类: {}, 标签: {}, 允许工具: {}, 选中: {})",
            skillName, category, tags, allowedTools, selectedSkills.contains(skillName));
    }

    private boolean isSystemSkill(Skill skill) {
        SkillMetadata metadata = skill.getMetadata();
        return metadata != null && metadata.getScope() == SkillMetadata.SkillScope.SYSTEM;
    }

    public void updateSelectionStatus(String skillName, boolean isSelected) {
        if (isSelected) {
            if (skills.containsKey(skillName)) {
                selectedSkills.add(skillName);
                log.info("技能 {} 已选中", skillName);
            } else {
                log.warn("尝试选中不存在的技能: {}", skillName);
            }
        } else {
            selectedSkills.remove(skillName);
            log.info("技能 {} 已取消选中", skillName);
        }
    }

    public void updateUserSelectionStatus(Long userId, String skillName, boolean isSelected) {
        if (userId == null) {
            updateSelectionStatus(skillName, isSelected);
            return;
        }
        
        Set<String> userSelected = userSelectedSkills.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
        
        if (isSelected) {
            if (skills.containsKey(skillName)) {
                userSelected.add(skillName);
                log.info("用户 {} 的技能 {} 已选中", userId, skillName);
            } else {
                log.warn("尝试选中不存在的技能: {}", skillName);
            }
        } else {
            userSelected.remove(skillName);
            log.info("用户 {} 的技能 {} 已取消选中", userId, skillName);
        }
    }

    public boolean isSkillSelected(String skillName) {
        return selectedSkills.contains(skillName);
    }
    
    public boolean isSkillSelectedByUser(Long userId, String skillName) {
        if (userId == null) {
            return isSkillSelected(skillName);
        }
        Set<String> userSelected = userSelectedSkills.get(userId);
        return userSelected != null && userSelected.contains(skillName);
    }

    public Set<String> getSelectedSkillNames() {
        return new HashSet<>(selectedSkills);
    }
    
    public Set<String> getSelectedSkillNamesForUser(Long userId) {
        if (userId == null) {
            return getSelectedSkillNames();
        }
        Set<String> userSelected = userSelectedSkills.get(userId);
        return userSelected != null ? new HashSet<>(userSelected) : new HashSet<>();
    }
    
    public void clearUserSelections(Long userId) {
        if (userId != null) {
            userSelectedSkills.remove(userId);
            log.info("清除用户 {} 的所有选择状态", userId);
        }
    }

    public void unregister(String skillName) {
        Skill skill = skills.remove(skillName);
        if (skill != null) {
            String category = skill.getCategory();
            if (category != null && !category.isEmpty()) {
                Set<String> categorySkills = categoryIndex.get(category);
                if (categorySkills != null) {
                    categorySkills.remove(skillName);
                }
            }

            List<String> tags = skill.getTags();
            if (tags != null) {
                for (String tag : tags) {
                    if (tag != null && !tag.isEmpty()) {
                        Set<String> tagSkills = tagIndex.get(tag.toLowerCase());
                        if (tagSkills != null) {
                            tagSkills.remove(skillName);
                        }
                    }
                }
            }

            metadataMap.remove(skillName);

            List<String> allowedTools = skill.getAllowedTools();
            if (allowedTools != null) {
                for (String tool : allowedTools) {
                    if (tool != null && !tool.isEmpty()) {
                        List<String> dependentSkills = toolDependencyIndex.get(tool);
                        if (dependentSkills != null) {
                            dependentSkills.remove(skillName);
                        }
                    }
                }
            }

            selectedSkills.remove(skillName);

            for (Set<String> userSelected : userSelectedSkills.values()) {
                userSelected.remove(skillName);
            }

            // 从所有用户的Skills缓存中移除该技能
            for (Map<String, Skill> userCache : userSkillsCache.values()) {
                userCache.remove(skillName);
            }

            log.info("已注销技能: {}", skillName);
        }
    }

    public Skill getSkill(String name) {
        return skills.get(name);
    }

    public List<Skill> getAllSkills() {
        return new ArrayList<>(skills.values());
    }

    public List<Skill> getSkillsByCategory(String category) {
        Set<String> skillNames = categoryIndex.get(category);
        if (skillNames == null) {
            return Collections.emptyList();
        }

        return skillNames.stream()
            .map(skills::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public List<Skill> getSkillsByTag(String tag) {
        Set<String> skillNames = tagIndex.get(tag.toLowerCase());
        if (skillNames == null) {
            return Collections.emptyList();
        }

        return skillNames.stream()
            .map(skills::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public List<Skill> getSkillsByTool(String toolName) {
        List<String> skillNames = toolDependencyIndex.get(toolName);
        if (skillNames == null) {
            return Collections.emptyList();
        }

        return skillNames.stream()
            .map(skills::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public Set<String> getCategories() {
        return categoryIndex.keySet();
    }

    public Set<String> getTags() {
        return tagIndex.keySet();
    }

    public SkillMetadata getMetadata(String skillName) {
        return metadataMap.get(skillName);
    }

    public List<Skill> searchSkills(String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        return skills.values().stream()
            .filter(skill -> 
                skill.getName().toLowerCase().contains(lowerKeyword) ||
                skill.getDescription().toLowerCase().contains(lowerKeyword) ||
                skill.getCategory().toLowerCase().contains(lowerKeyword) ||
                skill.getTags().stream().anyMatch(tag -> tag.toLowerCase().contains(lowerKeyword))
            )
            .collect(Collectors.toList());
    }

    public List<Skill> getApplicableSkills(String userRequest) {
        return skills.values().stream()
            .filter(skill -> skill.isApplicable(userRequest))
            .sorted((s1, s2) -> {
                SkillMetadata m1 = s1.getMetadata();
                SkillMetadata m2 = s2.getMetadata();
                int priorityCompare = Integer.compare(
                    m2.getPriority().getValue(),
                    m1.getPriority().getValue()
                );
                if (priorityCompare != 0) return priorityCompare;
                return Double.compare(m2.getSuccessRate(), m1.getSuccessRate());
            })
            .collect(Collectors.toList());
    }

    public boolean hasSkill(String name) {
        return skills.containsKey(name);
    }

    public int getSkillCount() {
        return skills.size();
    }

    public void updateSkillStats(String skillName, boolean success, long executionTime) {
        SkillMetadata metadata = metadataMap.get(skillName);
        if (metadata != null) {
            metadata.recordExecution(success, executionTime);
        }
    }

    public List<SkillMetadataDTO> getAllSkillMetadata() {
        return skills.values().stream()
            .map(SkillMetadataDTO::new)
            .collect(Collectors.toList());
    }

    public String generateSkillCatalogPrompt() {
        if (selectedSkills.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## 🎯 Available Skills Catalog\n");
        sb.append("The following skills are available. When a task matches a skill's domain, use the `load_skill` tool to retrieve the full SKILL.md body with detailed instructions.\n\n");
        
        List<Skill> selectedSkillList = selectedSkills.stream()
            .map(skills::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        Map<String, List<SkillMetadataDTO>> groupedByCategory = selectedSkillList.stream()
            .map(SkillMetadataDTO::new)
            .collect(Collectors.groupingBy(
                s -> s.getCategory() != null ? s.getCategory() : "Other",
                LinkedHashMap::new,
                Collectors.toList()
            ));

        for (Map.Entry<String, List<SkillMetadataDTO>> entry : groupedByCategory.entrySet()) {
            sb.append("### ").append(entry.getKey()).append("\n\n");
            for (SkillMetadataDTO skill : entry.getValue()) {
                sb.append(skill.toSystemPromptFormat()).append("\n");
            }
        }

        sb.append("\n**Usage Instructions**:\n");
        sb.append("1. Determine if the user request requires any of the above skills\n");
        sb.append("2. If needed, call `load_skill(skill_name)` to get full instructions\n");
        sb.append("3. If the skill instructions reference resource files, call `read_skill_resource(skill_name, resource_path)` to load them\n");
        sb.append("4. If the skill contains executable scripts, call `run_skill_script(skill_name, script_name, parameters)` to execute\n");
        sb.append("5. Execute the task according to the retrieved instructions\n\n");
        
        return sb.toString();
    }
    
    public String generateSkillCatalogPromptForUser(Long userId, SkillService skillService) {
        List<Skill> selectedSkillList = new ArrayList<>();
        Set<String> addedSkillNames = new HashSet<>();

        for (Skill skill : skills.values()) {
            SkillMetadata metadata = skill.getMetadata();
            if (metadata != null && metadata.getScope() == SkillMetadata.SkillScope.SYSTEM) {
                selectedSkillList.add(skill);
                addedSkillNames.add(skill.getName());
            }
        }

        if (userId != null && skillService != null) {
            // 使用缓存获取用户Skills
            List<Skill> userSkills = getUserSkillsWithCache(userId, skillService);
            for (Skill userSkill : userSkills) {
                if (!addedSkillNames.contains(userSkill.getName())) {
                    selectedSkillList.add(userSkill);
                    addedSkillNames.add(userSkill.getName());
                }
            }
        }

        if (selectedSkillList.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## 🎯 Skills (按需加载)\n");
        sb.append("使用 `load_skill(skill_name)` 获取详细指令、脚本路径和参考文档。优先检查 Skills 再使用 MCP 工具。\n");
        sb.append("调用流程：load_skill → read_skill_resource(可选) → run_skill_script\n\n");

        Map<String, List<SkillMetadataDTO>> groupedByCategory = selectedSkillList.stream()
            .map(SkillMetadataDTO::new)
            .collect(Collectors.groupingBy(
                s -> s.getCategory() != null ? s.getCategory() : "Other",
                LinkedHashMap::new,
                Collectors.toList()
            ));

        for (Map.Entry<String, List<SkillMetadataDTO>> entry : groupedByCategory.entrySet()) {
            sb.append("**").append(entry.getKey()).append("**: ");
            sb.append(entry.getValue().stream()
                .map(SkillMetadataDTO::toCompactSystemPromptFormat)
                .map(s -> s.replace("- **", "").replace("**: ", ": "))
                .collect(Collectors.joining("; ")));
            sb.append("\n");
        }

        log.debug("为用户 {} 生成技能目录，共 {} 个技能", userId, selectedSkillList.size());
        return sb.toString();
    }

    public String getSkillFullContent(String skillName) {
        Skill skill = skills.get(skillName);

        if (skill == null && skillService != null) {
            com.superfriend.superfriend.entity.Skill dbSkill = skillService.findByName(skillName);
            if (dbSkill != null) {
                skill = new DatabaseSkill(dbSkill);
                log.debug("从数据库按需加载技能: {}", skillName);
            }
        }

        if (skill == null) {
            return null;
        }

        StringBuilder content = new StringBuilder();

        // 【关键】在最前面添加醒目的工作流提示
        content.append("# ").append(skill.getName()).append("\n\n");
        content.append("================================================================================\n");
        content.append("⚡ CRITICAL: HOW TO USE THIS SKILL\n");
        content.append("================================================================================\n");
        content.append("After loading this skill, you MUST use `run_skill_script` to execute scripts.\n");
        content.append("DO NOT directly call bash-sandbox or write_file - use run_skill_script instead.\n");
        content.append("(run_skill_script internally uses bash-sandbox with proper setup)\n\n");
        content.append("**Correct Workflow:**\n");
        content.append("1. You have already called `load_skill(skill_name=\"").append(skillName).append("\")` ✓\n");
        content.append("2. NOW call `run_skill_script` with the appropriate script:\n");
        content.append("   run_skill_script(\n");
        content.append("     skill_name=\"").append(skillName).append("\",\n");
        content.append("     script_name=\"scripts/xxx.py\",  // See Available Scripts below\n");
        content.append("     parameters={...}\n");
        content.append("   )\n");
        content.append("================================================================================\n\n");

        content.append("## Description\n").append(skill.getDescription()).append("\n\n");

        if (skill.getCategory() != null) {
            content.append("## Category\n").append(skill.getCategory()).append("\n\n");
        }

        if (skill.getTags() != null && !skill.getTags().isEmpty()) {
            content.append("## Tags\n").append(String.join(", ", skill.getTags())).append("\n\n");
        }

        if (skill.getAllowedTools() != null && !skill.getAllowedTools().isEmpty()) {
            content.append("## Allowed Tools\n").append(String.join(", ", skill.getAllowedTools())).append("\n\n");
        }

        // 检查环境状态（提示式，不阻塞）
        if (autoEnvCheck && environmentManager != null) {
            try {
                EnvironmentStatus envStatus = environmentManager.checkEnvironment(skillName, false);
                if (envStatus != null) {
                    if (envStatus.isReady()) {
                        content.append("## ✅ Environment Status\n");
                        content.append("**Status**: Ready\n\n");
                    } else {
                        // 提示式：告知环境可能不完整，但不阻塞，让 Agent 自行决定是否继续
                        content.append("## ℹ️ Environment Note\n");
                        content.append("**Status**: Some dependencies may be missing\n");
                        if (envStatus.getErrorMessage() != null) {
                            content.append("**Details**: ").append(envStatus.getErrorMessage()).append("\n");
                        }
                        content.append("\n**You can proceed with the task**. If execution fails due to missing dependencies, ");
                        content.append("use `setup_skill_environment(skill_name=\"").append(skillName).append("\")` to install them.\n\n");
                    }
                }
            } catch (Exception e) {
                log.debug("环境检查失败: {}", e.getMessage());
            }
        }

        // 添加技能路径信息（关键：让模型知道文件在哪里）
        String basePath = skillBasePaths.get(skillName);
        if (basePath != null) {
            content.append("## 📁 Skill Location\n");
            content.append("**Base Path**: `").append(basePath).append("`\n\n");

            // 列出可用的脚本和资源
            try {
                java.io.File skillDir = new java.io.File(basePath);
                if (skillDir.exists()) {
                    // 脚本目录
                    java.io.File scriptsDir = new java.io.File(skillDir, "scripts");
                    String[] availableScripts = null;  // 用于后续示例
                    if (scriptsDir.exists() && scriptsDir.isDirectory()) {
                        content.append("## 📜 Available Scripts (CRITICAL - Use with run_skill_script)\n\n");
                        content.append("**Scripts Directory**: `").append(scriptsDir.getAbsolutePath()).append("`\n\n");

                        String[] scripts = scriptsDir.list((dir, name) ->
                            name.endsWith(".sh") || name.endsWith(".py") || name.endsWith(".js") ||
                            name.endsWith(".csx") || name.endsWith(".ps1"));
                        if (scripts != null && scripts.length > 0) {
                            availableScripts = scripts;  // 保存用于示例
                            content.append("**Available Scripts**:\n");
                            for (String script : scripts) {
                                content.append("- `scripts/").append(script).append("`\n");
                            }
                            content.append("\n**⚡ IMMEDIATE ACTION REQUIRED:**\n");
                            content.append("Call `run_skill_script` NOW with one of the scripts above:\n");
                            content.append("```\n");
                            content.append("run_skill_script(\n");
                            content.append("  skill_name=\"").append(skillName).append("\",\n");
                            content.append("  script_name=\"scripts/").append(availableScripts[0]).append("\",\n");
                            content.append("  parameters={\"key\": \"value\"}\n");
                            content.append(")\n");
                            content.append("```\n\n");
                        }
                    }

                    // 参考文档目录
                    java.io.File refsDir = new java.io.File(skillDir, "references");
                    if (refsDir.exists() && refsDir.isDirectory()) {
                        content.append("**References Directory**: `").append(refsDir.getAbsolutePath()).append("`\n");
                        String[] refs = refsDir.list((dir, name) -> name.endsWith(".md"));
                        if (refs != null && refs.length > 0) {
                            content.append("**Reference Files**: ").append(String.join(", ", refs)).append("\n");
                        }
                        content.append("\n");
                    }

                    // 使用read_skill_resource获取资源
                    content.append("**📖 To read resources**: Use `read_skill_resource(skill_name=\"").append(skillName).append("\", resource_path=\"path/to/file\")`\n\n");

                    // 输出文件说明
                    content.append("## 📤 Output Files\n");
                    content.append("**Working Directory**: `").append(basePath).append("`\n");
                    content.append("- 脚本在此目录下执行，相对路径相对于此目录解析\n");
                    content.append("- 输出文件请使用绝对路径或相对于工作目录的相对路径\n");
                    content.append("- 输出文件会自动检测并发送给用户（支持 .docx, .pdf, .xlsx, .png 等）\n");
                    content.append("- JSON 输出中包含 `outputFile` 或 `output_path` 字段可被自动识别\n\n");

                    // 沙箱执行环境说明
                    content.append("## 🔒 Execution Environment\n");
                    content.append("脚本在 **bash-sandbox** 沙箱中安全执行：\n");
                    content.append("- **隔离性**：每个技能有独立的沙箱会话\n");
                    content.append("- **安全性**：命令受安全策略过滤\n");
                    content.append("- **状态保持**：同一技能的多次调用共享会话状态\n");
                    content.append("- **文件访问**：可访问技能目录和系统临时目录\n\n");

                    // 添加调用示例
                    content.append("## 📝 Example Call\n");
                    content.append("```\n");
                    content.append("run_skill_script(\n");
                    content.append("  skill_name=\"").append(skillName).append("\",\n");
                    if (availableScripts != null && availableScripts.length > 0) {
                        content.append("  script_name=\"scripts/").append(availableScripts[0]).append("\",\n");
                    }
                    content.append("  parameters={\"key\": \"value\"}  // 参数通过 JSON 文件传递给脚本\n");
                    content.append(")\n");
                    content.append("```\n\n");
                }
            } catch (Exception e) {
                log.debug("列出技能目录失败: {}", e.getMessage());
            }
        }

        if (skill.getInstructions() != null && !skill.getInstructions().isEmpty()) {
            // 过滤掉 Setup 相关的阻塞指令，避免 Agent 陷入环境准备循环
            String filteredInstructions = filterSetupInstructions(skill.getInstructions());
            content.append("## Instructions\n").append(filteredInstructions).append("\n\n");
        }

        if (skill.getParameters() != null && !skill.getParameters().isEmpty()) {
            content.append("## Parameters\n");
            for (Map.Entry<String, Object> param : skill.getParameters().entrySet()) {
                content.append("- ").append(param.getKey()).append(": ").append(param.getValue()).append("\n");
            }
            content.append("\n");
        }

        return content.toString();
    }

    /**
     * 过滤 Setup 相关的阻塞指令
     * 移除要求 Agent 必须先运行环境检查/安装脚本的指令
     */
    private String filterSetupInstructions(String instructions) {
        if (instructions == null || instructions.isEmpty()) {
            return instructions;
        }

        // 需要过滤的阻塞模式
        String[] blockingPatterns = {
            // Setup 段落（整个段落）
            "(?s)## Setup\\s*\\n.*?(?=\\n## |$)",
            // 环境检查指令
            "(?i)\\*\\*First time:\\*\\*.*?(?=\\n\\n|\\n## |$)",
            "(?i)\\*\\*First operation.*?(?=\\n\\n|\\n## |$)",
            "(?i)scripts/env_check\\.sh.*?(?=\\n|\\.)",
            "(?i)do not proceed if.*?NOT READY.*?(?=\\n|\\.)",
            // 强制环境安装指令
            "(?i)run.*?setup\\.sh.*?(?=\\n|\\.)",
            "(?i)run.*?setup\\.ps1.*?(?=\\n|\\.)",
            "(?i)bash scripts/setup\\.sh.*?(?=\\n|\\.)",
            "(?i)powershell scripts/setup\\.ps1.*?(?=\\n|\\.)"
        };

        String result = instructions;
        for (String pattern : blockingPatterns) {
            result = result.replaceAll(pattern, "");
        }

        // 清理多余的空行
        result = result.replaceAll("\n{3,}", "\n\n").trim();

        // 添加提示：环境依赖是可选的
        if (!result.equals(instructions)) {
            result = "**Note**: Environment setup is optional. Proceed with the task directly. If execution fails, check dependencies.\n\n" + result;
        }

        return result;
    }
    
    public void registerSkillBasePath(String skillName, String basePath) {
        skillBasePaths.put(skillName, basePath);
    }

    /**
     * 获取技能基础路径
     *
     * @param skillName 技能名称
     * @return 基础路径，如果不存在返回 null
     */
    public String getSkillBasePath(String skillName) {
        return skillBasePaths.get(skillName);
    }
    
    public String getSkillResource(String skillName, String resourcePath) {
        return getSkillResource(skillName, resourcePath, null);
    }
    
    public String getSkillResource(String skillName, String resourcePath, Long userId) {
        Skill skill = skills.get(skillName);
        
        if (skill == null && skillService != null) {
            com.superfriend.superfriend.entity.Skill dbSkill = skillService.findByName(skillName);
            if (dbSkill != null) {
                skill = new DatabaseSkill(dbSkill);
            }
        }
        
        if (skill == null) {
            log.warn("未找到技能: {}", skillName);
            return null;
        }
        
        if (skill instanceof DatabaseSkill) {
            return getDatabaseSkillResource((DatabaseSkill) skill, resourcePath, userId);
        }
        
        String basePath = skillBasePaths.get(skillName);
        if (basePath == null) {
            basePath = "skills/" + skillName;
        }

        Path fullPath = Paths.get(basePath, resourcePath);

        // 如果文件不存在，尝试常见的子目录
        if (!Files.exists(fullPath)) {
            // 尝试 references/ 目录
            Path refPath = Paths.get(basePath, "references", resourcePath);
            if (Files.exists(refPath)) {
                fullPath = refPath;
                log.debug("资源在 references/ 目录中找到: {}", refPath);
            } else {
                // 尝试 assets/ 目录
                Path assetPath = Paths.get(basePath, "assets", resourcePath);
                if (Files.exists(assetPath)) {
                    fullPath = assetPath;
                    log.debug("资源在 assets/ 目录中找到: {}", assetPath);
                } else {
                    // 尝试 scripts/ 目录（用于 Samples 等代码资源）
                    Path scriptsPath = Paths.get(basePath, "scripts", resourcePath);
                    if (Files.exists(scriptsPath)) {
                        fullPath = scriptsPath;
                        log.debug("资源在 scripts/ 目录中找到: {}", scriptsPath);
                    } else {
                        // 尝试搜索 scripts 子目录（如 scripts/dotnet/Project/Samples/）
                        Path foundPath = searchInScriptsSubdirs(basePath, resourcePath);
                        if (foundPath != null) {
                            fullPath = foundPath;
                            log.debug("资源在 scripts 子目录中找到: {}", foundPath);
                        } else {
                            log.warn("资源文件不存在: {} (尝试了 references/, assets/, scripts/ 及子目录)", Paths.get(basePath, resourcePath));
                            return null;
                        }
                    }
                }
            }
        }
        
        try {
            String content = new String(Files.readAllBytes(fullPath));
            log.debug("读取资源文件: {} ({} 字符)", fullPath, content.length());
            return content;
        } catch (IOException e) {
            log.error("读取资源文件失败: {} - {}", fullPath, e.getMessage());
            return null;
        }
    }

    /**
     * 在 scripts 子目录中搜索资源文件
     * 用于处理如 Samples/*.cs 等位于 scripts/dotnet/Project/Samples/ 的文件
     */
    private Path searchInScriptsSubdirs(String basePath, String resourcePath) {
        Path scriptsDir = Paths.get(basePath, "scripts");
        if (!Files.exists(scriptsDir) || !Files.isDirectory(scriptsDir)) {
            return null;
        }

        // 获取资源文件名（如 Samples/DocumentCreationSamples.cs -> DocumentCreationSamples.cs）
        final String targetFileName;
        if (resourcePath.contains("/")) {
            targetFileName = resourcePath.substring(resourcePath.lastIndexOf("/") + 1);
        } else if (resourcePath.contains("\\")) {
            targetFileName = resourcePath.substring(resourcePath.lastIndexOf("\\") + 1);
        } else {
            targetFileName = resourcePath;
        }

        // 搜索 scripts 目录下的所有子目录
        try {
            return Files.walk(scriptsDir, 5)  // 最大深度5层
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equals(targetFileName))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            log.debug("搜索 scripts 子目录失败: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String getDatabaseSkillResource(DatabaseSkill skill, String resourcePath, Long userId) {
        try {
            com.superfriend.superfriend.entity.Skill dbSkillEntity = skillService.findByName(skill.getName());
            if (dbSkillEntity == null || dbSkillEntity.getResourcesJson() == null) {
                log.warn("技能 {} 没有资源", skill.getName());
                return null;
            }
            
            List<Map<String, Object>> resources = objectMapper.readValue(
                dbSkillEntity.getResourcesJson(), List.class);
            
            for (Map<String, Object> resource : resources) {
                String resName = (String) resource.get("resourceName");
                String resPath = (String) resource.get("resourcePath");
                
                if (resourcePath.equals(resName) || resourcePath.equals(resPath)) {
                    String storageType = (String) resource.get("storageType");
                    
                    if (dbSkillEntity.getScope() != null && dbSkillEntity.getScope() == 3) {
                        if (userId == null) {
                            log.warn("访问用户级技能资源需要用户ID: {}", skill.getName());
                            return "[Error] 访问此资源需要用户认证";
                        }
                        
                        Long ownerId = null;
                        Object ownerIdObj = resource.get("ownerId");
                        if (ownerIdObj != null) {
                            if (ownerIdObj instanceof Number) {
                                ownerId = ((Number) ownerIdObj).longValue();
                            } else if (ownerIdObj instanceof String) {
                                ownerId = Long.parseLong((String) ownerIdObj);
                            }
                        }
                        
                        if (ownerId != null && !ownerId.equals(userId)) {
                            log.warn("用户 {} 尝试访问非授权资源: skill={}, owner={}", 
                                userId, skill.getName(), ownerId);
                            return "[Error] 无权限访问此资源";
                        }
                        
                        if (resPath != null && !resPath.startsWith("skills/users/" + userId + "/")) {
                            log.warn("用户 {} 尝试访问非授权资源路径: {}", userId, resPath);
                            return "[Error] 无权限访问此资源";
                        }
                    }
                    
                    if ("oss".equals(storageType)) {
                        return getOssResource(resource, userId);
                    } else {
                        return (String) resource.get("resourceContent");
                    }
                }
            }
            
            log.warn("未找到资源: {} in skill {}", resourcePath, skill.getName());
            return null;
            
        } catch (Exception e) {
            log.error("获取数据库技能资源失败: {} - {}", skill.getName(), e.getMessage());
            return null;
        }
    }
    
    private String getOssResource(Map<String, Object> resource, Long userId) {
        String resourcePath = (String) resource.get("resourcePath");
        
        if (ossService != null && ossService.isEnabled()) {
            try {
                String presignedUrl = ossService.generatePresignedUrl(resourcePath, 60);
                return "[OSS Resource] Download URL: " + presignedUrl + 
                    "\nFile: " + resource.get("resourceName") +
                    "\nSize: " + resource.get("fileSize") + " bytes" +
                    "\nType: " + resource.get("mimeType");
            } catch (Exception e) {
                log.error("生成 OSS 资源链接失败: {}", e.getMessage());
                return "[OSS Resource] Error: " + e.getMessage();
            }
        } else {
            String url = (String) resource.get("resourceUrl");
            return "[OSS Resource] URL: " + url + 
                "\nFile: " + resource.get("resourceName") +
                "\nSize: " + resource.get("fileSize") + " bytes" +
                "\nType: " + resource.get("mimeType");
        }
    }
    
    public ScriptExecutor.ScriptExecutionResult executeSkillScript(
            String skillName, String scriptName, Map<String, Object> parameters) {
        return executeSkillScript(skillName, scriptName, parameters, null);
    }

    /**
     * 执行技能脚本（带 sessionId，确保会话一致性）
     */
    public ScriptExecutor.ScriptExecutionResult executeSkillScript(
            String skillName, String scriptName, Map<String, Object> parameters, String sessionId) {
        Skill skill = skills.get(skillName);

        if (skill == null && skillService != null) {
            com.superfriend.superfriend.entity.Skill dbSkill = skillService.findByName(skillName);
            if (dbSkill != null) {
                skill = new DatabaseSkill(dbSkill);
                log.debug("从数据库按需加载技能用于脚本执行: {}", skillName);
            }
        }

        if (skill == null) {
            return ScriptExecutor.ScriptExecutionResult.failure("未找到技能: " + skillName);
        }

        if (skill instanceof DatabaseSkill) {
            DatabaseSkill dbSkill = (DatabaseSkill) skill;
            return executeDatabaseSkillScript(dbSkill, scriptName, parameters);
        }

        // 使用 SkillScriptRunner 通过 bash-sandbox 执行脚本（传入 sessionId）
        log.info("通过 SkillScriptRunner 执行脚本: skill={}, script={}, sessionId={}", skillName, scriptName, sessionId);

        SkillScriptRunner.ScriptExecutionResult result = skillScriptRunner.executeScript(
            skillName, scriptName, parameters, sessionId);

        // 转换为兼容的返回类型
        ScriptExecutor.ScriptExecutionResult compatResult = new ScriptExecutor.ScriptExecutionResult();
        compatResult.setSuccess(result.isSuccess());
        compatResult.setScriptPath(result.getScriptPath());
        compatResult.setExitCode(result.getExitCode());
        compatResult.setOutput(result.getOutput());
        compatResult.setError(result.getError());
        compatResult.setExecutionTime(result.getExecutionTime());
        compatResult.setParsedOutput(result.getParsedOutput());
        compatResult.setOutputFiles(result.getOutputFiles());
        compatResult.setOutputDirectory(result.getOutputDirectory());

        return compatResult;
    }

    /**
     * 清理技能输出目录
     * 文件发送给前端后调用此方法清理临时文件
     */
    public void cleanupSkillOutputDirectory(String outputDirectory) {
        if (outputDirectory == null || outputDirectory.isEmpty()) {
            return;
        }
        skillScriptRunner.cleanupOutputDirectory(outputDirectory);
    }

    private ScriptExecutor.ScriptExecutionResult executeDatabaseSkillScript(
            DatabaseSkill skill, String scriptName, Map<String, Object> parameters) {
        
        com.superfriend.superfriend.entity.SkillScript targetScript = null;
        for (com.superfriend.superfriend.entity.SkillScript script : skill.getScripts()) {
            if (scriptName.equals(script.getScriptName())) {
                targetScript = script;
                break;
            }
        }
        
        if (targetScript == null) {
            return ScriptExecutor.ScriptExecutionResult.failure(
                "未找到脚本: " + scriptName + " (可用脚本: " + 
                skill.getScripts().stream().map(s -> s.getScriptName()).collect(java.util.stream.Collectors.joining(", ")) + ")");
        }
        
        String scriptContent = targetScript.getScriptContent();
        String scriptType = targetScript.getScriptType();
        
        if (scriptContent == null || scriptContent.isEmpty()) {
            return ScriptExecutor.ScriptExecutionResult.failure("脚本内容为空: " + scriptName);
        }
        
        try {
            String extension = getScriptExtension(scriptType);
            File tempFile = File.createTempFile("skill_script_", extension);
            java.io.FileWriter writer = new java.io.FileWriter(tempFile);
            writer.write(scriptContent);
            writer.close();
            tempFile.setExecutable(true);
            
            File workingDir = new File(System.getProperty("java.io.tmpdir"));
            
            log.info("执行数据库技能脚本: skill={}, script={}, type={}", 
                skill.getName(), scriptName, scriptType);
            
            ScriptExecutor.ScriptExecutionResult result = scriptExecutor.executeScript(
                tempFile.getAbsolutePath(),
                parameters,
                workingDir
            );
            
            if (tempFile.exists()) {
                tempFile.delete();
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("执行数据库技能脚本失败: {} - {}", scriptName, e.getMessage());
            return ScriptExecutor.ScriptExecutionResult.failure("脚本执行异常: " + e.getMessage());
        }
    }
    
    private String getScriptExtension(String scriptType) {
        if (scriptType == null) return ".py";
        switch (scriptType.toLowerCase()) {
            case "python": return ".py";
            case "bash": return ".sh";
            case "javascript": return ".js";
            case "typescript": return ".ts";
            case "ruby": return ".rb";
            case "powershell": return ".ps1";
            default: return ".py";
        }
    }

    /**
     * 使用缓存获取用户的Skills
     * 缓存30分钟过期，避免频繁查询数据库
     */
    private List<Skill> getUserSkillsWithCache(Long userId, SkillService skillService) {
        // 检查缓存是否存在且未过期
        Map<String, Skill> cachedSkills = userSkillsCache.get(userId);
        Long cacheTime = userSkillsCacheTimestamp.get(userId);
        long now = System.currentTimeMillis();

        if (cachedSkills != null && cacheTime != null && (now - cacheTime) < CACHE_EXPIRE_MS) {
            log.debug("[用户Skills缓存] 使用缓存: userId={}, skillsCount={}", userId, cachedSkills.size());
            return new ArrayList<>(cachedSkills.values());
        }

        // 缓存过期或不存在，重新加载
        log.debug("[用户Skills缓存] 缓存过期或不存在，重新加载: userId={}", userId);
        List<Skill> userSkills = loadUserSkillsFromDatabase(userId, skillService);

        // 更新缓存
        Map<String, Skill> newCache = new ConcurrentHashMap<>();
        for (Skill skill : userSkills) {
            newCache.put(skill.getName(), skill);
        }
        userSkillsCache.put(userId, newCache);
        userSkillsCacheTimestamp.put(userId, now);

        log.info("[用户Skills缓存] 已更新缓存: userId={}, skillsCount={}", userId, userSkills.size());
        return userSkills;
    }

    /**
     * 从数据库加载用户的Skills
     */
    private List<Skill> loadUserSkillsFromDatabase(Long userId, SkillService skillService) {
        List<Skill> result = new ArrayList<>();
        try {
            List<UserSkill> userSelectedSkills = skillService.findSelectedUserSkillsByUserId(userId);
            if (userSelectedSkills != null && !userSelectedSkills.isEmpty()) {
                for (UserSkill us : userSelectedSkills) {
                    com.superfriend.superfriend.entity.Skill dbSkill = skillService.findById(us.getSkillId());
                    if (dbSkill != null) {
                        // 先检查全局skills中是否存在
                        if (skills.containsKey(dbSkill.getName())) {
                            result.add(skills.get(dbSkill.getName()));
                        } else {
                            result.add(new DatabaseSkill(dbSkill));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[用户Skills缓存] 加载用户Skills失败: userId={}, error={}", userId, e.getMessage());
        }
        return result;
    }

    /**
     * 清除指定用户的Skills缓存
     * 在用户修改Skills后调用
     */
    public void clearUserSkillsCache(Long userId) {
        userSkillsCache.remove(userId);
        userSkillsCacheTimestamp.remove(userId);
        log.info("[用户Skills缓存] 已清除缓存: userId={}", userId);
    }

    /**
     * 清除所有用户的Skills缓存
     */
    public void clearAllUserSkillsCache() {
        userSkillsCache.clear();
        userSkillsCacheTimestamp.clear();
        log.info("[用户Skills缓存] 已清除所有用户缓存");
    }

    /**
     * 监听用户Skills变更事件，自动清除缓存
     */
    @EventListener
    public void onUserSkillChanged(UserSkillChangedEvent event) {
        if (event.getUserId() != null) {
            clearUserSkillsCache(event.getUserId());
            log.debug("[用户Skills缓存] 收到变更事件 {}，已清除缓存: userId={}",
                event.getChangeType(), event.getUserId());
        }
    }

    /**
     * 清理过期的缓存
     * 可由定时任务调用
     */
    public void cleanupExpiredCache() {
        long now = System.currentTimeMillis();
        List<Long> expiredUserIds = new ArrayList<>();

        for (Map.Entry<Long, Long> entry : userSkillsCacheTimestamp.entrySet()) {
            if ((now - entry.getValue()) > CACHE_EXPIRE_MS) {
                expiredUserIds.add(entry.getKey());
            }
        }

        for (Long userId : expiredUserIds) {
            userSkillsCache.remove(userId);
            userSkillsCacheTimestamp.remove(userId);
        }

        if (!expiredUserIds.isEmpty()) {
            log.info("[用户Skills缓存] 已清理过期缓存: count={}", expiredUserIds.size());
        }
    }
}
