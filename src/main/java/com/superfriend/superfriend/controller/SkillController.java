package com.superfriend.superfriend.controller;

import com.superfriend.superfriend.agent.skill.*;
import com.superfriend.superfriend.agent.skill.Skill;
import com.superfriend.superfriend.constant.SkillErrorCode;
import com.superfriend.superfriend.dto.*;
import com.superfriend.superfriend.entity.*;
import com.superfriend.superfriend.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v16/skills")
@CrossOrigin(origins = "*")
@Tag(name = "Skills 管理", description = "技能注册、查询、执行和管理接口")
public class SkillController {
    
    @Autowired
    private SkillRegistry skillRegistry;

    @Autowired
    private SkillManager skillManager;

    @Autowired
    private SkillExecutor skillExecutor;

    @Autowired
    private ScriptExecutor scriptExecutor;

    @Autowired
    private SkillService skillService;

    @Autowired
    private com.superfriend.superfriend.service.SkillRecommendationService skillRecommendationService;

    @Autowired
    private WorkflowExecutor workflowExecutor;
    
    @Autowired
    private com.superfriend.superfriend.service.OssService ossService;

    @Autowired
    private com.superfriend.superfriend.service.TencentCosService tencentCosService;

    @Autowired
    private com.superfriend.superfriend.service.SkillPackageParserService skillPackageParserService;

    @Value("${skills.system.path:skills/system}")
    private String systemSkillsPath;

    @Value("${skills.project.path:skills/project}")
    private String projectSkillsPath;

