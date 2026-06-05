package com.superfriend.superfriend.agent.skill;

import com.superfriend.superfriend.dto.McpToolCallRequest;
import com.superfriend.superfriend.dto.McpToolCallResponse;
import com.superfriend.superfriend.service.McpHostService;
import com.superfriend.superfriend.service.SkillService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.regex.*;
import java.util.Base64;

@Slf4j
@Service
public class SkillExecutor {
    
    @Autowired
    private SkillRegistry skillRegistry;

    @Autowired
    private McpHostService mcpHostService;

    @Autowired
    private ScriptExecutor scriptExecutor;

    @Autowired
    @Lazy
    private SkillHookManager hookManager;

    @Autowired
    private SkillService skillService;

    public SkillResult execute(String skillName, SkillContext context) {
        return execute(skillName, context, null);
    }

    public SkillResult execute(String skillName, SkillContext context, Long userId) {
        long startTime = System.currentTimeMillis();
        
        try {
            Skill skill = skillRegistry.getSkill(skillName);
            
            if (skill == null && userId != null) {
                skill = loadUserSkillFromDatabase(skillName, userId);
            }
            
            if (skill == null) {
                return SkillResult.failure("技能不存在: " + skillName);
            }

            if (!skill.validateContext(context)) {
                return SkillResult.failure("技能上下文验证失败");
            }

            if (skill instanceof DynamicSkill) {
                return executeDynamicSkill((DynamicSkill) skill, context, startTime);
            } else if (skill instanceof DatabaseSkill) {
                return executeDatabaseSkill((DatabaseSkill) skill, context, startTime);
            } else {
                SkillResult result = skill.execute(context);
                long executionTime = System.currentTimeMillis() - startTime;
                skillRegistry.updateSkillStats(skillName, result.isSuccess(), executionTime);
                return result;
            }

        } catch (Exception e) {
            log.error("执行技能失败: {} - {}", skillName, e.getMessage(), e);
            long executionTime = System.currentTimeMillis() - startTime;
            skillRegistry.updateSkillStats(skillName, false, executionTime);
            return SkillResult.failure("执行技能异常: " + e.getMessage());
        }
    }

    private Skill loadUserSkillFromDatabase(String skillName, Long userId) {
        try {
            com.superfriend.superfriend.entity.Skill entity = skillService.findByName(skillName);
            if (entity != null && entity.getScope() != null && entity.getScope() == 3) {
                log.info("从数据库加载用户技能: {} (id={})", skillName, entity.getId());
                
                return new DatabaseSkill(entity);
            }
        } catch (Exception e) {
            log.warn("从数据库加载技能失败: {} - {}", skillName, e.getMessage());
        }
        return null;
    }

    private SkillResult executeDatabaseSkill(DatabaseSkill skill, SkillContext context, long startTime) {
        try {
            SkillConfig config = skill.getConfig();
            String instructions = config.getInstructions();

            Map<String, Object> variables = new HashMap<>(context.getVariables());
            variables.put("userRequest", context.getUserRequest());
            variables.put("sessionId", context.getSessionId());
            variables.put("userId", context.getUserId());

            executeHooks(SkillHook.HookEvent.PRE_SKILL_EXECUTE, context, skill.getName());

            if (config.isHasScripts() && !skill.getScripts().isEmpty()) {
                ScriptExecutor.ScriptExecutionResult scriptResult = executeDatabaseSkillScripts(skill, context);
                
                if (!scriptResult.isSuccess()) {
                    long executionTime = System.currentTimeMillis() - startTime;
                    updateDatabaseSkillStats(skill.getSkillId(), false, executionTime);
                    return SkillResult.failure("脚本执行失败: " + scriptResult.getError());
                }

                if (scriptResult.hasParsedOutput()) {
                    variables.putAll(scriptResult.getParsedOutput());
                } else {
                    variables.put("scriptOutput", scriptResult.getOutput());
                }
            }

            if (instructions == null || instructions.isEmpty()) {
                long executionTime = System.currentTimeMillis() - startTime;
                updateDatabaseSkillStats(skill.getSkillId(), true, executionTime);
                
                if (variables.containsKey("outputFile")) {
                    return SkillResult.success(variables);
                }
                return SkillResult.success(variables.get("scriptOutput"));
            }

            String processedInstructions = processInstructions(instructions, variables);

            List<String> toolCalls = extractToolCalls(processedInstructions);

            Map<String, Object> results = new HashMap<>();
            for (String toolCall : toolCalls) {
                if (context.getExecutionContext() != null && context.getExecutionContext().isInterrupted()) {
                    return SkillResult.partial(results, "技能执行被中断");
                }

                ToolCallResult toolResult = executeToolCall(toolCall, context);
                results.put(toolResult.getToolName(), toolResult.getResult());

                String placeholder = "{{" + toolCall + "}}";
                processedInstructions = processedInstructions.replace(placeholder, 
                    toolResult.isSuccess() ? String.valueOf(toolResult.getResult()) : "ERROR: " + toolResult.getError());
            }

            Object finalResult = extractFinalResult(processedInstructions, results);

            executeHooks(SkillHook.HookEvent.POST_SKILL_EXECUTE, context, skill.getName());

            long executionTime = System.currentTimeMillis() - startTime;
            updateDatabaseSkillStats(skill.getSkillId(), true, executionTime);

            return SkillResult.success(finalResult);

        } catch (Exception e) {
            log.error("执行数据库技能失败: {} - {}", skill.getName(), e.getMessage(), e);
            long executionTime = System.currentTimeMillis() - startTime;
            updateDatabaseSkillStats(skill.getSkillId(), false, executionTime);
            
            executeHooks(SkillHook.HookEvent.ERROR_OCCURRED, context, skill.getName());
            
            return SkillResult.failure("执行数据库技能异常: " + e.getMessage());
        }
    }

    private ScriptExecutor.ScriptExecutionResult executeDatabaseSkillScripts(DatabaseSkill skill, SkillContext context) {
        try {
            com.superfriend.superfriend.entity.SkillScript mainScript = skill.getMainScript();
            if (mainScript == null) {
                return ScriptExecutor.ScriptExecutionResult.success("No main script found");
            }

            String scriptType = mainScript.getScriptType();
            String scriptContent = mainScript.getScriptContent();
            
            if (scriptContent == null || scriptContent.isEmpty()) {
                return ScriptExecutor.ScriptExecutionResult.failure("Script content is empty");
            }

            File tempScriptFile = createTempScriptFile(scriptType, scriptContent);
            if (tempScriptFile == null) {
                return ScriptExecutor.ScriptExecutionResult.failure("Failed to create temp script file");
            }

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("userRequest", context.getUserRequest());
            parameters.put("sessionId", context.getSessionId());
            parameters.put("userId", context.getUserId());
            parameters.putAll(context.getVariables());

            File workingDirectory = new File(System.getProperty("java.io.tmpdir"));
            long timeout = 60000L;

            log.info("Executing database skill script: skill={}, type={}", skill.getName(), scriptType);

            ScriptExecutor.ScriptExecutionResult result = scriptExecutor.executeScript(
                tempScriptFile.getAbsolutePath(),
                parameters,
                workingDirectory,
                timeout
            );

            if (tempScriptFile.exists()) {
                tempScriptFile.delete();
            }

            if (result.isSuccess()) {
                log.info("Database skill script executed successfully: skill={} (time={}ms)", 
                    skill.getName(), result.getExecutionTime());
            } else {
                log.error("Database skill script execution failed: skill={} - {}", 
                    skill.getName(), result.getError());
            }

            return result;

        } catch (Exception e) {
            log.error("Failed to execute database skill scripts: {} - {}", 
                skill.getName(), e.getMessage(), e);
            return ScriptExecutor.ScriptExecutionResult.failure(
                "Script execution error: " + e.getMessage()
            );
        }
    }