    @GetMapping
    @Operation(summary = "获取所有技能", description = "返回所有已注册的技能列表")
    public ApiResponse<List<SkillDTO>> getAllSkills(HttpServletRequest request) {
        try {
            List<SkillDTO> skills = skillRegistry.getAllSkills().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
            
            log.info("获取技能列表，文件系统技能数量: {}", skills.size());
            
            String userIdHeader = request.getHeader("X-User-Id");
            log.info("请求头 X-User-Id: {}", userIdHeader);
            
            if (userIdHeader != null && !userIdHeader.isEmpty()) {
                try {
                    Long userId = Long.parseLong(userIdHeader);
                    log.info("解析用户ID: {}", userId);
                    
                    skillManager.syncUserSelectionStatusFromDatabase(userId);
                    
                    List<UserSkill> userSkillRelations = skillService.findEnabledUserSkillsByUserId(userId);
                    log.info("用户 {} 的技能关联数量: {}", userId, userSkillRelations.size());
                    
                    for (UserSkill us : userSkillRelations) {
                        com.superfriend.superfriend.entity.Skill dbSkill = skillService.findById(us.getSkillId());
                        if (dbSkill != null && dbSkill.getScope() != null && dbSkill.getScope() == 3) {
                            SkillDTO dto = convertEntityToDTO(dbSkill);
                            dto.setIsSelected(us.getIsSelected() != null && us.getIsSelected());
                            log.info("添加用户技能到列表: name={}, category={}, scope={}, isSelected={}", 
                                dto.getName(), dto.getCategory(), dto.getScope(), dto.getIsSelected());
                            skills.add(dto);
                        }
                    }
                    
                    for (SkillDTO dto : skills) {
                        if (dto.getScope() == null || !dto.getScope().equals("USER")) {
                            dto.setIsSelected(skillRegistry.isSkillSelectedByUser(userId, dto.getName()));
                        }
                    }
                } catch (NumberFormatException e) {
                    log.warn("无效的用户 ID 请求头: {}", userIdHeader);
                }
            }
            
            log.info("最终返回技能数量: {}", skills.size());
            return ApiResponse.success(skills);
        } catch (Exception e) {
            log.error("获取技能列表失败", e);
            return ApiResponse.error("获取技能列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{name}")
    @Operation(summary = "获取技能详情", description = "根据名称获取技能详细信息")
    public ApiResponse<SkillDTO> getSkill(@PathVariable String name) {
        try {
            Skill skill = skillRegistry.getSkill(name);
            if (skill == null) {
                Map<String, Object> details = new HashMap<>();
                details.put("skillName", name);
                ApiErrorResponse error = ApiErrorResponse.of(
                    SkillErrorCode.SKILL_NOT_FOUND.getCode(),
                    "技能不存在: " + name,
                    SkillErrorCode.SKILL_NOT_FOUND.getSuggestion(),
                    details
                );
                return ApiResponse.error(error.getMessage());
            }
            return ApiResponse.success(convertToDTO(skill));
        } catch (Exception e) {
            log.error("获取技能详情失败", e);
            ApiErrorResponse error = ApiErrorResponse.of(
                SkillErrorCode.DATABASE_ERROR.getCode(),
                "获取技能详情失败: " + e.getMessage(),
                SkillErrorCode.DATABASE_ERROR.getSuggestion()
            );
            return ApiResponse.error(error.getMessage());
        }
    }

    @GetMapping("/categories")
    @Operation(summary = "获取技能分类", description = "返回所有技能分类")
    public ApiResponse<Set<String>> getCategories() {
        try {
            return ApiResponse.success(skillRegistry.getCategories());
        } catch (Exception e) {
            log.error("获取技能分类失败", e);
            return ApiResponse.error("获取技能分类失败: " + e.getMessage());
        }
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "按分类获取技能", description = "返回指定分类下的所有技能")
    public ApiResponse<List<SkillDTO>> getSkillsByCategory(@PathVariable String category) {
        try {
            List<SkillDTO> skills = skillRegistry.getSkillsByCategory(category).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
            return ApiResponse.success(skills);
        } catch (Exception e) {
            log.error("按分类获取技能失败", e);
            return ApiResponse.error("按分类获取技能失败: " + e.getMessage());
        }
    }

    @GetMapping("/tags")
    @Operation(summary = "获取所有标签", description = "返回所有技能标签")
    public ApiResponse<Set<String>> getTags() {
        try {
            return ApiResponse.success(skillRegistry.getTags());
        } catch (Exception e) {
            log.error("获取技能标签失败", e);
            return ApiResponse.error("获取技能标签失败: " + e.getMessage());
        }
    }

    @GetMapping("/tag/{tag}")
    @Operation(summary = "按标签获取技能", description = "返回包含指定标签的所有技能")
    public ApiResponse<List<SkillDTO>> getSkillsByTag(@PathVariable String tag) {
        try {
            List<SkillDTO> skills = skillRegistry.getSkillsByTag(tag).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
            return ApiResponse.success(skills);
        } catch (Exception e) {
            log.error("按标签获取技能失败", e);
            return ApiResponse.error("按标签获取技能失败: " + e.getMessage());
        }
    }

    @GetMapping("/search")
    @Operation(summary = "搜索技能", description = "根据关键词搜索技能")
    public ApiResponse<List<SkillDTO>> searchSkills(@RequestParam String keyword) {
        try {
            List<SkillDTO> skills = skillRegistry.searchSkills(keyword).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
            return ApiResponse.success(skills);
        } catch (Exception e) {
            log.error("搜索技能失败", e);
            return ApiResponse.error("搜索技能失败: " + e.getMessage());
        }
    }

    @PostMapping("/suggest")
    @Operation(summary = "推荐技能", description = "根据用户请求推荐适用的技能")
    public ApiResponse<List<SkillDTO>> suggestSkills(@RequestBody Map<String, String> request) {
        try {
            String userRequest = request.get("userRequest");
            if (userRequest == null || userRequest.isEmpty()) {
                return ApiResponse.error("用户请求不能为空");
            }

            List<SkillDTO> skills = skillManager.suggestSkills(userRequest).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
            return ApiResponse.success(skills);
        } catch (Exception e) {
            log.error("推荐技能失败", e);
            return ApiResponse.error("推荐技能失败: " + e.getMessage());
        }
    }

    @PostMapping("/execute")
    @Operation(summary = "执行技能", description = "执行指定的技能")
    public ApiResponse<SkillExecutionResponse> executeSkill(
            @RequestBody SkillExecutionRequest request,
            HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("执行技能请求: skillName={}, sessionId={}", request.getSkillName(), request.getSessionId());

            Long userId = null;
            String userIdHeader = httpRequest.getHeader("X-User-Id");
            if (userIdHeader != null && !userIdHeader.isEmpty()) {
                try {
                    userId = Long.parseLong(userIdHeader);
                } catch (NumberFormatException e) {
                    log.warn("无效的用户 ID 请求头: {}", userIdHeader);
                }
            }
            
            if (userId == null && request.getUserId() != null && !request.getUserId().isEmpty()) {
                try {
                    userId = Long.parseLong(request.getUserId());
                } catch (NumberFormatException e) {
                    log.warn("无效的用户 ID: {}", request.getUserId());
                }
            }

            SkillContext context = new SkillContext();
            context.setSessionId(request.getSessionId());
            context.setUserId(userId != null ? userId.toString() : null);
            context.setUserRequest(request.getUserRequest());
            context.setParameters(request.getParameters());
            context.setVariables(request.getVariables());
            context.setExecutionContext(new SkillContext.SkillExecutionContext());

            SkillResult result = skillExecutor.execute(request.getSkillName(), context, userId);
            
            if (result.isSuccess() && result.getData() != null) {
                result = processSkillFileOutput(result);
            }

            SkillExecutionResponse response;
            if (result.isSuccess()) {
                response = SkillExecutionResponse.success(result.getData(), result.getExecutionTime());
            } else {
                response = SkillExecutionResponse.failure(result.getError(), result.getExecutionTime());
                response.setErrorCode(SkillErrorCode.EXECUTION_FAILED.getCode());
                response.setSuggestion(SkillErrorCode.EXECUTION_FAILED.getSuggestion());
            }

            log.info("技能执行完成: skillName={}, success={}, time={}ms",
                request.getSkillName(), result.isSuccess(), result.getExecutionTime());

            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("执行技能失败", e);
            long executionTime = System.currentTimeMillis() - startTime;
            SkillExecutionResponse response = SkillExecutionResponse.failure(e.getMessage(), executionTime);
            response.setErrorCode(SkillErrorCode.EXECUTION_FAILED.getCode());
            response.setSuggestion(SkillErrorCode.EXECUTION_FAILED.getSuggestion());
            return ApiResponse.success(response);
        }
    }

    @PostMapping("/create")
    @Operation(summary = "创建技能", description = "创建新的自定义技能")
    public ApiResponse<String> createSkill(@RequestBody SkillConfig config,
                                           @RequestParam(required = false) Long userId) {
        try {
            if (config.getName() == null || config.getName().trim().isEmpty()) {
                return ApiResponse.error("技能名称不能为空");
            }
            
            com.superfriend.superfriend.entity.Skill existingSkill = skillService.findByName(config.getName());
            if (existingSkill != null) {
                return ApiResponse.error("技能名称已存在: " + config.getName());
            }
            
            if (skillRegistry.hasSkill(config.getName())) {
                return ApiResponse.error("技能名称已被文件系统技能占用: " + config.getName());
            }
            
            com.superfriend.superfriend.entity.Skill skillEntity = new com.superfriend.superfriend.entity.Skill();
            skillEntity.setName(config.getName());
            skillEntity.setDescription(config.getDescription());
            skillEntity.setCategory(config.getCategory());
            skillEntity.setVersion(config.getVersion() != null ? config.getVersion() : "1.0.0");
            skillEntity.setAuthor(config.getAuthor() != null ? config.getAuthor() : "user");
            skillEntity.setInstructions(config.getInstructions());
            skillEntity.setParameters(config.getParameters());
            skillEntity.setAllowedTools(config.getAllowedTools());
            skillEntity.setTags(config.getTags());
            skillEntity.setTimeout(60000L);
            
            if (config.getPriority() != null) {
                skillEntity.setPriority(config.getPriority().ordinal() + 1);
            } else {
                skillEntity.setPriority(3);
            }
            
            if (config.getScripts() != null && !config.getScripts().isEmpty()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    skillEntity.setScriptsJson(mapper.writeValueAsString(config.getScripts()));
                } catch (Exception e) {
                    log.warn("序列化脚本失败: {}", e.getMessage());
                }
            }
            
            if (config.getResources() != null && !config.getResources().isEmpty()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    skillEntity.setResourcesJson(mapper.writeValueAsString(config.getResources()));
                } catch (Exception e) {
                    log.warn("序列化资源失败: {}", e.getMessage());
                }
            }
            
            skillEntity.setScope(3);
            skillEntity.setStatus(1);
            
            if (userId == null) {
                return ApiResponse.error("创建技能需要提供用户ID");
            }
            skillService.createUserSkill(skillEntity, userId);
            
            log.info("用户级技能已保存到数据库，不全局注册: {}", config.getName());
            
            return ApiResponse.success("技能创建成功: " + config.getName());
        } catch (Exception e) {
            log.error("创建技能失败", e);
            return ApiResponse.error("创建技能失败: " + e.getMessage());
        }
    }

    @PostMapping("/update")
    @Operation(summary = "更新技能", description = "更新现有技能的信息")
    public ApiResponse<String> updateSkill(@RequestBody SkillConfig config,
                                           @RequestParam String skillName,
                                           @RequestParam(required = false) Long userId,
                                           HttpServletRequest request) {
        try {
            log.info("收到更新技能请求: name={}, userId={}", skillName, userId);
            
            com.superfriend.superfriend.entity.Skill existingSkill = skillService.findByName(skillName);
            
            if (existingSkill == null) {
                return ApiResponse.error("技能不存在: " + skillName);
            }
            
            if (!skillName.equals(config.getName())) {
                log.warn("尝试修改技能名称: {} -> {}, 不允许修改名称", skillName, config.getName());
                return ApiResponse.error("不允许修改技能名称");
            }
            
            log.info("找到技能: id={}, name={}, scope={}", existingSkill.getId(), existingSkill.getName(), existingSkill.getScope());
            
            if (existingSkill.getScope() == 1) {
                return ApiResponse.error("系统技能不允许修改");
            }
            
            if (existingSkill.getScope() == 3) {
                if (userId == null) {
                    String userIdHeader = request.getHeader("X-User-Id");
                    if (userIdHeader != null && !userIdHeader.isEmpty()) {
                        userId = Long.parseLong(userIdHeader);
                        log.info("从请求头获取用户ID: {}", userId);
                    }
                }
                if (userId == null) {
                    return ApiResponse.error("用户技能需要提供用户ID");
                }
                com.superfriend.superfriend.entity.UserSkill userSkill = skillService.findUserSkill(userId, existingSkill.getId());
                if (userSkill == null) {
                    return ApiResponse.error("无权限修改此用户技能");
                }
                log.info("用户权限验证通过: userId={}, skillId={}", userId, existingSkill.getId());
            }
            
            com.superfriend.superfriend.entity.Skill skillEntity = new com.superfriend.superfriend.entity.Skill();
            skillEntity.setId(existingSkill.getId());
            skillEntity.setName(existingSkill.getName());
            skillEntity.setDescription(config.getDescription());
            skillEntity.setCategory(config.getCategory());
            skillEntity.setVersion(config.getVersion() != null ? config.getVersion() : existingSkill.getVersion());
            skillEntity.setAuthor(config.getAuthor() != null ? config.getAuthor() : existingSkill.getAuthor());
            skillEntity.setLicense(config.getLicense() != null ? config.getLicense() : existingSkill.getLicense());
            skillEntity.setCompatibility(config.getCompatibility() != null ? config.getCompatibility() : existingSkill.getCompatibility());
            skillEntity.setInstructions(config.getInstructions());
            skillEntity.setParameters(config.getParameters());
            skillEntity.setAllowedTools(config.getAllowedTools());
            skillEntity.setTags(config.getTags());
            skillEntity.setTimeout(config.getTimeout() > 0 ? config.getTimeout() : (existingSkill.getTimeout() != null ? existingSkill.getTimeout() : 60000L));
            skillEntity.setScope(existingSkill.getScope());
            skillEntity.setStatus(1);
            
            if (config.getPriority() != null) {
                skillEntity.setPriority(config.getPriority().ordinal() + 1);
            } else {
                skillEntity.setPriority(existingSkill.getPriority() != null ? existingSkill.getPriority() : 3);
            }
            
            if (config.getScripts() != null && !config.getScripts().isEmpty()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    skillEntity.setScriptsJson(mapper.writeValueAsString(config.getScripts()));
                } catch (Exception e) {
                    log.warn("序列化脚本失败: {}", e.getMessage());
                }
            } else {
                skillEntity.setScriptsJson(existingSkill.getScriptsJson());
            }
            
            if (config.getResources() != null && !config.getResources().isEmpty()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    skillEntity.setResourcesJson(mapper.writeValueAsString(config.getResources()));
                } catch (Exception e) {
                    log.warn("序列化资源失败: {}", e.getMessage());
                }
            } else {
                skillEntity.setResourcesJson(existingSkill.getResourcesJson());
            }
            
            log.info("准备更新技能: id={}, name={}", skillEntity.getId(), skillEntity.getName());
            
            skillService.updateSkill(skillEntity);
            
            log.info("技能更新成功: id={}", skillEntity.getId());

            if (existingSkill.getScope() != 3) {
                skillRegistry.unregister(skillName);
                Skill agentSkill = new DatabaseSkill(skillEntity);
                skillRegistry.register(agentSkill);
            } else {
                // 用户技能更新后清除缓存，确保立即生效
                if (userId != null) {
                    skillRegistry.clearUserSkillsCache(userId);
                    log.info("已清除用户 {} 的Skills缓存", userId);
                }
            }

            if (existingSkill.getScope() == 2) {
                skillManager.reloadSkills();
            }

            return ApiResponse.success("技能更新成功: " + skillName);
        } catch (Exception e) {
            log.error("更新技能失败", e);
            return ApiResponse.error("更新技能失败: " + e.getMessage());
        }
    }

    @PostMapping("/update/scripts")
    @Operation(summary = "更新技能脚本", description = "更新技能的脚本内容")
    public ApiResponse<String> updateSkillScripts(
            @RequestParam String skillName,
            @RequestParam String scriptType,
            @RequestBody String scriptContent,
            @RequestParam(required = false) Long userId) {
        try {
            com.superfriend.superfriend.entity.Skill existingSkill = skillService.findByName(skillName);

            if (existingSkill == null) {
                return ApiResponse.error("技能不存在: " + skillName);
            }

            if (existingSkill.getScope() == 1) {
                return ApiResponse.error("系统技能不允许修改");
            }

            if (existingSkill.getScope() == 3 && userId == null) {
                return ApiResponse.error("用户技能需要提供用户ID");
            }

            // 将脚本保存到 scripts_json
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                List<Map<String, Object>> scriptsList = new ArrayList<>();

                // 解析现有的 scripts_json
                if (existingSkill.getScriptsJson() != null && !existingSkill.getScriptsJson().isEmpty()) {
                    scriptsList = mapper.readValue(existingSkill.getScriptsJson(), List.class);
                }

                // 查找是否已存在同类型的脚本
                boolean found = false;
                for (Map<String, Object> scriptMap : scriptsList) {
                    if (scriptType.equals(scriptMap.get("scriptType"))) {
                        scriptMap.put("scriptContent", scriptContent);
                        found = true;
                        break;
                    }
                }

                // 如果不存在，添加新脚本
                if (!found) {
                    Map<String, Object> newScript = new HashMap<>();
                    newScript.put("scriptType", scriptType);
                    newScript.put("scriptName", getScriptFileName(scriptType));
                    newScript.put("scriptContent", scriptContent);
                    newScript.put("isMain", scriptsList.isEmpty());
                    newScript.put("executionOrder", scriptsList.size());
                    scriptsList.add(newScript);
                }

                // 更新 scripts_json
                existingSkill.setScriptsJson(mapper.writeValueAsString(scriptsList));
                skillService.updateSkill(existingSkill);

                log.info("脚本已保存到 scripts_json: skill={}, type={}", skillName, scriptType);
            } catch (Exception e) {
                log.error("保存脚本失败: {}", e.getMessage());
                return ApiResponse.error("保存脚本失败: " + e.getMessage());
            }

            return ApiResponse.success("脚本更新成功");
        } catch (Exception e) {
            log.error("更新技能脚本失败", e);
            return ApiResponse.error("更新技能脚本失败: " + e.getMessage());
        }
    }

    private String getScriptFileName(String scriptType) {
        switch (scriptType.toLowerCase()) {
            case "python": return "main.py";
            case "bash": return "main.sh";
            case "javascript": return "main.js";
            case "typescript": return "main.ts";
            case "ruby": return "main.rb";
            case "powershell": return "main.ps1";
            default: return "main.py";
        }
    }

    @DeleteMapping("/{name}")
    @Operation(summary = "删除技能", description = "删除指定的自定义技能")
    public ApiResponse<String> deleteSkill(@PathVariable String name,
                                           @RequestParam(required = false) Long userId,
                                           HttpServletRequest request) {
        try {
            com.superfriend.superfriend.entity.Skill existingSkill = skillService.findByName(name);
            
            if (existingSkill == null) {
                return ApiResponse.error("技能不存在: " + name);
            }
            
            if (existingSkill.getScope() == 1) {
                return ApiResponse.error("系统技能不允许删除");
            }
            
            if (existingSkill.getScope() == 3) {
                if (userId == null) {
                    String userIdHeader = request.getHeader("X-User-Id");
                    if (userIdHeader != null && !userIdHeader.isEmpty()) {
                        userId = Long.parseLong(userIdHeader);
                    }
                }
                if (userId == null) {
                    return ApiResponse.error("用户技能需要提供用户ID");
                }
                com.superfriend.superfriend.entity.UserSkill userSkill = skillService.findUserSkill(userId, existingSkill.getId());
                if (userSkill == null) {
                    return ApiResponse.error("无权限删除此用户技能");
                }
            }
            
            skillService.deleteSkillCascade(existingSkill.getId());

            if (existingSkill.getScope() == 2) {
                try {
                    skillManager.deleteSkillFiles(name);
                } catch (Exception e) {
                    log.warn("删除技能文件失败，但数据库记录已删除: {}", e.getMessage());
                }
            }

            skillRegistry.unregister(name);

            // 清除用户Skills缓存，确保删除后立即生效
            if (existingSkill.getScope() == 3 && userId != null) {
                skillRegistry.clearUserSkillsCache(userId);
                log.info("已清除用户 {} 的Skills缓存", userId);
            }

            log.info("技能删除成功: {} (scope={})", name, existingSkill.getScope());
            return ApiResponse.success("技能删除成功: " + name);
        } catch (Exception e) {
            log.error("删除技能失败", e);
            return ApiResponse.error("删除技能失败: " + e.getMessage());
        }
    }

    @PostMapping("/reload")
    @Operation(summary = "重新加载技能", description = "重新加载所有技能")
    public ApiResponse<String> reloadSkills() {
        try {
            skillManager.reloadSkills();
            return ApiResponse.success("技能重新加载成功，共 " + skillRegistry.getSkillCount() + " 个技能");
        } catch (Exception e) {
            log.error("重新加载技能失败", e);
            return ApiResponse.error("重新加载技能失败: " + e.getMessage());
        }
    }

    @GetMapping("/stats")
    @Operation(summary = "获取技能统计", description = "返回技能系统的统计信息")
    public ApiResponse<Map<String, Object>> getSkillStats(HttpServletRequest request) {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            int fileSystemSkillCount = skillRegistry.getSkillCount();
            int categoriesCount = skillRegistry.getCategories().size();
            int tagsCount = skillRegistry.getTags().size();
            
            List<Skill> fileSystemSkills = skillRegistry.getAllSkills();
            long totalExecutions = fileSystemSkills.stream()
                .mapToLong(s -> s.getMetadata().getExecutionCount())
                .sum();
            double totalSuccessRate = fileSystemSkills.stream()
                .mapToDouble(s -> s.getMetadata().getSuccessRate())
                .sum();
            int skillCountForAvg = fileSystemSkills.size();

            String userIdHeader = request.getHeader("X-User-Id");
            if (userIdHeader != null && !userIdHeader.isEmpty()) {
                try {
                    Long userId = Long.parseLong(userIdHeader);
                    List<UserSkill> userSkillRelations = skillService.findEnabledUserSkillsByUserId(userId);
                    
                    for (UserSkill us : userSkillRelations) {
                        com.superfriend.superfriend.entity.Skill s = skillService.findById(us.getSkillId());
                        if (s != null) {
                            fileSystemSkillCount++;
                            if (s.getCategory() != null) {
                                categoriesCount++;
                            }
                            if (s.getTags() != null) {
                                tagsCount += s.getTags().size();
                            }
                            if (s.getExecutionCount() != null) {
                                totalExecutions += s.getExecutionCount();
                            }
                            if (s.getSuccessRate() != null) {
                                totalSuccessRate += s.getSuccessRate();
                                skillCountForAvg++;
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    log.warn("无效的用户 ID 请求头: {}", userIdHeader);
                }
            }
            
            stats.put("totalSkills", fileSystemSkillCount);
            stats.put("categories", categoriesCount);
            stats.put("tags", tagsCount);
            stats.put("totalExecutions", totalExecutions);
            stats.put("averageSuccessRate", skillCountForAvg > 0 ? totalSuccessRate / skillCountForAvg : 0.0);

            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("获取技能统计失败", e);
            return ApiResponse.error("获取技能统计失败: " + e.getMessage());
        }
    }

    private SkillDTO convertToDTO(Skill skill) {
        SkillDTO dto = new SkillDTO();
        SkillMetadata metadata = skill.getMetadata();

        dto.setName(skill.getName());
        dto.setDescription(skill.getDescription());
        dto.setCategory(skill.getCategory());
        dto.setVersion(skill.getVersion());
        dto.setAuthor(metadata.getAuthor());
        dto.setTags(skill.getTags() != null ? skill.getTags() : new ArrayList<>());
        dto.setAllowedTools(skill.getAllowedTools() != null ? skill.getAllowedTools() : new ArrayList<>());
        dto.setInstructions(skill.getInstructions());
        dto.setParameters(skill.getParameters());
        dto.setPriority(metadata.getPriority().name());
        dto.setScope(metadata.getScope().name());
        dto.setExecutionCount(metadata.getExecutionCount());
        dto.setSuccessRate(metadata.getSuccessRate());
        dto.setAverageExecutionTime(metadata.getAverageExecutionTime());
        dto.setIsSelected(skillRegistry.isSkillSelected(skill.getName()));

        // 加载文件系统技能的脚本和资源
        loadFileSystemSkillFiles(skill.getName(), dto);

        return dto;
    }
    
    private SkillDTO convertEntityToDTO(com.superfriend.superfriend.entity.Skill skill) {
        SkillDTO dto = new SkillDTO();

        dto.setName(skill.getName());
        dto.setDescription(skill.getDescription());
        dto.setCategory(skill.getCategory());
        dto.setVersion(skill.getVersion());
        dto.setAuthor(skill.getAuthor());
        dto.setTags(skill.getTags() != null ? skill.getTags() : new ArrayList<>());
        dto.setAllowedTools(skill.getAllowedTools() != null ? skill.getAllowedTools() : new ArrayList<>());
        dto.setInstructions(skill.getInstructions());
        dto.setParameters(skill.getParameters());

        String priorityStr = "MEDIUM";
        if (skill.getPriority() != null) {
            switch (skill.getPriority()) {
                case 1: priorityStr = "LOW"; break;
                case 5: priorityStr = "MEDIUM"; break;
                case 8: priorityStr = "HIGH"; break;
                case 10: priorityStr = "CRITICAL"; break;
            }
        }
        dto.setPriority(priorityStr);

        String scopeStr = "PROJECT";
        if (skill.getScope() != null) {
            switch (skill.getScope()) {
                case 1: scopeStr = "SYSTEM"; break;
                case 2: scopeStr = "PROJECT"; break;
                case 3: scopeStr = "USER"; break;
            }
        }
        dto.setScope(scopeStr);

        dto.setExecutionCount(skill.getExecutionCount() != null ? skill.getExecutionCount() : 0L);
        dto.setSuccessRate(skill.getSuccessRate() != null ? skill.getSuccessRate() : 1.0);
        dto.setAverageExecutionTime(skill.getAverageExecutionTime() != null ? skill.getAverageExecutionTime() : 0L);
        dto.setIsSelected(skill.getIsSelected() != null && skill.getIsSelected());

        // 解析数据库技能的脚本和资源
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            if (skill.getScriptsJson() != null && !skill.getScriptsJson().isEmpty()) {
                dto.setScripts(mapper.readValue(skill.getScriptsJson(),
                    mapper.getTypeFactory().constructCollectionType(List.class, Map.class)));
            }
            if (skill.getResourcesJson() != null && !skill.getResourcesJson().isEmpty()) {
                dto.setResources(mapper.readValue(skill.getResourcesJson(),
                    mapper.getTypeFactory().constructCollectionType(List.class, Map.class)));
            }
        } catch (Exception e) {
            log.warn("解析技能脚本/资源 JSON 失败: {}", skill.getName(), e);
        }

        // 对文件系统类型的数据库技能也尝试加载文件
        if (skill.getSkillFilePath() != null && !skill.getSkillFilePath().isEmpty()) {
            loadFileSystemSkillFiles(skill.getName(), dto);
        }

        return dto;
    }

    private void loadFileSystemSkillFiles(String skillName, SkillDTO dto) {
        try {
            String basePath = skillRegistry.getSkillBasePath(skillName);
            if (basePath == null) return;
            File skillDir = new File(basePath);
            if (!skillDir.exists() || !skillDir.isDirectory()) return;

            List<Map<String, Object>> scripts = new ArrayList<>();
            List<Map<String, Object>> resources = new ArrayList<>();

            // 递归加载 scripts/ 目录下的脚本文件
            File scriptsDir = new File(skillDir, "scripts");
            if (scriptsDir.exists() && scriptsDir.isDirectory()) {
                collectScriptFiles(scriptsDir, scriptsDir, scripts);
            }

            // 递归加载 references/ 目录下的资源文件
            File refsDir = new File(skillDir, "references");
            if (refsDir.exists() && refsDir.isDirectory()) {
                collectResourceFiles(refsDir, refsDir, resources);
            }

            // 递归加载 assets/ 目录下的资源文件
            File assetsDir = new File(skillDir, "assets");
            if (assetsDir.exists() && assetsDir.isDirectory()) {
                collectResourceFiles(assetsDir, assetsDir, resources);
            }

            // 递归加载 templates/ 目录下的资源文件
            File templatesDir = new File(skillDir, "templates");
            if (templatesDir.exists() && templatesDir.isDirectory()) {
                collectResourceFiles(templatesDir, templatesDir, resources, "template");
            }

            if (!scripts.isEmpty()) dto.setScripts(scripts);
            if (!resources.isEmpty()) dto.setResources(resources);
        } catch (Exception e) {
            log.warn("加载技能文件信息失败: {}", skillName, e);
        }
    }

    private String getScriptExtension(String filename) {
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        switch (ext) {
            case "py": return "python";
            case "sh": return "bash";
            case "js": return "javascript";
            case "ts": return "typescript";
            case "csx": return "csharp";
            case "ps1": return "powershell";
            default: return ext;
        }
    }

    private String getResourceTypeFromExt(String filename) {
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        switch (ext) {
            case "md": return "document";
            case "html": case "htm": return "template";
            case "json": case "yaml": case "yml": return "config";
            case "png": case "jpg": case "jpeg": case "gif": case "svg": case "webp": return "image";
            default: return "other";
        }
    }

    private boolean isTextFile(String filename) {
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return ext.equals("md") || ext.equals("txt") || ext.equals("json") ||
               ext.equals("yaml") || ext.equals("yml") || ext.equals("html") ||
               ext.equals("htm") || ext.equals("css") || ext.equals("js") ||
               ext.equals("ts") || ext.equals("py") || ext.equals("sh") ||
               ext.equals("xml") || ext.equals("csv");
    }

    private static final List<String> SCRIPT_EXTENSIONS = Arrays.asList(
        ".py", ".sh", ".js", ".ts", ".csx", ".ps1", ".rb", ".java"
    );

    private void collectScriptFiles(File baseDir, File currentDir, List<Map<String, Object>> scripts) {
        File[] files = currentDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectScriptFiles(baseDir, f, scripts);
            } else if (isScriptFile(f.getName())) {
                try {
                    String relativePath = baseDir.toPath().relativize(f.toPath()).toString().replace('\\', '/');
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("scriptName", f.getName().replaceFirst("\\.[^.]+$", ""));
                    s.put("scriptType", getScriptExtension(f.getName()));
                    s.put("isMain", f.getName().startsWith("main.") || f.getName().startsWith("index."));
                    s.put("filePath", "scripts/" + relativePath);
                    s.put("fileSize", f.length());
                    if (f.length() <= 512 * 1024) {
                        s.put("scriptContent", new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8"));
                    }
                    scripts.add(s);
                } catch (Exception e) {
                    log.warn("读取脚本文件失败: {}", f.getAbsolutePath(), e);
                }
            }
        }
    }

    private boolean isScriptFile(String name) {
        String lower = name.toLowerCase();
        for (String ext : SCRIPT_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private void collectResourceFiles(File baseDir, File currentDir, List<Map<String, Object>> resources) {
        collectResourceFiles(baseDir, currentDir, resources, null);
    }

    private void collectResourceFiles(File baseDir, File currentDir, List<Map<String, Object>> resources, String forceType) {
        File[] files = currentDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectResourceFiles(baseDir, f, resources, forceType);
            } else if (f.isFile()) {
                try {
                    String relativePath = baseDir.toPath().relativize(f.toPath()).toString().replace('\\', '/');
                    String dirName = baseDir.getName();
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("resourceName", f.getName());
                    r.put("resourceType", forceType != null ? forceType : getResourceTypeFromExt(f.getName()));
                    r.put("filePath", dirName + "/" + relativePath);
                    r.put("fileSize", f.length());
                    if (isTextFile(f.getName()) && f.length() <= 512 * 1024) {
                        r.put("resourceContent", new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8"));
                    }
                    resources.add(r);
                } catch (Exception e) {
                    log.warn("读取资源文件失败: {}", f.getAbsolutePath(), e);
                }
            }
        }
    }

    @Operation(summary = "执行脚本", description = "直接执行指定路径的脚本文件")
    public ApiResponse<ScriptExecutionResponse> executeScript(@RequestBody ScriptExecutionRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("执行脚本请求: scriptPath={}, timeout={}ms", 
                request.getScriptPath(), request.getTimeout());

            File workingDir = request.getWorkingDirectory() != null 
                ? new File(request.getWorkingDirectory()) 
                : new File(System.getProperty("user.dir"));

            long timeout = request.getTimeout() > 0 ? request.getTimeout() : 60000L;

            ScriptExecutor.ScriptExecutionResult result = scriptExecutor.executeScript(
                request.getScriptPath(),
                request.getParameters(),
                workingDir,
                timeout
            );

            ScriptExecutionResponse response = new ScriptExecutionResponse();
            response.setSuccess(result.isSuccess());
            response.setOutput(result.getOutput());
            response.setError(result.getError());
            response.setExitCode(result.getExitCode());
            response.setExecutionTime(result.getExecutionTime());
            response.setParsedOutput(result.getParsedOutput());

            log.info("脚本执行完成: scriptPath={}, success={}, time={}ms",
                request.getScriptPath(), result.isSuccess(), result.getExecutionTime());

            return ApiResponse.success(response);

        } catch (Exception e) {
            log.error("执行脚本失败", e);
            long executionTime = System.currentTimeMillis() - startTime;
            
            ScriptExecutionResponse response = new ScriptExecutionResponse();
            response.setSuccess(false);
            response.setError("脚本执行异常: " + e.getMessage());
            response.setExecutionTime(executionTime);
            
            return ApiResponse.success(response);
        }
    }

    @GetMapping("/{name}/scripts")
    @Operation(summary = "获取技能脚本列表", description = "获取指定技能的所有脚本文件")
    public ApiResponse<List<ScriptInfo>> getSkillScripts(@PathVariable String name) {
        try {
            Skill skill = skillRegistry.getSkill(name);
            if (skill == null) {
                return ApiResponse.error("技能不存在: " + name);
            }

            if (!(skill instanceof DynamicSkill)) {
                return ApiResponse.success(Collections.emptyList());
            }

            DynamicSkill dynamicSkill = (DynamicSkill) skill;
            File skillDir = dynamicSkill.getSkillDirectory();
            File scriptsDir = new File(skillDir, "scripts");

            if (!scriptsDir.exists() || !scriptsDir.isDirectory()) {
                return ApiResponse.success(Collections.emptyList());
            }

            List<ScriptInfo> scripts = new ArrayList<>();
            File[] scriptFiles = scriptsDir.listFiles((dir, fileName) -> 
                fileName.endsWith(".py") || fileName.endsWith(".sh") || 
                fileName.endsWith(".js") || fileName.endsWith(".ts")
            );

            if (scriptFiles != null) {
                for (File scriptFile : scriptFiles) {
                    ScriptInfo info = new ScriptInfo();
                    info.setName(scriptFile.getName());
                    info.setPath(scriptFile.getAbsolutePath());
                    info.setSize(scriptFile.length());
                    info.setLanguage(detectScriptLanguage(scriptFile.getName()));
                    scripts.add(info);
                }
            }

            return ApiResponse.success(scripts);

        } catch (Exception e) {
            log.error("获取技能脚本列表失败", e);
            return ApiResponse.error("获取技能脚本列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/{name}/scripts/execute")
    @Operation(summary = "执行技能脚本", description = "执行指定技能的脚本")
    public ApiResponse<ScriptExecutionResponse> executeSkillScript(
            @PathVariable String name,
            @RequestBody Map<String, Object> parameters) {
        
        long startTime = System.currentTimeMillis();
        try {
            Skill skill = skillRegistry.getSkill(name);
            if (skill == null) {
                return ApiResponse.error("技能不存在: " + name);
            }

            if (!(skill instanceof DynamicSkill)) {
                return ApiResponse.error("该技能不支持脚本执行");
            }

            DynamicSkill dynamicSkill = (DynamicSkill) skill;
            SkillConfig config = dynamicSkill.getConfig();

            if (!config.isHasScripts()) {
                return ApiResponse.error("该技能没有可执行的脚本");
            }

            File skillDir = dynamicSkill.getSkillDirectory();
            File scriptsDir = new File(skillDir, "scripts");
            File mainScript = findMainScript(scriptsDir);

            if (mainScript == null) {
                return ApiResponse.error("未找到主脚本文件");
            }

            long timeout = config.getTimeout() > 0 ? config.getTimeout() : 60000L;

            ScriptExecutor.ScriptExecutionResult result = scriptExecutor.executeScript(
                mainScript.getAbsolutePath(),
                parameters,
                skillDir,
                timeout
            );

            ScriptExecutionResponse response = new ScriptExecutionResponse();
            response.setSuccess(result.isSuccess());
            response.setOutput(result.getOutput());
            response.setError(result.getError());
            response.setExitCode(result.getExitCode());
            response.setExecutionTime(result.getExecutionTime());
            response.setParsedOutput(result.getParsedOutput());

            log.info("技能脚本执行完成: skillName={}, script={}, success={}, time={}ms",
                name, mainScript.getName(), result.isSuccess(), result.getExecutionTime());

            return ApiResponse.success(response);

        } catch (Exception e) {
            log.error("执行技能脚本失败", e);
            long executionTime = System.currentTimeMillis() - startTime;
            
            ScriptExecutionResponse response = new ScriptExecutionResponse();
            response.setSuccess(false);
            response.setError("脚本执行异常: " + e.getMessage());
            response.setExecutionTime(executionTime);
            
            return ApiResponse.success(response);
        }
    }

    private String detectScriptLanguage(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "py":
                return "Python";
            case "sh":
                return "Bash";
            case "js":
                return "JavaScript";
            case "ts":
                return "TypeScript";
            case "rb":
                return "Ruby";
            default:
                return "Unknown";
        }
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

    @Data
    public static class ScriptExecutionRequest {
        private String scriptPath;
        private Map<String, Object> parameters = new HashMap<>();
        private String workingDirectory;
        private long timeout = 60000;
    }

    @Data
    public static class ScriptExecutionResponse {
        private boolean success;
        private String output;
        private String error;
        private int exitCode;
        private long executionTime;
        private Map<String, Object> parsedOutput;
    }

    @Data
    public static class ScriptInfo {
        private String name;
        private String path;
        private long size;
        private String language;
    }

    @Data
    public static class SkillsPathConfig {
        private String systemPath;
        private String userPath;
        private String projectPath;
    }

    @GetMapping("/paths")
    @Operation(summary = "获取技能路径配置", description = "返回所有技能目录的路径配置")
    public ApiResponse<SkillsPathConfig> getSkillsPaths() {
        try {
            SkillsPathConfig config = new SkillsPathConfig();
            config.setSystemPath(systemSkillsPath);
            config.setProjectPath(projectSkillsPath);
            return ApiResponse.success(config);
        } catch (Exception e) {
            log.error("获取技能路径配置失败", e);
            return ApiResponse.error("获取技能路径配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/paths")
    @Operation(summary = "更新技能路径配置", description = "更新技能目录的路径配置")
    public ApiResponse<String> updateSkillsPaths(@RequestBody SkillsPathConfig config) {
        try {
            skillManager.updateSkillsPaths(
                config.getSystemPath(), 
                config.getProjectPath()
            );
            
            log.info("更新技能路径配置: system={}, project={}", 
                config.getSystemPath(), config.getProjectPath());
            
            return ApiResponse.success("技能路径配置已更新");
        } catch (Exception e) {
            log.error("更新技能路径配置失败", e);
            return ApiResponse.error("更新技能路径配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/recommend")
    @Operation(summary = "推荐技能", description = "根据用户消息推荐相关技能")
    public ApiResponse<List<com.superfriend.superfriend.service.SkillRecommendationService.SkillRecommendation>> recommendSkills(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        try {
            String message = (String) request.get("message");
            if (message == null || message.isEmpty()) {
                return ApiResponse.error("消息不能为空");
            }

            String modelName = (String) request.get("model");

            Long userId = null;
            String userIdHeader = httpRequest.getHeader("X-User-Id");
            if (userIdHeader != null && !userIdHeader.isEmpty()) {
                try {
                    userId = Long.parseLong(userIdHeader);
                } catch (NumberFormatException e) {
                    log.warn("无效的用户ID: {}", userIdHeader);
                }
            }
            
            List<com.superfriend.superfriend.service.SkillRecommendationService.SkillRecommendation> recommendations =
                skillRecommendationService.recommendSkills(message, userId, modelName);
            
            log.info("为消息 '{}' 推荐 {} 个技能", message, recommendations.size());
            
            return ApiResponse.success(recommendations);
        } catch (Exception e) {
            log.error("推荐技能失败", e);
            return ApiResponse.error("推荐技能失败: " + e.getMessage());
        }
    }

    @PutMapping("/{name}/selection")
    @Operation(summary = "更新技能选择状态", description = "更新用户技能是否被选中")
    public ApiResponse<Void> updateSkillSelection(
            @PathVariable String name,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        try {
            log.info("收到更新技能选择状态请求: name={}, request={}", name, request);
            
            Boolean isSelected = (Boolean) request.get("isSelected");
            if (isSelected == null) {
                log.warn("缺少参数：isSelected");
                return ApiResponse.error("缺少参数：isSelected");
            }
            
            Long userId = null;
            Object userIdObj = request.get("userId");
            if (userIdObj != null) {
                if (userIdObj instanceof Number) {
                    userId = ((Number) userIdObj).longValue();
                } else if (userIdObj instanceof String) {
                    userId = Long.parseLong((String) userIdObj);
                }
            }
            
            if (userId == null) {
                String userIdHeader = httpRequest.getHeader("X-User-Id");
                if (userIdHeader != null && !userIdHeader.isEmpty()) {
                    userId = Long.parseLong(userIdHeader);
                }
            }
            
            if (userId == null) {
                log.warn("缺少用户ID");
                return ApiResponse.error("缺少用户ID，请先登录");
            }
            
            com.superfriend.superfriend.entity.Skill dbSkill = skillService.findByName(name);
            
            if (dbSkill == null && skillRegistry.hasSkill(name)) {
                log.info("技能 {} 不在数据库中，但在内存中存在，更新内存选择状态", name);
                skillRegistry.updateUserSelectionStatus(userId, name, isSelected);
                log.info("用户 {} 的技能 {} 选择状态已更新为: {} (仅内存)", userId, name, isSelected);
                return ApiResponse.success(null);
            }
            
            if (dbSkill == null) {
                log.warn("技能不存在: {}", name);
                return ApiResponse.error("技能不存在: " + name);
            }
            
            log.info("找到技能: id={}, name={}, scope={}", 
                dbSkill.getId(), dbSkill.getName(), dbSkill.getScope());
            
            skillService.updateUserSkillSelectionStatus(userId, dbSkill.getId(), isSelected);
            
            skillRegistry.updateUserSelectionStatus(userId, name, isSelected);
            
            log.info("用户 {} 的技能 {} 选择状态已更新为: {} (数据库)", userId, name, isSelected);
            
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("更新技能选择状态失败", e);
            return ApiResponse.error("更新技能选择状态失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/user/selected")
    @Operation(summary = "获取用户选中的技能", description = "返回当前用户选中的技能列表")
    public ApiResponse<List<SkillDTO>> getUserSelectedSkills(HttpServletRequest request) {
        try {
            Long userId = null;
            String userIdHeader = request.getHeader("X-User-Id");
            if (userIdHeader != null && !userIdHeader.isEmpty()) {
                userId = Long.parseLong(userIdHeader);
            }
            
            if (userId == null) {
                return ApiResponse.error("缺少用户ID");
            }
            
            List<Long> selectedSkillIds = skillService.findSelectedSkillIdsByUserId(userId);
            List<SkillDTO> selectedSkills = selectedSkillIds.stream()
                .map(skillId -> skillService.findById(skillId))
                .filter(Objects::nonNull)
                .map(this::convertEntityToDTO)
                .collect(Collectors.toList());
            
            log.info("获取用户 {} 选中的技能列表，数量: {}", userId, selectedSkills.size());
            return ApiResponse.success(selectedSkills);
        } catch (Exception e) {
            log.error("获取用户选中技能列表失败", e);
            return ApiResponse.error("获取用户选中技能列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/selected")
    @Operation(summary = "获取选中的技能", description = "返回所有被选中的技能列表")
    public ApiResponse<List<SkillDTO>> getSelectedSkills() {
        try {
            Set<String> selectedNames = skillRegistry.getSelectedSkillNames();
            List<SkillDTO> selectedSkills = selectedNames.stream()
                .map(name -> skillRegistry.getSkill(name))
                .filter(Objects::nonNull)
                .map(this::convertToDTO)
                .collect(Collectors.toList());
            
            log.info("获取选中技能列表，数量: {}", selectedSkills.size());
            return ApiResponse.success(selectedSkills);
        } catch (Exception e) {
            log.error("获取选中技能列表失败", e);
            return ApiResponse.error("获取选中技能列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/analyze-intent")
    @Operation(summary = "分析用户意图", description = "使用AI分析用户消息的意图")
    public ApiResponse<com.superfriend.superfriend.service.SkillRecommendationService.AIIntentAnalysis> analyzeIntent(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        try {
            String message = (String) request.get("message");
            if (message == null || message.isEmpty()) {
                return ApiResponse.error("消息不能为空");
            }

            String modelName = (String) request.get("model");

            Long userId = null;
            String userIdHeader = httpRequest.getHeader("X-User-Id");
            if (userIdHeader != null && !userIdHeader.isEmpty()) {
                try {
                    userId = Long.parseLong(userIdHeader);
                } catch (NumberFormatException e) {
                    log.warn("无效的用户ID: {}", userIdHeader);
                }
            }
            
            com.superfriend.superfriend.service.SkillRecommendationService.AIIntentAnalysis analysis =
                skillRecommendationService.analyzeIntentWithAI(message, userId, modelName);
            
            log.info("分析用户意图: {} -> {}", message, analysis.getIntent());
            
            return ApiResponse.success(analysis);
        } catch (Exception e) {
            log.error("分析用户意图失败", e);
            return ApiResponse.error("分析用户意图失败: " + e.getMessage());
        }
    }

    @PostMapping("/batch/execute")
    @Operation(summary = "批量执行技能", description = "批量执行多个技能，支持停止条件和公共参数")
    public ApiResponse<BatchExecutionResult> batchExecute(
            @RequestBody BatchExecutionRequest request,
            HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("批量执行技能: count={}, stopOnError={}", 
                request.getRequests().size(), request.getStopOnError());
            
            Long userId = extractUserId(httpRequest, null);
            
            List<BatchExecutionResult.BatchExecutionItem> results = new ArrayList<>();
            
            for (SkillExecutionRequest skillRequest : request.getRequests()) {
                long itemStartTime = System.currentTimeMillis();
                BatchExecutionResult.BatchExecutionItem item = new BatchExecutionResult.BatchExecutionItem();
                item.setSkillName(skillRequest.getSkillName());
                
                try {
                    if (request.getCommonParameters() != null) {
                        Map<String, Object> mergedParams = new HashMap<>(request.getCommonParameters());
                        if (skillRequest.getParameters() != null) {
                            mergedParams.putAll(skillRequest.getParameters());
                        }
                        skillRequest.setParameters(mergedParams);
                    }
                    
                    if (request.getCommonVariables() != null) {
                        Map<String, Object> mergedVars = new HashMap<>(request.getCommonVariables());
                        if (skillRequest.getVariables() != null) {
                            mergedVars.putAll(skillRequest.getVariables());
                        }
                        skillRequest.setVariables(mergedVars);
                    }
                    
                    SkillContext context = new SkillContext();
                    context.setSessionId(skillRequest.getSessionId());
                    context.setUserId(userId != null ? userId.toString() : skillRequest.getUserId());
                    context.setUserRequest(skillRequest.getUserRequest());
                    context.setParameters(skillRequest.getParameters());
                    context.setVariables(skillRequest.getVariables());
                    
                    SkillResult result = skillExecutor.execute(skillRequest.getSkillName(), context, userId);
                    
                    item.setSuccess(result.isSuccess());
                    if (result.isSuccess()) {
                        item.setData(result.getData());
                    } else {
                        item.setError(result.getError());
                        item.setErrorCode(SkillErrorCode.EXECUTION_FAILED.getCode());
                        item.setSuggestion(SkillErrorCode.EXECUTION_FAILED.getSuggestion());
                    }
                    
                } catch (Exception e) {
                    log.error("批量执行技能失败: skillName={}", skillRequest.getSkillName(), e);
                    item.setSuccess(false);
                    item.setError("执行异常: " + e.getMessage());
                    item.setErrorCode(SkillErrorCode.EXECUTION_FAILED.getCode());
                    item.setSuggestion(SkillErrorCode.EXECUTION_FAILED.getSuggestion());
                }
                
                item.setExecutionTime(System.currentTimeMillis() - itemStartTime);
                results.add(item);
                
                if (request.getStopOnError() != null && request.getStopOnError() && !item.getSuccess()) {
                    log.warn("批量执行因错误停止: skillName={}", skillRequest.getSkillName());
                    break;
                }
            }
            
            BatchExecutionResult batchResult = BatchExecutionResult.of(results);
            log.info("批量执行完成: total={}, success={}, failure={}, time={}ms", 
                batchResult.getTotalCount(), batchResult.getSuccessCount(), 
                batchResult.getFailureCount(), System.currentTimeMillis() - startTime);
            
            return ApiResponse.success(batchResult);
        } catch (Exception e) {
            log.error("批量执行技能失败", e);
            return ApiResponse.error("批量执行技能失败: " + e.getMessage());
        }
    }

    @PostMapping("/batch/selection")
    @Operation(summary = "批量更新技能选择状态", description = "批量更新多个技能的选择状态")
    public ApiResponse<Void> batchUpdateSelection(
            @RequestBody BatchSelectionRequest request,
            HttpServletRequest httpRequest) {
        try {
            log.info("批量更新选择状态: count={}, isSelected={}", 
                request.getSkillNames().size(), request.getIsSelected());
            
            Long userId = extractUserId(httpRequest, request.getUserId());
            if (userId == null) {
                ApiErrorResponse error = ApiErrorResponse.of(
                    SkillErrorCode.USER_ID_REQUIRED.getCode(),
                    SkillErrorCode.USER_ID_REQUIRED.getMessage(),
                    SkillErrorCode.USER_ID_REQUIRED.getSuggestion()
                );
                return ApiResponse.error(error.getMessage());
            }
            
            int successCount = 0;
            for (String skillName : request.getSkillNames()) {
                try {
                    com.superfriend.superfriend.entity.Skill dbSkill = skillService.findByName(skillName);
                    if (dbSkill != null) {
                        skillService.updateUserSkillSelectionStatus(userId, dbSkill.getId(), request.getIsSelected());
                        skillRegistry.updateUserSelectionStatus(userId, skillName, request.getIsSelected());
                        successCount++;
                    } else {
                        log.warn("技能不存在: {}", skillName);
                    }
                } catch (Exception e) {
                    log.error("更新技能选择状态失败: skillName={}", skillName, e);
                }
            }
            
            log.info("批量更新选择状态完成: success={}/{}", successCount, request.getSkillNames().size());
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("批量更新选择状态失败", e);
            return ApiResponse.error("批量更新选择状态失败: " + e.getMessage());
        }
    }

    @PostMapping("/workflow/execute")
    @Operation(summary = "执行技能工作流", description = "执行预定义的技能工作流，支持条件分支和错误处理")
    public ApiResponse<WorkflowResult> executeWorkflow(
            @RequestBody SkillWorkflow workflow,
            HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("执行工作流: name={}, steps={}", workflow.getName(), workflow.getSteps().size());
            
            Long userId = extractUserId(httpRequest, null);
            
            SkillContext baseContext = new SkillContext();
            baseContext.setSessionId(UUID.randomUUID().toString());
            baseContext.setUserId(userId != null ? userId.toString() : null);
            baseContext.setUserRequest(workflow.getDescription());
            
            WorkflowResult result = workflowExecutor.execute(workflow, baseContext);
            
            log.info("工作流执行完成: name={}, success={}, time={}ms", 
                workflow.getName(), result.getSuccess(), System.currentTimeMillis() - startTime);
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("执行工作流失败", e);
            return ApiResponse.error("执行工作流失败: " + e.getMessage());
        }
    }

    @GetMapping("/export")
    @Operation(summary = "导出技能", description = "导出指定的技能配置")
    public ApiResponse<List<SkillDTO>> exportSkills(
            @RequestParam List<String> skillNames) {
        try {
            log.info("导出技能: count={}", skillNames.size());
            
            List<SkillDTO> exportedSkills = new ArrayList<>();
            for (String skillName : skillNames) {
                Skill skill = skillRegistry.getSkill(skillName);
                if (skill != null) {
                    exportedSkills.add(convertToDTO(skill));
                } else {
                    log.warn("导出时技能不存在: {}", skillName);
                }
            }
            
            log.info("导出技能完成: success={}/{}", exportedSkills.size(), skillNames.size());
            return ApiResponse.success(exportedSkills);
        } catch (Exception e) {
            log.error("导出技能失败", e);
            return ApiResponse.error("导出技能失败: " + e.getMessage());
        }
    }

    @PostMapping("/import")
    @Operation(summary = "导入技能", description = "导入技能配置")
    public ApiResponse<Map<String, Object>> importSkills(
            @RequestBody List<SkillDTO> skills,
            @RequestParam(required = false) Long userId,
            HttpServletRequest httpRequest) {
        try {
            log.info("导入技能: count={}", skills.size());
            
            userId = extractUserId(httpRequest, userId);
            
            int successCount = 0;
            int failureCount = 0;
            List<String> errors = new ArrayList<>();
            
            for (SkillDTO skillDTO : skills) {
                try {
                    if (skillRegistry.hasSkill(skillDTO.getName())) {
                        errors.add("技能名称已存在: " + skillDTO.getName());
                        failureCount++;
                        continue;
                    }
                    
                    com.superfriend.superfriend.entity.Skill skillEntity = new com.superfriend.superfriend.entity.Skill();
                    skillEntity.setName(skillDTO.getName());
                    skillEntity.setDescription(skillDTO.getDescription());
                    skillEntity.setCategory(skillDTO.getCategory());
                    skillEntity.setVersion(skillDTO.getVersion());
                    skillEntity.setAuthor(skillDTO.getAuthor());
                    skillEntity.setTags(skillDTO.getTags());
                    skillEntity.setAllowedTools(skillDTO.getAllowedTools());
                    skillEntity.setInstructions(skillDTO.getInstructions());
                    skillEntity.setParameters(skillDTO.getParameters());
                    skillEntity.setScope(userId != null ? 3 : 2);
                    skillEntity.setStatus(1);
                    
                    skillService.createSkill(skillEntity);
                    
                    if (userId != null) {
                        skillService.createUserSkill(skillEntity, userId);
                    }
                    
                    successCount++;
                } catch (Exception e) {
                    log.error("导入技能失败: name={}", skillDTO.getName(), e);
                    errors.add("导入失败: " + skillDTO.getName() + " - " + e.getMessage());
                    failureCount++;
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("successCount", successCount);
            result.put("failureCount", failureCount);
            result.put("errors", errors);
            
            log.info("导入技能完成: success={}, failure={}", successCount, failureCount);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("导入技能失败", e);
            return ApiResponse.error("导入技能失败: " + e.getMessage());
        }
    }

    private Long extractUserId(HttpServletRequest httpRequest, Long defaultValue) {
        if (defaultValue != null) {
            return defaultValue;
        }
        
        String userIdHeader = httpRequest.getHeader("X-User-Id");
        if (userIdHeader != null && !userIdHeader.isEmpty()) {
            try {
                return Long.parseLong(userIdHeader);
            } catch (NumberFormatException e) {
                log.warn("无效的用户ID: {}", userIdHeader);
            }
        }
        
        return null;
    }

    @GetMapping("/test-tool-conversion")
    @Operation(summary = "测试技能工具转换", description = "测试将 Skills 转换为工具定义")
    public ApiResponse<Map<String, Object>> testSkillToolConversion(HttpServletRequest request) {
        try {
            Long userId = extractUserId(request, null);
            
            List<Skill> allSkills = skillRegistry.getAllSkills();
            log.info("当前注册的 Skills 数量: {}", allSkills.size());
            
            com.superfriend.superfriend.service.SkillToolConverter converter = 
                new com.superfriend.superfriend.service.SkillToolConverter();
            
            List<McpToolDefinition> tools = converter.convertSkillsToToolDefinitions(allSkills);
            
            Map<String, Object> result = new HashMap<>();
            result.put("totalSkills", allSkills.size());
            result.put("convertedTools", tools.size());
            
            List<Map<String, Object>> toolDetails = new ArrayList<>();
            for (McpToolDefinition tool : tools) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("name", tool.getName());
                detail.put("serverName", tool.getServerName());
                detail.put("description", tool.getDescription() != null && tool.getDescription().length() > 100 
                    ? tool.getDescription().substring(0, 100) + "..." 
                    : tool.getDescription());
                detail.put("hasInputSchema", tool.getInputSchema() != null);
                if (tool.getInputSchema() != null && tool.getInputSchema().getProperties() != null) {
                    detail.put("parameterCount", tool.getInputSchema().getProperties().size());
                    detail.put("parameters", new ArrayList<>(tool.getInputSchema().getProperties().keySet()));
                }
                toolDetails.add(detail);
            }
            result.put("tools", toolDetails);
            
            List<Map<String, Object>> skillDetails = new ArrayList<>();
            for (Skill skill : allSkills) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("name", skill.getName());
                detail.put("description", skill.getDescription());
                detail.put("category", skill.getCategory());
                detail.put("tags", skill.getTags());
                detail.put("allowedTools", skill.getAllowedTools());
                detail.put("hasParameters", skill.getParameters() != null && !skill.getParameters().isEmpty());
                if (skill.getParameters() != null && !skill.getParameters().isEmpty()) {
                    detail.put("parameters", new ArrayList<>(skill.getParameters().keySet()));
                }
                skillDetails.add(detail);
            }
            result.put("skills", skillDetails);
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("测试技能工具转换失败", e);
            return ApiResponse.error("测试失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/resources/upload")
    @Operation(summary = "上传技能资源文件", description = "上传资源文件到 COS，返回资源元数据")
    public ApiResponse<Map<String, Object>> uploadResource(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam("skillName") String skillName,
            @RequestParam(value = "resourceType", defaultValue = "document") String resourceType,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request) {
        try {
            if (!tencentCosService.isEnabled()) {
                return ApiResponse.error("COS 服务未启用，请检查配置");
            }

            if (file.isEmpty()) {
                return ApiResponse.error("上传文件不能为空");
            }

            if (userId == null) {
                String userIdHeader = request.getHeader("X-User-Id");
                if (userIdHeader != null && !userIdHeader.isEmpty()) {
                    userId = Long.parseLong(userIdHeader);
                }
            }

            if (userId == null) {
                return ApiResponse.error("缺少用户ID");
            }

            com.superfriend.superfriend.entity.Skill skill = skillService.findByName(skillName);
            if (skill == null) {
                return ApiResponse.error("技能不存在: " + skillName);
            }

            if (skill.getScope() == 3) {
                com.superfriend.superfriend.entity.UserSkill userSkill = skillService.findUserSkill(userId, skill.getId());
                if (userSkill == null) {
                    return ApiResponse.error("无权限上传此技能的资源");
                }
            }

            // 上传到腾讯云 COS
            String directory = "skills/users/" + userId + "/" + skillName + "/resources";
            String mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

            com.superfriend.superfriend.service.TencentCosService.CosUploadResult uploadResult =
                tencentCosService.uploadBytes(file.getBytes(), file.getOriginalFilename(), mimeType, directory);

            if (uploadResult == null) {
                return ApiResponse.error("上传到 COS 失败");
            }

            Map<String, Object> resource = new HashMap<>();
            resource.put("resourceName", file.getOriginalFilename());
            resource.put("resourceType", resourceType);
            resource.put("storageType", "cos");
            resource.put("resourcePath", uploadResult.getObjectKey());
            resource.put("resourceUrl", uploadResult.getUrl());
            resource.put("fileSize", file.getSize());
            resource.put("mimeType", mimeType);
            resource.put("description", description);
            resource.put("ownerId", userId);

            // 将资源元数据添加到 skills.resources_json
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                List<Map<String, Object>> resourcesList = new ArrayList<>();

                // 解析现有的 resources_json
                if (skill.getResourcesJson() != null && !skill.getResourcesJson().isEmpty()) {
                    resourcesList = mapper.readValue(skill.getResourcesJson(), List.class);
                }

                // 添加新资源
                Map<String, Object> newResource = new HashMap<>();
                newResource.put("resourceName", file.getOriginalFilename());
                newResource.put("resourceType", resourceType);
                newResource.put("storageType", "cos");
                newResource.put("resourcePath", uploadResult.getObjectKey());
                newResource.put("resourceUrl", uploadResult.getUrl());
                newResource.put("fileSize", file.getSize());
                newResource.put("mimeType", mimeType);
                resourcesList.add(newResource);

                // 更新 resources_json
                skill.setResourcesJson(mapper.writeValueAsString(resourcesList));
                skillService.updateSkill(skill);

                log.info("资源元数据已保存到 resources_json: skill={}, resource={}", skillName, file.getOriginalFilename());
            } catch (Exception e) {
                log.warn("保存资源元数据失败: {}", e.getMessage());
            }

            log.info("资源文件上传成功: userId={}, skill={}, file={}, size={}",
                userId, skillName, file.getOriginalFilename(), file.getSize());

            return ApiResponse.success(resource);
        } catch (Exception e) {
            log.error("资源文件上传失败: {}", e.getMessage(), e);
            return ApiResponse.error("上传失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/resources/download")
    @Operation(summary = "下载技能资源文件", description = "获取资源文件的下载链接")
    public ApiResponse<String> downloadResource(
            @RequestParam("skillName") String skillName,
            @RequestParam("resourcePath") String resourcePath,
            @RequestParam(value = "expirationMinutes", defaultValue = "60") int expirationMinutes,
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request) {
        try {
            if (!tencentCosService.isEnabled()) {
                return ApiResponse.error("COS 服务未启用");
            }

            if (userId == null) {
                String userIdHeader = request.getHeader("X-User-Id");
                if (userIdHeader != null && !userIdHeader.isEmpty()) {
                    userId = Long.parseLong(userIdHeader);
                }
            }

            if (userId == null) {
                return ApiResponse.error("缺少用户ID");
            }

            if (!resourcePath.startsWith("skills/users/" + userId + "/")) {
                log.warn("用户 {} 尝试访问非授权资源: {}", userId, resourcePath);
                return ApiResponse.error("无权限访问此资源");
            }

            String presignedUrl = tencentCosService.generatePresignedUrl(resourcePath, expirationMinutes);

            if (presignedUrl == null) {
                return ApiResponse.error("生成下载链接失败");
            }

            log.info("生成资源下载链接: userId={}, skill={}, path={}", userId, skillName, resourcePath);

            return ApiResponse.success(presignedUrl);
        } catch (Exception e) {
            log.error("生成下载链接失败: {}", e.getMessage(), e);
            return ApiResponse.error("生成下载链接失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/resources")
    @Operation(summary = "删除技能资源文件", description = "从 COS 删除资源文件")
    public ApiResponse<String> deleteResource(
            @RequestParam("skillName") String skillName,
            @RequestParam("resourcePath") String resourcePath,
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request) {
        try {
            if (!tencentCosService.isEnabled()) {
                return ApiResponse.error("COS 服务未启用");
            }

            if (userId == null) {
                String userIdHeader = request.getHeader("X-User-Id");
                if (userIdHeader != null && !userIdHeader.isEmpty()) {
                    userId = Long.parseLong(userIdHeader);
                }
            }

            if (userId == null) {
                return ApiResponse.error("缺少用户ID");
            }

            if (!resourcePath.startsWith("skills/users/" + userId + "/")) {
                log.warn("用户 {} 尝试删除非授权资源: {}", userId, resourcePath);
                return ApiResponse.error("无权限删除此资源");
            }

            // 从 COS 删除文件
            tencentCosService.deleteFile(resourcePath);

            // 从 resources_json 中移除资源元数据
            try {
                com.superfriend.superfriend.entity.Skill skill = skillService.findByName(skillName);
                if (skill != null && skill.getResourcesJson() != null && !skill.getResourcesJson().isEmpty()) {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    List<Map<String, Object>> resourcesList = mapper.readValue(skill.getResourcesJson(), List.class);

                    // 过滤掉被删除的资源
                    resourcesList.removeIf(r -> resourcePath.equals(r.get("resourcePath")));

                    // 更新 resources_json
                    skill.setResourcesJson(mapper.writeValueAsString(resourcesList));
                    skillService.updateSkill(skill);

                    log.info("已从 resources_json 移除资源: skill={}, path={}", skillName, resourcePath);
                }
            } catch (Exception e) {
                log.warn("更新 resources_json 失败: {}", e.getMessage());
            }

            log.info("资源文件删除成功: userId={}, skill={}, path={}", userId, skillName, resourcePath);

            return ApiResponse.success("删除成功");
        } catch (Exception e) {
            log.error("资源文件删除失败: {}", e.getMessage(), e);
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    private SkillResult processSkillFileOutput(SkillResult result) {
        Object data = result.getData();
        
        if (data instanceof Map) {
            Map<?, ?> dataMap = (Map<?, ?>) data;
            
            if (dataMap.containsKey("outputFile") || dataMap.containsKey("output_path") || dataMap.containsKey("filePath")) {
                String filePath = (String) (dataMap.get("outputFile") != null ? dataMap.get("outputFile") :
                                            dataMap.get("output_path") != null ? dataMap.get("output_path") :
                                            dataMap.get("filePath"));
                
                if (filePath != null && (filePath.endsWith(".pdf") || filePath.endsWith(".docx") || 
                                         filePath.endsWith(".xlsx") || filePath.endsWith(".pptx"))) {
                    String base64Content = skillExecutor.convertFileToBase64(filePath);
                    if (base64Content != null) {
                        Map<String, Object> fileResult = new HashMap<>();
                        fileResult.put("type", "file");
                        String fileType = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();
                        fileResult.put("fileType", fileType);
                        fileResult.put("fileName", new java.io.File(filePath).getName());
                        fileResult.put("content", base64Content);
                        fileResult.put("fileSize", new java.io.File(filePath).length());
                        
                        result.setData(fileResult);
                        result.getMetadata().put("fileGenerated", true);
                        result.getMetadata().put("filePath", filePath);
                        
                        log.info("Skill generated file: {} ({} bytes)", filePath, new java.io.File(filePath).length());
                    }
                }
            }
        } else if (data instanceof String) {
            String dataStr = (String) data;
            if (dataStr.endsWith(".pdf") || dataStr.endsWith(".docx") || 
                dataStr.endsWith(".xlsx") || dataStr.endsWith(".pptx")) {
                java.io.File file = new java.io.File(dataStr);
                if (file.exists()) {
                    String base64Content = skillExecutor.convertFileToBase64(dataStr);
                    if (base64Content != null) {
                        Map<String, Object> fileResult = new HashMap<>();
                        fileResult.put("type", "file");
                        String fileType = dataStr.substring(dataStr.lastIndexOf('.') + 1).toLowerCase();
                        fileResult.put("fileType", fileType);
                        fileResult.put("fileName", file.getName());
                        fileResult.put("content", base64Content);
                        fileResult.put("fileSize", file.length());
                        
                        result.setData(fileResult);
                        result.getMetadata().put("fileGenerated", true);
                        result.getMetadata().put("filePath", dataStr);
                        
                        log.info("Skill generated file: {} ({} bytes)", dataStr, file.length());
                    }
                }
            }
        }
        
        return result;
    }

    @PostMapping("/package/import")
    @Operation(summary = "导入技能包", description = "上传 ZIP 技能包文件并解析导入")
    public ApiResponse<com.superfriend.superfriend.service.SkillPackageParserService.SkillImportResult> importSkillPackage(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "modelConfigId", required = false) String modelConfigId,
            @RequestParam(value = "userId", required = false) Long userId,
            HttpServletRequest request) {
        try {
            log.info("收到技能包导入请求: fileName={}, size={}bytes, modelConfigId={}",
                file.getOriginalFilename(), file.getSize(), modelConfigId);

            if (file.isEmpty()) {
                return ApiResponse.error("上传文件不能为空");
            }

            if (!file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
                return ApiResponse.error("只支持 ZIP 格式的技能包");
            }

            if (userId == null) {
                String userIdHeader = request.getHeader("X-User-Id");
                if (userIdHeader != null && !userIdHeader.isEmpty()) {
                    userId = Long.parseLong(userIdHeader);
                }
            }

            if (userId == null) {
                return ApiResponse.error("缺少用户ID");
            }

            com.superfriend.superfriend.service.SkillPackageParserService.SkillImportResult result =
                skillPackageParserService.importSkillPackage(userId, file, modelConfigId);

            if (result.isSuccess()) {
                log.info("技能包导入成功: userId={}, skillName={}, skillId={}",
                    userId, result.getSkillName(), result.getSkillId());
            } else {
                log.warn("技能包导入失败: userId={}, message={}", userId, result.getMessage());
            }

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("技能包导入异常: {}", e.getMessage(), e);
            return ApiResponse.error("导入失败: " + e.getMessage());
        }
    }
}