    private File createTempScriptFile(String scriptType, String scriptContent) {
        try {
            String extension = getScriptExtension(scriptType);
            File tempFile = File.createTempFile("skill_script_", extension);
            
            java.io.FileWriter writer = new java.io.FileWriter(tempFile);
            writer.write(scriptContent);
            writer.close();
            
            tempFile.setExecutable(true);
            return tempFile;
        } catch (Exception e) {
            log.error("Failed to create temp script file: {}", e.getMessage());
            return null;
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

    private void updateDatabaseSkillStats(Long skillId, boolean success, long executionTime) {
        try {
            com.superfriend.superfriend.entity.Skill skill = skillService.findById(skillId);
            if (skill != null) {
                long currentCount = skill.getExecutionCount() != null ? skill.getExecutionCount() : 0L;
                double currentRate = skill.getSuccessRate() != null ? skill.getSuccessRate() : 1.0;
                long currentTime = skill.getAverageExecutionTime() != null ? skill.getAverageExecutionTime() : 0L;
                
                long newCount = currentCount + 1;
                double newRate = ((currentRate * currentCount) + (success ? 1.0 : 0.0)) / newCount;
                long newTime = ((currentTime * currentCount) + executionTime) / newCount;
                
                skill.setExecutionCount(newCount);
                skill.setSuccessRate(newRate);
                skill.setAverageExecutionTime((int) newTime);
                
                skillService.updateSkill(skill);
            }
        } catch (Exception e) {
            log.warn("更新数据库技能统计失败: {}", e.getMessage());
        }
    }

    private SkillResult executeDynamicSkill(DynamicSkill skill, SkillContext context, long startTime) {
        try {
            SkillConfig config = skill.getConfig();
            String instructions = config.getInstructions();

            Map<String, Object> variables = new HashMap<>(context.getVariables());
            variables.put("userRequest", context.getUserRequest());
            variables.put("sessionId", context.getSessionId());
            variables.put("userId", context.getUserId());

            executeHooks(SkillHook.HookEvent.PRE_SKILL_EXECUTE, context, skill.getName());

            if (config.isHasScripts()) {
                ScriptExecutor.ScriptExecutionResult scriptResult = executeSkillScripts(skill, context);
                
                if (!scriptResult.isSuccess()) {
                    long executionTime = System.currentTimeMillis() - startTime;
                    skillRegistry.updateSkillStats(skill.getName(), false, executionTime);
                    return SkillResult.failure("脚本执行失败: " + scriptResult.getError());
                }

                if (scriptResult.hasParsedOutput()) {
                    variables.putAll(scriptResult.getParsedOutput());
                } else {
                    variables.put("scriptOutput", scriptResult.getOutput());
                }
            }

            if (instructions == null || instructions.isEmpty()) {
                long executionTime = System.currentTimeMillis() - startTime;
                skillRegistry.updateSkillStats(skill.getName(), true, executionTime);
                
                if (variables.containsKey("outputFile")) {
                    return SkillResult.success(variables);
                }
                return SkillResult.success(variables.get("scriptOutput"));
            }

            String processedInstructions = processInstructions(instructions, variables);

            List<String> toolCalls = extractToolCalls(processedInstructions);

            Map<String, Object> results = new HashMap<>();
            for (String toolCall : toolCalls) {
                if (context.getExecutionContext() != null && context.getExecutionContext().isInterrupted()) {
                    return SkillResult.partial(results, "技能执行被中断");
                }

                ToolCallResult toolResult = executeToolCall(toolCall, context);
                results.put(toolResult.getToolName(), toolResult.getResult());

                String placeholder = "{{" + toolCall + "}}";
                processedInstructions = processedInstructions.replace(placeholder, 
                    toolResult.isSuccess() ? String.valueOf(toolResult.getResult()) : "ERROR: " + toolResult.getError());
            }

            Object finalResult = extractFinalResult(processedInstructions, results);

            executeHooks(SkillHook.HookEvent.POST_SKILL_EXECUTE, context, skill.getName());

            long executionTime = System.currentTimeMillis() - startTime;
            skillRegistry.updateSkillStats(skill.getName(), true, executionTime);

            return SkillResult.success(finalResult);

        } catch (Exception e) {
            log.error("执行动态技能失败: {} - {}", skill.getName(), e.getMessage(), e);
            long executionTime = System.currentTimeMillis() - startTime;
            skillRegistry.updateSkillStats(skill.getName(), false, executionTime);
            
            executeHooks(SkillHook.HookEvent.ERROR_OCCURRED, context, skill.getName());
            
            return SkillResult.failure("执行动态技能异常: " + e.getMessage());
        }
    }

    private ScriptExecutor.ScriptExecutionResult executeSkillScripts(DynamicSkill skill, SkillContext context) {
        try {
            File skillDir = skill.getSkillDirectory();
            File scriptsDir = new File(skillDir, "scripts");
            
            if (!scriptsDir.exists() || !scriptsDir.isDirectory()) {
                return ScriptExecutor.ScriptExecutionResult.success("No scripts to execute");
            }

            File mainScript = findMainScript(scriptsDir);
            if (mainScript == null) {
                return ScriptExecutor.ScriptExecutionResult.success("No main script found");
            }

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("userRequest", context.getUserRequest());
            parameters.put("sessionId", context.getSessionId());
            parameters.put("userId", context.getUserId());
            parameters.putAll(context.getVariables());

            File workingDirectory = skillDir;
            long timeout = getScriptTimeout(skill.getConfig());

            log.info("Executing script for skill: {} - {}", skill.getName(), mainScript.getName());

            ScriptExecutor.ScriptExecutionResult result = scriptExecutor.executeScript(
                mainScript.getAbsolutePath(),
                parameters,
                workingDirectory,
                timeout
            );

            if (result.isSuccess()) {
                log.info("Script executed successfully for skill: {} (time={}ms)", 
                    skill.getName(), result.getExecutionTime());
            } else {
                log.error("Script execution failed for skill: {} - {}", 
                    skill.getName(), result.getError());
            }

            return result;

        } catch (Exception e) {
            log.error("Failed to execute scripts for skill: {} - {}", 
                skill.getName(), e.getMessage(), e);
            return ScriptExecutor.ScriptExecutionResult.failure(
                "Script execution error: " + e.getMessage()
            );
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

    private long getScriptTimeout(SkillConfig config) {
        long timeout = config.getTimeout();
        if (timeout <= 0) {
            timeout = 60000L;
            log.debug("Using default script timeout: {}ms", timeout);
        } else if (timeout > 300000L) {
            timeout = 300000L;
            log.warn("Script timeout exceeded maximum, capped at 300000ms");
        }
        return timeout;
    }

    private void executeHooks(SkillHook.HookEvent event, SkillContext context, String skillName) {
        try {
            SkillHookManager.HookExecutionContext hookContext = 
                new SkillHookManager.HookExecutionContext();
            hookContext.setSkillName(skillName);
            hookContext.setSessionId(context.getSessionId());
            hookContext.setUserId(context.getUserId());
            hookContext.setData(context.getVariables());

            SkillHookManager.HookExecutionResult result = 
                hookManager.executeHooks(event, hookContext);

            if (!result.isSuccess()) {
                log.warn("Hook execution failed for event: {} - {}", event, result.getResults());
            }
        } catch (Exception e) {
            log.error("Error executing hooks for event: {} - {}", event, e.getMessage(), e);
        }
    }

    private String processInstructions(String instructions, Map<String, Object> variables) {
        String result = instructions;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            result = result.replace(placeholder, String.valueOf(entry.getValue()));
        }
        return result;
    }

    private List<String> extractToolCalls(String instructions) {
        List<String> toolCalls = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\{\\{([^}]+)\\}\\}");
        Matcher matcher = pattern.matcher(instructions);

        while (matcher.find()) {
            toolCalls.add(matcher.group(1));
        }

        return toolCalls;
    }

    private ToolCallResult executeToolCall(String toolCall, SkillContext context) {
        ToolCallResult result = new ToolCallResult();

        try {
            String[] parts = toolCall.split("\\(", 2);
            String toolName = parts[0].trim();
            Map<String, Object> args = new HashMap<>();

            if (parts.length > 1) {
                String argsStr = parts[1].replaceAll("\\)$", "");
                args = parseArguments(argsStr);
            }

            String[] nameParts = toolName.split("__", 2);
            String serverName = nameParts[0];
            String actualToolName = nameParts.length > 1 ? nameParts[1] : toolName;

            McpToolCallRequest request = new McpToolCallRequest();
            request.setServerName(serverName);
            request.setToolName(actualToolName);
            request.setArguments(args);

            McpToolCallResponse response = mcpHostService.callTool(serverName, actualToolName, args);

            result.setToolName(toolName);
            result.setSuccess(response.isSuccess());
            if (response.isSuccess()) {
                if (response.getContent() != null && !response.getContent().isEmpty() 
                    && response.getContent().get(0) != null) {
                    result.setResult(response.getContent().get(0).getText());
                } else {
                    result.setResult(null);
                }
            } else {
                result.setError(response.getError());
            }

            context.recordExecution("Tool: " + toolName + " - " + (response.isSuccess() ? "SUCCESS" : "FAILED"));

        } catch (Exception e) {
            log.error("工具调用失败: {} - {}", toolCall, e.getMessage(), e);
            result.setToolName(toolCall);
            result.setSuccess(false);
            result.setError(e.getMessage());
        }

        return result;
    }

    private Map<String, Object> parseArguments(String argsStr) {
        Map<String, Object> args = new HashMap<>();

        if (argsStr == null || argsStr.trim().isEmpty()) {
            return args;
        }

        String[] pairs = argsStr.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String value = kv[1].trim();

                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                } else if (value.startsWith("'") && value.endsWith("'")) {
                    value = value.substring(1, value.length() - 1);
                }

                args.put(key, value);
            }
        }

        return args;
    }

    private Object extractFinalResult(String instructions, Map<String, Object> results) {
        if (results.size() == 1) {
            return results.values().iterator().next();
        }

        Map<String, Object> finalResult = new HashMap<>();
        finalResult.put("instructions", instructions);
        finalResult.put("results", results);
        return finalResult;
    }

    public String convertFileToBase64(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                log.warn("文件不存在: {}", filePath);
                return null;
            }
            
            byte[] fileContent = java.nio.file.Files.readAllBytes(file.toPath());
            String base64 = Base64.getEncoder().encodeToString(fileContent);
            
            String mimeType = detectMimeType(filePath);
            return "data:" + mimeType + ";base64," + base64;
            
        } catch (Exception e) {
            log.error("转换文件到Base64失败: {} - {}", filePath, e.getMessage());
            return null;
        }
    }

    private String detectMimeType(String filePath) {
        String extension = filePath.substring(filePath.lastIndexOf('.')).toLowerCase();
        switch (extension) {
            // Office文档
            case ".pdf": return "application/pdf";
            case ".docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case ".doc": return "application/msword";
            case ".xls": return "application/vnd.ms-excel";
            case ".ppt": return "application/vnd.ms-powerpoint";
            // 图片
            case ".png": return "image/png";
            case ".jpg":
            case ".jpeg": return "image/jpeg";
            case ".gif": return "image/gif";
            case ".bmp": return "image/bmp";
            case ".webp": return "image/webp";
            case ".svg": return "image/svg+xml";
            case ".ico": return "image/x-icon";
            case ".tiff":
            case ".tif": return "image/tiff";
            // 音频
            case ".mp3": return "audio/mpeg";
            case ".wav": return "audio/wav";
            case ".ogg": return "audio/ogg";
            case ".m4a": return "audio/mp4";
            case ".flac": return "audio/flac";
            case ".aac": return "audio/aac";
            // 视频
            case ".mp4": return "video/mp4";
            case ".webm": return "video/webm";
            case ".avi": return "video/x-msvideo";
            case ".mov": return "video/quicktime";
            case ".mkv": return "video/x-matroska";
            case ".flv": return "video/x-flv";
            case ".wmv": return "video/x-ms-wmv";
            // 压缩文件
            case ".zip": return "application/zip";
            case ".rar": return "application/vnd.rar";
            case ".7z": return "application/x-7z-compressed";
            case ".tar": return "application/x-tar";
            case ".gz": return "application/gzip";
            // 文本/代码
            case ".txt": return "text/plain";
            case ".html": return "text/html";
            case ".htm": return "text/html";
            case ".css": return "text/css";
            case ".js": return "application/javascript";
            case ".json": return "application/json";
            case ".xml": return "application/xml";
            case ".csv": return "text/csv";
            case ".md": return "text/markdown";
            case ".yaml":
            case ".yml": return "application/x-yaml";
            // 字体
            case ".ttf": return "font/ttf";
            case ".otf": return "font/otf";
            case ".woff": return "font/woff";
            case ".woff2": return "font/woff2";
            // 其他常见格式
            case ".epub": return "application/epub+zip";
            case ".mobi": return "application/x-mobipocket-ebook";
            case ".rtf": return "application/rtf";
            case ".odt": return "application/vnd.oasis.opendocument.text";
            case ".ods": return "application/vnd.oasis.opendocument.spreadsheet";
            case ".odp": return "application/vnd.oasis.opendocument.presentation";
            case ".sqlite":
            case ".db": return "application/x-sqlite3";
            case ".exe": return "application/vnd.microsoft.portable-executable";
            case ".dmg": return "application/x-apple-diskimage";
            case ".iso": return "application/x-iso9660-image";
            default: return "application/octet-stream";
        }
    }

    public SkillResult executeWithFileOutput(String skillName, SkillContext context, Long userId, String outputFilePath) {
        SkillResult result = execute(skillName, context, userId);
        
        if (result.isSuccess() && outputFilePath != null) {
            String base64Content = convertFileToBase64(outputFilePath);
            if (base64Content != null) {
                Map<String, Object> fileResult = new HashMap<>();
                fileResult.put("type", "file");
                fileResult.put("fileType", outputFilePath.toLowerCase().endsWith(".pdf") ? "pdf" : "other");
                fileResult.put("fileName", new File(outputFilePath).getName());
                fileResult.put("content", base64Content);
                fileResult.put("fileSize", new File(outputFilePath).length());
                
                result.setData(fileResult);
                result.getMetadata().put("fileGenerated", true);
                result.getMetadata().put("filePath", outputFilePath);
            }
        }
        
        return result;
    }

    @Data
    private static class ToolCallResult {
        private String toolName;
        private boolean success;
        private Object result;
        private String error;
    }
}
